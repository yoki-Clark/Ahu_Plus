package com.ahu_plus.data.job

import java.util.UUID

enum class BackgroundJobPlatform {
    CHAOXING,
    WELEARN,
}

enum class BackgroundJobPhase {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    RESUMING;

    val isActive: Boolean
        get() = this == QUEUED || this == RUNNING || this == RESUMING
}

enum class BackgroundJobFailure {
    AUTHENTICATION_REQUIRED,
    NETWORK_UNAVAILABLE,
    REMOTE_REJECTED,
    PROTOCOL_CHANGED,
    INVALID_REQUEST,
    SYSTEM_TIMEOUT,
    UNKNOWN,
}

enum class BackgroundJobInterruption {
    PROCESS_TERMINATED,
    SYSTEM_TIMEOUT,
    SERVICE_DESTROYED,
}

data class BackgroundJobPayload(
    val courseKeys: List<String> = emptyList(),
    val speed: Float = 1.0f,
    val concurrency: Int = 1,
    val answerMode: String = "",
    val enabledTaskTypes: Set<String> = emptySet(),
    val courseId: String = "",
    val accuracy: String = "100",
    val fullMode: Boolean = false,
    val unitIndices: List<Int> = emptyList(),
    val heartbeatEnabled: Boolean = true,
    val heartbeatSecondsPerSco: Int = 180,
)

data class BackgroundJobCommand(
    val platform: BackgroundJobPlatform,
    val payload: BackgroundJobPayload,
)

data class BackgroundJobProgress(
    val completed: Int = 0,
    val total: Int = 0,
)

data class BackgroundJobRecord(
    val id: String = UUID.randomUUID().toString(),
    val platform: BackgroundJobPlatform,
    val payload: BackgroundJobPayload,
    val phase: BackgroundJobPhase = BackgroundJobPhase.QUEUED,
    val progress: BackgroundJobProgress = BackgroundJobProgress(),
    val failure: BackgroundJobFailure? = null,
    val interruption: BackgroundJobInterruption? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long = createdAtMillis,
    val finishedAtMillis: Long? = null,
)

sealed interface BackgroundJobStartResult {
    data class Accepted(val record: BackgroundJobRecord) : BackgroundJobStartResult
    data class Rejected(val activeRecord: BackgroundJobRecord) : BackgroundJobStartResult
    data object Missing : BackgroundJobStartResult
}

fun BackgroundJobRecord.userStatusLabel(): String = when (phase) {
    BackgroundJobPhase.QUEUED -> "等待开始"
    BackgroundJobPhase.RUNNING -> "正在运行"
    BackgroundJobPhase.SUCCEEDED -> "已完成"
    BackgroundJobPhase.CANCELLED -> "已取消"
    BackgroundJobPhase.RESUMING -> "正在恢复"
    BackgroundJobPhase.INTERRUPTED -> when (interruption) {
        BackgroundJobInterruption.SYSTEM_TIMEOUT -> "达到系统运行时限，可重新开始"
        BackgroundJobInterruption.SERVICE_DESTROYED -> "后台服务已停止，可重新开始"
        else -> "上次运行被系统中断，可重新开始"
    }
    BackgroundJobPhase.FAILED -> when (failure) {
        BackgroundJobFailure.AUTHENTICATION_REQUIRED -> "登录状态已失效"
        BackgroundJobFailure.NETWORK_UNAVAILABLE -> "网络不可用"
        BackgroundJobFailure.REMOTE_REJECTED -> "平台拒绝了本次请求"
        BackgroundJobFailure.PROTOCOL_CHANGED -> "平台协议可能已变化"
        BackgroundJobFailure.INVALID_REQUEST -> "任务参数无效"
        BackgroundJobFailure.SYSTEM_TIMEOUT -> "达到系统运行时限"
        else -> "任务失败"
    }
}
