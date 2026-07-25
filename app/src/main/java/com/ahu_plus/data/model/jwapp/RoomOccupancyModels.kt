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
    // ⚠ 可空:无课教室接口返回 roomOccupationInfoVms=null。Gson 经 Unsafe 建对象、
    //   绕过 Kotlin 构造器与默认值,`= emptyList()` 不生效,故类型必须诚实标可空,
    //   用 .orEmpty() 消费(见 CengKeParser.parseCourses)。
    @SerializedName("roomOccupationInfoVms")
    val occupations: List<RoomOccupationInfo>? = emptyList(),
)

/** `data` 分页壳:`data.data` 是教室数组,`data._page_` 是分页信息。 */
data class RoomPlacePageData(
    // ⚠ 可空:同上 Gson 绕默认值,接口可能返回 data=null。消费方用 .orEmpty()。
    val data: List<RoomWithOccupancy>? = emptyList(),
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
 * 教室名由所在 [RoomWithOccupancy] 补齐。楼层不展示:jwapp 的 floor 字段不可靠
 * (博北等楼恒返回 0),真实楼层已含在教室号里(如 B211 → 2 楼)。
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
    val date: String,
    val startTimeString: String,
    val endTimeString: String,
    val startTime: Int,
    val timeSlot: TimeSlot,
) {
    /** 去重键:同课同教室同起始时间视为同一节。 */
    val dedupKey: String get() = "$fullCode|$date|$startTime|$roomName"
}

/**
 * 一节蹭课的富化详情,来自全校开课查询(lesson-search)按 [CengCourse.fullCode] 精确匹配的
 * `LessonRecord`。蹭课的教室占用接口只给到课名/教师/时间/地点,这些是它拿不到的字段:
 * 选课人数、学分、课程性质/类型、面向班级、考核方式、授课语言。
 *
 * 富化是 best-effort:需要 jw 教务会话且能在当前学期匹配到该教学班;缺任一条件时为 null,
 * 卡片保持蹭课原样。字段全可空,由 [CengKeParser.matchDetail] 从 `LessonRecord` 映射。
 */
data class CengCourseDetail(
    val stdCount: Int?,        // 已选人数
    val limitCount: Int?,      // 人数上限
    val credits: Double?,      // 学分
    val courseProperty: String?, // 课程性质(必修/选修)
    val className: String?,    // 面向班级(教学班名,如 "2024级功能材料1班")
    val courseType: String?,   // 课程类型(理论课/实验课…)
    val examMode: String?,     // 考核方式(考试/考查)
    val teachLang: String?,    // 授课语言(中文/双语/外语)
) {
    /** 至少有一个字段可展示(全空时 UI 不显示富化区)。 */
    val hasAny: Boolean
        get() = stdCount != null || limitCount != null || credits != null ||
            !courseProperty.isNullOrBlank() || !className.isNullOrBlank() ||
            !courseType.isNullOrBlank() || !examMode.isNullOrBlank() || !teachLang.isNullOrBlank()

    /** 是否已满员(已选 ≥ 上限,且上限有效)。 */
    fun isFull(): Boolean {
        val std = stdCount ?: return false
        val limit = limitCount ?: return false
        return limit > 0 && std >= limit
    }

    /** 选课人数展示,如 "已选 86 / 120"。两者都缺则返回 null。 */
    fun enrollmentText(): String? = when {
        stdCount != null && limitCount != null -> "已选 $stdCount / $limitCount"
        stdCount != null -> "已选 $stdCount"
        limitCount != null -> "上限 $limitCount"
        else -> null
    }
}
