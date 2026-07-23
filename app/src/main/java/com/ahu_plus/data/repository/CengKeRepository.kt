package com.ahu_plus.data.repository

import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.model.jwapp.CengCourse
import com.ahu_plus.data.model.jwapp.JwAppBuilding
import com.ahu_plus.data.model.jwapp.JwAppCampus
import com.ahu_plus.data.model.jwapp.JwAppRoomType
import com.ahu_plus.data.model.jwapp.RoomPlacePageData
import com.ahu_plus.data.model.jwapp.RoomPlaceRequest
import com.ahu_plus.data.model.jwapp.RoomWithOccupancy
import com.ahu_plus.data.model.jwapp.TimeSlot
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.reflect.Type

/**
 * 蹭课(随机听课)数据仓库。数据源 `jwapp.ahu.edu.cn/eams-micro-server/api/v1/room/place/…`,
 * 与 [RoomCourseTableRepository] 同后端、同鉴权,复用 [JwAppAuthRepository]。
 *
 * 主流程:[fetchLessons] 按 date + campus (+可选楼/教室类型) 分页拉取全部教室占用,
 * 解析出可蹭的 [CengCourse] 候选池;随机推荐与二次过滤(学院/时段/换一个)在 ViewModel 内存里做。
 *
 * 纯函数([CengKeParser])独立出来便于 JVM 单测(无 Android/网络依赖)。
 */
class CengKeRepository(
    private val authRepository: JwAppAuthRepository,
) {
    /** 校区列表(enabled 过滤留给 ViewModel,便于测试原始返回)。 */
    suspend fun getCampuses(): Result<List<JwAppCampus>> = requestList(
        "/room/place/campus",
        JwAppCampus::class.java,
    )

    /** 教室类型(13 种)。 */
    suspend fun getRoomTypes(): Result<List<JwAppRoomType>> = requestList(
        "/room/place/roomTypes",
        JwAppRoomType::class.java,
    )

    /** 指定校区的教学楼。查询参数是 campusAssoc,不是 campusId 路径段。 */
    suspend fun getBuildings(campusId: Int): Result<List<JwAppBuilding>> = requestList(
        "/room/place/building?campusAssoc=$campusId",
        JwAppBuilding::class.java,
    )

    /**
     * 拉取指定日期 + 校区(+可选楼/教室类型)的全部教室占用,解析成可蹭课程候选池。
     *
     * 先取第一页拿到 totalPages,再并发拉其余页(页数少:选楼通常 1 页,整校区约 5 页)。
     * 解析只保留 activityType == "Lesson" 且 activityName 能解析出课程信息的记录,并去重。
     */
    suspend fun fetchLessons(
        date: String,
        campusId: Int,
        buildingIds: List<Int> = emptyList(),
        roomTypeIds: List<Int> = emptyList(),
        buildingNames: Map<Int, String> = emptyMap(),
    ): Result<List<CengCourse>> = withContext(Dispatchers.IO) {
        runCatching {
            fun body(page: Int) = RoomPlaceRequest(
                currentPage = page,
                pageSize = PAGE_SIZE,
                campusAssoc = campusId,
                buildingIds = buildingIds,
                roomTypeIds = roomTypeIds,
                date = date,
            )

            val first = requestRooms(body(1))
            val totalPages = first.page.totalPages.coerceAtLeast(1)
            val remaining = if (totalPages > 1) {
                coroutineScope {
                    (2..totalPages).map { page ->
                        async { requestRooms(body(page)) }
                    }.awaitAll()
                }
            } else {
                emptyList()
            }
            val allRooms = first.data + remaining.flatMap { it.data }
            CengKeParser.parseCourses(allRooms, buildingNames)
        }
    }

    private fun requestRooms(request: RoomPlaceRequest): RoomPlacePageData {
        val json = GsonProvider.instance.toJson(request)
        val body = authRepository.executeAuthorizedPost("$API_BASE/room/place/rooms", json)
        val root = JsonParser.parseString(body).asJsonObject
        if (root.get("result")?.asInt != 0) {
            throw IOException(root.get("message")?.asString ?: "教室占用接口返回失败")
        }
        val data = root.get("data") ?: throw IOException("教室占用接口缺少 data")
        return GsonProvider.instance.fromJson(data, RoomPlacePageData::class.java)
    }

    private suspend fun <T> requestList(path: String, itemClass: Class<T>): Result<List<T>> {
        val type = TypeToken.getParameterized(List::class.java, itemClass).type
        return withContext(Dispatchers.IO) { runCatching { requestBlockingList(path, type) } }
    }

    private fun <T> requestBlockingList(path: String, type: Type): T {
        val body = authRepository.executeAuthorized("$API_BASE$path")
        val root = JsonParser.parseString(body).asJsonObject
        if (root.get("result")?.asInt != 0) {
            throw IOException(root.get("message")?.asString ?: "教务移动端接口返回失败")
        }
        val data = root.get("data") ?: throw IOException("教务移动端接口缺少 data")
        return GsonProvider.instance.fromJson(data, type)
    }

    companion object {
        const val PAGE_SIZE = 200
        private const val API_BASE = "https://jwapp.ahu.edu.cn/eams-micro-server/api/v1"
    }
}

/**
 * 蹭课纯逻辑:activityName 解析、时段分档、候选池构建、客户端过滤、随机挑选。
 * 无 Android/网络依赖,直接 JVM 单测。
 */
object CengKeParser {
    // "课程：<课程名>(<full_code>, <开课学院>)" —— 课程名可含全角括号（下）,
    // 半角 ( 才是 full_code 的锚点。full_code 形如 202620271-GG61015.087。
    private val COURSE_PATTERN =
        Regex("课程：([^,()]+?)\\((\\d{9}-[A-Z0-9]+\\.\\d{3}),?\\s*(.*?)\\)")
    private val CODE_PATTERN = Regex("(\\d{9})-([A-Z0-9]+)\\.(\\d{3})")

    /** 按开始时间(int,如 800/1400/1900)分时段。 */
    fun timeSlotOf(startTime: Int): TimeSlot = when {
        startTime < 1200 -> TimeSlot.MORNING
        startTime < 1800 -> TimeSlot.AFTERNOON
        else -> TimeSlot.EVENING
    }

    /**
     * 解析单条 activityName。非 "课程：..." 格式或无法匹配 full_code 时返回 null
     * (Triple: 课程名, full_code, 开课学院)。
     */
    fun parseActivityName(activityName: String?): Triple<String, String, String>? {
        if (activityName.isNullOrBlank()) return null
        val m = COURSE_PATTERN.find(activityName) ?: return null
        val name = m.groupValues[1].trim()
        val fullCode = m.groupValues[2].trim()
        val college = m.groupValues[3].trim().trimEnd(',', '，')
        if (name.isBlank() || !CODE_PATTERN.matches(fullCode)) return null
        return Triple(name, fullCode, college)
    }

    /** 从 full_code 抠出课程代码段(GG61015),失败回退整串。 */
    fun courseCodeOf(fullCode: String): String =
        CODE_PATTERN.matchEntire(fullCode)?.groupValues?.get(2) ?: fullCode

    /**
     * 把教室占用列表摊平解析成可蹭课程候选,去重(同课同教室同起始时间)。
     * [buildingNames] 提供 buildingId → 楼名 映射(可空,缺失时 buildingName=null)。
     */
    fun parseCourses(
        rooms: List<RoomWithOccupancy>,
        buildingNames: Map<Int, String> = emptyMap(),
    ): List<CengCourse> {
        val seen = HashSet<String>()
        val out = ArrayList<CengCourse>()
        for (room in rooms) {
            for (occ in room.occupations) {
                if (occ.activityType != "Lesson") continue
                val parsed = parseActivityName(occ.activityName) ?: continue
                val (name, fullCode, college) = parsed
                val date = occ.date ?: continue
                val startStr = occ.startTimeString ?: continue
                val course = CengCourse(
                    courseName = name,
                    fullCode = fullCode,
                    courseCode = courseCodeOf(fullCode),
                    college = college,
                    teacher = occ.teacherName?.trim().orEmpty(),
                    roomName = room.nameZh.orEmpty(),
                    buildingId = room.buildingId,
                    buildingName = room.buildingId?.let { buildingNames[it] },
                    campusName = room.campusNameZh,
                    floor = room.floor,
                    date = date,
                    startTimeString = startStr,
                    endTimeString = occ.endTimeString.orEmpty(),
                    startTime = occ.startTime,
                    timeSlot = timeSlotOf(occ.startTime),
                )
                if (seen.add(course.dedupKey)) out.add(course)
            }
        }
        return out
    }

    /** 候选池里出现过的开课学院(按出现频次降序,便于 chip 展示常见学院在前)。 */
    fun distinctColleges(pool: List<CengCourse>): List<String> =
        pool.asSequence()
            .map { it.college }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    /**
     * 客户端二次过滤:按时段 + 学院集合。[slots]/[colleges] 为空表示该维度不限。
     */
    fun filter(
        pool: List<CengCourse>,
        slots: Set<TimeSlot> = emptySet(),
        colleges: Set<String> = emptySet(),
    ): List<CengCourse> = pool.filter { course ->
        (slots.isEmpty() || course.timeSlot in slots) &&
            (colleges.isEmpty() || course.college in colleges)
    }

    /**
     * 从过滤后的池里随机挑一节,尽量不等于 [exclude](即"换一个")。
     * 池为空返回 null;池里只有 exclude 那一节时仍返回它(无从可换)。
     */
    fun pickRandom(
        filtered: List<CengCourse>,
        exclude: CengCourse? = null,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): CengCourse? {
        if (filtered.isEmpty()) return null
        if (exclude == null) return filtered[random.nextInt(filtered.size)]
        val others = filtered.filter { it.dedupKey != exclude.dedupKey }
        val source = others.ifEmpty { filtered }
        return source[random.nextInt(source.size)]
    }
}

