package com.ahu_plus.data.model

import com.google.gson.annotations.SerializedName

/**
 * kqcard.ahu.edu.cn 考勤 API 响应模型 (2026-06-17 Python 验证通过)。
 *
 * API: POST /attendance-student/classWater/getClassWaterPage
 * Auth: synjones-auth: bearer <JWT>
 */

// ── 顶层响应 ─────────────────────────────────────────────────────────

data class KqAttendanceResponse(
    val code: Int = 0,
    val success: Boolean = false,
    val msg: String? = null,
    val data: KqAttendanceData? = null
)

data class KqAttendanceData(
    val totalCount: Int = 0,
    val list: List<KqAttendanceRecord> = emptyList()
)

// ── 单条考勤记录 ─────────────────────────────────────────────────────

data class KqAttendanceRecord(
    @SerializedName("teachNameList")
    val teachNameList: String? = null,

    @SerializedName("subjectBean")
    val subjectBean: KqSubjectBean? = null,

    @SerializedName("accountBean")
    val accountBean: KqAccountBean? = null,

    @SerializedName("roomBean")
    val roomBean: KqRoomBean? = null,

    @SerializedName("buildBean")
    val buildBean: KqBuildBean? = null,

    @SerializedName("classWaterBean")
    val classWaterBean: KqClassWaterBean? = null,

    @SerializedName("normalLetime")
    val normalLetime: String? = null,

    @SerializedName("lateLetime")
    val lateLetime: String? = null,

    @SerializedName("absenceLetime")
    val absenceLetime: String? = null
)

// ── 嵌套 Bean ────────────────────────────────────────────────────────

data class KqSubjectBean(
    @SerializedName("sName") val sName: String? = null,
    @SerializedName("sSimple") val sSimple: String? = null,
    @SerializedName("sCode") val sCode: String? = null,
    @SerializedName("sDept") val sDept: String? = null
)

data class KqAccountBean(
    @SerializedName("checkdate") val checkdate: String? = null,
    @SerializedName("jtNo") val jtNo: String? = null,
    @SerializedName("startJc") val startJc: Int? = null,
    @SerializedName("endJc") val endJc: Int? = null,
    @SerializedName("week") val week: Int? = null
)

data class KqClassWaterBean(
    @SerializedName("status") val status: Int? = null,
    @SerializedName("operdate") val operdate: String? = null,
    @SerializedName("photo") val photo: String? = null
)

data class KqBuildBean(
    @SerializedName("name") val name: String? = null
)

data class KqRoomBean(
    @SerializedName("roomnum") val roomnum: String? = null
)

// ── 缓存包装 ─────────────────────────────────────────────────────────

data class KqAttendanceSummary(
    val records: List<KqAttendanceRecord> = emptyList(),
    val total: Int = 0,
    val lastUpdatedAt: Long = 0L
)
