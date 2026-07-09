package com.ahu_plus.data.repository

import android.util.Log
import com.ahu_plus.data.GsonProvider
import kotlin.math.abs
import com.ahu_plus.data.model.jw.CourseActivity
import com.ahu_plus.data.model.jw.CourseDisplayItem
import com.ahu_plus.data.model.jw.CourseUnit
import com.ahu_plus.data.model.jw.GetDataLesson
import com.ahu_plus.data.model.jw.GetDataResponse
import com.ahu_plus.data.model.jw.PrintDataResponse
import com.ahu_plus.data.model.jw.ScheduleData
import com.ahu_plus.data.model.jw.SemesterInfo
import com.ahu_plus.data.network.SecureHttpClientFactory
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 课程表数据仓库。
 *
 * 调用 jw.ahu.edu.cn 的 print-data API 获取学生课表。
 * 同时调用 get-data API 获取更丰富的课程信息(学期、当前周、考核方式等)。
 */
class CourseRepository(
    private val jwAuthRepository: JwAuthRepository,
) {

    private val gson = GsonProvider.instance

    // ── OkHttp(共享 JW 的 CookieJar,自动管理 cookie)──────
    // 不跟随重定向:让 ViewModel 决定如何处理 302
    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = jwAuthRepository.jwCookieJar,
        followRedirects = false,
        disableGzip = false,
        trustAll = true,  // jw.ahu.edu.cn 自签名证书
        extraInterceptors = listOf(
            okhttp3.Interceptor { chain ->
                // 给每个请求补上 UA 和 X-Requested-With
                val req = chain.request().newBuilder()
                    .header("User-Agent", UA)
                    .header("x-requested-with", "XMLHttpRequest")
                    .build()
                chain.proceed(req)
            }
        )
    )

    // ══════════════════════════════════════════════════════

    /**
     * 获取本学期课表数据(等价于 getSchedule(DEFAULT_SEMESTER_ID),保留旧调用方兼容)。
     */
    suspend fun getSchedule(): Result<ScheduleData> = getSchedule(DEFAULT_SEMESTER_ID)

    /**
     * 获取指定学期课表数据。
     *
     * @param semesterId 学期 ID(例如 112 = 2025-2026-2)
     */
    suspend fun getSchedule(semesterId: Int): Result<ScheduleData> {
        return try {
            Log.d(TAG, "开始获取课表数据: semesterId=$semesterId")

            // 并行请求两个端点
            val printData = fetchPrintData(semesterId)
            // get-data 为可选增强数据,失败也继续
            val getData = try {
                fetchGetData(semesterId)
            } catch (e: Exception) {
                Log.w(TAG, "get-data 获取失败(非致命): ${e.message}")
                null
            }

            val student = printData.studentTableVms?.firstOrNull()
            val layout = printData.timeTableLayout
            val unitTimes = layout?.courseUnitList ?: defaultUnitTimes()

            val activities = student?.activities ?: emptyList()
            val getDataLessons = getData?.lessons

            val semester = getData?.semester
            val currentWeek = getData?.currentWeek ?: 1
            val weekIndices = getData?.weekIndices ?: (1..20).toList()

            val scheduleData = ScheduleData(
                studentName = student?.name,
                className = student?.adminclass,
                department = student?.department,
                credits = student?.credits,
                activities = activities,
                unitTimes = unitTimes,
                semester = semester,
                currentWeek = currentWeek,
                weekIndices = weekIndices,
                lessons = getDataLessons
            )

            Log.d(TAG, "课表加载完成: ${activities.size} 个活动, ${unitTimes.size} 个节次, " +
                "第 $currentWeek 周")
            Result.success(scheduleData)
        } catch (e: Exception) {
            Log.e(TAG, "课表获取失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取本学生可用学期列表。
     *
     * JW 系统**没有**独立的学期列表 JSON API —— 学期列表嵌入在
     * `GET /student/for-std/course-table` 返回的 HTML 内的
     * `<select id="allSemesters">` 中。本方法用正则解析该 `<option>` 列表,
     * 返回按 semesterId 倒序的 [SemesterInfo] 列表(最新在前)。
     *
     * 已验证(2026-06-21): id+nameZh 完整可用;其他字段(id 之外)为 null,
     * 学期详情仍需配合 `get-data` 端点返回的 `semester` 字段获取。
     */
    suspend fun getSemesterList(): Result<List<SemesterInfo>> {
        val url = "$JW_BASE/student/for-std/course-table"
        val request = Request.Builder().url(url).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "semester list HTML HTTP ${response.code}, bytes=${body.length}")

                if (response.code == 302) {
                    return Result.failure(SessionExpiredException())
                }
                if (!response.isSuccessful) {
                    return Result.failure(Exception("学期列表请求失败: HTTP ${response.code}"))
                }
                if (body.isBlank()) {
                    return Result.failure(Exception("学期列表响应为空"))
                }

                val selectRegex = Regex(
                    """<select[^>]*id="allSemesters"[^>]*>(.*?)</select>""",
                    RegexOption.DOT_MATCHES_ALL,
                )
                val block = selectRegex.find(body)?.groupValues?.get(1)
                    ?: return Result.failure(Exception("未找到 allSemesters 列表"))

                val optionRegex = Regex("""<option\s+value="(\d+)"\s*>([^<]+)</option>""")
                val semesters = optionRegex.findAll(block).map { m ->
                    SemesterInfo(
                        id = m.groupValues[1].toInt(),
                        nameZh = m.groupValues[2],
                        code = null,
                        schoolYear = null,
                        startDate = null,
                        endDate = null,
                        season = null,
                    )
                }.sortedByDescending { it.id }.toList()

                if (semesters.isEmpty()) {
                    return Result.failure(Exception("学期列表解析为空"))
                }
                Log.d(TAG, "学期列表解析完成: ${semesters.size} 条")
                Result.success(semesters)
            }
        } catch (e: Exception) {
            Log.e(TAG, "学期列表获取失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 获取 print-data(cookie 由共享的 CookieJar 自动管理,无需手动设) */
    private suspend fun fetchPrintData(semesterId: Int): PrintDataResponse {
        val url = "$JW_BASE/student/for-std/course-table/semester/$semesterId/print-data" +
            "?semesterId=$semesterId&hasExperiment=false"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val code = response.code
            Log.d(TAG, "print-data HTTP $code, bytes=${body.length}")

            if (code == 302) {
                val loc = response.headers("Location").joinToString()
                Log.w(TAG, "print-data redirect Location: $loc")
                throw SessionExpiredException()
            }

            if (!response.isSuccessful) {
                throw Exception("print-data 请求失败: HTTP $code")
            }

            if (body.isBlank() || body[0] != '{') {
                throw Exception("print-data 返回非 JSON: ${body.take(200)}")
            }

            return gson.fromJson(body, PrintDataResponse::class.java)
        }
    }

    /** 获取 get-data(增强数据:学期信息、当前周、考核方式等) */
    private suspend fun fetchGetData(semesterId: Int): GetDataResponse {
        // dataId 和 bizTypeId 硬编码——对于同一学生的同一学期,这些值通常是固定的
        // TODO: 若学生换专业或后端变更 ID,此处可能返回错误数据或 404;理想情况应动态获取
        val url = "$JW_BASE/student/for-std/course-table/get-data" +
            "?semesterId=$semesterId&dataId=22720&bizTypeId=2"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("get-data 请求失败: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: ""
            return gson.fromJson(body, GetDataResponse::class.java)
        }
    }

    /** 默认节次时间表(兜底) */
    private fun defaultUnitTimes(): List<CourseUnit> {
        val times = listOf(
            800 to 845, 850 to 935, 950 to 1035, 1040 to 1125, 1130 to 1215,
            1400 to 1445, 1450 to 1535, 1550 to 1635, 1640 to 1725, 1730 to 1815,
            1900 to 1945, 1950 to 2035, 2040 to 2125
        )
        return times.mapIndexed { i, (start, end) ->
            CourseUnit(
                nameZh = "${i + 1}",
                indexNo = i + 1,
                startTime = start,
                endTime = end,
                dayPart = null,
                name = "${i + 1}"
            )
        }
    }

    // ══════════════════════════════════════════════════════
    // 数据转换工具
    // ══════════════════════════════════════════════════════

    companion object {
        private const val TAG = "CourseRepo"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0"
        private val COLOR_COUNT = 10

        /** 默认学期 ID (2024-2025-2,与原代码保持一致) */
        const val DEFAULT_SEMESTER_ID = 112

        /**
         * 从**所有** activities 构建稳定的 courseCode → colorIndex 映射。
         *
         * 同一 courseCode 始终得同一颜色，不同 courseCode 尽量不同色。
         * 按 hash 值贪心去重：若 hash 的槽位已被占用，选下一个空闲色。
         * 跨周共享此 map，切周不再洗牌。
         */
        fun buildColorMap(activities: List<CourseActivity>): Map<String, Int> {
            val codes = activities.mapNotNull { it.courseCode }.distinct()
            val used = HashSet<Int>(COLOR_COUNT)
            val map = LinkedHashMap<String, Int>(codes.size)
            for (code in codes.sorted()) {
                var pick = abs(code.hashCode()) % COLOR_COUNT
                if (!used.add(pick)) {
                    val free = (0 until COLOR_COUNT).firstOrNull { it !in used }
                    if (free != null) { pick = free; used.add(pick) }
                }
                map[code] = pick
            }
            return map
        }

        /**
         * 将 CourseActivity 列表转为 CourseDisplayItem 列表,
         * 只保留 selectedWeek 有课的条目。
         *
         * @param colorMap courseCode → colorIndex 映射 (由 [buildColorMap] 预计算)
         */
        fun toDisplayItems(
            activities: List<CourseActivity>,
            selectedWeek: Int,
            getDataLessons: List<GetDataLesson>?,
            colorMap: Map<String, Int> = emptyMap(),
        ): List<CourseDisplayItem> {
            val getDataMap = getDataLessons?.associateBy { it.id } ?: emptyMap()

            return activities
                .filter { activity ->
                    val weeks = activity.weekIndexes ?: emptyList()
                    weeks.contains(selectedWeek)
                }
                .map { activity ->
                    val gdLesson = activity.lessonId?.let { getDataMap[it] }
                    val courseName = activity.courseName ?: "未知课程"
                    val colorIndex = activity.courseCode?.let { colorMap[it] }
                        ?: (abs(courseName.hashCode()) % COLOR_COUNT)
                    CourseDisplayItem(
                        lessonId = activity.lessonId ?: 0,
                        courseName = courseName,
                        courseCode = activity.courseCode,
                        teacherNames = activity.teacherNames?.joinToString("、")
                            ?: activity.teachers?.joinToString("、") ?: "",
                        room = activity.room,
                        weekday = activity.weekday ?: 0,
                        startUnit = activity.startUnit ?: 0,
                        endUnit = activity.endUnit ?: 0,
                        weekIndexes = activity.weekIndexes ?: emptyList(),
                        weeksStr = activity.weeksStr,
                        startTime = activity.startTime,
                        endTime = activity.endTime,
                        courseType = activity.courseType?.nameZh,
                        credits = activity.credits ?: gdLesson?.course?.credits,
                        campus = activity.campus,
                        colorIndex = colorIndex,
                        lessonDetail = gdLesson,
                    )
                }
                .sortedWith(compareBy({ it.weekday }, { it.startUnit }, { it.courseCode }))
        }
    }
}
