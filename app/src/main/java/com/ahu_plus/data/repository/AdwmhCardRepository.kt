package com.ahu_plus.data.repository

import android.util.Log
import android.os.SystemClock
import com.google.gson.JsonObject
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

class AdwmhCardRepository(
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "AdwmhCardRepo"
        const val HOST = "adwmh.ahu.edu.cn"
        const val SESSION_COOKIE = "JSESSIONID"
        const val WECHAT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 " +
                "NetType/WIFI MicroMessenger/7.0.20.1781 WindowsWechat Flue"
        private const val REQUEST_MIN_GAP_MS = 1_500L
    }

    private val gson = GsonProvider.instance
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val cookieLock = Any()

    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            seedSessionCookie()
            return synchronized(cookieLock) { cookieStore[url.host]?.toList().orEmpty() }
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieLock) {
                val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
                for (cookie in cookies) {
                    hostCookies.removeAll { it.name == cookie.name }
                    hostCookies.add(cookie)
                }
            }
        }
    }

    /**
     * 专用于 adwmh.ahu.edu.cn 的 OkHttp 客户端：
     * - 强制 TLS 1.2（服务器 TLS 1.3 握手后不返回 HTTP 响应）
     * - 短超时（8s 连接 / 12s 读取），避免长时间卡住
     */
    private val client = SecureHttpClientFactory.create(
        cookieJar = cookieJar,
        tls12Only = true,
        trustAll = true,  // adwmh.ahu.edu.cn 走 DigiCert 但内部 302 跳转混合域名,沿用 trustAll
        connectTimeoutSec = 8,
        readTimeoutSec = 12
    )

    /**
     * adwmh 对重叠请求和突发请求都很敏感。锁必须覆盖完整 HTTP 往返，而不是只保护
     * 请求开始时间；否则前一个请求卡住时，后续请求仍会在 1.5 秒后并发发出。
     */
    private val requestMutex = Mutex()
    private var lastRequestCompletedAtMs = 0L

    private suspend fun <T> executeThrottled(request: () -> T): T = requestMutex.withLock {
        val elapsed = SystemClock.elapsedRealtime() - lastRequestCompletedAtMs
        if (lastRequestCompletedAtMs > 0L && elapsed < REQUEST_MIN_GAP_MS) {
            delay(REQUEST_MIN_GAP_MS - elapsed)
        }
        try {
            request()
        } finally {
            lastRequestCompletedAtMs = SystemClock.elapsedRealtime()
        }
    }

    // ── 登录 ─────────────────────────────────────────────

    /**
     * 后台登录只尝试无需验证码的协议路径。若服务端要求验证码，调用方必须转到
     * [fetchCaptcha] + [loginWithCaptcha] 的用户手动输入流程。
     */
    suspend fun autoLogin(
        username: String,
        password: String,
        generation: Long = sessionManager.currentAccountGeneration(),
    ): Result<AdwmhLoginInfo> = withContext(Dispatchers.IO) {
        val result = doLogin(username, password, "", generation)
        if (result.isSuccess) return@withContext result
        val failure = result.exceptionOrNull()
        if (!shouldRequestManualAdwmhCaptcha(failure)) {
            return@withContext result
        }
        Result.failure(
            AdwmhCaptchaRequiredException("智慧安大需要手动输入验证码")
        )
    }

    /** 从智慧安大获取验证码图片；图片只返回给本地 UI 展示。 */
    suspend fun fetchCaptcha(): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://$HOST/remind/authcode")
                .header("User-Agent", WECHAT_UA)
                .header("Accept", "image/webp,image/*,*/*")
                .header("X-Requested-With", "XMLHttpRequest")
                .get()
                .build()
            executeThrottled {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw AdwmhAuthException("验证码获取失败")
                    response.body?.bytes() ?: throw AdwmhAuthException("验证码为空")
                }
            }
        }
    }

    suspend fun loginWithCaptcha(
        username: String,
        password: String,
        captcha: String,
        generation: Long = sessionManager.currentAccountGeneration(),
    ): Result<AdwmhLoginInfo> {
        val normalizedCaptcha = captcha.trim()
        if (normalizedCaptcha.isBlank()) {
            return Result.failure(AdwmhAuthException("请输入验证码"))
        }
        return doLogin(username.trim(), password, normalizedCaptcha, generation)
    }

    /** 执行登录 POST /user/login */
    private suspend fun doLogin(
        username: String,
        password: String,
        captcha: String,
        generation: Long = sessionManager.currentAccountGeneration(),
    ): Result<AdwmhLoginInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val formBody = FormBody.Builder()
                .add("username", username)
                .add("pwd", password)
                .add("flag", "0")
                .add("imgcode", captcha)
                .build()
            val request = Request.Builder()
                .url("https://$HOST/user/login")
                .header("User-Agent", WECHAT_UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://$HOST/www/index.html")
                .post(formBody)
                .build()
            val (loginInfo, jsessionid) = executeThrottled {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw AdwmhAuthException("登录失败(${response.code})")
                    val json = gson.fromJson(body, JsonObject::class.java)
                        ?: throw AdwmhAuthException("登录响应为空")
                    val code = json.get("code")?.asInt ?: -1
                    if (code != 10000) {
                        val msg = json.get("msg")?.asString ?: "登录失败"
                        throw AdwmhAuthException(msg)
                    }
                    val jsessionid = synchronized(cookieLock) {
                        cookieStore[HOST]?.firstOrNull { it.name == SESSION_COOKIE }?.value
                    }
                    val obj = json.getAsJsonObject("object")
                    val user = obj?.getAsJsonObject("user")
                    AdwmhLoginInfo(
                        userName = user?.get("userName")?.asString ?: username,
                        cardId = user?.get("cardId")?.asString ?: "",
                        unitName = user?.get("unitName")?.asString ?: ""
                    ) to jsessionid
                }
            }
            currentCoroutineContext().ensureActive()
            if (!jsessionid.isNullOrBlank()) sessionManager.saveAdwmhSessionId(jsessionid, generation)
            loginInfo
        }
    }

    // ── 兼容旧版：手动导入 session ─────────────────────────

    /** @deprecated 推荐使用 [login] 直接登录。 */
    suspend fun importSessionId(sessionId: String) {
        val normalized = sessionId.trim()
        if (normalized.isBlank()) return
        sessionManager.saveAdwmhSessionId(normalized)
        setSessionCookie(normalized)
    }

    fun clearCookies() {
        synchronized(cookieLock) { cookieStore.clear() }
    }

    // ── 业务 API ─────────────────────────────────────────

    suspend fun getQrCode(): Result<AdwmhQrCode> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionId()
            Log.d(TAG, "getQrCode: fetching...")
            val json = getJson("/xzxcard/qrcode")
            val payload = json.get("object")?.asString?.takeIf { it.isNotBlank() }
                ?: throw AdwmhAuthException("QR payload is empty")
            Log.d(TAG, "getQrCode: OK (payload len=${payload.length})")
            AdwmhQrCode(
                payload = payload,
                statusMsg = json.get("msg")?.asString.orEmpty(),
                fetchedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun getBalance(): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionId()
            val json = getJson("/xzxcard/yue")
            json.get("object")?.asDouble ?: throw AdwmhAuthException("balance is empty")
        }
    }

    suspend fun validateSession(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureSessionId()
            getJson("/user/session", method = "POST")
            Unit
        }
    }

    /** 是否已配置 session（含持久化的旧 session）。 */
    fun hasSession(): Boolean {
        return !sessionManager.getAdwmhSessionId().isNullOrBlank()
    }

    // ── 内部方法 ──────────────────────────────────────────

    private fun ensureSessionId(): String {
        val sessionId = sessionManager.getAdwmhSessionId()?.takeIf { it.isNotBlank() }
            ?: throw AdwmhAuthException("请先登录智慧安大")
        setSessionCookie(sessionId)
        return sessionId
    }

    private fun seedSessionCookie() {
        val sessionId = sessionManager.getAdwmhSessionId()?.takeIf { it.isNotBlank() } ?: return
        val hasCookie = synchronized(cookieLock) {
            cookieStore[HOST]?.any { it.name == SESSION_COOKIE } == true
        }
        if (!hasCookie) setSessionCookie(sessionId)
    }

    private fun setSessionCookie(sessionId: String) {
        synchronized(cookieLock) {
            val hostCookies = cookieStore.getOrPut(HOST) { mutableListOf() }
            hostCookies.removeAll { it.name == SESSION_COOKIE }
            hostCookies.add(
                Cookie.Builder()
                    .domain(HOST)
                    .path("/")
                    .name(SESSION_COOKIE)
                    .value(sessionId)
                    .httpOnly()
                    .build()
            )
        }
    }

    private suspend fun getJson(path: String, method: String = "GET"): JsonObject {
        val url = "https://$HOST$path"
        Log.d(TAG, "$method $url")
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", WECHAT_UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://$HOST/www/index.html")

        if (method == "POST") {
            requestBuilder
                .header("Origin", "https://$HOST")
                .post(ByteArray(0).toRequestBody(null))
        }

        try {
            return executeThrottled {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    Log.d(TAG, "$method $path → ${response.code} (body len=${body.length})")
                    if (response.code == 401 || response.code == 403 || response.code in 300..399) {
                        throw AdwmhAuthException("智慧安大会话已过期，请重新登录")
                    }
                    if (!response.isSuccessful) {
                        throw AdwmhAuthException("智慧安大 HTTP ${response.code}")
                    }
                    if (body.trimStart().startsWith("<")) {
                        throw AdwmhAuthException("智慧安大返回 HTML，请重新登录")
                    }
                    val json = gson.fromJson(body, JsonObject::class.java)
                        ?: throw AdwmhAuthException("智慧安大响应为空")
                    val code = json.get("code")?.asInt
                    if (code != 10000) {
                        throw AdwmhAuthException(json.get("msg")?.asString ?: "智慧安大请求失败")
                    }
                    json
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "$method $path → TIMEOUT: ${e.message}")
            throw AdwmhAuthException("智慧安大连接超时")
        } catch (e: Exception) {
            if (e is AdwmhAuthException) throw e
            Log.e(TAG, "$method $path → ${e.javaClass.simpleName}: ${e.message}")
            throw AdwmhAuthException("智慧安大请求失败: ${e.message}")
        }
    }

}

// ── 数据模型 ─────────────────────────────────────────────

data class AdwmhQrCode(
    val payload: String,
    /**
     * 接口 `msg` 字段。经对照 AHUTong 实现确认:这是服务端状态文案(成功时类似
     * "操作成功"),**并非服务器时间**。仅作辅助展示,勿当时间戳解析。
     */
    val statusMsg: String,
    val fetchedAt: Long
)

/** 登录成功后返回的用户信息。 */
data class AdwmhLoginInfo(
    val userName: String,
    val cardId: String,
    val unitName: String
)

open class AdwmhAuthException(message: String) : Exception(message)

class AdwmhCaptchaRequiredException(message: String) : AdwmhAuthException(message)

internal fun shouldRequestManualAdwmhCaptcha(failure: Throwable?): Boolean =
    failure is AdwmhAuthException && !failure.message.orEmpty().startsWith("登录失败(")
