package com.ahu_plus.data.model.jw

import com.google.gson.annotations.SerializedName

/** program-completion-preview HTML 中 allCourseList 的每门课修读状态 */
data class CompletionCourse(
    val code: String? = null,
    @SerializedName("nameZh") val nameZh: String? = null,
    @SerializedName("nameEn") val nameEn: String? = null,
    val credits: Double? = null,
    val compulsory: Boolean? = null,
    val apply: Boolean? = null,
    @SerializedName("finalResultType") val finalResultType: ResultTypeEntry? = null,
    val gp: Double? = null,
    @SerializedName("gradeStr") val gradeStr: String? = null,
    val score: Double? = null,
    @SerializedName("semesterId") val semesterId: Int? = null,
    @SerializedName("courseId") val courseId: Int? = null,
    @SerializedName("moduleCompletionId") val moduleCompletionId: Int? = null,
    val recognized: Boolean? = null,
    /**
     * 课程所属模块的 typeId（与 [PlanModuleNode.type.id] 对齐），用于精确归属到子分类。
     *
     * HTML 的 `allCourseList` 课程对象本身没有 typeId，这里由解析器从所属模块上下文写入。
     * 该字段参与 Gson 序列化，以便缓存后冷启动能恢复 typeId，无需重新请求网络。
     */
    @SerializedName("typeId") var typeId: Int? = null,
    /**
     * 课程所属模块的名称，作为 typeId 为空或未命中时的回退匹配策略。
     * 同样由解析器写入，参与序列化以支持缓存恢复。
     */
    @SerializedName("moduleName") var moduleName: String? = null
) {
    val isPassed: Boolean get() = finalResultType?.name == "PASSED"
    val isTaking: Boolean get() = finalResultType?.name == "TAKING"
    val isFailed: Boolean get() = finalResultType?.name == "FAILED"
    val isUnrepaired: Boolean get() = finalResultType?.name == "UNREPAIRED"
}

data class ResultTypeEntry(
    @SerializedName("\$type") val type: String? = null,
    @SerializedName("\$name") val name: String? = null
)

data class CompletionSummary(
    val passedCredits: Double = 0.0,
    val takingCredits: Double = 0.0,
    val failedCredits: Double = 0.0,
    val requiredCredits: Double? = null,
    val passedCount: Int = 0,
    val takingCount: Int = 0,
    val unrepairedCount: Int = 0,
    val failedCount: Int = 0
) {
    val completionProgress: Float get() {
        if (requiredCredits == null || requiredCredits == 0.0) return 0f
        return (passedCredits / requiredCredits).toFloat().coerceIn(0f, 1f)
    }
}
