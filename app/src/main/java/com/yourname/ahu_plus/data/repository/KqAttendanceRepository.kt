package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.KqAttendanceResponse
import com.yourname.ahu_plus.data.model.KqAttendanceSummary
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

/**
 * kqcard 考勤仓库 (kqcard.ahu.edu.cn)。
 *
 * 认证链路 (已验证通过, 见 tools/kqcard_attendance_test.py):
 *   1. 复用 CasAuthRepository 的 CASTGC 换取 CAS ST ticket
 *   2. ST → OAuth2 token 端点 (?code=ST-...) 换取 synjones-auth JWT
 *   3. 用 JWT 调用 classWater/getClassWaterPage API
 *
 * 与 [YcardRepository] 使用相同的 synjones-auth JWT 模式。
 */
class KqAttendanceRepository(
    private val casAuthRepository: CasAuthRepository? = null,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "KqAttendanceRepo"
        private const val KQCARD_BASE = "https://kqcard.ahu.edu.cn"
        private const val KQCARD_SERVICE_URL =
            "$KQCARD_BASE/berserker-auth/auth/ahu/oauth2/token/attendance-pc"
        private const val CAS_BASE = "https://one.ahu.edu.cn/cas"
        private const val CAS_HOST = "one.ahu.edu.cn"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
        private val JSON_MEDIA = "application/json".toMediaType()

        private const val DEFAULT_TERM_NO = 105
        private const val PAGE_SIZE = 10
    }

    private val gson = GsonProvider.instance

    // Cookie 存储
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore[url.host] ?: emptyList()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
            for (cookie in cookies) {
                hostCookies.removeAll { it.name == cookie.name }
                hostCookies.add(cookie)
            }
        }
    }

    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = cookieJar, followRedirects = false, disableGzip = true
    )

    @Volatile
    private var cachedJwt: String? = null

    fun clearCookies() {
        cookieStore.clear()
        cachedJwt = null
    }

    // ══════════════════════════════════════════════════════
    // 公开 API
    // ══════════════════════════════════════════════════════

    suspend fun getAttendanceList(): Result<KqAttendanceSummary> = withContext(Dispatchers.IO) {
        try {
            Log.e(TAG, "开始获取考勤数据...")
            ensureLoggedIn()

            val jwt = cachedJwt
                ?: return@withContext Result.failure(Exception("考勤认证失败"))

            val startDate = "2026-03-02"
            val endDate = "2026-07-19"
            val allRecords = mutableListOf<com.yourname.ahu_plus.data.model.KqAttendanceRecord>()
            var currentPage = 1
            var totalCount = 0

            while (true) {
                val bodyJson = """
                    {
                        "classBean": {"termNo": $DEFAULT_TERM_NO},
                        "classWaterBean": {"status": ""},
                        "subjectBean": {"sCode": ""},
                        "timeCondition": "",
                        "startDate": "$startDate",
                        "endDate": "$endDate",
                        "pageSize": $PAGE_SIZE,
                        "current": $currentPage
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url("$KQCARD_BASE/attendance-student/classWater/getClassWaterPage")
                    .header("User-Agent", UA)
                    .header("synjones-auth", "bearer $jwt")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Origin", KQCARD_BASE)
                    .header("Referer", "$KQCARD_BASE/attendance-student-pc/")
                    .post(bodyJson.toRequestBody(JSON_MEDIA))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val code = response.code
                response.close()

                Log.e(TAG, "考勤 API page=$currentPage HTTP $code")

                if (code == 401 || code == 403) {
                    Log.e(TAG, "JWT 失效,重新登录")
                    cachedJwt = null
                    cookieStore.clear()
                    ensureLoggedIn()
                    continue // 用新 JWT 重试
                }

                if (code != 200) {
                    return@withContext Result.failure(Exception("考勤查询失败 HTTP $code"))
                }

                val parsed = gson.fromJson(body, KqAttendanceResponse::class.java)
                val data = parsed.data
                    ?: return@withContext Result.failure(
                        Exception(parsed.msg ?: "考勤数据为空"))

                totalCount = data.totalCount
                allRecords.addAll(data.list)

                // 空列表或不足一页就停止 (实测 page 14 返回空列表)
                if (data.list.isEmpty() || data.list.size < PAGE_SIZE) break
                currentPage++
            }

            allRecords.sortByDescending { it.accountBean?.checkdate ?: "" }
            Log.i(TAG, "考勤数据加载完成: ${allRecords.size} / $totalCount")

            val summary = KqAttendanceSummary(
                records = allRecords,
                total = totalCount,
                lastUpdatedAt = System.currentTimeMillis()
            )
            persistToCache(summary)
            Result.success(summary)

        } catch (e: Exception) {
            Log.e(TAG, "考勤数据获取失败", e)
            Result.failure(e)
        }
    }

    fun readCached(): KqAttendanceSummary? {
        return runCatching {
            val json = sessionManager.getKqcardAttendanceJson() ?: return null
            gson.fromJson(json, KqAttendanceSummary::class.java)
        }.getOrNull()
    }

    // ══════════════════════════════════════════════════════
    // 内部认证
    // ══════════════════════════════════════════════════════

    private suspend fun ensureLoggedIn() {
        if (cachedJwt != null) return
        login()
    }

    /**
     * CAS ST → OAuth2 JWT。
     *
     * 流程 (Python 已验证):
     *   1. 从 CasAuthRepository 获取 CASTGC
     *   2. GET CAS login?service=kqcard → 302 → 提取 ticket=ST-...
     *   3. GET kqcard OAuth2?code=ST-... → 302 → 提取 token=<JWT>
     */
    private suspend fun login() {
        cookieStore.clear()
        cachedJwt = null

        val castgc = casAuthRepository?.cookieStore?.get(CAS_HOST)
            ?.find { it.name == "CASTGC" }?.value
            ?: throw Exception("未找到 CASTGC")

        Log.e(TAG, "CASTGC: ${castgc.take(20)}...")

        // Step 1: CASTGC → ST
        val casLoginUrl = "$CAS_BASE/login?service=${java.net.URLEncoder.encode(KQCARD_SERVICE_URL, "UTF-8")}"
        val stTicket = client.newCall(Request.Builder()
            .url(casLoginUrl)
            .header("User-Agent", UA)
            .header("Cookie", "CASTGC=$castgc")
            .build()
        ).execute().use { response ->
            if (response.code !in 300..399)
                throw Exception("CAS 未重定向 (HTTP ${response.code})")
            val location = response.header("Location")
                ?: throw Exception("CAS 未返回 Location")
            Regex("""ticket=(ST-[^&"]+)""").find(location)?.groupValues?.get(1)
                ?: throw Exception("未找到 ST ticket: ${location.take(200)}")
        }
        Log.e(TAG, "ST: ${stTicket.take(30)}...")

        // Step 2: ST → JWT (OAuth2 code exchange)
        val jwt = client.newCall(Request.Builder()
            .url("$KQCARD_SERVICE_URL?code=$stTicket")
            .header("User-Agent", UA)
            .build()
        ).execute().use { response ->
            if (response.code !in 300..399)
                throw Exception("kqcard OAuth2 未重定向 (HTTP ${response.code})")
            val location = response.header("Location")
                ?: throw Exception("kqcard 未返回 Location")
            Regex("""token=([^&"]+)""").find(location)?.groupValues?.get(1)
                ?: throw Exception("未找到 token: ${location.take(200)}")
        }
        Log.e(TAG, "JWT: ${jwt.take(40)}...")

        cachedJwt = jwt
        Log.e(TAG, "kqcard 认证成功")
    }

    private suspend fun persistToCache(summary: KqAttendanceSummary) {
        try {
            sessionManager.saveKqcardAttendanceJson(gson.toJson(summary))
        } catch (e: Exception) {
            Log.e(TAG, "缓存失败", e)
        }
    }
}
