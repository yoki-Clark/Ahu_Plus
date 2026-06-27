package com.yourname.ahu_plus.data.repository

import android.util.Base64
import android.util.Log
import com.google.gson.JsonParser
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * WeLearn 随行课堂 (welearn.sflep.com) 认证仓库。
 *
 * 负责 OIDC 登录、Cookie 共享(sso.sflep.com ↔ welearn.sflep.com)、凭据存取。
 * 协议照搬 jhl337/Auto_WeLearn (2025-12 仍活跃):prelogin → idsvr/account/login → 二次 prelogin。
 * 刷课 / 课程查询由 [WeLearnRepository] 和 [WeLearnStudyRepository] 复用本仓库的 client/cookieJar。
 */
class WeLearnAuthRepository(
    private val sessionManager: SessionManager,
) {
    companion object {
        private const val TAG = "WeLearn"
        const val BASE_URL = "https://welearn.sflep.com"
        const val SSO_URL = "https://sso.sflep.com"
        private const val COOKIE_HOST = "sflep.com"

        private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"

        /** 登录结果码:参考 idsvr 返回 */
        const val LOGIN_OK = 0
        const val LOGIN_BAD_CRED = 1
    }

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // welearn.sflep.com 与 sso.sflep.com 共享 Cookie,统一归到 "sflep.com"
            val domain = if (url.host.endsWith(".sflep.com") || url.host == "sflep.com") "sflep.com" else url.host
            val list = cookieStore.getOrPut(domain) { mutableListOf() }
            for (c in cookies) {
                list.removeAll { it.name == c.name }
                list.add(c)
            }
            cookiesDirty = true
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val domain = if (url.host.endsWith(".sflep.com") || url.host == "sflep.com") "sflep.com" else url.host
            return cookieStore[domain]?.toList() ?: emptyList()
        }
    }

    val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = cookieJar,
        followRedirects = true,
        trustAll = false,  // SFLEP 是正规证书
        connectTimeoutSec = 20,
        readTimeoutSec = 30,
    )

    // ── 凭据持久化标记 ──────────────────────────────────────
    @Volatile
    private var cookiesDirty = false

    suspend fun flushCookies() {
        if (!cookiesDirty) return
        cookiesDirty = false
        val all = cookieStore.values.flatten()
        val str = all.joinToString(";") { "${it.name}=${it.value}" }
        sessionManager.saveWeLearnCookies(str)
    }

    /** 启动时把上次登录的 cookie 灌回 CookieJar */
    fun loadPersistedCookies() {
        val raw = sessionManager.getWeLearnCookies() ?: return
        if (raw.isBlank()) return
        val list = cookieStore.getOrPut(COOKIE_HOST) { mutableListOf() }
        for (part in raw.split(";")) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val name = part.substring(0, eq).trim()
            val value = part.substring(eq + 1).trim()
            if (name.isEmpty()) continue
            list.removeAll { it.name == name }
            list.add(Cookie.Builder().domain(COOKIE_HOST).path("/").name(name).value(value).build())
        }
    }

    suspend fun clearCookies() {
        cookieStore.clear()
        sessionManager.saveWeLearnCookies("")
    }

    // ── 登录态判定 ──────────────────────────────────────────
    /**
     * 是否有非 WAF cookie。SFLEP 真实业务 cookie 名随时会变,这里只看 WAF (acw_tc)。
     * 真正能不能用,靠 [WeLearnRepository.getCourses] 探活。
     */
    fun isLoggedIn(): Boolean {
        val cookies = cookieStore[COOKIE_HOST] ?: return false
        return cookies.any { it.name != "acw_tc" }
    }

    // ── 密码加密 (翻译 Auto_WeLearn/core/crypto.py) ─────────
    /**
     * 算法:取当前毫秒 T0,把 (T0>>16)&0xFF 与密码每个字节异或得 V,
     * T1 = (T0/100)*100 + V%100,明文 = "{T1}*" + 密码 hex,base64 编码。
     *
     * T1 必须和 POST body 的 `ts` 字段用同一时刻(同函数返回),否则 SFLEP 返回 code=-1。
     */
    internal fun encryptPassword(password: String, nowMs: Long = System.currentTimeMillis()): Pair<String, String> {
        val pwdBytes = password.toByteArray(Charsets.UTF_8)
        var v = ((nowMs shr 16) and 0xFF)
        for (b in pwdBytes) v = v xor (b.toLong() and 0xFFL)
        val t1 = (nowMs / 100) * 100 + v % 100
        val hexPwd = pwdBytes.joinToString("") { "%02x".format(it) }
        val encoded = Base64.encodeToString("$t1*$hexPwd".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return encoded to t1.toString()
    }

    // ── 登录流程 ────────────────────────────────────────────
    /**
     * OIDC 登录。
     * @return Result.success(msg) / Result.failure(LoginException)
     */
    suspend fun login(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val preloginUrl = "$BASE_URL/user/prelogin.aspx?loginret=http://welearn.sflep.com/user/loginredirect.aspx"

            // 1. 第一次 prelogin 拿 code_challenge / state (走 302 跳转链)
            val r1 = client.newCall(Request.Builder().url(preloginUrl).header("User-Agent", UA).build()).execute()
            r1.use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("prelogin HTTP ${resp.code}"))
                val finalUrl = resp.request.url
                Log.d(TAG, "prelogin 跳到: ${finalUrl}")

                // 解析 code_challenge / state。
                // 跳转链最终 URL 通常是:
                //   https://sso.sflep.com/idsvr/transfer.html?returnUrl=%2Fconnect%2Fauthorize%2Fcallback%3F...%26code_challenge%3DXXX%26state%3DYYY
                // 已登录会跳到 loginredirect.aspx(无 returnUrl),此时不需要再走 idsvr,直接成功。
                val returnUrlParam = finalUrl.queryParameter("returnUrl")
                if (returnUrlParam.isNullOrBlank()) {
                    // 已登录或确认页(loginredirect/loginconfirm)→ 直接视作登录成功
                    Log.d(TAG, "prelogin 无 returnUrl, 视为已登录态, 落地 cookie 完成")
                    sessionManager.saveWeLearnCredentials(username, password)
                    flushCookies()
                    return@withContext Result.success("登录成功")
                }

                // 解码 returnUrl: %2F → /, %3F → ?, %3D → =, 内部 %26 是原始 &
                val innerUrl = java.net.URLDecoder.decode(returnUrlParam, "UTF-8")
                val fullInner = ("https://sso.sflep.com$innerUrl").toHttpUrl()
                val codeChallenge = fullInner.queryParameter("code_challenge")
                val state = fullInner.queryParameter("state")
                if (codeChallenge.isNullOrBlank() || state.isNullOrBlank()) {
                    return@withContext Result.failure(IOException("prelogin 抠不出 code_challenge/state, innerUrl=$innerUrl"))
                }

                // rturl 是 idsvr 期待的"登录后跳回"参数;OIDC callback 路径 + 同 query
                val rturl = buildString {
                    append("/connect/authorize/callback?client_id=welearn_web")
                    append("&redirect_uri=https%3A%2F%2Fwelearn.sflep.com%2Fsignin-sflep")
                    append("&response_type=code&scope=openid%20profile%20email%20phone%20address")
                    append("&code_challenge=$codeChallenge&code_challenge_method=S256")
                    append("&state=$state")
                    append("&x-client-SKU=ID_NET472&x-client-ver=6.32.1.0")
                }

                val (enpwd, ts) = encryptPassword(password)

                val loginBody = okhttp3.FormBody.Builder()
                    .add("rturl", rturl)
                    .add("account", username)
                    .add("pwd", enpwd)
                    .add("ts", ts)
                    .build()

                val r2 = client.newCall(
                    Request.Builder()
                        .url("$SSO_URL/idsvr/account/login")
                        .header("User-Agent", UA)
                        .post(loginBody)
                        .build()
                ).execute()
                r2.use { resp2 ->
                    if (!resp2.isSuccessful) return@withContext Result.failure(IOException("login HTTP ${resp2.code}"))
                    val body = resp2.body?.string().orEmpty()
                    val code = runCatching {
                        JsonParser.parseString(body).asJsonObject.get("code")?.asInt ?: -1
                    }.getOrDefault(-1)

                    when (code) {
                        LOGIN_OK -> {
                            // 2. 二次 prelogin 落地最终 cookie
                            client.newCall(Request.Builder().url(preloginUrl).header("User-Agent", UA).build()).execute().use { resp3 ->
                                if (!resp3.isSuccessful) return@withContext Result.failure(IOException("二次 prelogin HTTP ${resp3.code}"))
                            }
                            sessionManager.saveWeLearnCredentials(username, password)
                            flushCookies()
                            Result.success("登录成功")
                        }
                        LOGIN_BAD_CRED -> Result.failure(IOException("账号或密码错误"))
                        else -> Result.failure(IOException("登录 code=$code body=${body.take(200)}"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "login 异常", e)
            Result.failure(e)
        }
    }

    /**
     * 用 SessionManager 里存的账密重新登录(供 Service 阶段自动续期用)。
     * 不存账密时返回 false。
     */
    suspend fun autoLoginIfPossible(): Boolean {
        val u = sessionManager.getWeLearnUsername() ?: return false
        val p = sessionManager.getWeLearnPassword() ?: return false
        return login(u, p).isSuccess
    }
}