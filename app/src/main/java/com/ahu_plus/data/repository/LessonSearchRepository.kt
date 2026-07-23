package com.ahu_plus.data.repository

import com.ahu_plus.data.diagnostic.SafeLog as Log
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.model.jw.LessonAdminClass
import com.ahu_plus.data.model.jw.LessonBuilding
import com.ahu_plus.data.model.jw.LessonCourseUnit
import com.ahu_plus.data.model.jw.LessonCourseUnitEnvelope
import com.ahu_plus.data.model.jw.LessonDepartment
import com.ahu_plus.data.model.jw.LessonMajorNode
import com.ahu_plus.data.model.jw.LessonRecord
import com.ahu_plus.data.model.jw.LessonSearchFilter
import com.ahu_plus.data.model.jw.LessonSearchMode
import com.ahu_plus.data.model.jw.LessonSearchResponse
import com.ahu_plus.data.network.SecureHttpClientFactory
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 全校开课查询仓库（lesson-search）。
 *
 * 端点链（2026-07-23 HAR 实测，经 WebVPN 抓包但 App 直连 jw.ahu.edu.cn）：
 * 1. `GET /student/for-std/lesson-search` → **302** →
 *    `Location: .../lesson-search/index/{dataId}`（dataId 是服务端会话内分配的资源号，
 *    HAR 里是 99166，**不能硬编码**，每会话/每用户可能不同）。
 * 2. `GET /student/for-std/lesson-search/semester/{semesterId}/search/{dataId}?...`
 *    → 200，UTF-8 JSON，`{"data":[...],"_page_":{...}}`。
 *
 * 鉴权：复用 [JwAuthRepository.jwCookieJar] 的 JW SESSION cookie。
 * 会话失效判定沿用 [JwSessionResponseClassifier]（302 到 CAS/登录页 = 失效；
 * 302 到 `/lesson-search/index/…` 不是登录页，正常提取 dataId）。
 *
 * 缓存：dataId 在会话内稳定，内存缓存即可；SessionExpired 时清空以便重连后重解析。
 */
class LessonSearchRepository(
    private val jwAuthRepository: JwAuthRepository
) {
    private val gson = GsonProvider.instance

    @Volatile
    private var cachedDataId: String? = null

    /** 学院列表一学期内不变，成功拉取后内存缓存（不落库）。 */
    @Volatile
    private var cachedDepartments: List<LessonDepartment>? = null

    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = jwAuthRepository.jwCookieJar,
        followRedirects = false,
        disableGzip = false,
        tlsPolicy = com.ahu_plus.data.network.TlsPolicy.LegacyCampusHosts(setOf("jw.ahu.edu.cn")),
        extraInterceptors = listOf(
            okhttp3.Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", UA)
                    .header("x-requested-with", "XMLHttpRequest")
                    .build()
                chain.proceed(req)
            }
        )
    )

    /**
     * 解析当前会话的 dataId（`/lesson-search/index/{dataId}`）。
     *
     * 命中内存缓存直接返回；否则 GET 入口页拿 302 Location 提取。
     * 会话失效时抛 [SessionExpiredException]（交给 executeWithSessionRetry 重连重试）。
     */
    private suspend fun resolveDataId(): String {
        cachedDataId?.let { return it }
        val request = Request.Builder()
            .url(ENTRY_URL)
            .get()
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", "$JW_BASE/student/")
            .build()

        client.newCall(request).execute().use { response ->
            val location = response.header("Location")
            if (JwSessionResponseClassifier.isExpired(response.code, location)) {
                throw SessionExpiredException()
            }
            // 入口页可能 302 到 /index/{dataId}，也可能直接 200 HTML 内嵌 index/{dataId}。
            val body = if (response.isSuccessful) response.body?.string() else null
            val dataId = extractDataId(location, body)
                ?: throw Exception("无法解析开课查询入口 (HTTP ${response.code})")
            cachedDataId = dataId
            Log.d(TAG, "resolveDataId: $dataId")
            return dataId
        }
    }

    /**
     * 拉取"按开课"的学院列表（服务端筛选 `openDepartmentAssocs` 的 id 源）。
     *
     * 端点：`GET /student/ws/select-department/departments/getAllByIsOpenCourse`
     * → 200 JSON **顶层数组** `[{id, nameZh}, ...]`（2026-07-23 HAR 实测 50 项）。
     * 命中内存缓存直接返回；一学期内不变。会话失效抛 [SessionExpiredException]。
     */
    suspend fun getDepartments(): Result<List<LessonDepartment>> = withContext(Dispatchers.IO) {
        cachedDepartments?.let { return@withContext Result.success(it) }
        try {
            val request = Request.Builder()
                .url(DEPARTMENTS_URL)
                .get()
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$JW_BASE/student/for-std/lesson-search")
                .build()

            client.newCall(request).execute().use { response ->
                if (JwSessionResponseClassifier.isExpired(
                        response.code, response.header("Location")
                    )) {
                    throw SessionExpiredException()
                }
                if (!response.isSuccessful) {
                    throw Exception("学院列表获取失败: HTTP ${response.code}")
                }
                val body = response.body?.string() ?: ""
                if (body.isBlank() || body[0] != '[') {
                    throw Exception("学院列表返回非数组: ${body.take(120)}")
                }
                val type = object : TypeToken<List<LessonDepartment>>() {}.type
                val parsed: List<LessonDepartment> = gson.fromJson(body, type) ?: emptyList()
                val usable = parsed.filter { it.isUsable() }
                if (usable.isNotEmpty()) cachedDepartments = usable
                Log.i(TAG, "getDepartments: ${usable.size} 个学院")
                Result.success(usable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDepartments 失败", e)
            Result.failure(e)
        }
    }

    // ── 教学班定位级联 + 取选项（2026-07-23 全量筛选） ─────────────────────
    //
    // 端点都在 HAR 返回过真数据；鉴权复用同一 client(jwCookieJar)。会话失效抛
    // SessionExpiredException 交给 executeWithSessionRetry。逐个用 runCatching 包成 Result。

    /**
     * 开课单位列表（教学班定位级联的第 2 环，`departmentAssocs` 的 id 源）。
     * `GET /student/ws/major-select/data/departments?bizTypeId=2` → 顶层数组。
     */
    suspend fun getMajorDepartments(): Result<List<LessonMajorNode>> =
        fetchArrayFromUrl("$JW_BASE/student/ws/major-select/data/departments?bizTypeId=$BIZ_TYPE") {
            Log.i(TAG, "getMajorDepartments: ${it.size}")
        }

    /**
     * 专业列表（级联第 3 环，`majorAssoc` 的 id 源）。
     * `GET /student/ws/major-select/data/mulMajors?bizTypeId=2` → 顶层数组。
     */
    suspend fun getMajors(): Result<List<LessonMajorNode>> =
        fetchArrayFromUrl("$JW_BASE/student/ws/major-select/data/mulMajors?bizTypeId=$BIZ_TYPE") {
            Log.i(TAG, "getMajors: ${it.size}")
        }

    /**
     * 行政班搜索（级联第 4 环，"具体年级教学班"的定位钥匙 → adminClassAssoc）。
     * `GET /student/ws/adminclass/search-adminclass?enabled=true&bizTypeId=2&grades=&departments=&majors[]=`
     * 按年级/开课单位/专业过滤缩小范围。参数空则该维度不限。
     */
    suspend fun searchAdminClasses(
        grades: List<String> = emptyList(),
        departmentIds: List<Long> = emptyList(),
        majorIds: List<Long> = emptyList(),
    ): Result<List<LessonAdminClass>> {
        val url = "$JW_BASE/student/ws/adminclass/search-adminclass".toHttpUrl().newBuilder()
            .addQueryParameter("enabled", "true")
            .addQueryParameter("bizTypeId", BIZ_TYPE.toString())
            .apply {
                grades.distinct().forEach { addQueryParameter("grades", it) }
                departmentIds.distinct().forEach { addQueryParameter("departments", it.toString()) }
                majorIds.distinct().forEach { addQueryParameter("majors[]", it.toString()) }
            }
            .build()
        return fetchArrayFromUrl(url.toString()) { Log.i(TAG, "searchAdminClasses: ${it.size}") }
    }

    /**
     * 指定校区的教学楼（`buildingAssoc` 的 id 源，按 [campusId] 联动）。
     * `GET /student/for-std/lesson-search/get-building-by-campus?campusId={id}` → 顶层数组。
     */
    suspend fun getBuildings(campusId: Long): Result<List<LessonBuilding>> =
        fetchArrayFromUrl(
            "$JW_BASE/student/for-std/lesson-search/get-building-by-campus?campusId=$campusId"
        ) { Log.i(TAG, "getBuildings(campus=$campusId): ${it.size}") }

    /**
     * 节次列表（`courseIndexs` 的 indexNo 源）。
     * `GET /student/ws/time-table-layout/course-units?semesterId={s}&campusId={c}`
     * 返回可能是 `{data:[...]}` 包装或顶层数组，两者都吃。campusId 可空（0=不限）。
     */
    suspend fun getCourseUnits(semesterId: Int, campusId: Long? = null): Result<List<LessonCourseUnit>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$JW_BASE/student/ws/time-table-layout/course-units".toHttpUrl().newBuilder()
                    .addQueryParameter("semesterId", semesterId.toString())
                    .apply { campusId?.let { addQueryParameter("campusId", it.toString()) } }
                    .build()
                val body = fetchBody(url.toString())
                val trimmed = body.trimStart()
                val units: List<LessonCourseUnit> = if (trimmed.startsWith("[")) {
                    val type = object : TypeToken<List<LessonCourseUnit>>() {}.type
                    gson.fromJson(body, type) ?: emptyList()
                } else {
                    gson.fromJson(body, LessonCourseUnitEnvelope::class.java)?.data.orEmpty()
                }
                Log.i(TAG, "getCourseUnits(sem=$semesterId): ${units.size}")
                Result.success(units.filter { it.indexNo != null })
            } catch (e: Exception) {
                Log.e(TAG, "getCourseUnits 失败", e)
                Result.failure(e)
            }
        }

    /** 取顶层 JSON 数组并反序列化为 [T] 列表；会话失效抛 SessionExpiredException。 */
    private suspend inline fun <reified T> fetchArrayFromUrl(
        url: String,
        crossinline onOk: (List<T>) -> Unit = {},
    ): Result<List<T>> = withContext(Dispatchers.IO) {
        try {
            val body = fetchBody(url)
            if (body.trimStart().firstOrNull() != '[') {
                throw Exception("期望顶层数组，实际: ${body.take(120)}")
            }
            val type = TypeToken.getParameterized(List::class.java, T::class.java).type
            val parsed: List<T> = gson.fromJson(body, type) ?: emptyList()
            onOk(parsed)
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "fetchArray 失败: ${url.substringBefore('?')}", e)
            Result.failure(e)
        }
    }

    /** 发一个 JSON GET，返回 body 字符串；会话失效/非 2xx 抛异常。调用方在 IO 线程内。 */
    private fun fetchBody(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "$JW_BASE/student/for-std/lesson-search")
            .build()
        client.newCall(request).execute().use { response ->
            if (JwSessionResponseClassifier.isExpired(response.code, response.header("Location"))) {
                throw SessionExpiredException()
            }
            if (!response.isSuccessful) {
                throw Exception("请求失败: HTTP ${response.code}")
            }
            return response.body?.string() ?: ""
        }
    }

    /**
     * 分页搜索开课（旧签名，向后兼容 VM/单测；委托给 [search] 的 filter 版）。
     *
     * @param departmentIds 服务端按开课学院筛选（多选，空则不限学院）
     */
    suspend fun search(
        semesterId: Int,
        mode: LessonSearchMode,
        keyword: String,
        page: Int,
        rowsPerPage: Int = DEFAULT_ROWS_PER_PAGE,
        departmentIds: List<Long> = emptyList(),
    ): Result<LessonSearchResponse> = search(
        filter = LessonSearchFilter(
            semesterId = semesterId,
            mode = mode,
            keyword = keyword,
            departmentIds = departmentIds,
        ),
        page = page,
        rowsPerPage = rowsPerPage,
    )

    /**
     * 分页搜索开课（全量筛选版）。
     *
     * @param filter 全量筛选载体（学期/关键词/开课筛选/教学班定位级联）
     * @param page 页码（从 1 开始）
     * @param rowsPerPage 每页条数
     */
    suspend fun search(
        filter: LessonSearchFilter,
        page: Int,
        rowsPerPage: Int = DEFAULT_ROWS_PER_PAGE,
    ): Result<LessonSearchResponse> = withContext(Dispatchers.IO) {
        try {
            val dataId = resolveDataId()
            val url = buildSearchUrl(filter, dataId, page, rowsPerPage)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$JW_BASE/student/for-std/lesson-search/index/$dataId")
                .build()

            client.newCall(request).execute().use { response ->
                if (JwSessionResponseClassifier.isExpired(
                        response.code, response.header("Location")
                    )) {
                    // 会话失效：清缓存的 dataId,重连后需重新解析
                    cachedDataId = null
                    throw SessionExpiredException()
                }
                if (!response.isSuccessful) {
                    throw Exception("开课查询失败: HTTP ${response.code}")
                }
                // 响应为 UTF-8 JSON（HAR 实测）。string() 按 Content-Type charset/UTF-8 解码。
                val body = response.body?.string() ?: ""
                if (body.isBlank()) {
                    throw Exception("开课查询返回空响应")
                }
                if (body[0] != '{') {
                    throw Exception("开课查询返回非 JSON: ${body.take(120)}")
                }
                val parsed = gson.fromJson(body, LessonSearchResponse::class.java)
                Log.i(
                    TAG,
                    "search: sem=${filter.semesterId} mode=${filter.mode.name} " +
                        "kw=${if (filter.keyword.isBlank()) "(all)" else "*"} " +
                        "filters=${filter.activeCount} page=$page " +
                        "rows=${parsed.data?.size ?: 0} total=${parsed.page?.totalRows}"
                )
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "search 失败 (sem=${filter.semesterId} page=$page)", e)
            Result.failure(e)
        }
    }

    /** 测试/调试用：强制丢弃缓存的 dataId（不影响学院列表缓存）。 */
    fun invalidateDataId() {
        cachedDataId = null
    }

    companion object {
        private const val TAG = "LessonSearchRepo"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val ENTRY_URL = "$JW_BASE/student/for-std/lesson-search"

        /** "按开课"学院列表端点（顶层数组 [{id,nameZh}]）。 */
        private const val DEPARTMENTS_URL =
            "$JW_BASE/student/ws/select-department/departments/getAllByIsOpenCourse"

        /** 默认每页条数（网页用 100，移动端用小一些以加快首屏 + 支持"加载更多"）。 */
        const val DEFAULT_ROWS_PER_PAGE = 30

        /** 本科生培养口径（网页默认浏览视图使用 bizTypeAssoc=2）。 */
        private const val BIZ_TYPE = 2

        /** 从 URL/HTML 中提取 `lesson-search/index/{dataId}` 的资源号。 */
        private val DATA_ID_REGEX = Regex("""lesson-search/index/(\d+)""")

        /**
         * 从 302 Location 或 200 HTML body 中提取 dataId（纯函数,便于单测）。
         * Location 优先；否则回退 body。都取不到返回 null。
         */
        internal fun extractDataId(location: String?, body: String?): String? {
            location?.let { DATA_ID_REGEX.find(it)?.groupValues?.get(1) }?.let { return it }
            body?.let { DATA_ID_REGEX.find(it)?.groupValues?.get(1) }?.let { return it }
            return null
        }

        /**
         * 构造搜索请求 URL（旧签名，向后兼容单测；委托给 filter 版）。
         */
        internal fun buildSearchUrl(
            semesterId: Int,
            dataId: String,
            mode: LessonSearchMode,
            keyword: String,
            page: Int,
            rowsPerPage: Int,
            departmentIds: List<Long> = emptyList(),
        ): okhttp3.HttpUrl = buildSearchUrl(
            filter = LessonSearchFilter(
                semesterId = semesterId,
                mode = mode,
                keyword = keyword,
                departmentIds = departmentIds,
            ),
            dataId = dataId,
            page = page,
            rowsPerPage = rowsPerPage,
        )

        /**
         * 构造搜索请求 URL（全量筛选版，纯函数便于单测 query 拼装）。
         *
         * 固定：`bizTypeAssoc=2` + `queryPage__=<page>,<rowsPerPage>` + `assembleFields`。
         *
         * 已验证参数（HAR 实测）：`openDepartmentAssocs`(多值) / `nameZhLike` / `codeLike`。
         *
         * 推断参数（页面 `<select name>` 口径，须运行时验证）：多值维度每值一个同名 query
         * （`openDepartmentAssocs`/`departmentAssocs`/`majorAssoc`/`weekIndexs`/`courseIndexs`），
         * 单值维度一个 query（`courseTypeAssoc`/`campusAssoc`/`compulsory`/`examModeAssoc`/
         * `teachLangAssoc`/`buildingAssoc`/`adminClassAssoc`/`roomNameLike`/`creditsGte`/`creditsLte`/
         * `grades`）。空维度一律不追加，保证不影响未使用筛选时的 URL。
         */
        internal fun buildSearchUrl(
            filter: LessonSearchFilter,
            dataId: String,
            page: Int,
            rowsPerPage: Int,
        ): okhttp3.HttpUrl {
            val builder =
                "$JW_BASE/student/for-std/lesson-search/semester/${filter.semesterId}/search/$dataId"
                    .toHttpUrl().newBuilder()
                    .addQueryParameter("bizTypeAssoc", BIZ_TYPE.toString())
                    .addQueryParameter("queryPage__", "$page,$rowsPerPage")
                    .addQueryParameter("assembleFields", ASSEMBLE_FIELDS)

            // ── 已验证：开课学院多值 ──
            filter.departmentIds.distinct().forEach {
                builder.addQueryParameter("openDepartmentAssocs", it.toString())
            }
            // ── 推断：开课筛选单值 ──
            filter.courseTypeId?.let { builder.addQueryParameter("courseTypeAssoc", it.toString()) }
            filter.campusId?.let { builder.addQueryParameter("campusAssoc", it.toString()) }
            filter.compulsory?.takeIf { it.isNotBlank() }
                ?.let { builder.addQueryParameter("compulsory", it) }
            filter.examModeId?.let { builder.addQueryParameter("examModeAssoc", it.toString()) }
            filter.teachLangId?.let { builder.addQueryParameter("teachLangAssoc", it.toString()) }
            filter.buildingId?.let { builder.addQueryParameter("buildingAssoc", it.toString()) }
            filter.roomNameLike?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { builder.addQueryParameter("roomNameLike", it) }
            filter.creditsGte?.let { builder.addQueryParameter("creditsGte", trimNumber(it)) }
            filter.creditsLte?.let { builder.addQueryParameter("creditsLte", trimNumber(it)) }
            // ── 推断：开课筛选多值（星期用服务端编码；节次用 indexNo）──
            filter.weekdays.distinct().forEach {
                builder.addQueryParameter("weekIndexs", LessonSearchFilter.serverWeekday(it).toString())
            }
            filter.courseUnitIndexes.distinct().forEach {
                builder.addQueryParameter("courseIndexs", it.toString())
            }
            // ── 推断：教学班定位级联 ──
            filter.grades.distinct().forEach { builder.addQueryParameter("grades", it) }
            filter.majorDeptIds.distinct().forEach {
                builder.addQueryParameter("departmentAssocs", it.toString())
            }
            filter.majorIds.distinct().forEach {
                builder.addQueryParameter("majorAssoc", it.toString())
            }
            filter.adminClassId?.let { builder.addQueryParameter("adminClassAssoc", it.toString()) }

            // ── 关键词（已验证）──
            val trimmed = filter.keyword.trim()
            if (trimmed.isNotEmpty()) {
                builder.addQueryParameter(filter.mode.paramName, trimmed)
            }
            return builder.build()
        }

        /** 学分等 Double 去掉整数的 `.0` 尾巴（2.0 → "2"），否则原样。 */
        private fun trimNumber(v: Double): String =
            if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

        /**
         * 需服务端展开的关联字段（与 HAR 抓包一致）。
         * 决定 course/openDepartment/teacherAssignmentList/examMode/… 是否被 assemble 进响应。
         */
        private const val ASSEMBLE_FIELDS =
            "course.code,minorCourse.nameZh,courseType,openDepartment,teacherAssignmentList," +
                "examMode,campus,teachLang,roomType,timeTableLayout,crossBizTypes,courseProperty"

        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0"
    }
}
