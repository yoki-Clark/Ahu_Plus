package com.yourname.ahu_plus.data.model.jw

import com.google.gson.annotations.SerializedName

/**
 * program-completion-preview HTML 中 outerCourseList 的每门课修读状态。
 *
 * 端点: GET /student/for-std/program-completion-preview/info/{studentId}
 * 数据在 HTML 内嵌 JS: outerCourseList': [{...}, ...]
 */
data class CompletionCourse(
    val code: String? = null,
    @SerializedName("nameZh")
    val nameZh: String? = null,
    @SerializedName("nameEn")
    val nameEn: String? = null,
    val credits: Double? = null,
    val compulsory: Boolean? = null,
    val apply: Boolean? = null,
    @SerializedName("finalResultType")
    val finalResultType: ResultTypeEntry? = null,
    val gp: Double? = null,
    @SerializedName("gradeStr")
    val gradeStr: String? = null,
    val score: Double? = null,
    @SerializedName("semesterId")
    val semesterId: Int? = null,
    @SerializedName("courseId")
    val courseId: Int? = null,
    @SerializedName("moduleCompletionId")
    val moduleCompletionId: Int? = null,
    val recognized: Boolean? = null
) {
    val isPassed: Boolean get() = finalResultType?.name == "PASSED"
    val isTaking: Boolean get() = finalResultType?.name == "TAKING"
    val isFailed: Boolean get() = finalResultType?.name == "FAILED"
    val isUnrepaired: Boolean get() = finalResultType?.name == "UNREPAIRED"
}

data class ResultTypeEntry(
    @SerializedName("\$type")
    val type: String? = null,
    @SerializedName("\$name")
    val name: String? = null
)

/**
 * 培养方案完成概览汇总数据。
 * 从 HTML 内嵌 JS 的 outerCourseList 附近提取。
 */
data class CompletionSummary(
    /** 已通过学分 */
    val passedCredits: Double = 0.0,
    /** 在读学分 */
    val takingCredits: Double = 0.0,
    /** 未通过学分 */
    val failedCredits: Double = 0.0,
    /** 总要求学分（从培养方案API获取） */
    val requiredCredits: Double? = null,
    /** 已通过课程数 */
    val passedCount: Int = 0,
    /** 修读中课程数 */
    val takingCount: Int = 0,
    /** 未修课程数 */
    val unrepairedCount: Int = 0,
    /** 挂科课程数 */
    val failedCount: Int = 0
) {
    val completionProgress: Float
        get() {
            if (requiredCredits == null || requiredCredits == 0.0) return 0f
            return (passedCredits / requiredCredits).toFloat().coerceIn(0f, 1f)
        }
}
