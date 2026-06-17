package com.yourname.ahu_plus.data.model.jw

import com.google.gson.annotations.SerializedName

/**
 * 成绩查询响应 —— JwSystem 9 教务处。
 *
 * 接口：`GET /student/for-std/grade/sheet/info/{studentId}?semester={semesterId}`
 *
 * studentId 是 per-user JW 内部 id（不是学号），需要从菜单 URL 的 302 Location 中抽取。
 *
 * 响应体形如：
 * ```
 * {
 *   "semesterId2studentGrades": {
 *     "112": [
 *       {
 *         "id": 8689757,
 *         "semesterId": 112,
 *         "courseName": "程序设计基础",
 *         "courseNameEn": "Fundamentals of Programming",
 *         "courseCode": "GG63027",
 *         "credits": 3.0,
 *         "courseType": "理论课",
 *         "courseProperty": "必修",
 *         "courseTaxon": "通识教育",
 *         "gaGrade": "85",
 *         "gp": 3.7,
 *         "gradeDetail": "<span ...>平时成绩:77.7</span>",
 *         "published": true,
 *         "compulsory": true
 *       }
 *     ]
 *   },
 *   "semesters": [ ... ],
 *   "id2semesters": { ... }
 * }
 * ```
 */
data class GradeResponse(
    @SerializedName("semesterId2studentGrades")
    val semesterId2studentGrades: Map<String, List<Grade>>? = null,
    val semesters: List<SemesterInfo>? = null,
    @SerializedName("id2semesters")
    val id2semesters: Map<String, SemesterInfo>? = null
) {
    /**
     * 把所有学期的成绩压平成一个列表，附带 semesterInfo。
     * 若想取指定学期，调用方应在 [GradeRepository] 层做 semesterId 过滤。
     */
    fun allGrades(): List<Grade> {
        val result = mutableListOf<Grade>()
        semesterId2studentGrades?.values?.forEach { list ->
            list?.let { result.addAll(it) }
        }
        return result
    }
}

/**
 * 单条成绩记录。
 *
 * 字段命名贴近 JwSystem 9 真实响应（snake_case 混 camelCase）。
 * 未发布/录入中：gaGrade = "--"，gp = null，published = false。
 */
data class Grade(
    val id: Long? = null,
    val semesterId: Int? = null,
    val semesterName: String? = null,
    val courseCode: String? = null,
    val courseName: String? = null,
    val courseNameEn: String? = null,
    val lessonCode: String? = null,
    val lessonName: String? = null,
    val minorCourseCode: String? = null,
    val minorCourseName: String? = null,
    val minorCourseNameEn: String? = null,
    val minorCourseCredits: Double? = null,
    val credits: Double? = null,
    val courseType: String? = null,
    val courseProperty: String? = null,
    val courseTaxon: String? = null,
    /**
     * 总评成绩（"85" / "通过" / "--" 等字符串）。
     * 注意不是 Double —— 教务处可能返回 "优秀"/"良好"/"合格"/"不合格" 等文字。
     */
    val gaGrade: String? = null,
    val passed: Boolean? = null,
    val gp: Double? = null,
    /**
     * 成绩明细（HTML 片段，例如 `<span ...>平时成绩:77.7</span>`）。
     * UI 层可保留原文或 strip 标签。
     */
    val gradeDetail: String? = null,
    val published: Boolean? = null,
    val compulsory: Boolean? = null
) {
    val displayScore: String get() = gaGrade?.takeIf { it.isNotBlank() && it != "--" } ?: "—"
    val displayCourse: String get() = courseName?.takeIf { it.isNotBlank() } ?: "未知课程"
    val displayCredits: String get() = credits?.let { "%.1f".format(it) } ?: "—"
    val displayGradePoint: String get() = gp?.let { "%.2f".format(it) } ?: "—"

    /** 字符串 score 解析成 Double（用于按分数着色），失败返回 null。 */
    fun scoreAsDouble(): Double? = gaGrade?.toDoubleOrNull()

    /** 成绩明细纯文本（去除 HTML 标签）。 */
    fun detailPlainText(): String? = gradeDetail
        ?.replace(Regex("<[^>]+>"), "")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

/**
 * GPA 元数据 —— 从成绩 semester-index 页 HTML inline script 提取。
 *
 * 来源：`var gpaSemesterModel = {...}` (inline JS, 非 JSON)
 * 解析逻辑见 [com.yourname.ahu_plus.data.repository.GradeRepository.getGpaMetadata]。
 */
data class GpaMetadata(
    val gpa: Double?,
    val totalCredits: Double?,
    val inPlanCredits: Double?,
    val outPlanCredits: Double?,
    val majorRank: Int?,
    val majorHeadCount: Int?,
    val perSemesterGpa: List<SemesterGpaEntry> = emptyList()
)

/**
 * 单学期 GPA 快照。
 *
 * 当前在读学期 gpa = null，credits = 0。
 */
data class SemesterGpaEntry(
    val semesterId: Int,
    val gpa: Double?,
    val totalCredits: Double,
    val inPlanCredits: Double,
    val outPlanCredits: Double,
    val majorRank: Int?
)
