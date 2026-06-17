package com.yourname.ahu_plus.data.model.jw

import com.google.gson.annotations.SerializedName

/**
 * 培养方案 API 响应模型。
 * 端点: GET /student/for-std/credit-certification-apply/other_apply/get-all-course-module?programId=3000
 * 递归树结构：根节点包含 children（模块组），每个模块可再嵌套子模块和课程。
 */

typealias TrainingPlanResponse = PlanModuleNode

data class PlanModuleNode(
    val id: Int? = null,
    val type: PlanTypeInfo? = null,
    @SerializedName("requireInfo") val requireInfo: PlanRequireInfo? = null,
    @SerializedName("limitInfo") val limitInfo: PlanLimitInfo? = null,
    val remark: String? = null,
    val reference: Boolean? = null, val copy: Boolean? = null, val index: Int? = null,
    @SerializedName("coursePlanId") val coursePlanId: Int? = null,
    val children: List<PlanModuleNode>? = null,
    @SerializedName("planCourses") val planCourses: List<PlanCourse>? = null,
    val hasChildren: Boolean? = null,
    @SerializedName("sumChildrenRequiredCreditsOrZero") val sumChildrenRequiredCreditsOrZero: String? = null,
    @SerializedName("sumPlanCourseCreditsOrZero") val sumPlanCourseCreditsOrZero: String? = null,
    @SerializedName("sumPlanCourseNum") val sumPlanCourseNum: Int? = null,
    @SerializedName("creditBySubModule") val creditBySubModule: Map<String, Double>? = null,
    @SerializedName("numBySubModule") val numBySubModule: Map<String, Int>? = null
) {
    val displayName: String get() = type?.nameZh ?: "未命名"
    val requiredCredits: Double? get() = requireInfo?.requiredCredits
    val courseCount: Int get() = planCourses?.size ?: 0
}

data class PlanCourse(
    val id: Int? = null, val compulsory: Boolean? = null,
    val terms: List<String>? = null, @SerializedName("suggestTerms") val suggestTerms: List<String>? = null,
    val index: Int? = null, @SerializedName("readableTerms") val readableTerms: List<String>? = null,
    val remark: String? = null, @SerializedName("periodInfo") val periodInfo: PlanPeriodInfo? = null,
    val course: PlanCourseInfo? = null,
    @SerializedName("openDepartment") val openDepartment: PlanDepartment? = null,
    @SerializedName("examMode") val examMode: PlanEnumValue? = null,
    @SerializedName("courseProperty") val courseProperty: PlanEnumValue? = null,
    @SerializedName("courseType") val courseType: PlanEnumValue? = null,
    @SerializedName("teachLang") val teachLang: PlanEnumValue? = null
) {
    val displayName: String get() = course?.nameZh ?: "未知课程"
    val displayCode: String get() = course?.code ?: ""
    val displayCredits: Double get() = course?.credits ?: 0.0
    val displayProperty: String get() = courseProperty?.nameZh ?: ""
    val displayExamMode: String get() = examMode?.nameZh ?: ""
}

data class PlanTypeInfo(val nameZh: String? = null, val nameEn: String? = null, val id: Int? = null, val code: String? = null, val name: String? = null, val enabled: Boolean? = null)
data class PlanRequireInfo(@SerializedName("requiredCredits") val requiredCredits: Double? = null, @SerializedName("requiredCourseNum") val requiredCourseNum: Int? = null, @SerializedName("requiredSubModuleNum") val requiredSubModuleNum: Int? = null, @SerializedName("requiredCreditsOrZero") val requiredCreditsOrZero: String? = null)
data class PlanLimitInfo(@SerializedName("creditsUpperLimit") val creditsUpperLimit: Double? = null, @SerializedName("courseNumUpperLimit") val courseNumUpperLimit: Int? = null)
data class PlanPeriodInfo(val total: Int? = null, val weeks: Double? = null, val theory: Int? = null, val practice: Int? = null, val experiment: Int? = null, val machine: Int? = null, val design: Int? = null, val test: Int? = null, val extra: Int? = null, @SerializedName("periodsPerWeek") val periodsPerWeek: Int? = null, @SerializedName("theoryUnit") val theoryUnit: String? = null, @SerializedName("experimentUnit") val experimentUnit: String? = null, @SerializedName("practiceUnit") val practiceUnit: String? = null)
data class PlanCourseInfo(val id: Int? = null, @SerializedName("nameZh") val nameZh: String? = null, @SerializedName("nameEn") val nameEn: String? = null, val code: String? = null, val credits: Double? = null, @SerializedName("courseProperty") val courseProperty: PlanEnumValue? = null, @SerializedName("courseType") val courseType: PlanEnumValue? = null, val enabled: Boolean? = null, val remark: String? = null)
data class PlanDepartment(val id: Int? = null, @SerializedName("nameZh") val nameZh: String? = null, @SerializedName("nameEn") val nameEn: String? = null, val code: String? = null, @SerializedName("abbrZh") val abbrZh: String? = null)
data class PlanEnumValue(val id: Int? = null, @SerializedName("nameZh") val nameZh: String? = null, @SerializedName("nameEn") val nameEn: String? = null, val code: String? = null, val name: String? = null, val enabled: Boolean? = null)
