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
import com.ahu_plus.data.repository.mail.XmlJs6Converter
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
     * 网易 Sirius 会话 sid(握手时从 entry/door 的 meta refresh URL 提取)。
     * js6/s 业务接口必须带此参数;仅在内存缓存,为空时强制重新握手。
     */
    @Volatile
    private var cachedSid: String? = null

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
                codeInt(json.get("code")) ?: -1,
                json.get("message")?.safeStr() ?: "响应缺少 data",
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
                codeInt(json.get("code")) ?: -1,
                json.get("desc")?.safeStr() ?: "响应缺少 result",
            )
            val data = result.getAsJsonObject("data") ?: throw MailApiException(
                codeInt(json.get("code")) ?: -1,
                "响应缺少 data",
            )
            MailAccountBaseInfo(
                email = data.get("email")?.safeStr() ?: "",
                nickName = data.get("nickName")?.safeStr() ?: "",
                orgName = data.get("orgName")?.safeStr() ?: "",
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
            val varEl = json.get("var")
                ?: throw MailApiException(codeInt(json.get("code")) ?: -1, "响应缺少 var")
            parseFolders(varEl)
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
            val varEl = json.get("var")
                ?: throw MailApiException(codeInt(json.get("code")) ?: -1, "响应缺少 var")
            parseMessageList(varEl, fid)
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
            val varEl = json.get("var")
                ?: throw MailApiException(codeInt(json.get("code")) ?: -1, "响应缺少 var")
            parseMessageDetail(varEl)
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
            val varEl = json.get("var")
                ?: throw MailApiException(codeInt(json.get("code")) ?: -1, "响应缺少 var")
            parseFolderStats(varEl)
        }
    }

    /** 清除所有内存 Cookie(退登时调用)。 */
    fun clearCookies() {
        synchronized(cookieLock) { cookieStore.clear() }
        synchronized(seedLock) { seeded = false }
        cachedSid = null
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

        // Step 1: 把 CASTGC 同步到 wvpn 网关(cookie 中转桥)。
        // 2026-07-31 实测关键:反代 CAS 按 wvpn 会话查 TGT,不经 bridge 同步会卡在登录页。
        syncCastgcToWvpn(generation)
        Log.d(TAG, "handshake: CASTGC 已同步到 wvpn 网关")

        // Step 2: 访问 tp_up/view 建立 wvpn ticket + tp_up 会话,再调用 generateSsoUrl
        val ssourl = ensureWvpnLoginAndGenerateSsoUrl(generation)
        Log.d(TAG, "handshake: ssourl 已获取,length=${ssourl.length}")

        // Step 3-8: 跟 302 跳转链(Entry → /login → CAS → token → entry/door),
        // 提取 sid 并跟随 /redirect 设置 Coremail cookie
        val doorParams = followSsoRedirectChain(ssourl)
        Log.d(TAG, "handshake: door params sid=${if (doorParams.sid != null) "present" else "null"}")
        doorParams.sid?.let { cachedSid = it }

        // Step 9: cookie 中转桥
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
     * 通过 wengine-vpn/cookie 中转桥把 one.ahu.edu.cn 的 CASTGC 同步到 wvpn 网关。
     * 网关替客户端持有内部域 cookie,反代 CAS 才能识别 TGT。
     */
    private suspend fun syncCastgcToWvpn(generation: Long) {
        val castgc = casAuthRepository.cookieStore["one.ahu.edu.cn"]
            ?.firstOrNull { it.name == "CASTGC" }
            ?.value
            ?: run {
                Log.w(TAG, "syncCastgc: 未找到 CASTGC,跳过同步")
                return
            }
        val setUrl = "https://${MailApi.HOST}/wengine-vpn/cookie" +
            "?method=set&host=one.ahu.edu.cn&scheme=https&path=/&ck_data=CASTGC=$castgc"
        val request = Request.Builder()
            .url(setUrl)
            .header("User-Agent", MailApi.USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || !body.contains("success")) {
                throw MailHandshakeFailedException(1, response.code, "CASTGC 同步失败: $body")
            }
        }
    }

    /**
     * 确保 wvpn ticket + tp_up 会话就绪,再调用 generateSsoUrl。
     *
     * 2026-07-31 实测:generateSsoUrl 404 的直接原因是 tp_up 会话未建立。
     * 总是先走 tp_up/view?m=up 链(幂等,已登录直接通过),同时建立 wvpn ticket。
     */
    private suspend fun ensureWvpnLoginAndGenerateSsoUrl(generation: Long): String {
        // 总是先走 tp_up/view 链:302 链自动完成 wvpn 登录,并建立 tp_up 会话
        performWvpnLogin(generation)
        // 从 SessionManager 恢复持久化的 wengine_vpn_ticket(可能复用旧 ticket)
        seedPersistedCookiesInternal()

        // 调用 generateSsoUrl(需要 tp_up 已登录会话)
        val request = Request.Builder()
            .url(MailApi.generateSsoUrlUrl())
            .header("User-Agent", MailApi.USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("X-Requested-With", "XMLHttpRequest")
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
            val ssourl = json.get("ssourl")?.safeStr()
                ?: throw MailHandshakeFailedException(1, it.code, "ssourl 为空")
            ssourl
        }
    }

    /**
     * wvpn 登录子链 + tp_up 会话建立(幂等,每次握手都执行)。
     *
     * 流程:GET tp_up/view?m=up → 302 /login → 302 /cas/login?service=...
     *      → CAS 检查 TGT(bridge 同步后可见)→ 302 /login?ticket=ST-
     *      → 302 /wengine-vpn-token-login?token=... → 302 /token-login?token=...
     *      → 302 原始 URL + Set wengine_vpn_ticketwvpn_ahu_edu_cn cookie
     *
     * 触发点必须是 tp_up/view:generateSsoUrl 需要 tp_up 会话,且该链同时建立 wvpn ticket。
     */
    private suspend fun performWvpnLogin(generation: Long) {
        val triggerUrl = "https://${MailApi.HOST}/https/${MailApi.HEX_ONE}/tp_up/view?m=up"
        Log.d(TAG, "wvpnLogin: trigger=$triggerUrl")

        // 跟随完整 302 链(最多 8 跳),直到 200
        var url = triggerUrl
        for (step in 1..8) {
            val next = followRedirect(url, step = step, expectedHost = MailApi.HOST)
            if (next == url) break // 200,链结束
            url = next
            Log.d(TAG, "wvpnLogin[$step]: → ${url.take(70)}")
        }
        Log.d(TAG, "wvpnLogin: 链结束,url=${url.take(70)}")
    }

    /**
     * 跟随 SSO 跳转链:ssourl → wvpn 反代 → /entry/door → meta refresh → /redirect。
     *
     * 2026-07-31 实测链:
     *   wvpn/https/{entryhz hex}/domain/oa/Entry?domain&account_name&time&enc
     *   → 302 /login → 302 cas/login → 302 login?ticket=ST- → 302 wengine-vpn-token-login
     *   → 302 /token-login → 302 回 Entry → 302 /entry/door?hl=zh_CN&... → 200(meta refresh)
     *   meta refresh → wvpn/http/{hex_mail}/redirect?l=...&sid=...&tk=...
     *   GET /redirect 会把 Coremail cookie 写入网关(供 cookie 桥拉取)。
     */
    private suspend fun followSsoRedirectChain(ssourl: String): DoorParams {
        // ssourl 是 entryhz.qiye.163.com 外部域名,必须转为 wvpn 反代 URL
        var url = MailApi.wvpnSsoUrl(ssourl)
        Log.d(TAG, "ssoChain: 起点=${url.take(90)}")

        var doorUrl: String? = null
        for (step in 2..12) {
            val next = followRedirect(url, step = step, expectedHost = MailApi.HOST)
            if (next == url) {
                doorUrl = url // 200,链结束
                break
            }
            url = next
            Log.d(TAG, "ssoChain[$step]: → ${url.take(80)}")
            if (url.contains("/entry/door")) {
                doorUrl = url
                break
            }
        }
        doorUrl ?: throw MailHandshakeFailedException(2, 0, "SSO 链未到达 /entry/door")

        // GET /entry/door → 200,解析 meta refresh 提取参数
        val doorResponse = executeGet(doorUrl)
        val params = parseDoorMetaRefresh(doorResponse)
        Log.d(TAG, "ssoChain: door params sid=${params.sid?.take(20)}")

        // 跟随 meta refresh 的 /redirect 链接:此步把 Coremail 等 cookie 写入 wvpn 网关
        var redirectUrl = params.redirectUrl
        for (step in 13..18) {
            val next = followRedirect(redirectUrl, step = step, expectedHost = MailApi.HOST)
            if (next == redirectUrl) break // 200,完成
            redirectUrl = next
        }
        Log.d(TAG, "ssoChain: /redirect 跟随完成")
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
        // cachedSid 为空(如冷启动)也必须重新握手,js6 接口依赖 sid
        if (cached && cachedSid != null && runCatching { validateSessionInternal() }.isSuccess) return
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
                val code = codeInt(json.get("code"))
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
        val url = MailApi.businessGetUrl(path, DEVICE_ID, extra.withSid())
        Log.d(TAG, "GET $path")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MailApi.USER_AGENT)
            .header("Accept", "application/json")
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
        val params = MailApi.commonQuery(DEVICE_ID) + extra.withSid()
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val url = "$base?$query"
        Log.d(TAG, "POST $path")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MailApi.USER_AGENT)
            // js6/s 按 Accept 协商格式:application/json 返回 JSON,其他返回 XML
            .header("Accept", "application/json")
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Referer", MailApi.businessUrl("/static/sirius-web/"))
            .post(bodyJson.toRequestBody("application/json;charset=UTF-8".toMediaType()))
            .build()
        return executeBusinessRequest(request, path)
    }

    /** js6 接口需要 sid 参数;accountInfo 等 JSON 接口不需要(sid 为空时也不附加)。 */
    private fun Map<String, String>.withSid(): Map<String, String> {
        if (this.containsKey("sid")) return this
        val sid = cachedSid ?: return this
        return this + ("sid" to sid)
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
                // js6/s 的 mbox:* 接口返回 XML,转换为 JSON 后统一解析
                if (XmlJs6Converter.isJs6Xml(body)) {
                    val xmlJson = XmlJs6Converter.toJson(body)
                    val xmlCode = xmlJson.get("code")?.safeStr()
                    if (xmlCode != null && xmlCode != "S_OK") {
                        throw MailApiException(-1, "js6 XML code=$xmlCode")
                    }
                    return@use xmlJson
                }
                // 其他 < 开头(HTML 登录页)视为会话过期
                if (body.trimStart().startsWith("<")) {
                    throw MailSessionExpiredException("邮箱返回 HTML,session 可能已过期")
                }
                val json = JsonParser.parseString(body).asJsonObject
                // code 可能是数字(0/200,格式 A/B)或字符串 "S_OK"(js6 JSON)
                val codeEl = json.get("code")
                val codeNum = codeEl?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.safeInt()
                if (codeEl != null && codeEl.isJsonPrimitive && codeEl.asJsonPrimitive.isString) {
                    val codeStr = codeEl.safeStr()
                    if (codeStr != "S_OK") {
                        throw MailApiException(-1, "js6 code=$codeStr")
                    }
                } else if (codeNum != null && codeNum != 0 && codeNum != 200) {
                    val success = json.get("success")?.safeBool()
                    if (success != true) {
                        val msg = json.get("message")?.safeStr()
                            ?: json.get("desc")?.safeStr()
                            ?: "邮箱请求失败"
                        throw MailApiException(codeNum, msg)
                    }
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


    /** code 可能是数字(0/200)或字符串 "S_OK";asInt 对 "S_OK" 抛异常,统一走安全解析。 */
    private fun codeInt(el: com.google.gson.JsonElement?): Int? =
        el?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.safeInt()

    // JsonNull.getAsX() 会抛 UnsupportedOperationException("JsonNull"),统一走安全访问
    private fun com.google.gson.JsonElement.safeStr(): String? =
        if (isJsonPrimitive) runCatching { asString }.getOrNull() else null

    private fun com.google.gson.JsonElement.safeInt(): Int? =
        if (isJsonPrimitive) runCatching { asInt }.getOrNull() else null

    private fun com.google.gson.JsonElement.safeLong(): Long? =
        if (isJsonPrimitive) runCatching { asLong }.getOrNull() else null

    private fun com.google.gson.JsonElement.safeBool(): Boolean? =
        if (isJsonPrimitive) runCatching { asBoolean }.getOrNull() else null

    private fun parseAccountInfo(data: JsonObject): MailAccountInfo {
        val defaultSenderObj = data.getAsJsonObject("defaultSender")
        val defaultSender = defaultSenderObj?.let {
            MailSender(
                email = it.get("email")?.safeStr() ?: "",
                nickName = it.get("nickName")?.safeStr() ?: "",
                senderName = it.get("senderName")?.safeStr() ?: "",
            )
        }
        return MailAccountInfo(
            qiyeAccountId = data.get("qiyeAccountId")?.safeStr() ?: "",
            accountName = data.get("accountName")?.safeStr() ?: "",
            email = data.get("email")?.safeStr() ?: "",
            nickName = data.get("nickName")?.safeStr() ?: "",
            senderName = data.get("senderName")?.safeStr() ?: "",
            yunxinAccountId = data.get("yunxinAccountId")?.safeStr(),
            yunxinToken = data.get("yunxinToken")?.safeStr(),
            yunxinTokenExpire = data.get("yunxinTokenExpire")?.safeStr()?.toLongOrNull(),
            authMobile = data.get("authMobile")?.safeStr(),
            orgName = data.get("orgName")?.safeStr() ?: "",
            displayEmail = data.get("displayEmail")?.safeStr() ?: "",
            defaultSender = defaultSender,
            domainLogo = data.get("domainLogo")?.safeStr(),
        )
    }

    private fun parseFolders(varEl: com.google.gson.JsonElement): List<MailFolder> {
        val folders = mutableListOf<MailFolder>()
        // getAllFolders XML → var 是数组:{"var": [{"id":1,"name":"收件箱","stats":{...},"flags":{...}}]}
        val items = if (varEl.isJsonArray) varEl.asJsonArray else return emptyList()
        items.forEach { element ->
            if (!element.isJsonObject) return@forEach
            val folderObj = element.asJsonObject
            val stats = folderObj.getAsJsonObject("stats")
            folders.add(
                MailFolder(
                    fid = folderObj.get("id")?.safeInt() ?: folderObj.get("fid")?.safeInt() ?: 0,
                    name = folderObj.get("name")?.safeStr() ?: "",
                    unreadCount = stats?.get("unreadMessageCount")?.safeInt() ?: 0,
                    totalCount = stats?.get("messageCount")?.safeInt() ?: 0,
                    children = null,
                    isSystem = folderObj.getAsJsonObject("flags")?.get("system")?.safeBool() ?: false,
                )
            )
        }
        return folders
    }

    private fun parseMessageList(varEl: com.google.gson.JsonElement, fid: Int): List<MailMessageSummary> {
        val messages = mutableListOf<MailMessageSummary>()
        // listMessages XML → var 是数组:{"var": [{"id":..,"subject":..,"from":"GitHub <..>","receivedDate":"2026-07-28 08:16:46",...}]}
        val items = if (varEl.isJsonArray) varEl.asJsonArray else return emptyList()
        items.forEach { element ->
            if (!element.isJsonObject) return@forEach
            val msg = element.asJsonObject
            val flags = msg.getAsJsonObject("flags")
            messages.add(
                MailMessageSummary(
                    id = msg.get("id")?.safeStr() ?: "",
                    subject = msg.get("subject")?.safeStr() ?: "(无主题)",
                    from = parseAddressString(msg.get("from")?.safeStr()),
                    to = msg.get("to")?.safeStr()?.let { parseAddressString(it) }?.let { listOf(it) }
                        ?: emptyList(),
                    cc = null,
                    date = parseJs6Date(msg.get("receivedDate")?.safeStr() ?: msg.get("sentDate")?.safeStr()),
                    size = msg.get("size")?.safeLong() ?: 0L,
                    hasAttachment = msg.get("attachCount")?.safeInt()?.let { it > 0 } ?: false,
                    isRead = flags?.get("read")?.safeBool() ?: flags?.get("flag")?.safeInt()?.let { it and 1 != 0 } ?: false,
                    isStarred = flags?.get("star")?.safeBool() ?: false,
                    isReplied = flags?.get("reply")?.safeBool() ?: false,
                    isForwarded = flags?.get("forward")?.safeBool() ?: false,
                    tid = msg.get("tid")?.safeStr(),
                    fid = fid,
                )
            )
        }
        return messages
    }

    private fun parseMessageDetail(varEl: com.google.gson.JsonElement): MailMessageDetail {
        val msg = if (varEl.isJsonObject) varEl.asJsonObject.getAsJsonObject("message") ?: varEl.asJsonObject
        else return MailMessageDetail(
            "", "", MailAddress("", null), emptyList(), null, null, 0L, "", null,
            emptyList(), emptyMap(), false, false, 0L
        )
        val attachments = msg.getAsJsonArray("attachments")?.mapNotNull { elem ->
            if (!elem.isJsonObject) return@mapNotNull null
            val att = elem.asJsonObject
            MailAttachment(
                id = att.get("id")?.safeStr() ?: "",
                name = att.get("name")?.safeStr() ?: "",
                size = att.get("size")?.safeLong() ?: 0L,
                contentType = att.get("contentType")?.safeStr() ?: "application/octet-stream",
                needAuth = att.get("needAuth")?.safeBool() ?: false,
            )
        }
        val headers = mutableMapOf<String, String>()
        msg.getAsJsonObject("headers")?.entrySet()?.forEach { (k, v) ->
            if (v.isJsonArray) {
                headers[k] = v.asJsonArray.firstOrNull()?.safeStr() ?: ""
            } else {
                headers[k] = v.safeStr() ?: ""
            }
        }
        // readMessage 响应:from/to 是字符串数组;text/html 是对象(content 字段)
        // HTML 邮件体在 html.content,纯文本在 text.content(HTML 邮件的 text 可能为空)
        // 注意:字段可能为 JsonNull(如 "html": null),getAsJsonObject 会抛异常,须先判 isJsonObject
        val fromArr = msg.get("from")?.takeIf { it.isJsonArray }?.asJsonArray
        val toArr = msg.get("to")?.takeIf { it.isJsonArray }?.asJsonArray
        val textObj = msg.get("text")?.takeIf { it.isJsonObject }?.asJsonObject
        val htmlObj = msg.get("html")?.takeIf { it.isJsonObject }?.asJsonObject
        return MailMessageDetail(
            id = msg.get("id")?.safeStr() ?: "",
            subject = msg.get("subject")?.safeStr() ?: "(无主题)",
            from = fromArr?.firstOrNull()?.takeIf { it.isJsonPrimitive }?.let { parseAddressString(it.safeStr()) }
                ?: MailAddress("", null),
            to = toArr?.mapNotNull { it.takeIf { p -> p.isJsonPrimitive }?.safeStr() }
                ?.map { parseAddressString(it) } ?: emptyList(),
            cc = null,
            bcc = null,
            date = parseJs6Date(msg.get("sentDate")?.safeStr() ?: msg.get("receivedDate")?.safeStr()),
            // content 可能为 JsonNull,须先判 isJsonPrimitive(JsonNull.safeStr() 返回 "null")
            htmlBody = htmlObj?.get("content")?.takeIf { it.isJsonPrimitive }?.safeStr() ?: "",
            textBody = textObj?.get("content")?.takeIf { it.isJsonPrimitive }?.safeStr(),
            attachments = attachments,
            headers = headers,
            isRead = msg.getAsJsonObject("flags")?.get("read")?.safeBool() ?: false,
            isStarred = false,
            size = msg.get("size")?.safeLong()
                ?: htmlObj?.get("contentLength")?.safeLong() ?: textObj?.get("contentLength")?.safeLong() ?: 0L,
        )
    }

    private fun parseFolderStats(varEl: com.google.gson.JsonElement): List<MailFolderStat> {
        val stats = mutableListOf<MailFolderStat>()
        val items = if (varEl.isJsonArray) varEl.asJsonArray else return emptyList()
        items.forEach { element ->
            if (!element.isJsonObject) return@forEach
            val statObj = element.asJsonObject
            val s = statObj.getAsJsonObject("stats")
            stats.add(
                MailFolderStat(
                    fid = statObj.get("id")?.safeInt() ?: statObj.get("fid")?.safeInt() ?: 0,
                    name = statObj.get("name")?.safeStr() ?: "",
                    unreadCount = s?.get("unreadMessageCount")?.safeInt() ?: 0,
                    totalCount = s?.get("messageCount")?.safeInt() ?: 0,
                )
            )
        }
        return stats
    }

    /** 解析网易地址字符串("Name <email@x.com>")。 */
    private fun parseAddressString(raw: String?): MailAddress {
        if (raw.isNullOrBlank()) return MailAddress("", null)
        val m = Regex("""(.*?)\s*<([^>]+)>""").find(raw)
        return if (m != null) {
            val name = m.groupValues[1].trim().trim('"')
            MailAddress(m.groupValues[2], name.takeIf { it.isNotBlank() })
        } else {
            MailAddress(raw.trim(), null)
        }
    }

    /** 解析 js6 日期("2026-07-28 08:16:46")→ epoch 毫秒。 */
    private fun parseJs6Date(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
                .parse(raw)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
