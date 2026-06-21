package com.yourname.ahu_plus.data.model

import com.google.gson.annotations.SerializedName

// ── 课程 ──────────────────────────────────────────────────────────

/** 超星课程 */
data class CxCourse(
    @SerializedName("courseId") val courseId: String,
    @SerializedName("clazzId") val clazzId: String,
    @SerializedName("cpi") val cpi: String,
    @SerializedName("title") val title: String,
    @SerializedName("teacher") val teacher: String = "",
    @SerializedName("desc") val desc: String = "",
)

// ── 章节 ──────────────────────────────────────────────────────────

/** 超星章节点 */
data class CxChapter(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("jobCount") val jobCount: Int = 1,
    @SerializedName("hasFinished") val hasFinished: Boolean = false,
    @SerializedName("needUnlock") val needUnlock: Boolean = false,
)

/** 课程章节列表 */
data class CxCoursePoints(
    @SerializedName("hasLocked") val hasLocked: Boolean = false,
    @SerializedName("points") val points: List<CxChapter> = emptyList(),
)

// ── 任务点 ────────────────────────────────────────────────────────

/** 任务点卡片的全局信息 */
data class CxJobInfo(
    @SerializedName("ktoken") val ktoken: String = "",
    @SerializedName("mtEnc") val mtEnc: String = "",
    @SerializedName("reportTimeInterval") val reportTimeInterval: Int = 60,
    @SerializedName("defenc") val defenc: String = "",
    @SerializedName("cardid") val cardid: String = "",
    @SerializedName("cpi") val cpi: String = "",
    @SerializedName("qnenc") val qnenc: String = "",
    @SerializedName("knowledgeid") val knowledgeid: String = "",
)

/** 单个任务点 */
data class CxJob(
    @SerializedName("type") val type: String,          // video / document / workid / read / live
    @SerializedName("jobid") val jobid: String,
    @SerializedName("name") val name: String = "",
    @SerializedName("objectid") val objectid: String = "",
    @SerializedName("otherinfo") val otherinfo: String = "",
    @SerializedName("mid") val mid: String = "",
    @SerializedName("aid") val aid: String = "",
    @SerializedName("playTime") val playTime: Int = 0,
    @SerializedName("rt") val rt: String = "",
    @SerializedName("attDuration") val attDuration: String = "",
    @SerializedName("attDurationEnc") val attDurationEnc: String = "",
    @SerializedName("videoFaceCaptureEnc") val videoFaceCaptureEnc: String = "",
    @SerializedName("enc") val enc: String = "",
    @SerializedName("jtoken") val jtoken: String = "",
)

// ── 视频 ──────────────────────────────────────────────────────────

/** 视频元信息 (ananas/status 响应) */
data class CxVideoInfo(
    @SerializedName("dtoken") val dtoken: String = "",
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("crc") val crc: String = "",
    @SerializedName("key") val key: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("filename") val filename: String = "",
)

// ── 答题 ──────────────────────────────────────────────────────────

/** 题目 */
data class CxQuestion(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("options") val options: String,
    @SerializedName("type") val type: String,           // single / multiple / completion / judgement / shortanswer
    @SerializedName("answerField") val answerField: Map<String, String> = emptyMap(),
)

/** 章节检测表单数据 */
data class CxWorkData(
    @SerializedName("questions") val questions: List<CxQuestion> = emptyList(),
    @SerializedName("answerwqbid") val answerwqbid: String = "",
    @SerializedName("pyFlag") val pyFlag: String = "",
    val formFields: Map<String, String> = emptyMap(),     // 其他隐藏 input 字段
)

/** 章节检测提交结果 */
data class CxWorkResult(
    @SerializedName("status") val status: Boolean,
    @SerializedName("msg") val msg: String = "",
)

// ── 签到 ──────────────────────────────────────────────────────────

/**
 * 签到类型(2026-06-20 集成 Phase 3,移植自 base.py:SignType)。
 *
 * - [NORMAL]    普通签到,直接 `stuSignajax`
 * - [GESTURE]   手势签到,需提交手势码(学号/姓名首字母等)
 * - [LOCATION]  位置签到,需提交经纬度 + 地址
 * - [PRE_SIGN]  preSign 前置动作,某些签到需先调 preSign 拿 token 再签到
 *
 * 通过超星响应中的 `type` 字段(`otherInfo` 或 `type` 字段)判断。
 */
enum class CxSignType(val code: Int, val label: String) {
    NORMAL(0, "普通签到"),
    PRE_SIGN(1, "预签到"),
    GESTURE(3, "手势签到"),
    LOCATION(4, "位置签到");

    companion object {
        fun fromCode(code: Int): CxSignType = entries.firstOrNull { it.code == code } ?: NORMAL
    }
}

/** 签到活动 */
data class CxActivity(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String = "",
    @SerializedName("type") val type: Int = 0,            // 2 = 签到
    @SerializedName("status") val status: Int = 0,        // 1 = 进行中
    @SerializedName("courseId") val courseId: String = "",
    @SerializedName("classId") val classId: String = "",
    @SerializedName("startTime") val startTime: Long = 0,
    @SerializedName("endTime") val endTime: Long = 0,
    /** 签到类型(解析自响应 otherInfo / type 字段,默认 NORMAL) */
    @SerializedName("signType") val signType: CxSignType = CxSignType.NORMAL,
    /** 位置签到所需坐标(用户配置,默认 -1/-1 表示未配置) */
    @SerializedName("latitude") val latitude: Double = -1.0,
    @SerializedName("longitude") val longitude: Double = -1.0,
    /** 手势签到码 */
    @SerializedName("gestureCode") val gestureCode: String = "",
)

// ── 学习结果 ──────────────────────────────────────────────────────

enum class CxStudyResult {
    SUCCESS,
    FORBIDDEN,
    ERROR,
    TIMEOUT,
    SKIPPED;

    fun isSuccess() = this == SUCCESS
    fun isFailure() = this != SUCCESS
}

// ── 学习进度 UI 状态 ──────────────────────────────────────────────

/** 单个任务点的学习状态 */
data class CxTaskProgress(
    val courseTitle: String,
    val chapterTitle: String,
    val job: CxJob,
    val status: CxTaskStatus = CxTaskStatus.PENDING,
    val progress: Float = 0f,          // 0..1
    val message: String = "",
)

enum class CxTaskStatus {
    PENDING, RUNNING, SUCCESS, FAILED, SKIPPED
}

/** 学习总览 UI 状态 */
data class CxStudyUiState(
    val isRunning: Boolean = false,
    val currentTask: CxTaskProgress? = null,
    val completedTasks: List<CxTaskProgress> = emptyList(),
    val totalTasks: Int = 0,
    val completedCount: Int = 0,
    val logs: List<String> = emptyList(),
    val error: String? = null,
)

// ── 消息中心 ──────────────────────────────────────────────────────

/** 附件信息（解析自通知 attachment JSON） */
data class CxAttachment(
    @SerializedName("name") val name: String,
    @SerializedName("fileSize") val fileSize: String = "",
    @SerializedName("suffix") val suffix: String = "",
    @SerializedName("objectId") val objectId: String = "",
    @SerializedName("preview") val preview: String = "",
    @SerializedName("puid") val puid: String = "",
    @SerializedName("forbidDownload") val forbidDownload: Int = 0,
)

/** 消息来源 */
enum class CxMessageSource { NOTICE, ACTIVITY }

/**
 * 统一消息模型（收件箱通知 + 课程活动通知）。
 *
 * - [CxMessageSource.NOTICE] → notice.chaoxing.com 收件箱
 * - [CxMessageSource.ACTIVITY] → mobilelearn 课程活动（签到/练习/通知）
 */
data class CxMessage(
    @SerializedName("id") val id: String,
    @SerializedName("source") val source: CxMessageSource,
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String = "",
    @SerializedName("senderName") val senderName: String = "",
    @SerializedName("senderId") val senderId: Long = 0,
    @SerializedName("time") val time: Long,
    @SerializedName("isRead") val isRead: Boolean = true,
    /** activity type: 2=签到, 11=选人, 42=随堂练习, 45=通知/作业 */
    @SerializedName("type") val type: Int = 0,
    @SerializedName("typeName") val typeName: String = "",
    @SerializedName("courseId") val courseId: String = "",
    @SerializedName("courseName") val courseName: String = "",
    @SerializedName("logo") val logo: String = "",
    @SerializedName("attachment") val attachment: String = "",
    @SerializedName("rtfContent") val rtfContent: String = "",
    @SerializedName("attachments") val attachments: List<CxAttachment> = emptyList(),
    /** activity status: 0=未开始, 1=进行中, 2=已结束 */
    @SerializedName("activityStatus") val activityStatus: Int = -1,
    /** 1=已参与 */
    @SerializedName("userStatus") val userStatus: Int = -1,
    @SerializedName("attendNum") val attendNum: Int = 0,
    @SerializedName("releaseNum") val releaseNum: Int = 0,
)
