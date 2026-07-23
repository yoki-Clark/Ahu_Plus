package com.ahu_plus.data.model.jw

import com.google.gson.annotations.SerializedName

/**
 * 全校开课查询（lesson-search）数据模型。
 *
 * 端点：`GET /student/for-std/lesson-search/semester/{semesterId}/search/{dataId}`
 * 鉴权：复用 JW SESSION cookie（CAS）。
 * 编码：响应为 **UTF-8 JSON**（2026-07-23 HAR 实测 content.encoding=None、mimeType=application/json，
 *      中文直接可读；早期"GBK"判断有误）。
 *
 * 顶层结构：`{"data":[...], "_page_":{...}, "_sorts_":[]}`。
 *
 * 注意：Gson 用 Unsafe 建对象不调用 Kotlin 构造器，非空 List 字段运行时可能为 null。
 * 因此所有集合字段声明为可空，读取处一律 `.orEmpty()`（见 [[gson-bypasses-kotlin-defaults]]）。
 */
data class LessonSearchResponse(
    @SerializedName("data") val data: List<LessonRecord>?,
    @SerializedName("_page_") val page: LessonPageInfo?,
)

/**
 * 分页信息。`totalPages`/`totalRows` 用于"加载更多"判断，`currentPage` 从 1 开始。
 * 请求分页参数 `queryPage__=<page>,<rowsPerPage>`（逗号分隔，page 从 1 起）。
 */
data class LessonPageInfo(
    @SerializedName("currentPage") val currentPage: Int?,
    @SerializedName("rowsInPage") val rowsInPage: Int?,
    @SerializedName("rowsPerPage") val rowsPerPage: Int?,
    @SerializedName("totalRows") val totalRows: Int?,
    @SerializedName("totalPages") val totalPages: Int?,
)

/**
 * 单条开课记录（一个教学班）。
 *
 * 字段对照 HAR 实测（2026-07-23）：
 * - [code] 教学班编号，如 `202620271-GG17008.010`
 * - [nameZh] 教学班名称（面向的班级），如 `2024级功能材料1班`
 * - [course] 课程本体（课程名/学分/课程号）
 * - [minorCourse] 子课程/分组名（可能含"（五）"等后缀），部分课程与 course.nameZh 不同
 * - [openDepartment] 开课学院
 * - [teacherAssignmentList] 授课教师列表（role=MAJOR 为主讲）
 * - [stdCount]/[limitCount] 已选/上限人数
 * - [courseType] 课程类型（实践课/理论课…）
 * - [courseProperty] 课程性质（必修/选修…）
 * - [examMode] 考核方式（考试/考查）
 * - [teachLang] 授课语言
 * - [scheduleText] 上课时间/地点/教师的展示文本（textZh 直接可展示）
 * - [requiredPeriodInfo] 学时信息（总学时/周数/每周节数）
 */
data class LessonRecord(
    @SerializedName("id") val id: Long?,
    @SerializedName("code") val code: String?,
    @SerializedName("nameZh") val nameZh: String?,
    @SerializedName("course") val course: LessonCourse?,
    @SerializedName("minorCourse") val minorCourse: LessonNamed?,
    @SerializedName("openDepartment") val openDepartment: LessonNamed?,
    @SerializedName("teacherAssignmentList") val teacherAssignmentList: List<LessonTeacher>?,
    @SerializedName("stdCount") val stdCount: Int?,
    @SerializedName("limitCount") val limitCount: Int?,
    @SerializedName("courseType") val courseType: LessonNamed?,
    @SerializedName("courseProperty") val courseProperty: LessonNamed?,
    @SerializedName("examMode") val examMode: LessonNamed?,
    @SerializedName("teachLang") val teachLang: LessonNamed?,
    @SerializedName("scheduleText") val scheduleText: LessonScheduleText?,
    @SerializedName("requiredPeriodInfo") val requiredPeriodInfo: LessonPeriodInfo?,
    @SerializedName("remark") val remark: String?,
) {
    /** 主讲教师优先；无 role 信息时取第一位。多教师用「、」连接。 */
    fun teacherNames(): String {
        val list = teacherAssignmentList.orEmpty()
        if (list.isEmpty()) return ""
        val names = list.mapNotNull { it.person?.nameZh?.takeIf { n -> n.isNotBlank() } }
        return names.distinct().joinToString("、")
    }

    /** 课程名：优先 course.nameZh，回退教学班名。 */
    fun courseName(): String = course?.nameZh?.takeIf { it.isNotBlank() } ?: nameZh.orEmpty()

    /** 上课时间/地点展示文本（含周次、星期、节次、校区、教室、教师）。 */
    fun scheduleZh(): String = scheduleText?.dateTimePlacePersonText?.textZh
        ?: scheduleText?.dateTimePlaceText?.textZh
        ?: scheduleText?.dateTimeText?.textZh
        ?: ""

    /** 是否已满员。 */
    fun isFull(): Boolean {
        val std = stdCount ?: return false
        val limit = limitCount ?: return false
        return limit > 0 && std >= limit
    }
}

/**
 * 开课学院（`GET /student/ws/select-department/departments/getAllByIsOpenCourse` 顶层数组元素）。
 *
 * 用作服务端筛选 `openDepartmentAssocs`（多值）的 id 源。2026-07-23 HAR 实测 50 项，
 * 如 数学科学学院(id=2) / 计算机科学与技术学院(id=7) / 材料科学与工程学院(id=76)。
 */
data class LessonDepartment(
    @SerializedName("id") val id: Long?,
    @SerializedName("nameZh") val nameZh: String?,
) {
    /** 是否可用作筛选（id 与名齐全）。 */
    fun isUsable(): Boolean = id != null && !nameZh.isNullOrBlank()
}

/** 课程本体：课程号 + 中文名 + 学分 + 内部 id。 */
data class LessonCourse(
    @SerializedName("code") val code: String?,
    @SerializedName("nameZh") val nameZh: String?,
    @SerializedName("credits") val credits: Double?,
    @SerializedName("id") val id: Long?,
)

/** 通用「只有中文名」的引用对象（学院/课程类型/性质/考核/语言/子课程）。 */
data class LessonNamed(
    @SerializedName("nameZh") val nameZh: String?,
    @SerializedName("id") val id: Long?,
)

/** 授课教师条目：role=MAJOR 主讲；person.nameZh 为姓名。 */
data class LessonTeacher(
    @SerializedName("role") val role: String?,
    @SerializedName("person") val person: LessonNamed?,
)

/** 上课时间/地点展示文本集合（各字段的 textZh 已是可直接展示的中文）。 */
data class LessonScheduleText(
    @SerializedName("dateTimePlacePersonText") val dateTimePlacePersonText: LessonText?,
    @SerializedName("dateTimePlaceText") val dateTimePlaceText: LessonText?,
    @SerializedName("dateTimeText") val dateTimeText: LessonText?,
    @SerializedName("roomSeatText") val roomSeatText: LessonText?,
)

data class LessonText(
    @SerializedName("textZh") val textZh: String?,
    @SerializedName("text") val text: String?,
)

/** 学时信息：total 总学时、weeks 周数、periodsPerWeek 每周节数。 */
data class LessonPeriodInfo(
    @SerializedName("total") val total: Int?,
    @SerializedName("weeks") val weeks: Int?,
    @SerializedName("periodsPerWeek") val periodsPerWeek: Int?,
)

/**
 * 搜索模式：决定 keyword 走哪个 Like 过滤参数（HAR 实测 codeLike / nameZhLike 均有效）。
 */
enum class LessonSearchMode(val paramName: String, val label: String) {
    /** 按课程/教学班名称模糊搜索 —— nameZhLike。 */
    NAME("nameZhLike", "名称"),

    /** 按教学班编号/课程号模糊搜索 —— codeLike。 */
    CODE("codeLike", "编号"),
}

// ─────────────────────────────────────────────────────────────────────────
// 全量筛选载体 + 级联取选项模型（2026-07-23 全量筛选升级）
//
// ⚠ 诚实边界：HAR 只在**搜索端点**实际验证过 openDepartmentAssocs(多值)/nameZhLike/codeLike。
//   其余筛选（courseTypeAssoc/campusAssoc/compulsory/examModeAssoc/teachLangAssoc/
//   weekIndexs/courseIndexs/buildingAssoc/grades/departmentAssocs/majorAssoc/adminClassAssoc/
//   range 输入）的搜索端点 param 名与单/多值编码是从页面 `<select name>` **推断**，
//   须用授权测试账号运行时验证（totalRows 应随筛选收窄）；验证不过的枚举型小筛选降级为
//   客户端过滤当前页（级联型院系/专业/行政班/教学楼必须服务端生效，否则定位不到教学班）。
// ─────────────────────────────────────────────────────────────────────────

/**
 * 全校开课查询的全量筛选载体。**唯一**筛选真相源，未来接入蹭课(CengKe)可直接复用本对象。
 *
 * 分三类：
 *  1. 上下文：[semesterId]/[mode]/[keyword]（keyword/mode 不计入 [activeCount]）。
 *  2. 开课筛选（服务端）：学院/类型/性质/校区/考核/语言/星期/节次/教学楼/学分·教室 range。
 *  3. 教学班定位级联（服务端）：年级 → 开课单位 → 专业 → 行政班([adminClassId] 是"具体教学班课表"的钥匙)。
 *
 * [weekdays] 用 ISO 星期（周一=1…周日=7）便于 UI；序列化时转服务端编码（见 buildSearchUrl）。
 */
data class LessonSearchFilter(
    val semesterId: Int,
    val mode: LessonSearchMode = LessonSearchMode.NAME,
    val keyword: String = "",
    // ── 开课筛选（服务端） ──
    val departmentIds: List<Long> = emptyList(),   // openDepartmentAssocs（多值，已验证）
    val courseTypeId: Long? = null,                // courseTypeAssoc（推断）
    val campusId: Long? = null,                    // campusAssoc（推断）
    val compulsory: String? = null,                // compulsory=COMPULSORY|ELECTIVE（推断）
    val examModeId: Long? = null,                  // examModeAssoc（推断）
    val teachLangId: Long? = null,                 // teachLangAssoc（推断）
    val weekdays: List<Int> = emptyList(),         // weekIndexs（ISO，序列化转服务端编码；推断）
    val courseUnitIndexes: List<Int> = emptyList(),// courseIndexs（推断）
    val buildingId: Long? = null,                  // buildingAssoc（推断）
    val roomNameLike: String? = null,              // roomNameLike（推断）
    val creditsGte: Double? = null,                // creditsGte（推断）
    val creditsLte: Double? = null,                // creditsLte（推断）
    // ── 教学班定位级联（服务端） ──
    val grades: List<String> = emptyList(),        // grades，如 "2024"（推断）
    val majorDeptIds: List<Long> = emptyList(),    // departmentAssocs 开课单位（推断）
    val majorIds: List<Long> = emptyList(),        // majorAssoc 专业（推断）
    val adminClassId: Long? = null,                // adminClassAssoc 行政班（推断，课表钥匙）
) {
    /** 激活的筛选维度数（keyword/mode/semester 不计）。用于「筛选」入口徽标。 */
    val activeCount: Int
        get() = listOf(
            departmentIds.isNotEmpty(), courseTypeId != null, campusId != null,
            compulsory != null, examModeId != null, teachLangId != null,
            weekdays.isNotEmpty(), courseUnitIndexes.isNotEmpty(), buildingId != null,
            !roomNameLike.isNullOrBlank(), creditsGte != null, creditsLte != null,
            grades.isNotEmpty(), majorDeptIds.isNotEmpty(), majorIds.isNotEmpty(),
            adminClassId != null,
        ).count { it }

    /** 恰好定位到单个教学班（行政班）→ 允许切「课表」周网格视图。 */
    val isSingleAdminClass: Boolean get() = adminClassId != null

    companion object {
        /** ISO 星期(周一=1…周日=7) → 服务端 weekIndexs 编码(周日=1、周一=2…周六=7)。 */
        fun serverWeekday(isoWeekday: Int): Int = (isoWeekday % 7) + 1
    }
}

/** 通用「id + 中文名」筛选选项。级联下拉项与内嵌枚举项统一用它，UI 无需区分来源。 */
data class LessonFilterOption(
    val id: Long,
    val nameZh: String,
) {
    companion object {
        /** 从 [LessonNamed] 安全构造（id/名齐全才产出）。 */
        fun of(named: LessonNamed?): LessonFilterOption? {
            val id = named?.id ?: return null
            val name = named.nameZh?.takeIf { it.isNotBlank() } ?: return null
            return LessonFilterOption(id, name)
        }
    }
}

/**
 * 开课单位（`major-select/data/departments`）/专业（`mulMajors`）级联项。
 * nameZh 常含「编码：名称」（如 `31：数学科学学院`），[displayName] 去掉编码前缀。
 */
data class LessonMajorNode(
    @SerializedName("id") val id: Long?,
    @SerializedName("nameZh") val nameZh: String?,
) {
    val displayName: String
        get() = nameZh?.substringAfter("：", nameZh)?.trim().orEmpty()

    fun toOption(): LessonFilterOption? {
        val id = id ?: return null
        val name = displayName.takeIf { it.isNotBlank() } ?: nameZh ?: return null
        return LessonFilterOption(id, name)
    }
}

/**
 * 行政班（`adminclass/search-adminclass`）。这是"具体年级教学班"的定位钥匙，
 * 选中后其 [id] 作为搜索端点 adminClassAssoc 过滤，取该班全部开课。
 */
data class LessonAdminClass(
    @SerializedName("id") val id: Long?,
    @SerializedName("nameZh") val nameZh: String?,
    @SerializedName("code") val code: String?,
    @SerializedName("grade") val grade: String?,
) {
    fun toOption(): LessonFilterOption? {
        val id = id ?: return null
        val name = nameZh?.takeIf { it.isNotBlank() } ?: code ?: return null
        return LessonFilterOption(id, name)
    }
}

/** 教学楼（`get-building-by-campus`），按校区联动。 */
data class LessonBuilding(
    @SerializedName("id") val id: Long?,
    @SerializedName("nameZh") val nameZh: String?,
    @SerializedName("code") val code: String?,
) {
    fun toOption(): LessonFilterOption? {
        val id = id ?: return null
        val name = nameZh?.takeIf { it.isNotBlank() } ?: code ?: return null
        return LessonFilterOption(id, name)
    }
}

/** 节次（`time-table-layout/course-units`），indexNo 即 courseIndexs 过滤值。 */
data class LessonCourseUnit(
    @SerializedName("nameZh") val nameZh: String?,
    @SerializedName("indexNo") val indexNo: Int?,
)

/** `time-table-layout/course-units` 可能返回 `{data:[...]}` 包装或顶层数组，两者都要吃。 */
data class LessonCourseUnitEnvelope(
    @SerializedName("data") val data: List<LessonCourseUnit>?,
)

/**
 * 内嵌固定小集合（首页 HTML `<option>`，零网络）。值/名来自 2026-07-23 HAR 页面表单。
 * ⚠ id 与蹭课 jwapp 的 campusId 空间**不同源**，勿混用（本处是 lesson-search 页面口径）。
 */
object LessonInlineOptions {
    /** 课程类型 courseTypeAssoc。 */
    val COURSE_TYPES = listOf(
        LessonFilterOption(1, "理论课"),
        LessonFilterOption(2, "实验课"),
        LessonFilterOption(3, "实践课"),
        LessonFilterOption(47, "理论+实验"),
    )

    /** 校区 campusAssoc（同时是教学楼级联的 campusId）。 */
    val CAMPUSES = listOf(
        LessonFilterOption(1, "磬苑校区"),
        LessonFilterOption(2, "龙河校区"),
        LessonFilterOption(22, "金寨路校区"),
        LessonFilterOption(6, "纯线上"),
    )

    /** 课程性质 compulsory（枚举值，非 id）。 */
    val COMPULSORY = listOf(
        "COMPULSORY" to "必修",
        "ELECTIVE" to "选修",
    )

    /** 考核方式 examModeAssoc。 */
    val EXAM_MODES = listOf(
        LessonFilterOption(1, "考试"),
        LessonFilterOption(2, "考查"),
    )

    /** 授课语言 teachLangAssoc。 */
    val TEACH_LANGS = listOf(
        LessonFilterOption(2, "中文"),
        LessonFilterOption(1, "双语"),
        LessonFilterOption(21, "外语"),
    )

    /** 年级候选（当前学年往前 8 届，够用；[currentYear] 由 UI 传入 DebugClock 年份）。 */
    fun grades(currentYear: Int, back: Int = 8): List<String> =
        (currentYear downTo (currentYear - back)).map { it.toString() }
}
