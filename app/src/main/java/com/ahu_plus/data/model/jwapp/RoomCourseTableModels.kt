package com.ahu_plus.data.model.jwapp

import com.google.gson.annotations.SerializedName

data class JwAppAccount(
    val id: String? = null,
    val name: String? = null,
    val userName: String? = null,
    val identityTypeName: String? = null,
    val identityTypeCode: String? = null,
) {
    fun displayName(): String = listOfNotNull(name, userName, identityTypeName)
        .firstOrNull { it.isNotBlank() } ?: id.orEmpty()
}

data class JwAppSemester(
    val id: Int,
    val code: String? = null,
    val nameZh: String,
    val schoolYear: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val weekIndices: List<Int> = emptyList(),
)

data class JwAppCampus(
    val id: Int,
    val nameZh: String,
    val code: String? = null,
    val enabled: Boolean = true,
)

data class JwAppBuilding(
    val id: Int,
    val nameZh: String,
    val code: String? = null,
    val enabled: Boolean = true,
    val campus: JwAppCampus? = null,
)

data class JwAppRoomType(
    val id: Int,
    val nameZh: String,
    val code: String? = null,
    val enabled: Boolean = true,
)

data class JwAppRoom(
    val id: Long,
    val nameZh: String,
    val code: String? = null,
    val floor: Int? = null,
    val virtual: Boolean = false,
    val seatsForLesson: Int? = null,
    val enabled: Boolean = true,
    val experiment: Boolean = false,
    val building: JwAppBuilding? = null,
    val roomType: JwAppRoomType? = null,
)

data class JwAppPage(
    val currentPage: Int = 1,
    val rowsInPage: Int = 0,
    val rowsPerPage: Int = 20,
    val totalRows: Int = 0,
    val totalPages: Int = 0,
)

data class JwAppRoomPageData(
    val data: List<JwAppRoom> = emptyList(),
    @SerializedName("_page_") val page: JwAppPage = JwAppPage(),
)

data class RoomSearchFilter(
    val name: String = "",
    val semesterId: Int? = null,
    val occupied: Boolean? = null,
    val campusId: Int? = null,
    val buildingId: Int? = null,
    val floor: Int? = null,
    val roomTypeId: Int? = null,
    val seatsLower: Int? = null,
    val seatsUpper: Int? = null,
    val includeVirtual: Boolean = false,
    val bizTypeId: Int = 2,
)

data class JwAppRoomTableData(
    val timeTableLayoutId: Int,
    val weekIndices: List<Int> = emptyList(),
    val currentWeek: Int = 1,
    val roomDeepVm: JwAppRoom,
)

data class JwAppTimetableLayout(
    val id: Int,
    val nameZh: String? = null,
    val courseUnitList: List<JwAppCourseUnit> = emptyList(),
)

data class JwAppCourseUnit(
    val nameZh: String? = null,
    val indexNo: Int,
    val startTime: Int,
    val endTime: Int,
    val dayPart: String? = null,
)

data class JwAppCourse(
    val id: Long? = null,
    val code: String? = null,
    val nameZh: String? = null,
    val nameEn: String? = null,
    val credits: Double? = null,
)

data class JwAppNamedValue(
    val id: Int? = null,
    val nameZh: String? = null,
    val code: String? = null,
)

data class JwAppScheduleRoom(
    val id: Long,
    val nameZh: String? = null,
    val building: JwAppBuilding? = null,
    val campus: JwAppCampus? = null,
)

data class JwAppSchedule(
    val date: String? = null,
    val originalDate: String? = null,
    val weekday: Int,
    val startTime: Int,
    val endTime: Int,
    val teacherName: String? = null,
    val room: JwAppScheduleRoom? = null,
    val startUnit: Int,
    val endUnit: Int,
    val weekIndex: Int,
    val lessonType: String? = null,
    val state: String? = null,
    val realStartTime: Int? = null,
    val realEndTime: Int? = null,
)

data class JwAppText(
    val textZh: String? = null,
    val text: String? = null,
)

data class JwAppScheduleText(
    val dateTimeText: JwAppText? = null,
    val dateTimePlaceText: JwAppText? = null,
    val dateTimePlacePersonText: JwAppText? = null,
)

data class JwAppLesson(
    val id: Long,
    val nameZh: String? = null,
    val code: String? = null,
    val course: JwAppCourse? = null,
    val stdCount: Int? = null,
    val openDepartment: JwAppNamedValue? = null,
    val courseType: JwAppNamedValue? = null,
    val teacherAssignmentList: List<String> = emptyList(),
    val scheduleText: JwAppScheduleText? = null,
    val schedules: List<JwAppSchedule> = emptyList(),
    val timeTableLayoutAssoc: Int? = null,
)

data class RoomCourseTableData(
    val semester: JwAppSemester,
    val room: JwAppRoom,
    val weekIndices: List<Int>,
    val currentWeek: Int,
    val unitTimes: List<com.ahu_plus.data.model.jw.CourseUnit>,
    val lessons: List<JwAppLesson>,
    val displayItems: List<com.ahu_plus.data.model.jw.CourseDisplayItem>,
)
