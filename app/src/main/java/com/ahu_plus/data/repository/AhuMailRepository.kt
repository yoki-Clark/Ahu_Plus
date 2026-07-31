package com.ahu_plus.data.repository

import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.diagnostic.SafeLog as Log
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.mail.MailAccountBaseInfo
import com.ahu_plus.data.model.mail.MailAccountInfo
import com.ahu_plus.data.model.mail.MailAddress
import com.ahu_plus.data.model.mail.MailAttachment
import com.ahu_plus.data.model.mail.MailFolder
import com.ahu_plus.data.model.mail.MailFolderStat
import com.ahu_plus.data.model.mail.MailMessageDetail
import com.ahu_plus.data.model.mail.MailMessageSummary
import com.ahu_plus.data.model.mail.MailSender
import com.ahu_plus.data.network.SecureHttpClientFactory
import com.ahu_plus.data.network.mail.MailApi
import com.ahu_plus.data.repository.mail.MailApiException
import com.ahu_plus.data.repository.mail.MailAuthException
import com.ahu_plus.data.repository.mail.MailHandshakeFailedException
import com.ahu_plus.data.repository.mail.MailRateLimitedException
import com.ahu_plus.data.repository.mail.MailSessionExpiredException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * 教育邮箱 Repository(Sirius 教育版,通过 WebVPN 反代访问 mail.stu.ahu.edu.cn)。
 *
 * 协议链路(参考 `docs/24-教育邮箱接口接入方案.md`):
 * 1. generateSsoUrl:智慧安大 tp_up 后端返回网易 SSO URL(含 domain/account_name/time/enc 签名)
 * 2. 7 步跳转:CAS 认证 → WebVPN token 中转 → /entry/door 派发 sid + Coremail cookie
 * 3. cookie 中转桥:/wengine-vpn/cookie 把内部域 cookie 暴露给客户端
 * 4. Sirius 业务 API:OkHttp 直调,cookie 自动注入
 *
 * 三层认证:
 * - L1 CAS(复用 [casAuthRepository] 的 TGT,通过组合 CookieJar 共享)
 * - L2 WebVPN ticket(wengine_vpn_ticketwvpn_ahu_edu_cn cookie)
 * - L3 Sirius(Coremail/mCoremail/QIYE_SESS cookie + sid + JWT)
 *
 * 任一层失效按顺序回滚;cookie 持久化到 [EncryptedCredentialStore] 的 MAIL_* key。
 */
class AhuMailRepository(
    private val sessionManager: SessionManager,
    private val casAuthRepository: CasAuthRepository,
) {
    companion object {
        private const val TAG = "AhuMailRepo"
        /** 握手链最大重试次数(避免 CAS TGT 与 WebVPN ticket 双重失效导致死循环)。 */
        private const val MAX_HANDSHAKE_RETRIES = 2
        /** 设备 ID(首版固定,后续改为 DataStore UUID)。 */
        private const val DEVICE_ID = "174796e776c5b2859c980f4f907e3ca0"
    }

    private val gson = GsonProvider.instance

    /**
     * 私有 cookie 存储(按 host 分桶),仅存放 wvpn.ahu.edu.cn 与 mail.stu.ahu.edu.cn 域的 cookie。
     * cas.ahu.edu.cn 域的 cookie(TGT 等)通过 [casAuthCookieJar] 复用,不在此存储。
     */
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val cookieLock = Any()

    /** CAS 的 CookieJar(复用 TGT,避免重复登录)。 */
    private val casAuthCookieJar: CookieJar = casAuthRepository.getCookieJar()

    /**
     * 组合 CookieJar:
     * - cas.ahu.edu.cn host → 走 [casAuthCookieJar](复用 TGT)
     * - 其他 host(wvpn.ahu.edu.cn / mail.stu.ahu.edu.cn)→ 走私有 [cookieStore]
     *
     * 这样握手第 ③ 步 GET /cas/login 时会自动带 CAS TGT,无需重新登录。
     */
    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            seedPersistedCookies()
            val host = url.host.trim().lowercase()
            // CAS 域直接走 casAuthRepository 的 CookieJar
            if (host == "cas.ahu.edu.cn" || host.endsWith(".cas.ahu.edu.cn")) {
                return casAuthCookieJar.loadForRequest(url)
            }
            // 其他域(wvpn / mail)走私有存储
            return synchronized(cookieLock) {
                // 同时注入从 SessionManager 持久化恢复的 cookie
                seedPersistedCookiesInternal()
                cookieStore[host]?.toList().orEmpty()
            }
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host.trim().lowercase()
            // CAS 域的 cookie 交给 casAuthRepository 处理(避免污染)
            if (host == "cas.ahu.edu.cn" || host.endsWith(".cas.ahu.edu.cn")) {
                casAuthCookieJar.saveFromResponse(url, cookies)
                return
            }
            synchronized(cookieLock) {
                val hostCookies = cookieStore.getOrPut(host) { mutableListOf() }
                for (cookie in cookies) {
                    hostCookies.removeAll { it.name == cookie.name }
                    hostCookies.add(cookie)
                }
            }
        }
    }

    /**
     * 专用于教育邮箱的 OkHttp 客户端:
     * - SystemDefault TLS(wvpn.ahu.edu.cn 是公网合法证书,无需降级)
     * - followRedirects=false:握手链需手动处理 302 提取 Location/Set-Cookie
     * - 较长读取超时(15s):邮件详情可能较大
     */
    private val client = SecureHttpClientFactory.create(
        cookieJar = cookieJar,
        tlsPolicy = com.ahu_plus.data.network.TlsPolicy.SystemDefault,
        followRedirects = false,
        connectTimeoutSec = 10,
        readTimeoutSec = 20,
    )

    // ══════════════════════════════════════════════════════
    // 公开 API
    // ══════════════════════════════════════════════════════

    /**
     * 自动登录入口(由 AutoLoginViewModel 在 adwmh 成功后触发,失败静默)。
     * 从 SessionManager 恢复持久化 cookie,尝试 validateSession,失败则走握手。
     */
    suspend fun autoLogin(
        generation: Long = sessionManager.currentAccountGeneration(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. 从 SessionManager 恢复持久化的 Sirius cookie
            seedPersistedCookiesInternal()
            // 2. 试探性验证 session(失败会 throw,这里 catch 后走握手)
            val validateOk = runCatching { validateSessionInternal() }.isSuccess
            if (validateOk) {
                Log.i(TAG, "autoLogin: session 有效,无需握手")
                return@runCatching Unit
            }
            // 3. session 失效,走握手链
            Log.i(TAG, "autoLogin: session 失效,开始握手")
            handshakeAndSeedCookies(generation)
        }
    }

    /** 是否已配置 Sirius session(含持久化的旧 session)。 */
    fun hasSession(): Boolean {
        return !sessionManager.getMailCoremail().isNullOrBlank()
    }

    /** 确保会话有效(业务 API 调用前调用)。 */
    suspend fun ensureSession(generation: Long = sessionManager.currentAccountGeneration()): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                seedPersistedCookiesInternal()
                val validateOk = runCatching { validateSessionInternal() }.isSuccess
                if (validateOk) return@runCatching Unit
                handshakeAndSeedCookies(generation)
            }
        }

    /** 校验当前 session 是否有效(调用 /cowork/api/biz/enter/accountInfo)。 */
    suspend fun validateSession(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { validateSessionInternal() }
    }

    /** 获取账户完整信息(/cowork/api/biz/enter/accountInfo)。 */
    suspend fun getAccountInfo(): Result<MailAccountInfo> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionInternal()
            val json = getBusinessJson(
                "/cowork/api/biz/enter/accountInfo",
                extra = mapOf("sid" to "", "needUnitNamePath" to "false"),
            )
            val data = json.getAsJsonObject("data") ?: throw MailApiException(
                json.get("code")?.asInt ?: -1,
                json.get("message")?.asString ?: "响应缺少 data",
            )
            parseAccountInfo(data)
        }
    }

    /** 获取账户基础信息(/commonweb/account/getAccountBaseInfo)。 */
    suspend fun getAccountBaseInfo(): Result<MailAccountBaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionInternal()
            val json = getBusinessJson(
                "/commonweb/account/getAccountBaseInfo",
                extra = mapOf(
                    "domain" to MailApi.MAIL_DOMAIN,
                    "account_name" to (sessionManager.getUsername() ?: ""),
                    "output" to "json",
                ),
            )
            val result = json.getAsJsonObject("result") ?: throw MailApiException(
                json.get("code")?.asInt ?: -1,
                json.get("desc")?.asString ?: "响应缺少 result",
            )
            val data = result.getAsJsonObject("data") ?: throw MailApiException(
                json.get("code")?.asInt ?: -1,
                "响应缺少 data",
            )
            MailAccountBaseInfo(
                email = data.get("email")?.asString ?: "",
                nickName = data.get("nickName")?.asString ?: "",
                orgName = data.get("orgName")?.asString ?: "",
            )
        }
    }

    /** 获取文件夹列表(/js6/s?func=mbox:getAllFolders)。 */
    suspend fun getFolders(): Result<List<MailFolder>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionInternal()
            val json = postBusinessJson(
                "/js6/s",
                extra = mapOf("func" to "mbox:getAllFolders"),
                bodyJson = """{"order":"custom_virtual"}""",
            )
            val varObj = json.getAsJsonObject("var")
                ?: throw MailApiException(json.get("code")?.asInt ?: -1, "响应缺少 var")
            parseFolders(varObj)
        }
    }

    /**
     * 获取邮件列表(/js6/s?func=mbox:listMessages)。
     *
     * @param fid 文件夹 ID(1=收件箱)
     * @param limit 每页数量(默认 30)
     * @param start 起始偏移(默认 0)
     */
    suspend fun listMessages(
        fid: Int = 1,
        limit: Int = 30,
        start: Int = 0,
    ): Result<List<MailMessageSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionInternal()
            val body = buildString {
                append("{")
                append("\"limit\":$limit,\"start\":$start,")
                append("\"summaryWindowSize\":$limit,")
                append("\"returnTotal\":true,\"returnTid\":true,\"returnTag\":true,")
                append("\"returnAttachments\":true,")
                append("\"order\":\"date\",\"desc\":true,")
                append("\"skipLockedFolders\":false,")
                append("\"filter\":{},")
                append("\"topFlag\":\"top\",")
                append("\"fid\":$fid")
                append("}")
            }
            val json = postBusinessJson(
                "/js6/s",
                extra = mapOf("func" to "mbox:listMessages"),
                bodyJson = body,
            )
            val varObj = json.getAsJsonObject("var")
                ?: throw MailApiException(json.get("code")?.asInt ?: -1, "响应缺少 var")
            parseMessageList(varObj, fid)
        }
    }

    /**
     * 获取邮件详情(/js6/s?func=mbox:readMessage)。
     *
     * @param id 邮件 ID
     * @param markRead 是否同时标记为已读
     */
    suspend fun readMessage(
        id: String,
        markRead: Boolean = true,
    ): Result<MailMessageDetail> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionInternal()
            val body = buildString {
                append("{")
                append("\"id\":\"${id}\",")
                append("\"level\":32,")
                append("\"mode\":\"html\",")
                append("\"returnHeaders\":{\"Resent-From\":\"A\",\"Sender\":\"A\"},")
                append("\"markRead\":$markRead")
                append("}")
            }
            val json = postBusinessJson(
                "/js6/s",
                extra = mapOf("func" to "mbox:readMessage"),
                bodyJson = body,
            )
            val varObj = json.getAsJsonObject("var")
                ?: throw MailApiException(json.get("code")?.asInt ?: -1, "响应缺少 var")
            parseMessageDetail(varObj)
        }
    }

    /**
     * 标记邮件已读/未读(/js6/s?func=mbox:updateMessageInfos)。
     *
     * @param ids 邮件 ID 列表
     * @param isRead true=已读,false=未读
     */
    suspend fun markRead(ids: List<String>, isRead: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionInternal()
            val idsJson = ids.joinToString(",") { "\"$it\"" }
            val body = """{"ids":[$idsJson],"flags":{"flag":${if (isRead) 1 else 0}}}"""
            postBusinessJson(
                "/js6/s",
                extra = mapOf("func" to "mbox:updateMessageInfos"),
                bodyJson = body,
            )
            Unit
        }
    }

    /** 获取邮件统计(/js6/s?func=mbox:statMessages)。 */
    suspend fun statMessages(): Result<List<MailFolderStat>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionInternal()
            val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT)
                .format(java.util.Date())
            val body = """{"filter":{"defer":":$today"}}"""
            val json = postBusinessJson(
                "/js6/s",
                extra = mapOf("func" to "mbox:statMessages"),
                bodyJson = body,
            )
            val varObj = json.getAsJsonObject("var")
                ?: throw MailApiException(json.get("code")?.asInt ?: -1, "响应缺少 var")
            parseFolderStats(varObj)
        }
    }

    /** 清除所有内存 Cookie(退登时调用)。 */
    fun clearCookies() {
        synchronized(cookieLock) { cookieStore.clear() }
    }

    // ══════════════════════════════════════════════════════
    // 内部:握手链
    // ══════════════════════════════════════════════════════

    /**
     * 完整握手链:generateSsoUrl → 7 步 302 跳转 → cookie 中转桥。
     * 成功后所有 cookie 持久化到 SessionManager。
     */
    internal suspend fun handshakeAndSeedCookies(
        generation: Long = sessionManager.currentAccountGeneration(),
    ) {
        // Step 0: 确保 CAS TGT 有效(复用 CasAuthRepository)
        casAuthRepository.ensureValidSession(generation).getOrThrow()
        Log.d(TAG, "handshake: CAS TGT 已就绪")

        // Step 1: 调用 generateSsoUrl(若 wengine_vpn_ticket 未建立,会触发 wvpn 登录子链)
        val ssourl = ensureWvpnLoginAndGenerateSsoUrl(generation)
        Log.d(TAG, "handshake: ssourl=${ssourl.take(80)}...")

        // Step 2-7: 跟 302 跳转链,提取 sid/Coremail/JWT
        val doorParams = followSsoRedirectChain(ssourl)
        Log.d(TAG, "handshake: door params sid=${doorParams.sid?.take(20)}")

        // Step 8: cookie 中转桥
        val mailCookies = fetchCookieBridge()
        Log.d(TAG, "handshake: mail cookies keys=${mailCookies.keys}")

        // 持久化
        currentCoroutineContext().ensureActive()
        sessionManager.saveMailSession(
            wvpnTicket = getCookieValue(MailApi.HOST, "wengine_vpn_ticketwvpn_ahu_edu_cn"),
            coremail = mailCookies["Coremail"],
            mCoremail = mailCookies["mCoremail"],
            qiyeSess = mailCookies["QIYE_SESS"],
            generation = generation,
        )
        // 把 Coremail/mCoremail 注入 mail.stu.ahu.edu.cn host 的 cookie store
        seedMailCookiesIntoStore(mailCookies)
        Log.i(TAG, "handshake: 完成,session 已持久化")
    }

    /**
     * 调用 generateSsoUrl;若 wengine_vpn_ticket 不存在,先走 wvpn 登录子链。
     */
    private suspend fun ensureWvpnLoginAndGenerateSsoUrl(generation: Long): String {
        // 检查 wengine_vpn_ticket 是否已建立
        val hasWvpnTicket = synchronized(cookieLock) {
            cookieStore[MailApi.HOST]?.any { it.name == "wengine_vpn_ticketwvpn_ahu_edu_cn" } == true
        } || !sessionManager.getMailWvpnTicket().isNullOrBlank()

        if (!hasWvpnTicket) {
            Log.d(TAG, "ensureWvpn: ticket 缺失,触发 wvpn 登录子链")
            performWvpnLogin(generation)
        } else {
            // 从 SessionManager 恢复 wengine_vpn_ticket 到 cookie store
            seedPersistedCookiesInternal()
        }

        // 调用 generateSsoUrl
        val request = Request.Builder()
            .url(MailApi.generateSsoUrlUrl())
            .header("User-Agent", MailApi.USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Referer", "https://${MailApi.HOST}/https/${MailApi.HEX_ONE}/tp_up/view?m=up")
            .post("{}".toRequestBody("application/json;charset=UTF-8".toMediaType()))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: SocketTimeoutException) {
            throw MailHandshakeFailedException(1, 0, "generateSsoUrl 超时: ${e.message}")
        }
        return response.use {
            if (!it.isSuccessful) {
                throw MailHandshakeFailedException(1, it.code, "generateSsoUrl HTTP ${it.code}")
            }
            val body = it.body?.string().orEmpty()
            val json = JsonParser.parseString(body).asJsonObject
            val ssourl = json.get("ssourl")?.asString
                ?: throw MailHandshakeFailedException(1, it.code, "ssourl 为空")
            ssourl
        }
    }

    /**
     * wvpn 登录子链(当 wengine_vpn_ticket 不存在时触发)。
     *
     * 流程:GET 任意 wvpn 保护 URL → 302 /login → 302 /cas/login?service=...
     *      → CAS 检查 TGT(已通过 casAuthRepository.ensureValidSession 建立)→ 302 /login?ticket=ST-
     *      → 302 /wengine-vpn-token-login?token=... → 302 /token-login?token=...
     *      → 302 原始 URL + Set wengine_vpn_ticketwvpn_ahu_edu_cn cookie
     */
    private suspend fun performWvpnLogin(generation: Long) {
        // 触发点:GET /domain/oa/Entry(不带参数,仅用于触发登录)
        val triggerUrl = MailApi.ssoEntryUrl()
        Log.d(TAG, "wvpnLogin: trigger=$triggerUrl")

        // Step A: GET trigger → 302 /login
        val loginUrl = followRedirect(triggerUrl, step = 1, expectedHost = MailApi.HOST)
        Log.d(TAG, "wvpnLogin: → /login")

        // Step B: GET /login → 302 /cas/login?service=...
        val casLoginUrl = followRedirect(loginUrl, step = 2, expectedHost = MailApi.HOST)
        Log.d(TAG, "wvpnLogin: → /cas/login?service=...")

        // Step C: GET /cas/login?service=... → 302 /login?cas_login=true&ticket=ST-
        // CAS 检查 TGT(已通过 casAuthRepository 共享),有 TGT 直接给 ticket
        val ticketedUrl = followRedirect(casLoginUrl, step = 3, expectedHost = MailApi.HOST)
        if (!ticketedUrl.contains("ticket=")) {
            throw MailHandshakeFailedException(3, 0, "CAS 未给 ticket(TGT 可能已失效)")
        }
        Log.d(TAG, "wvpnLogin: → /login?ticket=ST-...")

        // Step D: GET /login?cas_login=true&ticket=ST-... → 302 /wengine-vpn-token-login?token=...
        val tokenLoginUrl = followRedirect(ticketedUrl, step = 4, expectedHost = MailApi.HOST)
        if (!tokenLoginUrl.contains("token=")) {
            throw MailHandshakeFailedException(4, 0, "未拿到 WebVPN token")
        }
        Log.d(TAG, "wvpnLogin: → /wengine-vpn-token-login?token=...")

        // Step E: GET /wengine-vpn-token-login?token=... → 302 /token-login?token=...
        val tokenLoginPath = followRedirect(tokenLoginUrl, step = 5, expectedHost = MailApi.HOST)
        Log.d(TAG, "wvpnLogin: → /token-login?token=...")

        // Step F: GET /token-login?token=... → 302 原始 URL + Set wengine_vpn_ticketwvpn_ahu_edu_cn
        // 注意:此步会写入 wengine_vpn_ticketwvpn_ahu_edu_cn cookie(由 CookieJar.saveFromResponse 自动捕获)
        followRedirect(tokenLoginPath, step = 6, expectedHost = MailApi.HOST)
        Log.d(TAG, "wvpnLogin: wengine_vpn_ticket 已建立")
    }

    /**
     * 跟随 SSO 跳转链(从 ssourl 到 /entry/door 响应)。
     * 提取 sid/c/mc/s/tk 等 door 参数。
     */
    private suspend fun followSsoRedirectChain(ssourl: String): DoorParams {
        // Step 2: GET ssourl → 302 /login(若无 wvpn ticket)或 302 /entry/door(若有)
        val firstRedirect = followRedirect(ssourl, step = 2, expectedHost = MailApi.HOST)

        // 如果直接跳到 /entry/door,说明 wvpn ticket 已建立,继续
        val doorUrl = if (firstRedirect.contains("/entry/door")) {
            firstRedirect
        } else {
            // 否则需要走 wvpn 登录子链(wengine_vpn_ticket 可能在 SSO 链中失效)
            Log.w(TAG, "ssoChain: ssourl 未直接跳到 /entry/door,触发 wvpn 重新登录")
            performWvpnLogin(sessionManager.currentAccountGeneration())
            // 重放 ssourl
            val retryRedirect = followRedirect(ssourl, step = 2, expectedHost = MailApi.HOST)
            if (!retryRedirect.contains("/entry/door")) {
                throw MailHandshakeFailedException(2, 0, "重放 ssourl 仍未跳到 /entry/door")
            }
            retryRedirect
        }
        Log.d(TAG, "ssoChain: → /entry/door")

        // Step 7: GET /entry/door → 200,解析 meta refresh 提取参数
        val doorResponse = executeGet(doorUrl)
        val params = parseDoorMetaRefresh(doorResponse)
        Log.d(TAG, "ssoChain: door params sid=${params.sid?.take(20)}")
        return params
    }

    /** 执行一次 GET,跟随 302(followRedirects=false 时手动)。 */
    private suspend fun followRedirect(
        url: String,
        step: Int,
        expectedHost: String,
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MailApi.USER_AGENT)
            .header("Accept", "text/html,application/json,*/*")
            .get()
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: SocketTimeoutException) {
            throw MailHandshakeFailedException(step, 0, "请求超时: ${e.message}")
        }
        return response.use {
            val code = it.code
            when {
                code in 300..399 -> {
                    val location = it.header("Location")
                        ?: throw MailHandshakeFailedException(step, code, "302 缺少 Location")
                    resolveRedirectUrl(url, location)
                }
                code == 200 -> {
                    // 200 响应(如 /entry/door 的 meta refresh 页),返回 URL 本身
                    url
                }
                code == 401 || code == 403 -> {
                    throw MailHandshakeFailedException(step, code, "认证失败")
                }
                code == 429 -> {
                    throw MailRateLimitedException("握手第 $step 步被限流")
                }
                else -> {
                    throw MailHandshakeFailedException(step, code, "意外 HTTP $code")
                }
            }
        }
    }

    /** 执行 GET 拿响应体(用于 /entry/door 的 meta refresh 页)。 */
    private suspend fun executeGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MailApi.USER_AGENT)
            .header("Accept", "text/html,application/json,*/*")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw MailHandshakeFailedException(7, response.code, "/entry/door HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }

    /**
     * 把相对 Location 解析为绝对 URL。
     * WebVPN 的 302 Location 可能是相对路径(如 /token-login?token=...)。
     */
    private fun resolveRedirectUrl(baseUrl: String, location: String): String {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location
        }
        val base = baseUrl.toHttpUrlOrNull()
            ?: return "https://${MailApi.HOST}$location"
        return base.resolve(location)?.toString()
            ?: "https://${MailApi.HOST}$location"
    }

    /**
     * 解析 /entry/door 响应里的 meta refresh,提取 sid/c/mc/s/tk 等。
     *
     * meta refresh 格式:
     *   <meta http-equiv="REFRESH" content="0;url=https://wvpn.ahu.edu.cn/http/{hex_mail}/redirect?l=...&c=...&mc=...&s=...&sid=...&tk=...">
     */
    private fun parseDoorMetaRefresh(html: String): DoorParams {
        val metaRegex = Regex(
            """<meta[^>]*http-equiv=["']REFRESH["'][^>]*content=["']\d+;url=([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
        val match = metaRegex.find(html)
            ?: throw MailHandshakeFailedException(7, 200, "/entry/door 缺少 meta refresh")
        val redirectUrl = match.groupValues[1]
            .replace("&amp;", "&")
        // 解析 query 参数
        val httpUrl = redirectUrl.toHttpUrlOrNull()
            ?: throw MailHandshakeFailedException(7, 200, "meta refresh URL 解析失败")
        return DoorParams(
            sid = httpUrl.queryParameter("sid"),
            coremail = httpUrl.queryParameter("c"),
            mCoremail = httpUrl.queryParameter("mc"),
            qiyeSess = httpUrl.queryParameter("s"),
            jwt = httpUrl.queryParameter("tk"),
            redirectUrl = redirectUrl,
        )
    }

    /** /entry/door 响应里 meta refresh 提取的参数。 */
    internal data class DoorParams(
        val sid: String?,
        val coremail: String?,
        val mCoremail: String?,
        val qiyeSess: String?,
        val jwt: String?,
        val redirectUrl: String,
    )

    /**
     * 调用 cookie 中转桥 /wengine-vpn/cookie,拉取 mail.stu.ahu.edu.cn 域的 cookie。
     * 返回 Map<cookieName, cookieValue>。
     */
    private suspend fun fetchCookieBridge(): Map<String, String> {
        val bridgeUrl = MailApi.cookieBridgeUrl(
            host = MailApi.MAIL_HOST,
            scheme = "http",
            path = "/static/sirius-web/jump/index.html",
            timestamp = System.currentTimeMillis(),
        )
        val request = Request.Builder()
            .url(bridgeUrl)
            .header("User-Agent", MailApi.USER_AGENT)
            .header("Accept", "*/*")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw MailHandshakeFailedException(8, response.code, "cookie 桥 HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parseCookieBridgeResponse(body)
            }
        } catch (e: SocketTimeoutException) {
            throw MailHandshakeFailedException(8, 0, "cookie 桥超时: ${e.message}")
        }
    }

    /** 解析 cookie 桥 text/plain 响应,格式:`name1=val1; name2=val2; ...`。 */
    private fun parseCookieBridgeResponse(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        body.split(";").forEach { part ->
            val trimmed = part.trim()
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                val name = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                if (name.isNotEmpty() && name.lowercase() !in setOf(
                        "path", "domain", "expires", "max-age",
                        "secure", "httponly", "samesite",
                    )
                ) {
                    result[name] = value
                }
            }
        }
        return result
    }

    // ══════════════════════════════════════════════════════
    // 内部:cookie 持久化与恢复
    // ══════════════════════════════════════════════════════

    /** 从 SessionManager 恢复持久化的 wvpn/sirius cookie 到内存 cookieStore。 */
    private fun seedPersistedCookies() {
        // CookieJar.loadForRequest 调用入口,委托给 internal 版本
    }

    private val seedLock = Any()
    @Volatile private var seeded = false

    private fun seedPersistedCookiesInternal() {
        synchronized(seedLock) {
            if (seeded) return
            // 恢复 wengine_vpn_ticket
            val wvpnTicket = sessionManager.getMailWvpnTicket()
            if (!wvpnTicket.isNullOrBlank()) {
                setCookie(MailApi.HOST, "wengine_vpn_ticketwvpn_ahu_edu_cn", wvpnTicket)
            }
            // 恢复 Coremail/mCoremail/QIYE_SESS(注入到 mail.stu.ahu.edu.cn host)
            val coremail = sessionManager.getMailCoremail()
            if (!coremail.isNullOrBlank()) {
                setCookie(MailApi.MAIL_HOST, "Coremail", coremail)
            }
            val mCoremail = sessionManager.getMailMCoremail()
            if (!mCoremail.isNullOrBlank()) {
                setCookie(MailApi.MAIL_HOST, "mCoremail", mCoremail)
            }
            val qiyeSess = sessionManager.getMailQiyeSess()
            if (!qiyeSess.isNullOrBlank()) {
                setCookie(MailApi.MAIL_HOST, "QIYE_SESS", qiyeSess)
            }
            seeded = true
        }
    }

    /** 把 cookie 桥拉取的 Sirius cookie 注入 mail.stu.ahu.edu.cn host 的 cookie store。 */
    private fun seedMailCookiesIntoStore(cookies: Map<String, String>) {
        cookies["Coremail"]?.let { setCookie(MailApi.MAIL_HOST, "Coremail", it) }
        cookies["mCoremail"]?.let { setCookie(MailApi.MAIL_HOST, "mCoremail", it) }
        cookies["QIYE_SESS"]?.let { setCookie(MailApi.MAIL_HOST, "QIYE_SESS", it) }
        cookies["qiye_uid"]?.let { setCookie(MailApi.MAIL_HOST, "qiye_uid", it) }
        // 重置 seeded 标志,下次请求重新从 SessionManager 恢复(保持一致性)
        synchronized(seedLock) { seeded = true }
    }

    private fun setCookie(host: String, name: String, value: String) {
        synchronized(cookieLock) {
            val hostCookies = cookieStore.getOrPut(host) { mutableListOf() }
            hostCookies.removeAll { it.name == name }
            hostCookies.add(
                Cookie.Builder()
                    .domain(host)
                    .path("/")
                    .name(name)
                    .value(value)
                    .httpOnly()
                    .build()
            )
        }
    }

    private fun getCookieValue(host: String, name: String): String? =
        synchronized(cookieLock) {
            cookieStore[host]?.firstOrNull { it.name == name }?.value
        }

    // ══════════════════════════════════════════════════════
    // 内部:业务 API 请求封装
    // ══════════════════════════════════════════════════════

    private suspend fun ensureSessionInternal() {
        val cached = !sessionManager.getMailCoremail().isNullOrBlank()
        if (cached && runCatching { validateSessionInternal() }.isSuccess) return
        handshakeAndSeedCookies()
    }

    private suspend fun validateSessionInternal() {
        val request = Request.Builder()
            .url(MailApi.businessGetUrl("/cowork/api/biz/enter/accountInfo", DEVICE_ID))
            .header("User-Agent", MailApi.USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", MailApi.businessUrl("/static/sirius-web/"))
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    throw MailSessionExpiredException()
                }
                if (!response.isSuccessful) {
                    throw MailAuthException("邮箱会话校验失败 HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val json = JsonParser.parseString(body).asJsonObject
                val code = json.get("code")?.asInt
                if (code != null && code != 0 && code != 200) {
                    throw MailSessionExpiredException("邮箱 session 校验业务 code=$code")
                }
            }
        } catch (e: SocketTimeoutException) {
            throw MailAuthException("邮箱连接超时")
        }
    }

    /** GET 业务 API。 */
    private suspend fun getBusinessJson(
        path: String,
        extra: Map<String, String> = emptyMap(),
    ): JsonObject {
        val url = MailApi.businessGetUrl(path, DEVICE_ID, extra)
        Log.d(TAG, "GET $path")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MailApi.USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", MailApi.businessUrl("/static/sirius-web/"))
            .get()
            .build()
        return executeBusinessRequest(request, path)
    }

    /** POST 业务 API(JSON body)。 */
    private suspend fun postBusinessJson(
        path: String,
        extra: Map<String, String> = emptyMap(),
        bodyJson: String,
    ): JsonObject {
        val base = MailApi.businessUrl(path)
        val params = MailApi.commonQuery(DEVICE_ID) + extra
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val url = "$base?$query"
        Log.d(TAG, "POST $path")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MailApi.USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Referer", MailApi.businessUrl("/static/sirius-web/"))
            .post(bodyJson.toRequestBody("application/json;charset=UTF-8".toMediaType()))
            .build()
        return executeBusinessRequest(request, path)
    }

    private fun executeBusinessRequest(request: Request, path: String): JsonObject {
        try {
            return client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(TAG, "$path → ${response.code} (len=${body.length})")
                if (response.code == 401 || response.code == 403) {
                    throw MailSessionExpiredException()
                }
                if (response.code == 429) {
                    throw MailRateLimitedException("邮箱 API 被限流")
                }
                if (!response.isSuccessful) {
                    throw MailAuthException("邮箱 HTTP ${response.code}")
                }
                if (body.trimStart().startsWith("<")) {
                    throw MailSessionExpiredException("邮箱返回 HTML,session 可能已过期")
                }
                val json = JsonParser.parseString(body).asJsonObject
                // 格式 A: success/code; 格式 B: code/result
                val code = json.get("code")?.asInt
                val success = json.get("success")?.asBoolean
                if (code != null && code != 0 && code != 200 && success != true) {
                    val msg = json.get("message")?.asString
                        ?: json.get("desc")?.asString
                        ?: "邮箱请求失败"
                    throw MailApiException(code, msg)
                }
                json
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "$path → TIMEOUT")
            throw MailAuthException("邮箱连接超时")
        } catch (e: MailAuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$path → ${e.javaClass.simpleName}: ${e.message}")
            throw MailAuthException("邮箱请求失败: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════
    // 内部:响应解析
    // ══════════════════════════════════════════════════════

    private fun parseAccountInfo(data: JsonObject): MailAccountInfo {
        val defaultSenderObj = data.getAsJsonObject("defaultSender")
        val defaultSender = defaultSenderObj?.let {
            MailSender(
                email = it.get("email")?.asString ?: "",
                nickName = it.get("nickName")?.asString ?: "",
                senderName = it.get("senderName")?.asString ?: "",
            )
        }
        return MailAccountInfo(
            qiyeAccountId = data.get("qiyeAccountId")?.asString ?: "",
            accountName = data.get("accountName")?.asString ?: "",
            email = data.get("email")?.asString ?: "",
            nickName = data.get("nickName")?.asString ?: "",
            senderName = data.get("senderName")?.asString ?: "",
            yunxinAccountId = data.get("yunxinAccountId")?.asString,
            yunxinToken = data.get("yunxinToken")?.asString,
            yunxinTokenExpire = data.get("yunxinTokenExpire")?.asString?.toLongOrNull(),
            authMobile = data.get("authMobile")?.asString,
            orgName = data.get("orgName")?.asString ?: "",
            displayEmail = data.get("displayEmail")?.asString ?: "",
            defaultSender = defaultSender,
            domainLogo = data.get("domainLogo")?.asString,
        )
    }

    private fun parseFolders(varObj: JsonObject): List<MailFolder> {
        val folders = mutableListOf<MailFolder>()
        varObj.entrySet().forEach { (key, value) ->
            if (value.isJsonObject) {
                val folderObj = value.asJsonObject
                folders.add(
                    MailFolder(
                        fid = folderObj.get("fid")?.asInt ?: key.toIntOrNull() ?: 0,
                        name = folderObj.get("name")?.asString ?: key,
                        unreadCount = folderObj.get("stats")?.asJsonObject
                            ?.get("unread")?.asInt ?: 0,
                        totalCount = folderObj.get("stats")?.asJsonObject
                            ?.get("total")?.asInt ?: 0,
                        children = null,
                        isSystem = folderObj.get("system")?.asBoolean ?: false,
                    )
                )
            }
        }
        return folders
    }

    private fun parseMessageList(varObj: JsonObject, fid: Int): List<MailMessageSummary> {
        val messages = mutableListOf<MailMessageSummary>()
        // mbox:listMessages 响应格式:{"var": {"...": [...], "list": [...]}}
        val list = varObj.getAsJsonArray("list") ?: varObj.getAsJsonArray("messages")
            ?: return emptyList()
        list.forEach { element ->
            if (!element.isJsonObject) return@forEach
            val msg = element.asJsonObject
            messages.add(
                MailMessageSummary(
                    id = msg.get("id")?.asString ?: "",
                    subject = msg.get("subject")?.asString ?: "(无主题)",
                    from = parseAddress(msg.get("from")),
                    to = parseAddressList(msg.get("to")),
                    cc = msg.get("cc")?.takeIf { it.isJsonArray }?.let { parseAddressList(it) },
                    date = msg.get("date")?.asString?.toLongOrNull() ?: 0L,
                    size = msg.get("size")?.asLong ?: 0L,
                    hasAttachment = msg.get("attachCount")?.asInt?.let { it > 0 } ?: false,
                    isRead = msg.get("flags")?.asJsonObject?.get("flag")?.asInt?.let { it and 1 != 0 } ?: false,
                    isStarred = msg.get("flags")?.asJsonObject?.get("flag")?.asInt?.let { it and 4 != 0 } ?: false,
                    isReplied = msg.get("flags")?.asJsonObject?.get("flag")?.asInt?.let { it and 2 != 0 } ?: false,
                    isForwarded = msg.get("flags")?.asJsonObject?.get("flag")?.asInt?.let { it and 8 != 0 } ?: false,
                    tid = msg.get("tid")?.asString,
                    fid = fid,
                )
            )
        }
        return messages
    }

    private fun parseMessageDetail(varObj: JsonObject): MailMessageDetail {
        val msg = varObj.getAsJsonObject("message") ?: varObj
        val attachments = msg.getAsJsonArray("attachments")?.map { elem ->
            val att = elem.asJsonObject
            MailAttachment(
                id = att.get("id")?.asString ?: "",
                name = att.get("name")?.asString ?: "",
                size = att.get("size")?.asLong ?: 0L,
                contentType = att.get("contentType")?.asString ?: "application/octet-stream",
                needAuth = att.get("needAuth")?.asBoolean ?: false,
            )
        }
        val headers = mutableMapOf<String, String>()
        msg.getAsJsonObject("headers")?.entrySet()?.forEach { (k, v) ->
            headers[k] = v.asString
        }
        return MailMessageDetail(
            id = msg.get("id")?.asString ?: "",
            subject = msg.get("subject")?.asString ?: "(无主题)",
            from = parseAddress(msg.get("from")),
            to = parseAddressList(msg.get("to")),
            cc = msg.get("cc")?.takeIf { it.isJsonArray }?.let { parseAddressList(it) },
            bcc = msg.get("bcc")?.takeIf { it.isJsonArray }?.let { parseAddressList(it) },
            date = msg.get("date")?.asString?.toLongOrNull() ?: 0L,
            htmlBody = msg.get("html")?.asString ?: msg.get("body")?.asString ?: "",
            textBody = msg.get("text")?.asString,
            attachments = attachments,
            headers = headers,
            isRead = msg.get("flags")?.asJsonObject?.get("flag")?.asInt?.let { it and 1 != 0 } ?: false,
            isStarred = msg.get("flags")?.asJsonObject?.get("flag")?.asInt?.let { it and 4 != 0 } ?: false,
            size = msg.get("size")?.asLong ?: 0L,
        )
    }

    private fun parseFolderStats(varObj: JsonObject): List<MailFolderStat> {
        val stats = mutableListOf<MailFolderStat>()
        varObj.entrySet().forEach { (fid, value) ->
            if (value.isJsonObject) {
                val statObj = value.asJsonObject
                stats.add(
                    MailFolderStat(
                        fid = fid.toIntOrNull() ?: 0,
                        name = statObj.get("name")?.asString ?: fid,
                        unreadCount = statObj.get("unread")?.asInt ?: 0,
                        totalCount = statObj.get("total")?.asInt ?: 0,
                    )
                )
            }
        }
        return stats
    }

    private fun parseAddress(element: com.google.gson.JsonElement?): MailAddress {
        if (element == null || !element.isJsonObject) return MailAddress("", null)
        val obj = element.asJsonObject
        return MailAddress(
            address = obj.get("address")?.asString ?: obj.get("email")?.asString ?: "",
            name = obj.get("name")?.asString?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseAddressList(element: com.google.gson.JsonElement?): List<MailAddress> {
        if (element == null || !element.isJsonArray) return emptyList()
        return element.asJsonArray.map { parseAddress(it) }
    }
}
