package com.ahu_plus.data.repository

import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.model.jw.CourseDisplayItem
import com.ahu_plus.data.model.jw.CourseUnit
import com.ahu_plus.data.model.jwapp.JwAppBuilding
import com.ahu_plus.data.model.jwapp.JwAppCampus
import com.ahu_plus.data.model.jwapp.JwAppLesson
import com.ahu_plus.data.model.jwapp.JwAppRoomPageData
import com.ahu_plus.data.model.jwapp.JwAppRoomTableData
import com.ahu_plus.data.model.jwapp.JwAppRoomType
import com.ahu_plus.data.model.jwapp.JwAppSemester
import com.ahu_plus.data.model.jwapp.JwAppTimetableLayout
import com.ahu_plus.data.model.jwapp.RoomCourseTableData
import com.ahu_plus.data.model.jwapp.RoomSearchFilter
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.lang.reflect.Type

class RoomCourseTableRepository(
    private val authRepository: JwAppAuthRepository,
) {
    suspend fun getCurrentSemester(): Result<JwAppSemester> = request("/lesson/room/current-semester")

    suspend fun getSemesters(): Result<List<JwAppSemester>> = requestList(
        "/semester/bizType-semesters?bizTypeId=2",
        JwAppSemester::class.java,
    )

    suspend fun getCampuses(): Result<List<JwAppCampus>> = requestList(
        "/lesson/room/getCampuses",
        JwAppCampus::class.java,
    )

    suspend fun getRoomTypes(): Result<List<JwAppRoomType>> = requestList(
        "/lesson/room/getRoomTypes",
        JwAppRoomType::class.java,
    )

    suspend fun getBuildings(campusId: Int): Result<List<JwAppBuilding>> = requestList(
        "/lesson/room/get-buildings-by-campus/$campusId",
        JwAppBuilding::class.java,
    )

    suspend fun searchRooms(
        filter: RoomSearchFilter,
        page: Int,
        pageSize: Int = PAGE_SIZE,
    ): Result<JwAppRoomPageData> {
        val url = "$API_BASE/lesson/room/searchRooms".toHttpUrl().newBuilder().apply {
            addQueryParameter("queryPage__", "$page,$pageSize")
            addQueryParameter("name", filter.name.trim())
            addQueryParameter("semesterAssoc", filter.semesterId?.toString().orEmpty())
            addQueryParameter("occupied", filter.occupied?.toString().orEmpty())
            addQueryParameter("campus", filter.campusId?.toString().orEmpty())
            addQueryParameter("building", filter.buildingId?.toString().orEmpty())
            addQueryParameter("floor", filter.floor?.toString().orEmpty())
            addQueryParameter("roomType", filter.roomTypeId?.toString().orEmpty())
            addQueryParameter("seatsForLessonLowerLimit", filter.seatsLower?.toString().orEmpty())
            addQueryParameter("seatsForLessonUpperLimit", filter.seatsUpper?.toString().orEmpty())
            addQueryParameter("virtual", if (filter.includeVirtual) "" else "false")
            addQueryParameter("bizTypeAssoc", filter.bizTypeId.toString())
        }.build()
        return requestUrl(url.toString(), JwAppRoomPageData::class.java)
    }

    suspend fun getRoomSchedule(
        roomId: Long,
        semester: JwAppSemester,
    ): Result<RoomCourseTableData> = withContext(Dispatchers.IO) {
        runCatching {
            val table: JwAppRoomTableData = requestBlocking(
                "/lesson/room/semester/${semester.id}/getCourseTableData/$roomId",
                JwAppRoomTableData::class.java,
            )
            coroutineScope {
                val layoutDeferred = async<JwAppTimetableLayout> {
                    requestBlocking<JwAppTimetableLayout>(
                        "/lesson/room/getTimetableLayout/${table.timeTableLayoutId}",
                        JwAppTimetableLayout::class.java,
                    )
                }
                val lessonsDeferred = async {
                    val type = TypeToken.getParameterized(List::class.java, JwAppLesson::class.java).type
                    requestBlocking<List<JwAppLesson>>(
                        "/lesson/room/getLessons?semesterAssoc=${semester.id}&roomAssoc=$roomId",
                        type,
                    )
                }
                val layout = layoutDeferred.await()
                val lessons = lessonsDeferred.await()
                val units = layout.courseUnitList.map { unit ->
                    CourseUnit(
                        nameZh = unit.nameZh,
                        indexNo = unit.indexNo,
                        startTime = unit.startTime,
                        endTime = unit.endTime,
                        dayPart = unit.dayPart,
                        name = unit.nameZh,
                    )
                }
                RoomCourseTableData(
                    semester = semester,
                    room = table.roomDeepVm,
                    weekIndices = table.weekIndices.ifEmpty { semester.weekIndices },
                    currentWeek = table.currentWeek,
                    unitTimes = units,
                    lessons = lessons,
                    displayItems = mapLessonsToDisplayItems(roomId, table.roomDeepVm.nameZh, lessons),
                )
            }
        }
    }

    private suspend inline fun <reified T> request(path: String): Result<T> =
        requestUrl("$API_BASE$path", T::class.java)

    private suspend fun <T> requestList(path: String, itemClass: Class<T>): Result<List<T>> {
        val type = TypeToken.getParameterized(List::class.java, itemClass).type
        return requestUrl("$API_BASE$path", type)
    }

    private suspend fun <T> requestUrl(url: String, type: Type): Result<T> =
        withContext(Dispatchers.IO) { runCatching { requestBlockingUrl(url, type) } }

    private fun <T> requestBlocking(path: String, type: Type): T =
        requestBlockingUrl("$API_BASE$path", type)

    private fun <T> requestBlockingUrl(url: String, type: Type): T {
        val body = authRepository.executeAuthorized(url)
        val root = JsonParser.parseString(body).asJsonObject
        if (root.get("result")?.asInt != 0) {
            throw IOException(root.get("message")?.asString ?: "教室课表接口返回失败")
        }
        val data = root.get("data") ?: throw IOException("教室课表接口缺少 data")
        return GsonProvider.instance.fromJson(data, type)
    }

    companion object {
        const val PAGE_SIZE = 20
        private const val API_BASE = "https://jwapp.ahu.edu.cn/eams-micro-server/api/v1"

        internal fun mapLessonsToDisplayItems(
            roomId: Long,
            roomName: String,
            lessons: List<JwAppLesson>,
        ): List<CourseDisplayItem> {
            data class SlotKey(val weekday: Int, val startUnit: Int, val endUnit: Int)

            return lessons.flatMap { lesson ->
                val roomSchedules = lesson.schedules.filter { it.room?.id == roomId }
                roomSchedules.groupBy { SlotKey(it.weekday, it.startUnit, it.endUnit) }
                    .map { (slot, schedules) ->
                        val courseName = lesson.course?.nameZh ?: lesson.nameZh ?: "未命名课程"
                        val teachers = (schedules.mapNotNull { it.teacherName } + lesson.teacherAssignmentList)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString("、")
                        val weeks = schedules.map { it.weekIndex }.distinct().sorted()
                        val first = schedules.first()
                        CourseDisplayItem(
                            lessonId = lesson.id,
                            courseName = courseName,
                            courseCode = lesson.course?.code ?: lesson.code,
                            teacherNames = teachers,
                            room = roomName,
                            weekday = slot.weekday,
                            startUnit = slot.startUnit,
                            endUnit = slot.endUnit,
                            weekIndexes = weeks,
                            weeksStr = weeks.joinToString(",") { it.toString() },
                            startTime = formatTime(first.realStartTime ?: first.startTime),
                            endTime = formatTime(first.realEndTime ?: first.endTime),
                            courseType = lesson.courseType?.nameZh,
                            credits = lesson.course?.credits,
                            campus = first.room?.campus?.nameZh,
                            colorIndex = (courseName.hashCode() and Int.MAX_VALUE) % 10,
                        )
                    }
            }.sortedWith(compareBy<CourseDisplayItem> { it.weekday }.thenBy { it.startUnit })
        }

        private fun formatTime(value: Int): String = "%02d:%02d".format(value / 100, value % 100)
    }
}
