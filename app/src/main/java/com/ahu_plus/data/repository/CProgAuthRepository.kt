package com.ahu_plus.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.JsonParser
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * 大学计算机平台(C 语言在线评测, issuer w2eesweb)认证仓库。
 *
 * 登录闭环(在 App 内完成,唯一人工步骤是填验证码):
 *   GET  /redirect/login          建 session,下发 JSESSIONID
 *   GET  /kaptcha/jpg             取验证码图(绑到 session)
 *   POST /login/get               学号+身份证后6位+验证码 → {userId, jwt1}
 *   POST /login/unified           dfgdfg=jwt1.payload.sub → 主站 JWT
 *
 * ⚠️ 坑:/login/get 返回的 data.dfgdfg 是 userId(uuid),但 /login/unified 提交的
 *    dfgdfg 是 jwt1 payload 的 sub(32 位 hex),两者同名不同值(见 replay_exam.py)。
 *
 * 传输层自动选择:校园网直连内网 IP,不可达时通过 WebVPN + 代理 CAS 建立外网会话。
 * JWT 与目标系统 session 强绑定,切换传输通道后需要重新完成本平台验证码登录。
 */
class CProgAuthRepository(
    private val sessionManager: SessionManager,
) {
    companion object {
        private const val TAG = "CProg"
        const val DEFAULT_BASE_URL = "http://172.17.106.232:8080"
        const val WEBVPN_BASE_URL = "https://wvpn.ahu.edu.cn/http-8080/" +
            "77726476706e69737468656265737421a1a013d2766726012e5ec7fecb07"
        private const val WEBVPN_PORTAL_URL = "https://wvpn.ahu.edu.cn/https/" +
            "77726476706e69737468656265737421fff944d226387d1e7b0c9ce29b5b/tp_up/view?m=up"
        private const val WEBVPN_LOGIN_URL = "https://wvpn.ahu.edu.cn/login"
        private const val WEBVPN_AJAX_MARKER = "vpn-12-o1-172.17.106.232:8080"
        private const val DIRECT_PROBE_TIMEOUT_MS = 2_500
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

        internal fun normalizeBaseUrl(configured: String?): String {
            val normalized = configured?.trim()?.trimEnd('/').orEmpty()
            if (normalized.isBlank()) return DEFAULT_BASE_URL
            return if (isWebVpnUrl(normalized)) DEFAULT_BASE_URL else normalized
        }

        internal fun buildEndpointUrl(
            baseUrl: String,
            path: String,
            query: Map<String, String?> = emptyMap(),
            proxiedAjax: Boolean = false,
        ): HttpUrl = "${baseUrl.trimEnd('/')}$path".toHttpUrl().newBuilder().apply {
            if (proxiedAjax && isWebVpnUrl(baseUrl)) addQueryParameter(WEBVPN_AJAX_MARKER, null)
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

        private fun isWebVpnUrl(value: String): Boolean {
            val url = runCatching { value.toHttpUrl() }.getOrNull() ?: return false
            return url.host == "wvpn.ahu.edu.cn" && url.encodedPath.startsWith("/http-8080/")
        }
    }

    private enum class TransportMode { UNKNOWN, DIRECT, WEBVPN }

    @Volatile private var activeBaseUrl: String = normalizeBaseUrl(sessionManager.getCProgBaseUrl())
    @Volatile private var transportMode: TransportMode = TransportMode.UNKNOWN
    private val transportMutex = Mutex()

    /** 当前已选择的目标系统地址。 */
    val baseUrl: String
        get() = activeBaseUrl

    internal fun endpointUrl(
        path: String,
        query: Map<String, String?> = emptyMap(),
        proxiedAjax: Boolean = false,
    ): String = buildEndpointUrl(baseUrl, path, query, proxiedAjax).toString()

    // ── Cookie(JSESSIONID)存储,按 host 归集 ────────────────────
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = cookieStore.getOrPut(url.host) { mutableListOf() }
            for (c in cookies) {
                list.removeAll { it.name == c.name }
                list.add(c)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore[url.host]?.toList() ?: emptyList()
    }

    val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = cookieJar,
        followRedirects = false,   // 登录跳转/踢回登录页需要自己判 302,不自动跟
        trustAll = false,          // 明文 HTTP,不涉及证书;走 network config cleartext 白名单
        connectTimeoutSec = 15,
        readTimeoutSec = 20,
    )

    private val webVpnAuthenticator = CProgWebVpnAuthenticator(
        client = client,
        portalUrl = WEBVPN_PORTAL_URL.toHttpUrl(),
        webVpnLoginUrl = WEBVPN_LOGIN_URL.toHttpUrl(),
        credentials = {
            val username = sessionManager.getUsername()?.takeIf { it.isNotBlank() }
            val password = sessionManager.getPassword()?.takeIf { it.isNotBlank() }
            if (username != null && password != null) username to password else null
        },
        userAgent = UA,
    )

    /** Selects campus direct access when reachable, otherwise authenticates a WebVPN session. */
    suspend fun prepareTransport(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            transportMutex.withLock {
                if (transportMode != TransportMode.UNKNOWN) return@withLock
                val directUrl = normalizeBaseUrl(sessionManager.getCProgBaseUrl())
                if (canConnect(directUrl)) {
                    activeBaseUrl = directUrl
                    transportMode = TransportMode.DIRECT
                } else {
                    activateWebVpnLocked()
                }
            }
        }
    }

    private suspend fun switchToWebVpn(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            transportMutex.withLock { activateWebVpnLocked() }
        }
    }

    private fun activateWebVpnLocked() {
        webVpnAuthenticator.authenticate()
        activeBaseUrl = WEBVPN_BASE_URL
        transportMode = TransportMode.WEBVPN
    }

    private fun canConnect(url: String): Boolean = runCatching {
        val httpUrl = url.toHttpUrl()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(httpUrl.host, httpUrl.port), DIRECT_PROBE_TIMEOUT_MS)
        }
        true
    }.getOrDefault(false)

    // ── 登录态 ───────────────────────────────────────────────
    @Volatile var tk: String? = sessionManager.getCProgJwt()
        private set
    @Volatile var userId: String? = sessionManager.getCProgUserId()
        private set

    fun isLoggedIn(): Boolean = !tk.isNullOrBlank() && !userId.isNullOrBlank()

    /** 启动时把持久化的 JSESSIONID 灌回 CookieJar(JWT/userId 已在字段初始化时读出) */
    fun loadPersistedSession() {
        val jsid = sessionManager.getCProgJsessionid()?.takeIf { it.isNotBlank() } ?: return
        val host = runCatching { baseUrl.toHttpUrl().host }.getOrNull() ?: return
        val list = cookieStore.getOrPut(host) { mutableListOf() }
        list.removeAll { it.name == "JSESSIONID" }
        list.add(Cookie.Builder().domain(host).path("/").name("JSESSIONID").value(jsid).build())
    }

    private fun currentJsessionid(): String? {
        val host = runCatching { baseUrl.toHttpUrl().host }.getOrNull() ?: return null
        return cookieStore[host]?.firstOrNull { it.name == "JSESSIONID" }?.value
    }

    // ── 验证码 ───────────────────────────────────────────────
    /**
     * 建 session + 取验证码图。每次调用都先 GET /redirect/login 刷新 session,
     * 再 GET /kaptcha/jpg,保证验证码答案与当前 JSESSIONID 绑定。
     */
    suspend fun fetchCaptcha(): Result<Bitmap> = withContext(Dispatchers.IO) {
        prepareTransport().fold(
            onSuccess = {
                val first = runCatching { fetchCaptchaOnce() }
                if (first.isSuccess || transportMode == TransportMode.WEBVPN) {
                    first
                } else {
                    switchToWebVpn().fold(
                        onSuccess = { runCatching { fetchCaptchaOnce() } },
                        onFailure = { first },
                    )
                }
            },
            onFailure = { Result.failure(it) },
        ).onFailure { error ->
            Log.e(TAG, "fetchCaptcha 失败: ${error.message}", error)
        }
    }

    private fun fetchCaptchaOnce(): Bitmap {
        val loginUrl = endpointUrl("/redirect/login")
        client.newCall(
            Request.Builder().url(loginUrl).header("User-Agent", UA).build()
        ).execute().use { response ->
            if (!response.isSuccessful) throw IOException("平台会话初始化 HTTP ${response.code}")
        }
        val captchaQuery = if (transportMode == TransportMode.WEBVPN) mapOf("vpn-1" to null) else emptyMap()
        client.newCall(
            Request.Builder().url(endpointUrl("/kaptcha/jpg", captchaQuery))
                .header("User-Agent", UA)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", loginUrl)
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) throw IOException("验证码 HTTP ${response.code}")
            val bytes = response.body?.bytes() ?: throw IOException("验证码为空")
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IOException("验证码图解码失败")
        }
    }

    // ── 登录 ─────────────────────────────────────────────────
    /**
     * 完整登录。需先 [fetchCaptcha] 展示验证码图,用户输入后调此方法。
     * @param username 用户名 = C + 学号(如 CG62314006)
     * @param password 密码 = 学号本身(如 G62314006);文档表格误标为"身份证后6位",
     *                 抓包/复现脚本实际跑通值是学号
     * @param captcha 4 位验证码
     */
    suspend fun login(username: String, password: String, captcha: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                prepareTransport().getOrThrow()
                val loginPageUrl = endpointUrl("/redirect/login")
                // step1: /login/get
                val step1Body = FormBody.Builder()
                    .add("asdfsdf", username).add("dfgdfg", password).add("fghfghfg", captcha).build()
                val r1 = client.newCall(
                    Request.Builder().url(
                        endpointUrl("/login/get", mapOf("tk" to "null"), proxiedAjax = true)
                    )
                        .header("User-Agent", UA)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", loginPageUrl)
                        .post(step1Body).build()
                ).execute()
                val body1 = r1.use { if (!it.isSuccessful) return@withContext Result.failure(IOException("login/get HTTP ${it.code}")); it.body?.string().orEmpty() }
                val json1 = runCatching { JsonParser.parseString(body1).asJsonObject }.getOrNull()
                    ?: return@withContext Result.failure(IOException("login/get 非 JSON: ${body1.take(120)}"))
                val err1 = json1.get("errCode")?.asString
                if (err1 != "0") {
                    val msg = if (err1 == "993") "验证码错误" else json1.get("errMsg")?.asString ?: "登录失败($err1)"
                    return@withContext Result.failure(IOException(msg))
                }
                val data1 = json1.getAsJsonObject("data")
                    ?: return@withContext Result.failure(IOException("登录返回 data 为空"))
                val uid = data1.get("dfgdfg")?.asString.orEmpty()
                val jwt1 = data1.get("zxczxc")?.asString.orEmpty()
                // errCode=0 但 data 为空串 → 账密错(服务端不报错,靠空 data 表达)
                if (uid.isBlank() || jwt1.isBlank())
                    return@withContext Result.failure(IOException("用户名或密码错误(用户名 = C+学号,密码 = 学号)"))

                // step2: /login/unified
                // dfgdfg = MD5(uuid + 密码).大写 —— 登录页 JS login1(): $.md5(data + dfgdfg).toUpperCase()
                // (曾误用 jwt1.sub,服务端拒绝返回 data:null;算法从 redirect/login 内联脚本破解)
                val hashed = md5Upper(uid + password)
                val step2Body = FormBody.Builder()
                    .add("asdfsdf", username).add("dfgdfg", hashed).add("zxczxc", jwt1)
                    .add("fghfghfg", captcha).add("vbnvbnvbn", "false")
                    .add("url", loginPageUrl).build()
                val r2 = client.newCall(
                    Request.Builder().url(
                        endpointUrl("/login/unified", mapOf("tk" to "null"), proxiedAjax = true)
                    )
                        .header("User-Agent", UA)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", loginPageUrl)
                        .post(step2Body).build()
                ).execute()
                val body2 = r2.use { if (!it.isSuccessful) return@withContext Result.failure(IOException("login/unified HTTP ${it.code}")); it.body?.string().orEmpty() }
                val json2 = runCatching { JsonParser.parseString(body2).asJsonObject }.getOrNull()
                    ?: return@withContext Result.failure(IOException("login/unified 非 JSON: ${body2.take(120)}"))
                val err2 = json2.get("errCode")?.asString
                if (err2 != "0") {
                    val msg = when (err2) {
                        "995" -> "登录校验失败(参数错误)"
                        "993" -> "验证码错误"
                        "997" -> "登录已超时,请重试"
                        else -> json2.get("errMsg")?.asString ?: "统一登录失败($err2)"
                    }
                    return@withContext Result.failure(IOException(msg))
                }
                val mainJwt = json2.getAsJsonObject("data")?.get("t")?.asString.orEmpty()
                if (mainJwt.isBlank())
                    return@withContext Result.failure(IOException("未拿到主站 JWT"))

                // 落地
                tk = mainJwt
                userId = uid
                sessionManager.saveCProgSession(mainJwt, uid, currentJsessionid().orEmpty())
                sessionManager.saveCProgCredentials(username, password)
                Result.success("登录成功")
            } catch (e: Exception) {
                Log.e(TAG, "login 失败", e)
                Result.failure(e)
            }
        }

    /** 标准 MD5,输出大写 hex(对齐登录页 $.md5(...).toUpperCase()) */
    private fun md5Upper(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02X".format(it) }
    }

    suspend fun clearSession() {
        tk = null
        userId = null
        cookieStore.clear()
        activeBaseUrl = normalizeBaseUrl(sessionManager.getCProgBaseUrl())
        transportMode = TransportMode.UNKNOWN
        sessionManager.clearCProgSession()
    }

    /** 保存的用户名/密码,供登录卡预填 */
    fun savedUsername(): String? = sessionManager.getCProgUsername()
    fun savedPassword(): String? = sessionManager.getCProgIdno()
}
