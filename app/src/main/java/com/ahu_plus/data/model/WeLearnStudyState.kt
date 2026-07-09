package com.ahu_plus.data.model

/**
 * WeLearn 刷课 UI 状态 (供 WeLearnStudyRepository.studyState 暴露给 Compose)。
 *
 * 比超星 CxStudyUiState 简单:WeLearn 没有"任务点"概念,只到"章节完成"。
 */
data class WeLearnStudyUiState(
    val isRunning: Boolean = false,
    val currentUnitName: String = "",
    val currentScoLocation: String = "",
    val totalCount: Int = 0,           // 总章节数(已完成也计入,便于显示进度)
    val pendingCount: Int = 0,         // 还没处理的
    val completedCount: Int = 0,       // way1+way2 都 ret=0 的
    val partialCount: Int = 0,         // 只有 way1 或 way2 成功
    val failedCount: Int = 0,          // 两路都失败
    val skippedCount: Int = 0,         // 已完成/未开放 跳过
    val answersFetched: Int = 0,       // 2026-06-28:从 CDN 拉到的真答案数量
    val accuracy: Int = 100,
    val logs: List<String> = emptyList(),
    val error: String? = null,
    // 2026-06-28:刷时长字段(每节心跳)
    val elapsedSec: Int = 0,                    // 当前 sco 已刷秒数
    val currentScoHeartbeatSec: Int = 0,        // 当前 sco 计划总秒数(0=未启用心跳)
    val heartbeatKeepFails: Int = 0,            // 2026-06-29:本节 keep 累计失败次数(便于手机端排查 carrier NAT 等瞬时断)
) {
    /** 进度条比例 0..1,基于 completed + partial/2 (UI 可调权重) */
    val progress: Float
        get() = if (totalCount == 0) 0f
        else (completedCount + partialCount * 0.5f) / totalCount
}