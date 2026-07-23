package com.ahu_plus.data.model.jwapp

import com.google.gson.annotations.SerializedName

/**
 * `/room/place/rooms` (POST) 教室占用数据模型。
 *
 * 与 [RoomCourseTableModels] 里的 `/lesson/room/…` 家族区分:
 *  - `/lesson/room/…`  → 单教室整学期课表(RoomCourseTable 功能用)
 *  - `/room/place/…`   → 按日期返回教室 + 当日占用(蹭课/CengKe 功能用)
 *
 * ⚠ campusId 空间与静态 [com.ahu_plus.data.model.CampusBuildingData] **不同**:
 *   本接口 磬苑=1 / 龙河=2 / 金寨路=22 / 纯线上=6,不可复用静态表,元数据一律从 API 拉。
 *
 * 校区 [JwAppCampus]、教室类型 [JwAppRoomType]、教学楼 [JwAppBuilding] 字段吻合,直接复用。
 * 教学楼查询参数是 `campusAssoc=<campusId>`(见 [CengKeRepository])。
 */

/** POST /room/place/rooms 请求体。 */
data class RoomPlaceRequest(
    val currentPage: Int,
    val pageSize: Int,
    val campusAssoc: Int?,
    val buildingIds: List<Int> = emptyList(),
    val roomTypeIds: List<Int> = emptyList(),
    val floors: List<Int> = emptyList(),
    val minSeat: String = "",
    val maxSeat: String = "",
    val date: String,
)

/**
 * 单条占用记录。`week/weekDay/unitStart/unitEnd` 接口恒为 null(实测),故本功能按具体
 * 日历日期查询,不做教学周换算。[activityName] 形如
 * `课程：<课程名>(<full_code>, <开课学院>)`,由 [CengKeParser] 解析。
 */
data class RoomOccupationInfo(
    val activityType: String? = null,
    val activityName: String? = null,
    val date: String? = null,
    val startTime: Int = 0,
    val endTime: Int = 0,
    val startTimeString: String? = null,
    val endTimeString: String? = null,
    val teacherName: String? = null,
)

/** 单间教室 + 当日占用列表。 */
data class RoomWithOccupancy(
    val id: Long,
    val nameZh: String? = null,
    val nameEn: String? = null,
    val code: String? = null,
    val floor: Int? = null,
    val buildingId: Int? = null,
    val campusNameZh: String? = null,
    val seatsForLesson: Int? = null,
    val virtual: Boolean = false,
    val enabled: Boolean = true,
    val experiment: Boolean = false,
    @SerializedName("roomOccupationInfoVms")
    val occupations: List<RoomOccupationInfo> = emptyList(),
)

/** `data` 分页壳:`data.data` 是教室数组,`data._page_` 是分页信息。 */
data class RoomPlacePageData(
    val data: List<RoomWithOccupancy> = emptyList(),
    @SerializedName("_page_") val page: JwAppPage = JwAppPage(),
)

/** 一天中的时段。分界:上午 <12:00,下午 12:00~18:00,晚上 ≥18:00(按 startTime int)。 */
enum class TimeSlot(val label: String) {
    MORNING("上午"),
    AFTERNOON("下午"),
    EVENING("晚上"),
}

/**
 * 解析后的一节可蹭课程(推荐候选)。一条 [RoomOccupationInfo] 解析成一个候选,
 * 教室名/楼由所在 [RoomWithOccupancy] 补齐。
 */
data class CengCourse(
    val courseName: String,
    val fullCode: String,
    val courseCode: String,
    val college: String,
    val teacher: String,
    val roomName: String,
    val buildingId: Int?,
    val buildingName: String?,
    val campusName: String?,
    val floor: Int?,
    val date: String,
    val startTimeString: String,
    val endTimeString: String,
    val startTime: Int,
    val timeSlot: TimeSlot,
) {
    /** 去重键:同课同教室同起始时间视为同一节。 */
    val dedupKey: String get() = "$fullCode|$date|$startTime|$roomName"
}
