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
 * 目标：用户从 LoginScreen 首次登录成功后，串行预热以下 7 项核心数据，
 * 同时通过 [onProgress] 回调抛出"正在初始化 XXX"消息给 UI 层做底部冒泡。
 *
 * 顺序（每项 try/catch,失败不中断）：
 *   1. 我的信息     (StudentInfoRepository)
 *   2. 水电费余额   (YcardRepository — 浴室/空调/照明/网费 4 个并行)
 *   3. 成绩         (GradeRepository)
 *   4. 考试         (ExamRepository)
 *   5. 培养方案     (TrainingPlanRepository)
 *   6. 消费账单     (YcardRepository.getAllBills)
 *   7. 考勤信息     (KqAttendanceRepository)
 *
 * 每步默认 1 秒冒泡间隔（前置 onProgress + 600ms 延迟确保 UI 渲染完成）。
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
     * 预热数据。第 1 步必须先做(后续依赖学生信息预填),其余 6 步并行执行。
     *
     * 风控考量:6 个并行请求落在 3 个域名 (one.ahu / ycard.ahu / jw.ahu),
     * 每域 2-3 个请求,远低于浏览器同站 6 连接 baseline,不会触发风控。
     *
     * 失败不中断:单步异常仅记日志,继续其它步骤。
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
