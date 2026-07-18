package com.ahu_plus.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.CxActivity
import com.ahu_plus.data.model.CxAttachment
import com.ahu_plus.data.model.CxChapter
import com.ahu_plus.data.model.CxCourse
import com.ahu_plus.data.model.CxCoursePoints
import com.ahu_plus.data.model.CxCourseProgress
import com.ahu_plus.data.model.CxJob
import com.ahu_plus.data.model.CxJobInfo
import com.ahu_plus.data.model.CxMessage
import com.ahu_plus.data.model.CxMessageSource
import com.ahu_plus.data.model.CxPreSignInfo
import com.ahu_plus.data.model.CxSignType
import com.ahu_plus.data.model.CxVideoInfo
import com.ahu_plus.data.model.CxWorkData
import com.ahu_plus.data.model.CxHomeworkItem
import com.ahu_plus.data.model.CxQuestion
import com.ahu_plus.data.network.ChaoxingAuthExpiredException
import com.ahu_plus.data.network.ChaoxingTrafficCooldownException
import com.ahu_plus.data.network.ChaoxingTrafficException
import com.ahu_plus.data.network.ChaoxingTrafficGovernor
import com.ahu_plus.data.network.ChaoxingTrafficStateSnapshot
import com.ahu_plus.data.network.awaitResponse
import com.ahu_plus.data.network.SecureHttpClientFactory
import com.ahu_plus.util.AESCipher
import com.ahu_plus.util.CxFontDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 超星学习通 Repository。
 *
 * 负责登录、课程列表、章节列表、任务点卡片、视频信息、签到活动等 API。
 * 认证方式：AES 加密手机号+密码 → Cookie（`_uid`/`uf`/`vc3`/`_d`）。
 */
class ChaoxingRepository(
    private val sessionManager: SessionManager,
) {
    companion object {
        private const val TAG = "Chaoxing"
        private const val BASE_PASSPORT = "https://passport2.chaoxing.com"
        private const val BASE_MOOC2 = "https://mooc2-ans.chaoxing.com"
        private const val BASE_MOOC1 = "https://mooc1.chaoxing.com"
        private const val BASE_MOBILE = "https://mobilelearn.chaoxing.com"
        /** Stable, transparent native client identity. Browser flows belong in WebView. */
        private const val UA_FIXED = "AhuPlus/Android"

        private fun userAgent(): String = UA_FIXED
    }

    // ── Cookie 存储 ──────────────────────────────────────────────
    private val gson = Gson()
    private val cookieStore = ConcurrentHashMap<String, Cookie>()
    private val cxHost = "chaoxing.com"
    private val cookiePersistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cookiePersistLock = Any()
    private val trafficStateLock = Any()
    @Volatile private var trafficStateRestored = false
    @Volatile private var trafficAccountScope = "anonymous"
    private var trafficStatePersistJob: Job? = null
    private val trafficGovernor = ChaoxingTrafficGovernor(
        onStateChanged = { persistTrafficState() },
    )

    private data class PersistedCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
        val persistent: Boolean,
    )

    private fun cookieKey(cookie: Cookie): String =
        "${cookie.name}\u0000${cookie.domain}\u0000${cookie.path}"

    val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val now = System.currentTimeMillis()
            for (cookie in cookies) {
                val key = cookieKey(cookie)
                if (cookie.expiresAt <= now) {
                    cookieStore.remove(key)
                } else {
                    cookieStore[key] = cookie
                }
            }
            persistCookies()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return cookieStore.values.filter { cookie ->
                cookie.expiresAt > now && cookie.matches(url)
            }
        }
    }

    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = cookieJar,
        followRedirects = true,
        retryOnConnectionFailure = false,
        trustAll = false,  // 超星用标准 HTTPS 证书
        connectTimeoutSec = 20,
        readTimeoutSec = 30,
        extraInterceptors = listOf(
            trafficGovernor.asEntryTagInterceptor(accountProvider = { trafficAccountKey() }),
        ),
        extraNetworkInterceptors = listOf(
            trafficGovernor.asInterceptor(accountProvider = { trafficAccountKey() }),
        ),
    )
    @Volatile private var cachedCourseList: List<CxCourse>? = null
    @Volatile private var cachedCourseListAt: Long = 0L
    private data class CacheEntry<T>(val value: T, val storedAt: Long)
    private val coursePointsCache = ConcurrentHashMap<String, CacheEntry<CxCoursePoints>>()
    private val jobListCache = ConcurrentHashMap<String, CacheEntry<Pair<List<CxJob>, CxJobInfo>>>()
    private val activityCache = ConcurrentHashMap<String, CacheEntry<List<CxActivity>>>()
    private val resourceCache = ConcurrentHashMap<String, CacheEntry<List<CxAttachment>>>()

    fun invalidateJobListCache(course: CxCourse, chapterId: String) {
        jobListCache.remove("${course.courseId}_${course.clazzId}_$chapterId")
    }

    // ── Cookie 持久化 ────────────────────────────────────────────

    @Volatile
    private var cookiesDirty = false
    private var cookiePersistJob: Job? = null

    private fun persistCookies() {
        cookiesDirty = true
        synchronized(cookiePersistLock) {
            cookiePersistJob?.cancel()
            cookiePersistJob = cookiePersistScope.launch {
                delay(500)
                flushCookies()
            }
        }
    }

    /** 将内存中的 Cookie 持久化到 DataStore（需在协程中调用） */
    suspend fun flushCookies() {
        if (!cookiesDirty) return
        val snapshot = cookieStore.values.map { cookie ->
            PersistedCookie(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain,
                path = cookie.path,
                expiresAt = cookie.expiresAt,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                hostOnly = cookie.hostOnly,
                persistent = cookie.persistent,
            )
        }
        sessionManager.saveCxCookies(gson.toJson(snapshot))
        cookiesDirty = false
    }

    fun loadPersistedCookies() {
        val raw = sessionManager.getCxCookies() ?: return
        if (raw.isBlank()) return
        val now = System.currentTimeMillis()
        val cookies = if (raw.trimStart().startsWith("[")) {
            runCatching {
                gson.fromJson(raw, Array<PersistedCookie>::class.java).mapNotNull { saved ->
                    if (saved.persistent && saved.expiresAt <= now) return@mapNotNull null
                    runCatching {
                        Cookie.Builder()
                            .name(saved.name)
                            .value(saved.value)
                            .path(saved.path)
                            .apply {
                                if (saved.hostOnly) hostOnlyDomain(saved.domain) else domain(saved.domain)
                                if (saved.persistent) expiresAt(saved.expiresAt)
                                if (saved.secure) secure()
                                if (saved.httpOnly) httpOnly()
                            }
                            .build()
                    }.getOrNull()
                }
            }.getOrDefault(emptyList())
        } else {
            // Backward-compatible import of the legacy name=value;name=value format.
            raw.split(";").mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                Cookie.Builder()
                    .domain(cxHost)
                    .path("/")
                    .name(part.substring(0, eq).trim())
                    .value(part.substring(eq + 1).trim())
                    .build()
            }
        }
        cookieStore.clear()
        cookies.forEach { cookieStore[cookieKey(it)] = it }
        if (trafficAccountScope == "anonymous") {
            cookieStore.values.firstOrNull { it.name == "_uid" }?.value?.let {
                trafficAccountScope = stableTrafficAccountKey(it)
            }
        }
    }

    suspend fun clearCookies() {
        synchronized(cookiePersistLock) {
            cookiePersistJob?.cancel()
            cookiePersistJob = null
        }
        cookieStore.clear()
        cachedCourseList = null
        cachedCourseListAt = 0L
        coursePointsCache.clear()
        jobListCache.clear()
        activityCache.clear()
        resourceCache.clear()
        cookiesDirty = false
        sessionManager.saveCxCookies("")
    }

    // ── 获取当前用户 ID ─────────────────────────────────────────

    fun getUid(): String {
        return cookieStore.values.firstOrNull { it.name == "_uid" && it.expiresAt > System.currentTimeMillis() }?.value
            ?: throw IllegalStateException("未登录超星，无法获取 uid")
    }

    fun getFid(): String {
        return cookieStore.values.firstOrNull { it.name == "fid" && it.expiresAt > System.currentTimeMillis() }?.value ?: "1024"
    }

    fun isLoggedIn(): Boolean {
        return cookieStore.values.any { it.name == "_uid" && it.expiresAt > System.currentTimeMillis() }
    }

    /**
     * 自动登录：使用 SessionManager 中已保存的凭据重新登录，
     * 避免用户手动输入。返回 true 表示登录成功。
     */
    suspend fun autoLogin(): Boolean {
        val phone = sessionManager.getCxPhone() ?: return false
        val password = sessionManager.getCxPassword() ?: return false
        return login(phone, password).isSuccess
    }

    /** 返回当前所有 Cookie 的 "name=value;name=value" 字符串，供外部 HTTP 客户端使用。 */
    fun getCookieString(): String {
        val now = System.currentTimeMillis()
        return cookieStore.values
            .filter { it.expiresAt > now }
            .joinToString("; ") { "${it.name}=${it.value}" }
    }

    private fun trafficAccountKey(): String {
        ensureTrafficStateRestored()
        return trafficAccountScope
    }

    private fun selectTrafficAccount(account: String) {
        ensureTrafficStateRestored()
        if (account.isNotBlank()) trafficAccountScope = stableTrafficAccountKey(account)
    }

    private fun stableTrafficAccountKey(account: String): String {
        val normalized = account.trim().lowercase(Locale.US)
        if (normalized.isBlank()) return "anonymous"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return "account_" + digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun ensureTrafficStateRestored() {
        if (trafficStateRestored) return
        synchronized(trafficStateLock) {
            if (trafficStateRestored) return
            val persisted = runCatching {
                gson.fromJson(
                    sessionManager.getCxTrafficStateJson(),
                    Array<ChaoxingTrafficStateSnapshot>::class.java,
                )?.toList().orEmpty()
            }.getOrDefault(emptyList())
            trafficGovernor.restore(persisted)
            val knownAccount = sessionManager.getCxPhone()
                ?: cookieStore.values.firstOrNull { it.name == "_uid" }?.value
            if (!knownAccount.isNullOrBlank()) {
                trafficAccountScope = stableTrafficAccountKey(knownAccount)
            }
            trafficStateRestored = true
        }
    }

    private fun persistTrafficState() {
        if (!trafficStateRestored) return
        val json = gson.toJson(trafficGovernor.snapshot())
        synchronized(trafficStateLock) {
            trafficStatePersistJob?.cancel()
            trafficStatePersistJob = cookiePersistScope.launch {
                sessionManager.saveCxTrafficStateJson(json)
            }
        }
    }

    fun isTrafficCooldown(error: Throwable): Boolean = error is ChaoxingTrafficCooldownException

    fun isExplicitAuthExpiry(error: Throwable): Boolean = error is ChaoxingAuthExpiredException

    // ══════════════════════════════════════════════════════════════
    //  登录
    // ══════════════════════════════════════════════════════════════

    /**
     * 超星登录。
     *
     * POST https://passport2.chaoxing.com/fanyalogin
     * 参数: fid=-1, uname=AES(手机号), password=AES(密码), refer=..., t=true
     */
    suspend fun login(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        selectTrafficAccount(username)
        try {
            val encUser = AESCipher.encrypt(username)
            val encPass = AESCipher.encrypt(password)

            val body = FormBody.Builder()
                .add("fid", "-1")
                .add("uname", encUser)
                .add("password", encPass)
                .add("refer", "https%3A%2F%2Fi.chaoxing.com")
                .add("t", "true")
                .add("forbidotherlogin", "0")
                .add("validate", "")
                .add("doubleFactorLogin", "0")
                .add("independentId", "0")
                .build()

            val request = Request.Builder()
                .url("$BASE_PASSPORT/fanyalogin")
                .post(body)
                .header("User-Agent", UA_FIXED)
                .build()

            val resp = client.newCall(request).awaitResponse()
            val code = resp.code
            val text = resp.body?.string() ?: "{}"
            resp.close()
            if (code !in 200..299) {
                return@withContext Result.failure(IOException("登录失败: HTTP $code"))
            }
            val json = JsonParser.parseString(text).asJsonObject

            if (json.has("status") && json.get("status").asBoolean) {
                Log.i(TAG, "登录成功")
                flushCookies()
                Result.success("登录成功")
            } else {
                val msg = json.str("msg2").ifBlank { json.str("msg") }.ifBlank { "未知错误" }
                Log.w(TAG, "登录失败: server rejected credentials")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "登录异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    /**
     * 校验当前 Cookie 是否仍然有效。
     */
    suspend fun validateSessionResult(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("courseType", "1")
                .add("courseFolderId", "0")
                .add("query", "")
                .add("superstarClass", "0")
                .build()

            val request = Request.Builder()
                .url("$BASE_MOOC2/mooc2-ans/visit/courselistdata")
                .post(body)
                .header("User-Agent", userAgent())
                .build()

            val resp = client.newCall(request).awaitResponse()
            val code = resp.code
            val text = resp.body?.string() ?: ""
            resp.close()

            if (code !in 200..299) {
                return@withContext Result.failure(IOException("校验会话失败: HTTP $code"))
            }
            if (looksLikeRiskPage(text)) {
                return@withContext Result.failure(IOException("校验会话失败: 访问限制页面"))
            }
            if (looksLikeLoginPage(text)) return@withContext Result.success(false)
            if (!looksLikeCourseListDocument(text)) {
                return@withContext Result.failure(IOException("session validation returned an unexpected document"))
            }
            cachedCourseList = decodeCourseList(text)
            cachedCourseListAt = System.currentTimeMillis()
            Result.success(true)
        } catch (_: ChaoxingAuthExpiredException) {
            // The response interceptor recognizes login forms before this method can
            // inspect the body. This is the one failure that means "definitely logged
            // out"; rate limits, challenges and network errors must remain failures.
            Result.success(false)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "校验会话异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    suspend fun validateSession(): Boolean = validateSessionResult().getOrDefault(false)

    // ══════════════════════════════════════════════════════════════
    //  课程列表
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取所有课程列表。
     *
     * POST https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata
     * 响应: HTML → Jsoup 解析 div.course
     */
    suspend fun getCourseList(forceRefresh: Boolean = false): Result<List<CxCourse>> = withContext(Dispatchers.IO) {
        try {
            val cached = cachedCourseList
            if (!forceRefresh && cached != null && System.currentTimeMillis() - cachedCourseListAt < 5 * 60_000L) {
                return@withContext Result.success(cached)
            }
            val body = FormBody.Builder()
                .add("courseType", "1")
                .add("courseFolderId", "0")
                .add("query", "")
                .add("superstarClass", "0")
                .build()

            val request = Request.Builder()
                .url("$BASE_MOOC2/mooc2-ans/visit/courselistdata")
                .post(body)
                .header("User-Agent", userAgent())
                .header("Referer", "$BASE_MOOC2/mooc2-ans/visit/interaction?moocDomain=https://mooc1-1.chaoxing.com/mooc-ans")
                .build()

            val resp = client.newCall(request).awaitResponse()
            val code = resp.code
            val html = resp.body?.string() ?: ""
            resp.close()

            if (code !in 200..299) {
                return@withContext Result.failure(IOException("获取课程列表失败: HTTP $code"))
            }
            if (looksLikeBlockedOrLoginPage(html)) {
                return@withContext Result.failure(IOException("获取课程列表失败: 登录或访问限制页面"))
            }

            if (!looksLikeCourseListDocument(html)) {
                return@withContext Result.failure(IOException("course list returned an unexpected document"))
            }
            val courses = decodeCourseList(html)
            cachedCourseList = courses
            cachedCourseListAt = System.currentTimeMillis()
            Log.i(TAG, "获取课程列表成功: ${courses.size} 门")
            Result.success(courses)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "获取课程列表异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  章节列表
    // ══════════════════════════════════════════════════════════════

    /**
     * 批量获取所有课程的任务点进度（严格串行）。
     *
     * 遍历每门课 -> 拉取章节列表 -> 汇总 jobCount / hasFinished。
     *
     * @return Map<courseKey, CxCourseProgress>  其中 key 为 "${courseId}_${clazzId}"
     */
    suspend fun getAllCoursesProgress(courses: List<CxCourse>): Map<String, CxCourseProgress> =
        withContext(Dispatchers.IO) {
            val progress = linkedMapOf<String, CxCourseProgress>()
            // Keep this deliberately sequential. A progress refresh is an optional
            // fan-out operation; concurrent chapter requests add load without improving
            // the information shown to the user. A typed restriction is terminal for the
            // refresh so later courses do not continue probing the same account.
            for (course in courses) {
                val key = course.courseId + "_" + course.clazzId
                val points = getCoursePoints(course).getOrThrow().points
                if (points.isEmpty()) {
                    progress[key] = CxCourseProgress()
                    continue
                }

                val totalJobs = points.sumOf { it.jobCount }
                val completedJobs = points.sumOf { if (it.hasFinished) it.jobCount else 0 }
                progress[key] = CxCourseProgress(
                    totalJobs = totalJobs,
                    completedJobs = minOf(completedJobs, totalJobs),
                )
            }
            progress
        }

    /**
     * 获取课程的所有章节。
     *
     * GET https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse?courseid=X&clazzid=Y&cpi=Z&ut=s
     */
    suspend fun getCoursePoints(
        course: CxCourse,
        forceRefresh: Boolean = false,
    ): Result<CxCoursePoints> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "${course.courseId}_${course.clazzId}"
            val cached = coursePointsCache[cacheKey]
            if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.storedAt < 2 * 60_000L) {
                return@withContext Result.success(cached.value)
            }
            val url = "$BASE_MOOC2/mooc2-ans/mycourse/studentcourse" +
                "?courseid=${course.courseId}&clazzid=${course.clazzId}&cpi=${course.cpi}&ut=s"

            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent())
                .build()

            val resp = client.newCall(request).awaitResponse()
            val code = resp.code
            val html = resp.body?.string() ?: ""
            resp.close()

            if (code !in 200..299) {
                return@withContext Result.failure(IOException("获取章节失败: HTTP $code"))
            }
            if (looksLikeBlockedOrLoginPage(html)) {
                return@withContext Result.failure(IOException("获取章节失败: 登录或访问限制页面"))
            }

            val points = decodeCoursePoints(html)
            coursePointsCache[cacheKey] = CacheEntry(points, System.currentTimeMillis())
            Log.i(TAG, "获取章节成功: ${points.points.size} 个")
            Result.success(points)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "获取章节异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  任务点卡片
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取章节的所有任务点。
     *
     * GET https://mooc1.chaoxing.com/mooc-ans/knowledge/cards
     * 响应: JS+HTML → 正则提取 mArg JSON → 解析 attachments
     */
    suspend fun getJobList(
        course: CxCourse,
        chapter: CxChapter,
        forceRefresh: Boolean = false,
    ): Result<Pair<List<CxJob>, CxJobInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val cacheKey = "${course.courseId}_${course.clazzId}_${chapter.id}"
                val cached = jobListCache[cacheKey]
                if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.storedAt < 10 * 60_000L) {
                    return@withContext Result.success(cached.value)
                }
                val allJobs = mutableListOf<CxJob>()
                var jobInfo = CxJobInfo()
                val seenJobs = mutableSetOf<String>()
                val expectedJobs = chapter.jobCount.coerceAtLeast(0)
                var sawStructuredCard = false

                // Card count is not present on every course page. Walk forward only while
                // each response contributes a new card, and stop on the first terminal card.
                for (num in 0..6) {
                    val url = "$BASE_MOOC1/mooc-ans/knowledge/cards" +
                        "?clazzid=${course.clazzId}&courseid=${course.courseId}" +
                        "&knowledgeid=${chapter.id}&ut=s&cpi=${course.cpi}" +
                        "&v=2025-0424-1038-3&mooc2=1&num=$num"

                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .header("User-Agent", userAgent())
                        .header("Referer", "https://mooc2-ans.chaoxing.com/mycourse/studentcourse")
                        .build()

                    val resp = client.newCall(request).awaitResponse()
                    val code = resp.code
                    val html = resp.body?.string() ?: ""
                    resp.close()
                    Log.d(TAG, "getJobList num=$num code=$code htmlLen=${html.length}")

                    if (code !in 200..299) {
                        return@withContext Result.failure(IOException("获取任务卡失败: HTTP $code"))
                    }
                    if (looksLikeBlockedOrLoginPage(html)) {
                        return@withContext Result.failure(IOException("获取任务卡失败: 登录或访问限制页面"))
                    }

                    // 检查章节未开放
                    if (html.contains("章节未开放")) {
                        return@withContext Result.success(Pair(emptyList(), CxJobInfo()))
                    }

                    val hasStructuredCard = extractMArgJson(html) != null
                    if (!hasStructuredCard) {
                        if (num == 0 && expectedJobs > 0 && !chapter.hasFinished) {
                            return@withContext Result.failure(
                                IOException("获取任务卡失败: 响应缺少任务卡数据"),
                            )
                        }
                        break
                    }
                    sawStructuredCard = true

                    val (jobs, info) = decodeCourseCard(html)
                    if (info.knowledgeid.isNotEmpty()) {
                        jobInfo = info
                    }
                    val freshJobs = jobs.filter { job ->
                        seenJobs.add("${job.type}:${job.jobid}:${job.objectid}")
                    }
                    if (freshJobs.isEmpty()) break
                    allJobs.addAll(freshJobs)
                    if (expectedJobs > 0 && allJobs.size >= expectedJobs) break
                }

                Log.i(TAG, "获取任务点成功: ${allJobs.size} 个")
                val result = Pair(allJobs, jobInfo)
                if (sawStructuredCard || expectedJobs == 0) {
                    jobListCache[cacheKey] = CacheEntry(result, System.currentTimeMillis())
                }
                Result.success(result)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "获取任务点异常" + ": " + e.javaClass.simpleName)
                Result.failure(e)
            }
        }

    // ══════════════════════════════════════════════════════════════
    //  视频信息
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取视频元信息。
     *
     * GET https://mooc1.chaoxing.com/ananas/status/{objectid}?k={fid}&flag=normal
     */
    suspend fun getVideoInfo(objectId: String): Result<CxVideoInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_MOOC1/ananas/status/$objectId?k=${getFid()}&flag=normal"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent())
                .header("Referer", "https://mooc1.chaoxing.com/ananas/modules/video/index.html")
                .build()

            val resp = client.newCall(request).awaitResponse()
            val code = resp.code
            val json = resp.body?.string() ?: "{}"
            resp.close()

            if (code !in 200..299) {
                return@withContext Result.failure(IOException("获取视频信息失败: HTTP $code"))
            }
            if (looksLikeBlockedOrLoginPage(json)) {
                return@withContext Result.failure(IOException("获取视频信息失败: 登录或访问限制页面"))
            }

            val obj = JsonParser.parseString(json).asJsonObject
            val info = CxVideoInfo(
                dtoken = obj.str("dtoken"),
                duration = obj.get("duration")?.asInt ?: 0,
                crc = obj.str("crc"),
                key = obj.str("key"),
                status = obj.str("status"),
                filename = obj.str("filename"),
            )

            if (info.status == "success") {
                Result.success(info)
            } else {
                Result.failure(Exception("视频状态异常: ${info.status}"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "获取视频信息异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  视频进度上报
    // ══════════════════════════════════════════════════════════════

    /**
     * 上报视频播放进度。
     *
     * GET https://mooc1.chaoxing.com/mooc-ans/multimedia/log/a/{cpi}/{dtoken}
     * 返回: {"isPassed": true/false}
     */
    suspend fun reportVideoProgress(
        course: CxCourse,
        job: CxJob,
        jobInfo: CxJobInfo,
        dtoken: String,
        duration: Int,
        playingTime: Int,
        isdrag: Int = 3,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val enc = getEnc(course.clazzId, job.jobid, job.objectid, playingTime, duration, getUid())

            // Resolve rt once. Access-control failures must never trigger parameter probing.
            var rt = job.rt
            if (rt.isBlank()) {
                val rtMatch = Regex("""-rt_([1d])""").find(job.otherinfo)
                if (rtMatch != null) {
                    rt = if (rtMatch.groupValues[1] == "d") "0.9" else "1"
                }
            }
            if (rt.isBlank()) rt = "1"

            val url = "$BASE_MOOC1/mooc-ans/multimedia/log/a/${course.cpi}/$dtoken" +
                "?clazzId=${course.clazzId}&playingTime=$playingTime&duration=$duration" +
                "&clipTime=0_$duration&objectId=${job.objectid}" +
                "&otherInfo=${job.otherinfo}&courseId=${course.courseId}" +
                "&jobid=${job.jobid}&userid=${getUid()}" +
                "&isdrag=$isdrag&view=pc&enc=$enc&dtype=Video" +
                "&rt=$rt&_t=${System.currentTimeMillis()}"

            val extraParams = buildString {
                if (job.videoFaceCaptureEnc.isNotEmpty()) append("&videoFaceCaptureEnc=${job.videoFaceCaptureEnc}")
                if (job.attDuration.isNotEmpty()) append("&attDuration=${job.attDuration}")
                if (job.attDurationEnc.isNotEmpty()) append("&attDurationEnc=${job.attDurationEnc}")
            }

            val request = Request.Builder()
                .url(url + extraParams)
                .get()
                .header("User-Agent", userAgent())
                .header("Referer", "https://mooc1.chaoxing.com/ananas/modules/video/index.html")
                .build()

            val resp = client.newCall(request).awaitResponse()
            val code = resp.code
            val text = resp.body?.string() ?: ""
            resp.close()

            if (code != 200) {
                return@withContext Result.failure(IOException("视频进度上报失败: HTTP $code"))
            }
            if (looksLikeBlockedOrLoginPage(text)) {
                return@withContext Result.failure(IOException("视频进度上报失败: 登录或访问限制页面"))
            }
            val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                ?: return@withContext Result.failure(IOException("视频进度上报失败: 非预期响应"))
            val passed = json.get("isPassed")
                ?: return@withContext Result.failure(IOException("视频进度上报失败: 缺少 isPassed"))
            Result.success(passed.asBoolean)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "视频进度上报异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  章节检测（答题）
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取章节检测题目。
     *
     * GET https://mooc1.chaoxing.com/mooc-ans/api/work
     */
    suspend fun getWorkQuestions(
        course: CxCourse,
        job: CxJob,
        jobInfo: CxJobInfo,
    ): Result<CxWorkData> = withContext(Dispatchers.IO) {
        // Automatic retries are intentionally disabled. A user refresh is safer than
        // multiplying requests after an ambiguous server or network failure.
        val maxAttempts = 1
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                val url = "$BASE_MOOC1/mooc-ans/api/work" +
                    "?api=1&workId=${job.jobid.removePrefix("work-")}" +
                    "&jobid=${job.jobid}&originJobId=${job.jobid}" +
                    "&needRedirect=true&skipHeader=true" +
                    "&knowledgeid=${jobInfo.knowledgeid}&ktoken=${jobInfo.ktoken}" +
                    "&cpi=${jobInfo.cpi}&ut=s&clazzId=${course.clazzId}" +
                    "&type=&enc=${job.enc}&mooc2=1&courseid=${course.courseId}"

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", userAgent())
                    .build()

                val resp = client.newCall(request).awaitResponse()
                val html = resp.body?.string() ?: ""
                val code = resp.code
                resp.close()

                if (looksLikeBlockedOrLoginPage(html)) {
                    return@withContext Result.failure(IOException("获取题目失败: 登录或访问限制页面"))
                }

                // 检查"教师未创建完成该测验" (与 Python study_work 一致)
                if (html.contains("教师未创建完成该测验")) {
                    Log.w(TAG, "获取题目: 教师未创建完成该测验")
                    return@withContext Result.failure(IllegalStateException("教师未创建完成该测验，暂无法作答"))
                }

                if (code != 200 || html.isBlank()) {
                    val retryable = code in 500..599 || (code == 200 && html.isBlank())
                    if (retryable && attempt < maxAttempts) {
                        val delayMs = 2_000L
                        Log.w(TAG, "获取题目短暂失败 HTTP $code, ${delayMs}ms 后重试")
                        delay(delayMs)
                        continue
                    }
                    return@withContext Result.failure(IOException("获取题目失败: HTTP $code"))
                }

                val workData = decodeQuestions(html)
                if (workData.questions.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("题目解析失败：未找到题目"))
                }

                Log.i(TAG, "获取题目成功: ${workData.questions.size} 道")
                return@withContext Result.success(workData)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastException = e
                if (attempt < maxAttempts && e is IOException && !isNonRetryableCxFailure(e)) {
                    val delayMs = 2_000L
                    Log.w(TAG, "获取题目网络异常: ${e.javaClass.simpleName}, ${delayMs}ms 后重试")
                    delay(delayMs)
                } else {
                    break
                }
            }
        }

        Log.e(TAG, "获取题目失败: ${lastException?.javaClass?.simpleName ?: "unknown"}")
        Result.failure(lastException ?: IOException("获取题目失败"))
    }

    /**
     * 提交/保存章节检测答案。
     *
     * POST https://mooc1.chaoxing.com/mooc-ans/work/addStudentWorkNew
     */
    /**
     * 提交/保存章节检测答案。
     *
     * POST https://mooc1.chaoxing.com/mooc-ans/work/addStudentWorkNew
     *
     * 与 Python study_work 提交逻辑完全对齐:
     *   1. 先加 answerwqbid + pyFlag
     *   2. 再加所有表单隐藏字段 (formFields)
     *   3. 最后逐题加 answer{id} + answertype{id}
     */
    suspend fun submitWork(workData: CxWorkData): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formBuilder = FormBody.Builder()

            // 与 Python 一致: 先加 answerwqbid 和 pyFlag
            formBuilder.add("answerwqbid", workData.answerwqbid)
            formBuilder.add("pyFlag", workData.pyFlag)

            // 再加所有表单隐藏字段 (跳过 pyFlag 避免重复)
            for ((k, v) in workData.formFields) {
                if (k == "pyFlag") continue
                formBuilder.add(k, v)
            }

            // 防御性: 从 question.answerField 补充答案字段
            // 关键：跳过 answer{qId}（答案已在 formFields 中由 studyWork 填入），只补缺少的元数据
            val addedKeys = mutableSetOf<String>()
            // formFields 已添加的 key 集合
            addedKeys.addAll(workData.formFields.keys)
            addedKeys.add("answerwqbid")
            addedKeys.add("pyFlag")
            for (q in workData.questions) {
                for ((key, value) in q.answerField) {
                    // answer{qId} 已在 formFields 中有正确答案，跳过避免空值覆盖
                    if (key in addedKeys) continue
                    if (value.isBlank()) continue
                    formBuilder.add(key, value)
                    addedKeys.add(key)
                }
            }

            val body = formBuilder.build()
            Log.i(TAG, "[submit] fields=${body.size}")

            val request = Request.Builder()
                .url("$BASE_MOOC1/mooc-ans/work/addStudentWorkNew")
                .post(body)
                .header("User-Agent", UA_FIXED)
                .build()

            val resp = client.newCall(request).awaitResponse()
            val code = resp.code
            val json = resp.body?.string() ?: "{}"
            resp.close()

            Log.i(TAG, "[submit] HTTP $code, response body omitted")

            if (code !in 200..299) {
                return@withContext Result.failure(IOException("提交失败: HTTP $code"))
            }
            if (looksLikeBlockedOrLoginPage(json)) {
                return@withContext Result.failure(IOException("提交失败: 登录或访问限制页面"))
            }

            val obj = JsonParser.parseString(json).asJsonObject
            if (obj.get("status")?.asBoolean == true) {
                Result.success(obj.str("msg").ifBlank { "成功" })
            } else {
                val errMsg = obj.str("msg").ifBlank { "提交失败: server did not confirm success" }
                Log.e(TAG, "[submit] server rejected request")
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "提交答案异常: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Disabled synthetic completion operations
    // ══════════════════════════════════════════════════════════════

    @Deprecated("Instant document completion is disabled")
    suspend fun studyDocument(course: CxCourse, job: CxJob, jobInfo: CxJobInfo): Result<Unit> =
        disabledCompletionResult()

    @Deprecated("Instant reading completion is disabled")
    suspend fun studyRead(course: CxCourse, job: CxJob, jobInfo: CxJobInfo): Result<Unit> =
        disabledCompletionResult()

    @Deprecated("Instant audio completion is disabled")
    suspend fun studyAudio(course: CxCourse, job: CxJob, jobInfo: CxJobInfo): Result<Unit> =
        disabledCompletionResult()

    @Deprecated("Instant live completion is disabled")
    suspend fun studyLive(course: CxCourse, job: CxJob, jobInfo: CxJobInfo): Result<Unit> =
        disabledCompletionResult()

    @Deprecated("Synthetic empty-page completion is disabled")
    suspend fun studyEmptyPage(course: CxCourse, chapter: CxChapter): Result<Unit> =
        disabledCompletionResult()

    private fun disabledCompletionResult(): Result<Unit> = Result.failure(
        UnsupportedOperationException("后台瞬时完成任务已禁用"),
    )

    /** Disabled: synthetic visit-count mutations are not a supported client operation. */
    @Deprecated("Synthetic visit-count reporting is disabled")
    suspend fun brushVisitCount(course: CxCourse, chapter: CxChapter): Result<Unit> =
        Result.failure(UnsupportedOperationException("刷访问次数功能已禁用"))

    /**
     * 获取课程章节的资源附件列表。
     * 解析 studentstudyAjax 页面中的附件链接（MP4/PDF/PPTX/PNG/DOC/ZIP 等）。
     */
    suspend fun getCourseResources(
        course: CxCourse, chapter: CxChapter
    ): Result<List<CxAttachment>> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "${course.courseId}_${course.clazzId}_${chapter.id}"
            val cached = resourceCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.storedAt < 10 * 60_000L) {
                return@withContext Result.success(cached.value)
            }
            val url = "$BASE_MOOC1/mooc-ans/mycourse/studentstudyAjax" +
                "?courseId=${course.courseId}&clazzid=${course.clazzId}" +
                "&chapterId=${chapter.id}&cpi=${course.cpi}" +
                "&verificationcode=&mooc2=1&microTopicId=0&editorPreview=0"

            val request = Request.Builder().url(url).get().header("User-Agent", userAgent()).build()
            val resp = client.newCall(request).awaitResponse()
            val code = resp.code
            val html = resp.use { it.body?.string().orEmpty() }
            if (code !in 200..299) {
                return@withContext Result.failure(IOException("resource list returned HTTP $code"))
            }
            if (looksLikeBlockedOrLoginPage(html)) {
                return@withContext Result.failure(IOException("resource list returned a login or access-control page"))
            }

            val doc = org.jsoup.Jsoup.parse(html)
            val allowedExts = setOf("mp4", "pdf", "pptx", "ppt", "png", "jpg", "doc", "docx", "zip", "rar")
            val resources = doc.select("a[href]").mapNotNull { el ->
                val href = el.attr("href")
                val name = el.text().trim()
                if (href.isBlank() || name.isBlank()) return@mapNotNull null
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in allowedExts && !href.contains("/download/") && !href.contains("attachment"))
                    return@mapNotNull null
                CxAttachment(
                    name = name,
                    preview = if (href.startsWith("http")) href
                    else if (href.startsWith("//")) "https:$href"
                    else "$BASE_MOOC1$href",
                    suffix = ".$ext",
                    objectId = "",
                )
            }
            resourceCache[cacheKey] = CacheEntry(resources, System.currentTimeMillis())
            Result.success(resources)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    /**
     * 下载资源文件到外部存储。
     * 返回本地文件路径。
     */
    suspend fun downloadResource(
        context: android.content.Context,
        url: String,
        fileName: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val parsedUrl = runCatching { url.toHttpUrl() }
                .getOrElse { return@withContext Result.failure(IOException("invalid resource URL")) }
            if (parsedUrl.scheme !in setOf("http", "https") ||
                !(parsedUrl.host == "chaoxing.com" || parsedUrl.host.endsWith(".chaoxing.com") ||
                    parsedUrl.host == "cldisk.com" || parsedUrl.host.endsWith(".cldisk.com"))
            ) {
                return@withContext Result.failure(IOException("resource host is not allowed"))
            }
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) {
                return@withContext Result.failure(IOException("resource directory is unavailable"))
            }
            val baseName = fileName.substringAfterLast('/').substringAfterLast('\\')
            val safeName = baseName.replace(Regex("""[\\/:*?"<>|]"""), "_")
                .trim().ifBlank { "resource_${System.currentTimeMillis()}" }
                .let { if (it == "." || it == "..") "resource_${System.currentTimeMillis()}" else it }
            val file = java.io.File(dir, safeName)
            if (file.isFile && file.length() > 0L) {
                return@withContext Result.success(file.absolutePath)
            }
            val partFile = java.io.File(dir, ".${safeName}.${System.nanoTime()}.part")
            val request = Request.Builder().url(parsedUrl).get()
                .header("User-Agent", userAgent())
                .header("Referer", "https://mooc1.chaoxing.com/")
                .build()
            val response = client.newCall(request).awaitResponse()
            val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { response.close() }
            try {
                val code = response.code
                if (code !in 200..299) {
                    return@withContext Result.failure(IOException("resource download returned HTTP $code"))
                }
                val sample = runCatching { response.peekBody(16L * 1024L).string() }.getOrDefault("")
                if (looksLikeBlockedOrLoginPage(sample)) {
                    return@withContext Result.failure(IOException("resource download returned a login or access-control page"))
                }
                val body = response.body
                    ?: return@withContext Result.failure(IOException("resource response body is empty"))
                try {
                    body.byteStream().use { input ->
                        partFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    partFile.delete()
                    throw e
                }
                if (file.exists() && file.length() > 0L) {
                    partFile.delete()
                    return@withContext Result.success(file.absolutePath)
                }
                if (file.exists()) file.delete()
                if (!partFile.renameTo(file)) {
                    partFile.delete()
                    return@withContext Result.failure(IOException("resource file could not be finalized"))
                }
                Result.success(file.absolutePath)
            } finally {
                cancellationHandle?.dispose()
                response.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  签到
    // ══════════════════════════════════════════════════════════════

    /** 获取签到活动列表 */
    suspend fun getActivityList(
        course: CxCourse,
        forceRefresh: Boolean = false,
    ): Result<List<CxActivity>> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "${course.courseId}_${course.clazzId}"
            val cached = activityCache[cacheKey]
            if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.storedAt < 30_000L) {
                return@withContext Result.success(cached.value)
            }
            val url = "$BASE_MOBILE/v2/apis/active/student/activelist" +
                "?fid=${getFid()}&courseId=${course.courseId}&classId=${course.clazzId}" +
                "&showNotStartedActive=0&_=${System.currentTimeMillis()}"

            val request = Request.Builder().url(url).get().header("User-Agent", userAgent()).build()
            val resp = client.newCall(request).awaitResponse()
            val code = resp.code
            val json = resp.body?.string() ?: "{}"
            resp.close()

            if (code !in 200..299) {
                return@withContext Result.failure(IOException("获取活动失败: HTTP $code"))
            }
            if (looksLikeBlockedOrLoginPage(json)) {
                return@withContext Result.failure(IOException("获取活动失败: 登录或访问限制页面"))
            }

            val obj = JsonParser.parseString(json).asJsonObject
            if (obj.get("result")?.asInt != 1) {
                return@withContext Result.failure(IOException("获取活动失败: 服务端未确认成功"))
            }

            val list = obj.getAsJsonObject("data").getAsJsonArray("activeList")
            val activities = list.map { el ->
                val o = el.asJsonObject
                val otherInfo = o.str("otherInfo")
                // 解析签到类型:otherInfo 里类似 "type=4&..." 或顶级 type 字段
                val typeCode = Regex("""type=(\d+)""").find(otherInfo)?.groupValues?.get(1)?.toIntOrNull()
                    ?: o.get("type")?.asInt
                    ?: 0
                CxActivity(
                    id = o.get("id")?.asLong ?: 0,
                    name = o.str("nameOne").ifBlank { o.str("name") },
                    type = o.get("type")?.asInt ?: 0,
                    status = o.get("status")?.asInt ?: 0,
                    courseId = course.courseId,
                    classId = course.clazzId,
                    startTime = o.get("startTime")?.asLong ?: 0,
                    endTime = o.get("endTime")?.asLong ?: 0,
                    signType = com.ahu_plus.data.model.CxSignType.fromCode(typeCode),
                )
            }
            activityCache[cacheKey] = CacheEntry(activities, System.currentTimeMillis())
            Result.success(activities)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "获取签到活动异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    /**
     * preSign 前置签名(2026-06-24 改为解析真实签到子类型)。
     *
     * preSign 响应是 HTML,内联 JS / hidden 字段含 `otherId` + `ifphoto`,
     * 据此判定 6 种签到子类型(activelist 的 type 不可靠)。
     * 某些签到(尤其位置)也需先 preSign 再 stuSignajax。
     */
    suspend fun preSign(course: CxCourse, activityId: Long): Result<CxPreSignInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_MOBILE/newsign/preSign" +
                "?general=1&sys=1&ls=1&appType=15&tid=&ut=s" +
                "&uid=${getUid()}&activePrimaryId=$activityId" +
                "&courseId=${course.courseId}&classId=${course.clazzId}"

            val request = Request.Builder().url(url).get().header("User-Agent", userAgent()).build()
            val resp = client.newCall(request).awaitResponse()
            val code = resp.code
            val text = resp.body?.string() ?: ""
            resp.close()

            if (code !in 200..299) {
                return@withContext Result.failure(IOException("preSign 失败: HTTP $code"))
            }
            if (looksLikeBlockedOrLoginPage(text)) {
                return@withContext Result.failure(IOException("preSign 失败: 登录或访问限制页面"))
            }
            val looksLikeSignPage = text.contains("otherId", ignoreCase = true) ||
                text.contains("ifphoto", ignoreCase = true) ||
                text.contains("签到") ||
                text.contains("activePrimaryId", ignoreCase = true)
            if (!looksLikeSignPage) {
                return@withContext Result.failure(IOException("preSign 失败: 非预期响应"))
            }

            // otherId / ifphoto 可能形如 otherId=3 / "otherId":3 / ifphoto=1,做宽松匹配
            val otherId = Regex("""otherId['"]?\s*[=:]\s*['"]?(\d+)""").find(text)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val ifPhoto = Regex("""ifphoto['"]?\s*[=:]\s*['"]?(\d+)""", RegexOption.IGNORE_CASE).find(text)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val signType = CxSignType.fromPreSign(otherId, ifPhoto)
            Log.i(TAG, "preSign parsed: type=$signType")
            Result.success(CxPreSignInfo(signType = signType, otherId = otherId, ifPhoto = ifPhoto, raw = text))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "preSign 异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    private suspend fun executeSignRequest(label: String, url: String): Result<String> {
        return try {
            val request = Request.Builder().url(url).get().header("User-Agent", userAgent()).build()
            val resp = client.newCall(request).awaitResponse()
            val code = resp.code
            val text = resp.body?.string().orEmpty()
            resp.close()

            if (code !in 200..299) {
                return Result.failure(IOException("$label 失败: HTTP $code"))
            }
            if (looksLikeBlockedOrLoginPage(text)) {
                return Result.failure(IOException("$label 失败: 登录或访问限制页面"))
            }
            val confirmed = text.contains("成功") ||
                text.contains("success", ignoreCase = true) ||
                text.contains("已签到") ||
                text.contains("already", ignoreCase = true)
            if (!confirmed) {
                return Result.failure(IOException("$label 结果未确认: ${text.take(80)}"))
            }
            Result.success(text)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "$label 异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    /**
     * 位置签到(2026-06-20 集成 Phase 3,移植自 base.py:sign_in_normal + type_=LOCATION)。
     *
     * 携带真实经纬度 + 地址名称 + objectId。
     */
    suspend fun signInLocation(
        course: CxCourse,
        activityId: Long,
        latitude: Double,
        longitude: Double,
        address: String = "",
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = "$BASE_MOBILE/pptSign/stuSignajax" +
            "?activeId=$activityId&uid=${getUid()}&fid=${getFid()}" +
            "&courseId=${course.courseId}&classId=${course.clazzId}" +
            "&clientip=&objectId=aaa" +
            "&name=${java.net.URLEncoder.encode(address, "UTF-8")}" +
            "&useragent=&latitude=$latitude&longitude=$longitude&appType=15"
        executeSignRequest("位置签到", url)
    }

    /**
     * 手势签到(2026-06-20 集成 Phase 3,移植自 base.py:SignType.GESTURE)。
     *
     * 超星手势签到通常需要提交一个手势编码字符串(学号 / 姓名首字母等),
     * 编码由用户在设置中自行配置。
     */
    suspend fun signInGesture(
        course: CxCourse,
        activityId: Long,
        gestureCode: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = "$BASE_MOBILE/pptSign/stuSignajax" +
            "?activeId=$activityId&uid=${getUid()}&fid=${getFid()}" +
            "&courseId=${course.courseId}&classId=${course.clazzId}" +
            "&clientip=&objectId=aaa" +
            "&name=${java.net.URLEncoder.encode(gestureCode, "UTF-8")}" +
            "&useragent=&latitude=-1&longitude=-1&appType=15&signCode=${java.net.URLEncoder.encode(gestureCode, "UTF-8")}"
        executeSignRequest("手势签到", url)
    }

    /** 执行普通签到 */
    suspend fun signNormal(course: CxCourse, activityId: Long): Result<String> = withContext(Dispatchers.IO) {
        val url = "$BASE_MOBILE/pptSign/stuSignajax" +
            "?activeId=$activityId&uid=${getUid()}&fid=${getFid()}" +
            "&courseId=${course.courseId}&classId=${course.clazzId}" +
            "&clientip=&objectId=aaa&name=&useragent=&latitude=-1&longitude=-1&appType=15"
        executeSignRequest("签到", url)
    }

    /**
     * 签到码签到(2026-06-24)。老师口播一个数字码,用户输入后提交。
     *
     * TODO(抓包校准):signCode 参数名与是否需 name 字段未经真机确认,
     * 当前按社区公认形态(signCode=<code>)实现,待用测试账号抓真实请求修正。
     */
    suspend fun signInSignCode(course: CxCourse, activityId: Long, code: String): Result<String> = withContext(Dispatchers.IO) {
        val url = "$BASE_MOBILE/pptSign/stuSignajax" +
            "?activeId=$activityId&uid=${getUid()}&fid=${getFid()}" +
            "&courseId=${course.courseId}&classId=${course.clazzId}" +
            "&clientip=&objectId=&name=&useragent=&latitude=-1&longitude=-1&appType=15" +
            "&signCode=${java.net.URLEncoder.encode(code, "UTF-8")}"
        executeSignRequest("签到码签到", url)
    }

    /**
     * 二维码签到(2026-06-24)。扫码得到的 URL 含 `enc` 参数,提交时携带。
     * 二维码签到通常也是位置签到的变体,故一并带经纬度(可由调用方传入)。
     *
     * TODO(抓包校准):enc 是否需配合 preSign 的其他字段未确认,
     * 当前按 enc + 经纬度形态实现。
     */
    suspend fun signInQrCode(
        course: CxCourse,
        activityId: Long,
        enc: String,
        latitude: Double = -1.0,
        longitude: Double = -1.0,
        address: String = "",
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = "$BASE_MOBILE/pptSign/stuSignajax" +
            "?activeId=$activityId&uid=${getUid()}&fid=${getFid()}" +
            "&courseId=${course.courseId}&classId=${course.clazzId}" +
            "&clientip=&objectId=&useragent=&appType=15" +
            "&enc=${java.net.URLEncoder.encode(enc, "UTF-8")}" +
            "&name=${java.net.URLEncoder.encode(address, "UTF-8")}" +
            "&latitude=$latitude&longitude=$longitude"
        executeSignRequest("二维码签到", url)
    }

    /**
     * 拍照签到(2026-06-24)。先上传图片到云盘拿 objectId(复用 [uploadHomeworkFile]),
     * 再带 objectId 提交。
     *
     * TODO(抓包校准):拍照签到上传是否走与作业相同的云盘端点未经确认,
     * 当前复用 uploadHomeworkFile 的 objectId,待真机验证。
     */
    suspend fun signInPhoto(
        course: CxCourse,
        activityId: Long,
        objectId: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = "$BASE_MOBILE/pptSign/stuSignajax" +
            "?activeId=$activityId&uid=${getUid()}&fid=${getFid()}" +
            "&courseId=${course.courseId}&classId=${course.clazzId}" +
            "&clientip=&objectId=${java.net.URLEncoder.encode(objectId, "UTF-8")}" +
            "&name=&useragent=&latitude=-1&longitude=-1&appType=15"
        executeSignRequest("拍照签到", url)
    }

    // ══════════════════════════════════════════════════════════════
    //  工具方法
    // ══════════════════════════════════════════════════════════════

    private fun looksLikeLoginPage(body: String): Boolean {
        if (body.isBlank()) return false
        val lower = body.lowercase()
        return lower.contains("passport2.chaoxing.com") ||
            (lower.contains("fanyalogin") && lower.contains("uname")) ||
            (lower.contains("name=\"password\"") && lower.contains("name=\"uname\""))
    }

    private fun looksLikeRiskPage(body: String): Boolean {
        if (body.isBlank()) return false
        val lower = body.lowercase()
        return lower.contains("captcha") ||
            lower.contains("verifycode") ||
            lower.contains("validatecode") ||
            body.contains("访问过于频繁") ||
            body.contains("操作过于频繁") ||
            body.contains("访问受限") ||
            body.contains("安全验证") ||
            body.contains("账号异常")
    }

    private fun looksLikeBlockedOrLoginPage(body: String): Boolean =
        looksLikeLoginPage(body) || looksLikeRiskPage(body)

    /**
     * Course-list endpoints normally return an HTML shell even when the account
     * has no enrolled courses. Treat an unrelated 2xx document as a failure so a
     * malformed/login proxy page cannot be cached as an empty course list.
     */
    private fun looksLikeCourseListDocument(body: String): Boolean {
        if (body.isBlank()) return false
        val lower = body.lowercase()
        val doc = Jsoup.parse(body)
        if (doc.select("div.course, li.course, .course-list, .courseList, #courseList").isNotEmpty()) {
            return true
        }
        if (doc.select("input.courseId, input.clazzId, [data-courseid], [data-clazzid]").isNotEmpty()) {
            return true
        }
        val shellMarkers = listOf(
            "courselistdata",
            "coursefolderid",
            "course-list",
            "courselist",
            "course_type",
            "mooc2-ans",
        )
        val emptyMarkers = listOf(
            "no course",
            "no courses",
            "暂无课程",
            "没有课程",
        )
        return shellMarkers.any(lower::contains) || emptyMarkers.any(body::contains)
    }

    private fun isNonRetryableCxFailure(error: Throwable): Boolean {
        if (error is ChaoxingTrafficException) return true
        val message = error.message.orEmpty()
        return message.contains("HTTP 401") ||
            message.contains("HTTP 403") ||
            message.contains("HTTP 429") ||
            message.contains("访问限制") ||
            message.contains("登录")
    }

    /** 视频进度 enc 签名 */
    private fun getEnc(clazzId: String, jobid: String, objectId: String, playingTime: Int, duration: Int, userid: String): String {
        val raw = "[$clazzId][$userid][$jobid][$objectId][${playingTime * 1000}][d_yHJ!\$pdA~5][${duration * 1000}][0_$duration]"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ══════════════════════════════════════════════════════════════
    //  HTML 解析 (移植 decode.py)
    // ══════════════════════════════════════════════════════════════

    /** 解析课程列表 HTML */
    private fun decodeCourseList(html: String): List<CxCourse> {
        val doc = Jsoup.parse(html)
        val courses = mutableListOf<CxCourse>()

        for (el in doc.select("div.course")) {
            // 跳过未开放
            if (el.select("a.not-open-tip").isNotEmpty() || el.select("div.not-open-tip").isNotEmpty()) continue

            val id = el.attr("id")
            if (id.isBlank()) continue

            val clazzId = el.selectFirst("input.clazzId")?.attr("value") ?: continue
            val courseId = el.selectFirst("input.courseId")?.attr("value") ?: continue
            val aHref = el.selectFirst("a")?.attr("href") ?: ""
            val cpi = Regex("""cpi=(\d+)&""").find(aHref)?.groupValues?.get(1) ?: ""
            val title = CxFontDecoder.decode(html, el.selectFirst("span.course-name")?.attr("title") ?: "")
            val teacher = CxFontDecoder.decode(html, el.selectFirst("p.color3")?.attr("title") ?: "")

            // 课程入口 URL：相对路径补全为绝对 URL
            val courseUrl = if (aHref.startsWith("http")) aHref else "$BASE_MOOC2$aHref"

            courses.add(CxCourse(courseId = courseId, clazzId = clazzId, cpi = cpi, title = title, teacher = teacher, url = courseUrl))
        }
        return courses
    }

    /** 解析章节列表 HTML */
    private fun decodeCoursePoints(html: String): CxCoursePoints {
        val doc = Jsoup.parse(html)
        var hasLocked = false
        val points = mutableListOf<CxChapter>()

        for (unit in doc.select("div.chapter_unit")) {
            for (li in unit.select("li")) {
                val div = li.selectFirst("div") ?: continue
                val rawId = div.attr("id")
                if (rawId.isBlank()) continue

                val id = Regex("""cur(\d+)""").find(rawId)?.groupValues?.get(1) ?: continue
                val titleRaw = div.selectFirst("a.clicktitle")?.text()?.replace("\n", "")?.trim() ?: ""
                val title = CxFontDecoder.decode(html, titleRaw)

                val jobCountEl = div.selectFirst("input.knowledgeJobCount")
                val jobCount = jobCountEl?.attr("value")?.toIntOrNull() ?: 1

                val tipsEl = div.selectFirst("span.bntHoverTips")
                val tipsText = tipsEl?.text() ?: ""
                val needUnlock = tipsText.contains("解锁")
                val hasFinished = tipsText.contains("已完成")

                if (needUnlock) hasLocked = true

                points.add(CxChapter(id = id, title = title, jobCount = jobCount, hasFinished = hasFinished, needUnlock = needUnlock))
            }
        }
        return CxCoursePoints(hasLocked = hasLocked, points = points)
    }

    /** 从 HTML 中用括号计数法提取 mArg={...} JSON 字符串 */
    private fun extractMArgJson(html: String): String? {
        // 找到 "mArg=" 或 "mArg =" 的位置
        val startMarker = Regex("""mArg\s*=\s*\{""").find(html) ?: return null
        val jsonStart = startMarker.range.last  // 指向 '{'
        // 从 jsonStart 开始括号计数，找到匹配的 '}'
        var depth = 0
        for (i in jsonStart until html.length) {
            when (html[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return html.substring(jsonStart, i + 1)
                }
            }
        }
        return null
    }

    /** 解析任务点卡片 JS → (jobs, jobInfo) */
    private fun decodeCourseCard(html: String): Pair<List<CxJob>, CxJobInfo> {
        if (html.contains("章节未开放")) {
            Log.d(TAG, "章节未开放")
            return Pair(emptyList(), CxJobInfo())
        }

        val mArgStr = extractMArgJson(html)
        if (mArgStr == null) {
            // 有些章节页面确实没有 mArg（纯文本/阅读类章节）
            val hasContent = html.length > 500
            Log.d(TAG, "mArg 未匹配, html 长度=${html.length}, 有内容=$hasContent")
            return Pair(emptyList(), CxJobInfo())
        }
        Log.d(TAG, "mArg 匹配成功, 长度: ${mArgStr.length}")

        val json = try {
            JsonParser.parseString(mArgStr).asJsonObject
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw IOException("任务卡 JSON 解析失败", e)
        }

        // 提取 jobInfo
        val defaults = json.getAsJsonObject("defaults")
        val jobInfo = CxJobInfo(
            ktoken = defaults?.str("ktoken") ?: "",
            mtEnc = defaults?.str("mtEnc") ?: "",
            reportTimeInterval = defaults?.get("reportTimeInterval")?.asInt
                ?: json.get("reportTimeInterval")?.asInt
                ?: 60,
            defenc = defaults?.str("defenc") ?: "",
            cardid = defaults?.str("cardid") ?: "",
            cpi = defaults?.str("cpi") ?: "",
            qnenc = defaults?.str("qnenc") ?: "",
            knowledgeid = defaults?.str("knowledgeid") ?: "",
        )

        // 解析 attachments
        val attachments = json.getAsJsonArray("attachments")
        if (attachments == null) {
            Log.w(TAG, "attachments 为 null, json keys: ${json.keySet()}")
            return Pair(emptyList(), jobInfo)
        }
        Log.d(TAG, "attachments 数量: ${attachments.size()}")
        val jobs = mutableListOf<CxJob>()
        var allPassedCount = 0  // count of already-passed tasks

        for ((idx, el) in attachments.withIndex()) {
            val card = el.asJsonObject
            val rawType = card.str("type")
            val isPassed = card.get("isPassed")?.asBoolean == true
            Log.d(TAG, "  attachment[$idx] type=$rawType isPassed=$isPassed")

            // skip already passed (but count them)
            if (isPassed) {
                allPassedCount++
                continue
            }

            // job 字段可能是 JsonObject / JsonPrimitive(true) / null
            // null → 可能是阅读任务；其他情况按 type 分类处理
            val jobEl = card.get("job")
            if (jobEl == null || jobEl.isJsonNull) {
                val readJob = parseReadJob(card)
                if (readJob != null) {
                    Log.d(TAG, "    → 阅读任务: ${readJob.name}")
                    jobs.add(readJob)
                } else {
                    Log.d(TAG, "    → job 为 null 且非阅读任务, 跳过")
                }
                continue
            }

            // 清理 otherInfo
            var otherInfo = card.str("otherInfo")
            if (otherInfo.contains("courseId")) {
                otherInfo = otherInfo.split("&")[0]
            }

            val type = rawType.lowercase()
            val prop = card.getAsJsonObject("property") ?: JsonObject()

            val parsedJob = when {
                type.contains("live") || prop.str("liveId").isNotBlank() -> parseLiveJob(card, prop, otherInfo)
                type == "video" -> parseVideoJob(card, prop, otherInfo)
                type == "audio" -> parseAudioJob(card, prop, otherInfo)
                type == "document" -> parseDocumentJob(card, prop, otherInfo)
                type.contains("work") -> parseWorkJob(card, otherInfo)
                else -> null
            }
            if (parsedJob != null) {
                Log.d(TAG, "    → ${parsedJob.type}: ${parsedJob.name}")
                jobs.add(parsedJob)
            } else {
                Log.d(TAG, "    → 未知类型: $type, 跳过")
            }
        }
        // Log when all tasks are already completed
        if (allPassedCount > 0 && jobs.isEmpty()) {
            Log.i(TAG, "所有 $allPassedCount 个任务点已完成，无需学习")
        }
        return Pair(jobs, jobInfo)
    }

    private fun parseVideoJob(card: JsonObject, prop: JsonObject, otherInfo: String): CxJob? {
        val mid = card.str("mid")
        if (mid.isBlank()) return null
        return CxJob(
            type = "video",
            jobid = card.str("jobid"),
            name = prop.str("name").ifBlank { card.str("name") },
            objectid = card.str("objectId"),
            otherinfo = otherInfo,
            mid = mid,
            // playTime 来自超星 mArg attachments[].playTime，单位是秒
            playTime = card.get("playTime")?.asInt ?: 0,
            rt = prop.str("rt"),
            attDuration = card.str("attDuration"),
            attDurationEnc = card.str("attDurationEnc"),
            videoFaceCaptureEnc = card.str("videoFaceCaptureEnc"),
        )
    }

    private fun parseAudioJob(card: JsonObject, prop: JsonObject, otherInfo: String): CxJob {
        return CxJob(
            type = "audio",
            jobid = card.str("jobid"),
            name = prop.str("name").ifBlank { card.str("name") }.ifBlank { "音频" },
            objectid = card.str("objectId"),
            otherinfo = otherInfo,
            jtoken = card.str("jtoken"),
            mid = card.str("mid"),
            enc = card.str("enc"),
            aid = card.str("aid"),
        )
    }

    private fun parseDocumentJob(card: JsonObject, prop: JsonObject, otherInfo: String): CxJob {
        return CxJob(
            type = "document",
            jobid = card.str("jobid"),
            objectid = prop.str("objectid"),
            otherinfo = otherInfo,
            jtoken = card.str("jtoken"),
            mid = card.str("mid"),
            enc = card.str("enc"),
            aid = card.str("aid"),
        )
    }

    private fun parseWorkJob(card: JsonObject, otherInfo: String): CxJob {
        return CxJob(
            type = "workid",
            jobid = card.str("jobid"),
            otherinfo = otherInfo,
            mid = card.str("mid"),
            enc = card.str("enc"),
            aid = card.str("aid"),
        )
    }

    /** 从 JsonObject 安全获取字符串值 */
    private fun JsonObject.str(key: String): String {
        val el = get(key) ?: return ""
        return if (el.isJsonPrimitive) el.asString else el.toString()
    }

    private fun parseReadJob(card: JsonObject): CxJob? {
        if (card.str("type") != "read") return null
        return CxJob(
            type = "read",
            jobid = card.str("jobid"),
            name = card.getAsJsonObject("property")?.str("title") ?: "",
            jtoken = card.str("jtoken"),
            mid = card.str("mid"),
            otherinfo = card.str("otherInfo"),
            enc = card.str("enc"),
            aid = card.str("aid"),
        )
    }

    private fun parseLiveJob(card: JsonObject, prop: JsonObject, otherInfo: String): CxJob {
        return CxJob(
            type = "live",
            jobid = card.str("jobid").ifBlank { card.str("id") },
            name = prop.str("title").ifBlank { prop.str("name") }.ifBlank { "直播" },
            objectid = card.str("objectId"),
            otherinfo = otherInfo,
            mid = card.str("mid"),
            aid = card.str("aid"),
        )
    }

    /**
     * 解析章节检测/课程作业 HTML。
     *
     * 兼容两种 HTML 结构：
     * - 章节测验：div.TiMu[data=typeCode] + div.Zy_TItle
     * - 课程作业：h3.mark_name + input[name^=answertype]
     *
     * 与 Python decode_questions_info 对齐:
     *   1. _extract_form_data: 提取所有非 answer* 的 input 字段
     *   2. answerwqbid 由题目 ID 列表生成
     *   3. 每道题的 answerField 包含 answer{id} 和 answertype{id}
     */
    private fun decodeQuestions(html: String): CxWorkData {
        val doc = Jsoup.parse(html)
        val form = doc.selectFirst("form")
        val formActionUrl = form?.attr("action") ?: ""

        // 提取表单隐藏字段 (与 Python _extract_form_data 一致: 跳过所有含 "answer" 的 name)
        val formFields = mutableMapOf<String, String>()
        if (form != null) {
            for (input in form.select("input")) {
                val name = input.attr("name")
                if (name.isBlank() || name.contains("answer")) continue
                formFields[name] = input.attr("value")
            }
        }

        // 题目 div — 可能在 form 内，也可能在整个 doc 中（作业查看模式无 form）
        val qDivs = (form?.select("div.singleQuesId") ?: emptyList()).ifEmpty {
            doc.select("div.singleQuesId")
        }

        val questions = mutableListOf<CxQuestion>()
        for (div in qDivs) {
            val qId = div.attr("data")
            if (qId.isBlank()) continue

            // ── 题型代码 ──────────────────────────────────────
            // 优先从 div.TiMu[data] 取（章节测验格式）
            val tiMu = div.selectFirst("div.TiMu")
            val typeCodeFromTiMu = tiMu?.attr("data") ?: ""
            // 其次从 input[name=answertype{qId}] value 取（作业格式）
            val typeCodeFromInput = div.selectFirst("input[name=answertype$qId]")
                ?.attr("value") ?: doc.selectFirst("input[name=answertype$qId]")?.attr("value") ?: ""
            // 合并
            val typeCode = typeCodeFromTiMu.ifBlank { typeCodeFromInput }

            val type = when (typeCode) {
                "0" -> "single"
                "1" -> "multiple"
                "2" -> "completion"
                "3" -> "judgement"
                "4" -> "shortanswer"
                // 作业页可能没有 typeCode，尝试从 h3 文本推断
                else -> {
                    val h3Text = div.selectFirst("h3.mark_name")?.text() ?: ""
                    when {
                        "单选" in h3Text -> "single"
                        "多选" in h3Text -> "multiple"
                        "判断" in h3Text -> "judgement"
                        "填空" in h3Text -> "completion"
                        "简答" in h3Text -> "shortanswer"
                        "论述" in h3Text -> "shortanswer"
                        "编程" in h3Text -> "shortanswer"
                        else -> "unknown"
                    }
                }
            }

            // ── 标题 ──────────────────────────────────────────
            val titleDiv = div.selectFirst("div.Zy_TItle")
            var titleRaw: String
            if (titleDiv != null) {
                // 章节测验格式
                titleRaw = titleDiv.text().replace("\r", "").replace("\t", "").replace("\n", "")
            } else {
                // 作业格式：h3.mark_name 文本，去掉前缀 "1." 和题型标记
                val h3 = div.selectFirst("h3.mark_name")
                val h3Text = h3?.text()?.trim() ?: ""
                // 去掉 "1.(简答题, 100分)" 中的题号+题型标记部分，保留后面的内容
                // 仅匹配题型/分数模式的括号：(单选题|多选题|判断题|填空题|简答题|论述题|编程题, 分数)
                titleRaw = h3Text.replace(Regex("""^\d+\.\s*\([^)]*(?:单选题|多选题|判断题|填空题|简答题|论述题|编程题)[^)]*\)\s*"""), "")
                    .trim()
                    .replace("\r", "").replace("\t", "").replace("\n", "")
                // 如果去掉前缀后为空，回退到去掉 "1. " 的版本
                if (titleRaw.isBlank()) {
                    titleRaw = h3Text.replace(Regex("""^\d+\.\s*"""), "").trim()
                }
            }
            val title = CxFontDecoder.decode(html, titleRaw).ifBlank {
                // fallback: 尝试不带 class 约束取 h3
                CxFontDecoder.decode(html, div.selectFirst("h3")?.text()?.trim() ?: "")
            }

            // ── 选项 ──────────────────────────────────────────
            // 章节测验格式：ul li
            val optionsList = div.select("ul li").map { li ->
                val raw = (li.attr("aria-label").ifBlank { li.text() }).trim()
                var decoded = CxFontDecoder.decode(html, raw).trim()
                if (decoded.endsWith("选择")) decoded = decoded.dropLast(2).trimEnd()
                decoded
            }.sorted()
            // 作业格式：div.answerBg (多选/单选)
            val answerBgOptions = if (optionsList.isEmpty()) {
                div.select("div.answerBg").map { bg ->
                    val raw = bg.attr("aria-label").ifBlank { bg.text() }.trim()
                    var decoded = CxFontDecoder.decode(html, raw).trim()
                    if (decoded.endsWith("选择")) decoded = decoded.dropLast(2).trimEnd()
                    decoded
                }.sorted()
            } else emptyList()

            val options = (optionsList.ifEmpty { answerBgOptions }).joinToString("\n")

            // ── answerField ───────────────────────────────────
            val finalTypeCode = typeCode.ifBlank {
                // 如果还没有 typeCode，尝试从整个 doc 的 answertype input 获取
                doc.selectFirst("input[name=answertype$qId]")?.attr("value") ?: ""
            }
            val answerField = mapOf(
                "answer$qId" to "",
                "answertype$qId" to finalTypeCode,
            )

            questions.add(CxQuestion(
                id = qId, title = title, options = options,
                type = type, answerField = answerField,
            ))
        }

        // answerwqbid 由所有题目 ID 拼接 (与 Python 一致)
        val answerwqbid = questions.joinToString(",") { it.id } + ","

        // 检测 randomOptions 字段 (服务端可能随机打乱选项顺序)
        val hasRandomOptions = formFields.containsKey("randomOptions") ||
            doc.selectFirst("input[name=randomOptions]") != null
        if (hasRandomOptions) {
            Log.i(TAG, "decodeQuestions: 检测到 randomOptions 字段，选项顺序可能已随机化")
        }

        return CxWorkData(
            questions = questions,
            answerwqbid = answerwqbid,
            formFields = formFields,
            formActionUrl = formActionUrl,
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  消息中心 (2026-06-21)
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取收件箱通知列表（notice.chaoxing.com）。
     *
     * @param lastValue 游标，首次为空，后续用上一页 lastGetId
     * @return Pair(消息列表, 下一页游标)
     */
    suspend fun getNoticeList(lastValue: String = ""): Result<Pair<List<CxMessage>, String>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://notice.chaoxing.com/pc/notice/getNoticeList"
                val body = FormBody.Builder()
                    .add("type", "")
                    .add("notice_type", "")
                    .add("lastValue", lastValue)
                    .add("sort", "")
                    .add("folderUUID", "")
                    .add("kw", "")
                    .add("startTime", "")
                    .add("endTime", "")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("User-Agent", UA_FIXED)
                    .build()

                val resp = client.newCall(request).awaitResponse()
                val code = resp.code
                val json = resp.body?.string() ?: "{}"
                resp.close()

                if (code !in 200..299) {
                    return@withContext Result.failure(IOException("获取通知失败: HTTP $code"))
                }
                if (looksLikeBlockedOrLoginPage(json)) {
                    return@withContext Result.failure(IOException("获取通知失败: 登录或访问限制页面"))
                }

                val obj = JsonParser.parseString(json).asJsonObject
                val notices = obj.getAsJsonObject("notices") ?: run {
                    Log.w(TAG, "getNoticeList: notices 为 null")
                    return@withContext Result.success(Pair(emptyList(), ""))
                }
                val nextCursor = notices.str("lastGetId")
                val list = notices.getAsJsonArray("list") ?: return@withContext Result.success(Pair(emptyList(), nextCursor))

                val messages = list.mapNotNull { el ->
                    try {
                        val o = el.asJsonObject
                        val sendTimeStr = o.str("sendTime")
                        val sendTimeMs = parseTimeToMillis(sendTimeStr)
                        CxMessage(
                            id = o.str("idCode"),
                            source = CxMessageSource.NOTICE,
                            title = o.str("title"),
                            content = o.str("content"),
                            senderName = o.str("createrName"),
                            senderId = o.get("createrId")?.asLong ?: 0,
                            time = sendTimeMs,
                            isRead = o.get("grayReadTag")?.asBoolean == true || o.get("isread")?.asInt == 1,
                            logo = o.str("logo"),
                            attachment = o.str("attachment"),
                            rtfContent = o.str("rtf_content"),
                            attachments = parseAttachments(o.str("attachment")),
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "解析通知失败: ${e.javaClass.simpleName}")
                        null
                    }
                }
                Log.i(TAG, "getNoticeList: ${messages.size} 条")
                Result.success(Pair(messages, nextCursor))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "getNoticeList 异常" + ": " + e.javaClass.simpleName)
                Result.failure(e)
            }
        }

    /**
     * 获取课程活动通知并转为 CxMessage 列表。
     *
     * 复用 [getActivityList] 的 API，但解析更多字段。
     */
    suspend fun getActivityMessages(courses: List<CxCourse>): Result<List<CxMessage>> =
        withContext(Dispatchers.IO) {
            try {
                val allMessages = mutableListOf<CxMessage>()
                for (course in courses) {
                    val cacheKey = "${course.courseId}_${course.clazzId}"
                    val cached = activityCache[cacheKey]
                    if (cached != null && System.currentTimeMillis() - cached.storedAt < 30_000L) {
                        allMessages += cached.value.map { activity ->
                            CxMessage(
                                id = "act_${activity.id}",
                                source = CxMessageSource.ACTIVITY,
                                title = activity.name,
                                content = "",
                                senderName = course.title,
                                time = activity.startTime,
                                type = activity.type,
                                typeName = activityTypeName(activity.type),
                                courseId = course.courseId,
                                courseName = course.title,
                                activityStatus = activity.status,
                            )
                        }
                        continue
                    }
                    val url = "$BASE_MOBILE/v2/apis/active/student/activelist" +
                        "?fid=${getFid()}&courseId=${course.courseId}&classId=${course.clazzId}" +
                        "&showNotStartedActive=0&_=${System.currentTimeMillis()}"

                    val request = Request.Builder().url(url).get().header("User-Agent", userAgent()).build()
                    val resp = client.newCall(request).awaitResponse()
                    val code = resp.code
                    val json = resp.body?.string() ?: "{}"
                    resp.close()

                    if (code !in 200..299) {
                        return@withContext Result.failure(IOException("获取课程活动失败: HTTP $code"))
                    }
                    if (looksLikeBlockedOrLoginPage(json)) {
                        return@withContext Result.failure(IOException("获取课程活动失败: 登录或访问限制页面"))
                    }

                    val obj = JsonParser.parseString(json).asJsonObject
                    if (obj.get("result")?.asInt != 1) {
                        return@withContext Result.failure(IOException("获取课程活动失败: 服务端未确认成功"))
                    }

                    val data = obj.getAsJsonObject("data") ?: continue
                    val list = data.getAsJsonArray("activeList") ?: continue
                    val courseActivities = mutableListOf<CxActivity>()

                    for (el in list) {
                        try {
                            val o = el.asJsonObject
                            val actType = o.get("type")?.asInt ?: 0
                            val typeName = activityTypeName(actType)
                            val activityId = o.get("id")?.asLong ?: 0
                            val activityName = o.str("nameOne").ifBlank { o.str("name") }
                            val status = o.get("status")?.asInt ?: 0
                            val otherInfo = o.str("otherInfo")
                            val signTypeCode = Regex("""type=(\d+)""").find(otherInfo)
                                ?.groupValues?.get(1)?.toIntOrNull()
                                ?: actType
                            courseActivities += CxActivity(
                                id = activityId,
                                name = activityName,
                                type = actType,
                                status = status,
                                courseId = course.courseId,
                                classId = course.clazzId,
                                startTime = o.get("startTime")?.asLong ?: 0,
                                endTime = o.get("endTime")?.asLong ?: 0,
                                signType = CxSignType.fromCode(signTypeCode),
                            )
                            allMessages.add(
                                CxMessage(
                                    id = "act_$activityId",
                                    source = CxMessageSource.ACTIVITY,
                                    title = activityName,
                                    content = "",
                                    senderName = course.title,
                                    time = o.get("startTime")?.asLong ?: 0,
                                    isRead = o.get("userStatus")?.asInt == 1,
                                    type = actType,
                                    typeName = typeName,
                                    courseId = course.courseId,
                                    courseName = course.title,
                                    logo = o.str("logo"),
                                    activityStatus = status,
                                    userStatus = o.get("userStatus")?.asInt ?: 0,
                                    attendNum = o.get("attendNum")?.asInt ?: 0,
                                    releaseNum = o.get("releaseNum")?.asInt ?: 0,
                                )
                            )
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w(TAG, "解析活动失败: ${e.javaClass.simpleName}")
                        }
                    }
                    activityCache[cacheKey] = CacheEntry(courseActivities, System.currentTimeMillis())
                }
                Log.i(TAG, "getActivityMessages: ${allMessages.size} 条")
                Result.success(allMessages)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "getActivityMessages 异常" + ": " + e.javaClass.simpleName)
                Result.failure(e)
            }
        }

    private fun activityTypeName(type: Int): String = when (type) {
        2 -> "签到"
        11 -> "选人"
        42 -> "随堂练习"
        45 -> "通知"
        62 -> "分组任务"
        else -> "活动"
    }

    /**
     * 获取附件的真实下载 URL。
     *
     * 超星云盘下载需要签名，直接拼 URL 会 404。
     * 实际流程：preview 页面（已签名）→ 页面内嵌 download URL（d0.cldisk.com，带 at_/ak_/ad_ 参数）。
     */
    suspend fun getAttachmentDownloadUrl(att: CxAttachment): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val previewUrl = att.preview
            if (previewUrl.isBlank()) return@withContext Result.failure(Exception("无预览地址"))

            val request = Request.Builder().url(previewUrl).get().header("User-Agent", userAgent()).build()
            val resp = client.newCall(request).awaitResponse()
            val code = resp.code
            val html = resp.use { it.body?.string().orEmpty() }
            if (code !in 200..299) {
                return@withContext Result.failure(IOException("attachment preview returned HTTP $code"))
            }
            if (looksLikeBlockedOrLoginPage(html)) {
                return@withContext Result.failure(IOException("attachment preview returned a login or access-control page"))
            }

            // 从 HTML 中提取 d0.cldisk.com/download/... 签名 URL
            val match = Regex("""https?://d\d+\.cldisk\.com/download/[^"'\s]+""").find(html)
            if (match != null) {
                // 下载需要 Referer 指向 preview 页面
                Result.success(Pair(match.value, previewUrl))
            } else {
                Result.failure(Exception("未找到下载链接"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "获取下载链接异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    /**
     * 解析通知的 attachment JSON 字符串为 [CxAttachment] 列表。
     */
    private fun parseAttachments(raw: String): List<CxAttachment> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JsonParser.parseString(raw).asJsonArray
            arr.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    val cloud = obj.getAsJsonObject("att_clouddisk") ?: return@mapNotNull null
                    CxAttachment(
                        name = cloud.str("name"),
                        fileSize = cloud.str("fileSize"),
                        suffix = cloud.str("suffix"),
                        objectId = cloud.str("objectId"),
                        preview = cloud.str("preview"),
                        puid = cloud.str("puid"),
                        forbidDownload = cloud.get("forbidDownload")?.asInt ?: 0,
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * 解析 "2026-06-15 16:31:48" 格式时间为毫秒时间戳。
     */
    private fun parseTimeToMillis(timeStr: String): Long {
        if (timeStr.isBlank()) return 0
        return try {
            val parts = timeStr.split(" ", "-", ":")
            if (parts.size >= 6) {
                val cal = java.util.Calendar.getInstance()
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(),
                    parts[3].toInt(), parts[4].toInt(), parts[5].toInt())
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            } else 0
        } catch (e: Exception) { 0 }
    }

    // ══════════════════════════════════════════════════════════════
    //  课程作业 (2026-06-22)
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取某门课程的作业列表。
     *
     * API: GET mooc1.chaoxing.com/mooc2/work/list
     *
     * @param courseUrl 课程页面 URL，用于提取 courseId/clazzId/cpi 和 workEnc
     * @return 作业列表
     */
    suspend fun getHomeworkList(courseUrl: String): Result<List<CxHomeworkItem>> = withContext(Dispatchers.IO) {
        try {
            // 1. 访问课程页面，提取 workEnc 和 URL 参数
            val courseUrlWithParams = courseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("v", System.currentTimeMillis().toString())
                .addQueryParameter("start", "0")
                .addQueryParameter("size", "500")
                .addQueryParameter("catalogId", "0")
                .addQueryParameter("superstarClass", "0")
                .build()

            val courseResp = client.newCall(
                Request.Builder().url(courseUrlWithParams).get().header("User-Agent", userAgent()).build()
            ).awaitResponse()

            val courseCode = courseResp.code
            val courseHtml = courseResp.body?.string() ?: ""
            val finalUrl = courseResp.request.url.toString()
            courseResp.close()

            if (courseCode !in 200..299) {
                return@withContext Result.failure(IOException("获取课程作业入口失败: HTTP $courseCode"))
            }
            if (looksLikeBlockedOrLoginPage(courseHtml)) {
                return@withContext Result.failure(IOException("获取课程作业入口失败: 登录或访问限制页面"))
            }

            Log.i(TAG, "getHomeworkList: course page loaded")

            val urlParams = finalUrl.toHttpUrl().queryParameterNames.associateWith {
                finalUrl.toHttpUrl().queryParameter(it) ?: ""
            }

            val doc = Jsoup.parse(courseHtml)
            // 多选择器回退: input#workEnc → input[name=workEnc] → input[name=enc_work]
            var workEnc = doc.selectFirst("input#workEnc")?.attr("value")
                ?: doc.selectFirst("input[name=workEnc]")?.attr("value")
                ?: doc.selectFirst("input[name=enc_work]")?.attr("value")
                ?: doc.selectFirst("input[name=enc]")?.attr("value")
            if (workEnc == null) {
                Log.w(TAG, "getHomeworkList: 未找到 workEnc (已尝试 #workEnc/[name=workEnc]/[name=enc_work]/[name=enc], 页面可能无作业)")
                return@withContext Result.success(emptyList())
            }

            val courseId = urlParams["courseid"] ?: ""
            val classId = urlParams["clazzid"] ?: ""
            val cpi = urlParams["cpi"] ?: ""

            // 2. 请求作业列表
            val workUrl = "$BASE_MOOC1/mooc2/work/list".toHttpUrl().newBuilder()
                .addQueryParameter("courseId", courseId)
                .addQueryParameter("classId", classId)
                .addQueryParameter("cpi", cpi)
                .addQueryParameter("ut", "s")
                .addQueryParameter("enc", workEnc)
                .build()

            val workResp = client.newCall(
                Request.Builder()
                    .url(workUrl)
                    .get()
                    .header("User-Agent", userAgent())
                    .header("Referer", "https://mooc2-ans.chaoxing.com/")
                    .build()
            ).awaitResponse()

            val workCode = workResp.code
            val workHtml = workResp.body?.string() ?: ""
            workResp.close()

            if (workCode !in 200..299) {
                return@withContext Result.failure(IOException("获取作业列表失败: HTTP $workCode"))
            }
            if (looksLikeBlockedOrLoginPage(workHtml)) {
                return@withContext Result.failure(IOException("获取作业列表失败: 登录或访问限制页面"))
            }

            // 3. 解析作业列表 HTML
            val workDoc = Jsoup.parse(workHtml)
            val items = workDoc.select("li[data]").mapNotNull { li ->
                try {
                    val dataUrl = li.attr("data")
                    if (dataUrl.isBlank()) return@mapNotNull null

                    val queryMap = try {
                        dataUrl.toHttpUrl().queryParameterNames.associateWith {
                            dataUrl.toHttpUrl().queryParameter(it) ?: ""
                        }
                    } catch (_: Exception) {
                        // data 属性可能是相对路径，尝试补全
                        val fullUrl = if (dataUrl.startsWith("/")) {
                            "$BASE_MOOC1$dataUrl"
                        } else {
                            "$BASE_MOOC1/$dataUrl"
                        }
                        fullUrl.toHttpUrl().queryParameterNames.associateWith {
                            fullUrl.toHttpUrl().queryParameter(it) ?: ""
                        }
                    }

                    val nameP = li.selectFirst("p")
                    val name = nameP?.text()?.trim() ?: "未知作业"

                    // 稳健的状态文本提取: 优先取第二个 <p>（原 DOM 方式），
                    // 回退到关键字匹配（包含所有已知状态）
                    val statusKeywords = setOf("未交", "已完成", "待批阅", "已批阅", "待做", "已提交", "未完成", "待审批")
                    var status = li.select("p").getOrNull(1)?.text()?.trim() ?: ""
                    // 如果第二个 <p> 取到的不是状态文本（可能为空或非状态），用关键字匹配
                    if (status.isBlank() || statusKeywords.none { it in status }) {
                        for (p in li.select("p")) {
                            val text = p.text().trim()
                            if (statusKeywords.any { it in text }) {
                                status = text
                                break
                            }
                        }
                    }
                    if (status.isBlank()) {
                        status = li.select("p").lastOrNull()?.text()?.trim() ?: ""
                    }

                    CxHomeworkItem(
                        workId = queryMap["workId"] ?: "",
                        name = name,
                        status = status,
                        courseName = "",
                        courseId = courseId,
                        classId = classId,
                        cpi = cpi,
                        workUrl = dataUrl,
                        answerId = queryMap["answerId"] ?: "",
                        enc = queryMap["enc"] ?: "",
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "解析作业项失败: ${e.javaClass.simpleName}")
                    null
                }
            }

            Log.i(TAG, "getHomeworkList: ${items.size} 个作业")
            Result.success(items)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "getHomeworkList 异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    /**
     * 获取作业页面，解析题目和表单数据。
     *
     * API: GET the workUrl (mooc1.chaoxing.com/mooc-ans/mooc2/work/task?...)
     *
     * @param workUrl 作业页面完整 URL
     * @param courseId 课程 ID（用于构造 Referer）
     * @param classId  班级 ID（用于构造 Referer）
     * @param cpi      cpi
     * @return 解析后的 CxWorkData（题目 + 表单字段 + answerwqbid）
     */
    suspend fun getHomeworkPage(
        workUrl: String,
        courseId: String,
        classId: String,
        cpi: String,
    ): Result<CxWorkData> = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(
                Request.Builder()
                    .url(workUrl)
                    .get()
                    .header("User-Agent", userAgent())
                    .header("Referer", "$BASE_MOOC1/mooc2/work/list?courseId=$courseId&classId=$classId&cpi=$cpi&ut=s")
                    .build()
            ).awaitResponse()

            val code = resp.code
            val html = resp.body?.string() ?: ""
            resp.close()

            if (code !in 200..299) {
                return@withContext Result.failure(IOException("获取作业页面失败: HTTP $code"))
            }
            if (looksLikeBlockedOrLoginPage(html)) {
                return@withContext Result.failure(IOException("获取作业页面失败: 登录或访问限制页面"))
            }

            val workData = decodeQuestions(html)
            Log.i(TAG, "getHomeworkPage: ${workData.questions.size} 道题")
            Result.success(workData)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "getHomeworkPage 异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    /**
     * 提交作业答案。
     *
     * API: POST mooc1.chaoxing.com/mooc-ans/work/addStudentWorkNewWeb
     * （注意：作业提交通用此 URL，与章节测验的 addStudentWorkNew 不同）
     *
     * @param workData 题目数据和表单字段
     * @param formActionUrl 从 HTML form action 提取的完整 URL（含查询参数）
     * @return 提交结果消息
     */
    suspend fun submitHomework(workData: CxWorkData, formActionUrl: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                // 解析 form action URL 中的查询参数
                val actionUrl = if (formActionUrl.startsWith("/")) {
                    "$BASE_MOOC1$formActionUrl"
                } else if (!formActionUrl.startsWith("http")) {
                    "$BASE_MOOC1/$formActionUrl"
                } else {
                    formActionUrl
                }

                val formBuilder = FormBody.Builder()
                formBuilder.add("answerwqbid", workData.answerwqbid)
                formBuilder.add("pyFlag", workData.pyFlag)

                for ((k, v) in workData.formFields) {
                    if (k == "pyFlag") continue
                    formBuilder.add(k, v)
                }

                // 添加每道题的答案字段 (answer{qId} + answertype{qId})
                // 注意: answer{qId} 已在 formFields 中, 这里只补缺失的元数据
                val addedKeys = mutableSetOf<String>()
                addedKeys.addAll(workData.formFields.keys)
                addedKeys.add("answerwqbid")
                addedKeys.add("pyFlag")
                for (q in workData.questions) {
                    for ((key, value) in q.answerField) {
                        if (key in addedKeys) continue // 避免空值覆盖 formFields 中的正确答案
                        if (value.isBlank()) continue
                        formBuilder.add(key, value)
                        addedKeys.add(key)
                    }
                }

                // 验证提交 URL 合法性 (避免解析错误时发到错误端点)
                if (!actionUrl.contains("addStudentWorkNew", ignoreCase = true)) {
                    Log.e(TAG, "[submitHomework] 非法提交 URL")
                    return@withContext Result.failure(Exception("提交地址异常，请刷新后重试"))
                }

                val body = formBuilder.build()
                Log.i(TAG, "[submitHomework] fields=${body.size}")

                val request = Request.Builder()
                    .url(actionUrl)
                    .post(body)
                    .header("User-Agent", userAgent())
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", "*/*")
                    .header("Origin", BASE_MOOC1)
                    .header("Referer", actionUrl.takeWhile { it != '?' })
                    .build()

                val resp = client.newCall(request).awaitResponse()
                val code = resp.code
                val json = resp.body?.string() ?: "{}"
                resp.close()

                Log.i(TAG, "[submitHomework] HTTP $code, response body omitted")

                if (code !in 200..299) {
                    return@withContext Result.failure(IOException("提交作业失败: HTTP $code"))
                }
                if (looksLikeBlockedOrLoginPage(json)) {
                    return@withContext Result.failure(IOException("提交作业失败: 登录或访问限制页面"))
                }

                val obj = JsonParser.parseString(json).asJsonObject
                if (obj.get("status")?.asBoolean == true) {
                    Result.success(obj.str("msg").ifBlank { "提交成功" })
                } else {
                    val errMsg = obj.str("msg").ifBlank { "提交失败: server did not confirm success" }
                    Result.failure(Exception(errMsg))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "submitHomework 异常" + ": " + e.javaClass.simpleName)
                Result.failure(e)
            }
        }

    /**
     * 重做作业。
     *
     * API: GET mooc1.chaoxing.com/work/phone/redo
     */
    suspend fun redoHomework(
        courseId: String,
        classId: String,
        cpi: String,
        workId: String,
        workAnswerId: String,
        enc: String = "",
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = "$BASE_MOOC1/work/phone/redo".toHttpUrl().newBuilder()
                .addQueryParameter("courseId", courseId)
                .addQueryParameter("classId", classId)
                .addQueryParameter("cpi", cpi)
                .addQueryParameter("workId", workId)
                .addQueryParameter("workAnswerId", workAnswerId)
            if (enc.isNotBlank()) {
                urlBuilder.addQueryParameter("enc", enc)
            }
            val url = urlBuilder.build()

            val resp = client.newCall(
                Request.Builder().url(url).get().header("User-Agent", userAgent()).build()
            ).awaitResponse()

            val code = resp.code
            val json = resp.body?.string() ?: "{}"
            resp.close()

            if (code !in 200..299 || looksLikeBlockedOrLoginPage(json)) {
                return@withContext Result.failure(IOException("重做作业失败: HTTP $code"))
            }

            val obj = JsonParser.parseString(json).asJsonObject
            if (obj.get("status")?.asBoolean == true) {
                Result.success(true)
            } else {
                Result.failure(IOException("重做作业失败: 服务端未确认成功"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "redoHomework 异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    /**
     * 上传作业附件到超星云盘。
     * 端点链: noteyd.chaoxing.com → pan-yz.chaoxing.com → pc/resource/addResource
     * @return 上传后的 objectId（可在答题字段中引用）
     */
    suspend fun uploadHomeworkFile(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取上传 token 和 puid
            val configUrl = "https://noteyd.chaoxing.com/pc/files/getUploadConfig"
            val configResp = client.newCall(
                Request.Builder().url(configUrl).get().header("User-Agent", userAgent()).build()
            ).awaitResponse()
            val configCode = configResp.code
            val configJson = configResp.body?.string() ?: "{}"
            configResp.close()

            if (configCode !in 200..299 || looksLikeBlockedOrLoginPage(configJson)) {
                return@withContext Result.failure(IOException("获取上传凭证失败: HTTP $configCode"))
            }

            val configObj = JsonParser.parseString(configJson).asJsonObject
            val token = configObj.getAsJsonObject("msg")?.get("token")?.asString
                ?: configObj.getAsJsonPrimitive("token")?.asString ?: ""
            val puid = configObj.getAsJsonObject("msg")?.get("puid")?.asString
                ?: configObj.getAsJsonPrimitive("puid")?.asString ?: ""

            if (token.isBlank()) return@withContext Result.failure(Exception("获取上传凭证失败"))

            // 2. 上传文件 (multipart)
            val uploadClient = SecureHttpClientFactory.create(
                cookieJar = cookieJar,
                followRedirects = true,
                retryOnConnectionFailure = false,
                trustAll = false,
                connectTimeoutSec = 30,
                readTimeoutSec = 60,
                extraInterceptors = listOf(
                    trafficGovernor.asEntryTagInterceptor(accountProvider = { trafficAccountKey() }),
                ),
                extraNetworkInterceptors = listOf(
                    trafficGovernor.asInterceptor(accountProvider = { trafficAccountKey() }),
                ),
            )
            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("_token", token)
                .addFormDataPart("puid", puid)
                .addFormDataPart(
                    "file", fileName,
                    fileBytes.toRequestBody(mimeType.toMediaType()),
                )
                .build()

            val uploadResp = uploadClient.newCall(
                Request.Builder()
                    .url("https://pan-yz.chaoxing.com/upload")
                    .post(requestBody)
                    .header("User-Agent", userAgent())
                    .build()
            ).awaitResponse()
            val uploadCode = uploadResp.code
            val uploadJson = uploadResp.body?.string() ?: "{}"
            uploadResp.close()

            if (uploadCode !in 200..299 || looksLikeBlockedOrLoginPage(uploadJson)) {
                return@withContext Result.failure(IOException("上传失败: HTTP $uploadCode"))
            }

            val uploadObj = JsonParser.parseString(uploadJson).asJsonObject
            val objectId = uploadObj.getAsJsonObject("msg")?.get("objectId")?.asString
                ?: uploadObj.getAsJsonPrimitive("objectId")?.asString
                ?: uploadObj.getAsJsonObject("data")?.get("objectId")?.asString
                ?: return@withContext Result.failure(Exception("上传失败: ${uploadJson.take(200)}"))

            Log.i(TAG, "uploadHomeworkFile: upload completed")

            // 3. 注册文件资源
            val params = """[{"Key":"$objectId","Cataid":"100000019"}]"""
            val registerUrl = "https://pan-yz.chaoxing.com/pc/resource/addResource".toHttpUrl().newBuilder()
                .addQueryParameter("bbsid", "")
                .addQueryParameter("pid", "")
                .addQueryParameter("type", "yunpan")
                .addQueryParameter("params", params)
                .build()
            val regResp = client.newCall(
                Request.Builder().url(registerUrl).get().header("User-Agent", userAgent()).build()
            ).awaitResponse()
            val registerCode = regResp.code
            val registerBody = regResp.body?.string().orEmpty()
            regResp.close()

            if (registerCode !in 200..299 || looksLikeBlockedOrLoginPage(registerBody)) {
                return@withContext Result.failure(IOException("注册上传资源失败: HTTP $registerCode"))
            }
            val registerConfirmed = runCatching {
                val obj = JsonParser.parseString(registerBody).asJsonObject
                obj.get("status")?.asBoolean == true ||
                    obj.get("result")?.let { it.asInt == 1 } == true ||
                    obj.get("success")?.asBoolean == true
            }.getOrDefault(false) || registerBody.trim().equals("success", ignoreCase = true)
            if (!registerConfirmed) {
                return@withContext Result.failure(IOException("注册上传资源失败: 服务端未确认成功"))
            }

            Result.success(objectId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "uploadHomeworkFile 异常" + ": " + e.javaClass.simpleName)
            Result.failure(e)
        }
    }
}
