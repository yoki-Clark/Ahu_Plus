package com.yourname.ahu_plus.data.model.jw

import com.google.gson.annotations.SerializedName

/**
 * 培养方案 API 响应模型。
 *
 * 端点: GET /student/for-std/credit-certification-apply/other_apply/get-all-course-module?programId=3000
 * 响应是一个递归树结构：根节点包含 children（模块组），每个模块可再嵌套子模块和课程。
 *
 * 实测 (2026-06-17): HTTP 200, ~1.5MB JSON, programId=28765, 7 个一级模块, 164 总学分。
 */

// ── 顶层响应（即根节点本身） ─────────────────────────────────────────

typealias TrainingPlanResponse = PlanModuleNode

// ── 模块节点（递归树） ────────────────────────────────────────────────

data class PlanModuleNode(
    val id: Int? = null,
    val type: PlanTypeInfo? = null,
    @SerializedName("requireInfo")
    val requireInfo: PlanRequireInfo? = null,
    @SerializedName("limitInfo")
    val limitInfo: PlanLimitInfo? = null,
    val remark: String? = null,
    val reference: Boolean? = null,
    val copy: Boolean? = null,
    val index: Int? = null,
    @SerializedName("coursePlanId")
    val coursePlanId: Int? = null,
    /** 子模块（递归） */
    val children: List<PlanModuleNode>? = null,
    /** 本模块直接包含的课程 */
    @SerializedName("planCourses")
    val planCourses: List<PlanCourse>? = null,
    val hasChildren: Boolean? = null,

    // ── 学分/数量汇总 ──
    @SerializedName("sumChildrenRequiredCreditsOrZero")
    val sumChildrenRequiredCreditsOrZero: String? = null,
    @SerializedName("sumPlanCourseCreditsOrZero")
    val sumPlanCourseCreditsOrZero: String? = null,
    @SerializedName("sumPlanCourseNum")
    val sumPlanCourseNum: Int? = null,
    @SerializedName("creditBySubModule")
    val creditBySubModule: Map<String, Double>? = null,
    @SerializedName("numBySubModule")
    val numBySubModule: Map<String, Int>? = null
) {
    /** 模块显示名称 */
    val displayName: String get() = type?.nameZh ?: "未命名"

    /** 要求学分（number 型） */
    val requiredCredits: Double?
        get() = requireInfo?.requiredCredits

    /** 所有直接课程数 */
    val courseCount: Int
        get() = planCourses?.size ?: 0

    /** 是否叶子节点（无子模块） */
    val isLeaf: Boolean
        get() = children.isNullOrEmpty()
}

// ── 课程 ───────────────────────────────────────────────────────────────

data class PlanCourse(
    val id: Int? = null,
    val compulsory: Boolean? = null,
    val terms: List<String>? = null,
    @SerializedName("suggestTerms")
    val suggestTerms: List<String>? = null,
    val index: Int? = null,
    @SerializedName("readableTerms")
    val readableTerms: List<String>? = null,
    val remark: String? = null,
    @SerializedName("periodInfo")
    val periodInfo: PlanPeriodInfo? = null,
    /** 课程详情 */
    val course: PlanCourseInfo? = null,
    @SerializedName("openDepartment")
    val openDepartment: PlanDepartment? = null,
    @SerializedName("examMode")
    val examMode: PlanEnumValue? = null,
    @SerializedName("courseProperty")
    val courseProperty: PlanEnumValue? = null,
    @SerializedName("courseType")
    val courseType: PlanEnumValue? = null,
    @SerializedName("teachLang")
    val teachLang: PlanEnumValue? = null
) {
    /** 课程名 */
    val displayName: String get() = course?.nameZh ?: "未知课程"

    /** 课程代码 */
    val displayCode: String get() = course?.code ?: ""

    /** 学分 */
    val displayCredits: Double get() = course?.credits ?: 0.0

    /** 属性（必修/选修） */
    val displayProperty: String get() = courseProperty?.nameZh ?: ""

    /** 考核方式 */
    val displayExamMode: String get() = examMode?.nameZh ?: ""
}

// ── 嵌套子对象 ────────────────────────────────────────────────────────

data class PlanTypeInfo(
    val nameZh: String? = null,
    val nameEn: String? = null,
    val id: Int? = null,
    val code: String? = null,
    val name: String? = null,
    val enabled: Boolean? = null
)

data class PlanRequireInfo(
    @SerializedName("requiredCredits")
    val requiredCredits: Double? = null,
    @SerializedName("requiredCourseNum")
    val requiredCourseNum: Int? = null,
    @SerializedName("requiredSubModuleNum")
    val requiredSubModuleNum: Int? = null,
    @SerializedName("requiredCreditsOrZero")
    val requiredCreditsOrZero: String? = null
)

data class PlanLimitInfo(
    @SerializedName("creditsUpperLimit")
    val creditsUpperLimit: Double? = null,
    @SerializedName("courseNumUpperLimit")
    val courseNumUpperLimit: Int? = null
)

data class PlanPeriodInfo(
    val total: Int? = null,
    val weeks: Double? = null,
    val theory: Int? = null,
    val practice: Int? = null,
    val experiment: Int? = null,
    val machine: Int? = null,
    val design: Int? = null,
    val test: Int? = null,
    val extra: Int? = null,
    @SerializedName("periodsPerWeek")
    val periodsPerWeek: Int? = null,
    @SerializedName("theoryUnit")
    val theoryUnit: String? = null,
    @SerializedName("experimentUnit")
    val experimentUnit: String? = null,
    @SerializedName("practiceUnit")
    val practiceUnit: String? = null
)

data class PlanCourseInfo(
    val id: Int? = null,
    @SerializedName("nameZh")
    val nameZh: String? = null,
    @SerializedName("nameEn")
    val nameEn: String? = null,
    val code: String? = null,
    val credits: Double? = null,
    @SerializedName("courseProperty")
    val courseProperty: PlanEnumValue? = null,
    @SerializedName("courseType")
    val courseType: PlanEnumValue? = null,
    val enabled: Boolean? = null,
    val remark: String? = null
)

data class PlanDepartment(
    val id: Int? = null,
    @SerializedName("nameZh")
    val nameZh: String? = null,
    @SerializedName("nameEn")
    val nameEn: String? = null,
    val code: String? = null,
    @SerializedName("abbrZh")
    val abbrZh: String? = null
)

data class PlanEnumValue(
    val id: Int? = null,
    @SerializedName("nameZh")
    val nameZh: String? = null,
    @SerializedName("nameEn")
    val nameEn: String? = null,
    val code: String? = null,
    val name: String? = null,
    val enabled: Boolean? = null
)
