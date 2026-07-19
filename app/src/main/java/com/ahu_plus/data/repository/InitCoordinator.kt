package com.ahu_plus.data.repository

import android.util.Log
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.local.DataRefreshPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 首次登录后的"预热"协调器 (2026-06-22)。
 *
 * 当前只在缺少或过期时预热“我的信息”，用于页面展示和生活服务预填。
 * 成绩、考试、培养方案、消费账单、考勤和水电费均保持页面按需加载。
 */
class InitCoordinator(
    private val sessionManager: SessionManager,
    private val casAuthRepository: CasAuthRepository,
    private val studentInfoRepository: StudentInfoRepository,
    private val ycardRepository: YcardRepository,
    private val gradeRepository: GradeRepository,
    private val examRepository: ExamRepository,
    private val trainingPlanRepository: TrainingPlanRepository,
    private val kqAttendanceRepository: KqAttendanceRepository,
) {
    private val tag = "InitCoordinator"

    /**
     * 预热学生信息。失败只记日志，不影响登录完成。
     */
    suspend fun runSequentially(onProgress: (String) -> Unit) {
        Log.d(tag, "首次登录初始化开始")

        val missing = studentInfoRepository.readCachedStudentInfo() == null
        val stale = DataRefreshPolicy.isStale(
            sessionManager.getStudentInfoUpdatedAt(),
            30L * 24 * 60 * 60 * 1000,
        )
        if (missing || stale) {
            onProgress("正在初始化我的信息...")
            runStep { studentInfoRepository.getStudentInfo() }
        }

        Log.d(tag, "首次登录初始化完成")
        sessionManager.firstLoginInitDone = true
    }

    /**
     * 单步执行包装：15 秒超时,失败仅记日志,不抛出。
     */
    private suspend fun runStep(block: suspend () -> Any?) {
        try {
            withTimeoutOrNull(15_000) {
                withContext(Dispatchers.IO) { block() }
            }
        } catch (e: Exception) {
            Log.w(tag, "步骤失败(已跳过): ${e.message}")
        }
    }
}
