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
    @SerializedName("url") val url: String = "",  // 课程入口 URL（从课程列表 a href 提取）
)

/** 课程进度信息（与 CxCourse 分离，由 ViewModel 维护 map） */
data class CxCourseProgress(
    val totalJobs: Int = 0,
    val completedJobs: Int = 0,
) {
    val progress: Float get() = if (totalJobs > 0) completedJobs.toFloat() / totalJobs else 0f
    val text: String get() = "$completedJobs/$totalJobs"
}

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
    /** 已播放时长，单位：秒（来自超星 mArg attachments[].playTime） */
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

/** 章节检测/课程作业 表单数据 */
data class CxWorkData(
    @SerializedName("questions") val questions: List<CxQuestion> = emptyList(),
    @SerializedName("answerwqbid") val answerwqbid: String = "",
    @SerializedName("pyFlag") val pyFlag: String = "",
    val formFields: Map<String, String> = emptyMap(),     // 其他隐藏 input 字段
    val formActionUrl: String = "",                       // HTML form action URL（作业提交用）
)

/** 章节检测提交结果 */
data class CxWorkResult(
    @SerializedName("status") val status: Boolean,
    @SerializedName("msg") val msg: String = "",
)

// ── 签到 ──────────────────────────────────────────────────────────

/**
 * 签到类型(2026-06-24 扩展为全部 6 种)。
 *
 * - [NORMAL]    普通签到,直接 `stuSignajax`
 * - [PHOTO]     拍照签到,需上传图片到云盘拿 objectId
 * - [GESTURE]   手势签到,提交九宫格路径串(作为 signCode)
 * - [QRCODE]    二维码签到,扫码提取 enc 参数
 * - [LOCATION]  位置签到,提交经纬度 + 地址
 * - [SIGNCODE]  签到码签到,提交老师口播的数字码
 * - [PRE_SIGN]  preSign 前置占位(activelist 初判用,真实类型以 preSign 响应为准)
 *
 * ⚠️ 真实子类型应从 **preSign 响应**的 `otherId`/`ifphoto` 判定([fromPreSign]),
 * activelist 的 `type` 字段是活动大类(2=签到),不可靠。
 */
enum class CxSignType(val code: Int, val label: String) {
    NORMAL(0, "普通签到"),
    PRE_SIGN(1, "预签到"),
    GESTURE(3, "手势签到"),
    LOCATION(4, "位置签到"),
    PHOTO(10, "拍照签到"),
    QRCODE(11, "二维码签到"),
    SIGNCODE(12, "签到码签到");

    companion object {
        fun fromCode(code: Int): CxSignType = entries.firstOrNull { it.code == code } ?: NORMAL

        /**
         * 从 preSign 响应的 otherId + ifphoto 判定真实签到子类型(cxOrz 公认映射):
         * otherId=0 & ifphoto=0 → 普通;otherId=0 & ifphoto=1 → 拍照;
         * otherId=1 → 手势;otherId=2 → 二维码;otherId=3 → 位置;otherId=4 → 签到码。
         */
        fun fromPreSign(otherId: Int, ifPhoto: Int): CxSignType = when (otherId) {
            0 -> if (ifPhoto == 1) PHOTO else NORMAL
            1 -> GESTURE
            2 -> QRCODE
            3 -> LOCATION
            4 -> SIGNCODE
            else -> NORMAL
        }
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

/**
 * preSign 响应解析结果(2026-06-24)。
 * [signType] 由 otherId/ifphoto 推导,是判定签到子类型的权威来源。
 */
data class CxPreSignInfo(
    val signType: CxSignType,
    val otherId: Int,
    val ifPhoto: Int,
    val raw: String,
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

// ── 课程作业 (2026-06-22) ──────────────────────────────────────────

/**
 * 课程作业列表项。
 *
 * API: GET mooc1.chaoxing.com/mooc2/work/list → HTML <li data="..."> 解析
 */
data class CxHomeworkItem(
    @SerializedName("workId") val workId: String,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,        // "未交" / "已完成" / "待批阅"
    @SerializedName("courseName") val courseName: String,
    @SerializedName("courseId") val courseId: String,
    @SerializedName("classId") val classId: String,
    @SerializedName("cpi") val cpi: String,
    @SerializedName("workUrl") val workUrl: String,      // 作业页面 URL
    @SerializedName("answerId") val answerId: String = "",
    @SerializedName("enc") val enc: String = "",
)

/** 作业列表 UI 状态 */
data class CxHomeworkListState(
    val isLoading: Boolean = false,
    val homework: List<CxHomeworkItem> = emptyList(),
    val error: String? = null,
)

/** 单个作业的详情状态（题目 + 提交） */
data class CxHomeworkDetailState(
    val isLoading: Boolean = false,
    val workData: CxWorkData? = null,
    val isSubmitting: Boolean = false,
    val submitResult: String? = null,
    val error: String? = null,
    val userAnswers: Map<String, String> = emptyMap(),  // qId → answer (用户手动输入)
    val userFiles: Map<String, List<String>> = emptyMap(),  // qId → uploaded objectIds
)
