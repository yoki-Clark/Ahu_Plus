package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.data.local.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
     * 串行预热数据。每完成一步, 通过 [onProgress] 抛出"正在初始化 XXX"消息。
     *
     * 失败不中断：单步异常仅记日志,继续下一步。
     */
    suspend fun runSequentially(onProgress: (String) -> Unit) {
        Log.d(tag, "首次登录初始化开始")

        // 第 1 步：我的信息
        onProgress("正在初始化我的信息...")
        runStep { studentInfoRepository.getStudentInfo() }
        delay(600)

        // 第 2 步：水电费 (浴室 + 网费 — 空调/照明需要 building/floor/room 配置,跳过)
        onProgress("正在初始化水电费余额...")
        runStep {
            coroutineScope {
                val phone = sessionManager.getBathroomPhone().orEmpty()
                val bathroomDeferred = async {
                    if (phone.isNotBlank()) ycardRepository.getBathroomBalance(phone) else null
                }
                val internetDeferred = async { ycardRepository.getInternetBalance() }
                bathroomDeferred.await()
                internetDeferred.await()
            }
        }
        delay(600)

        // 第 3 步：成绩
        onProgress("正在初始化成绩...")
        runStep { gradeRepository.getGrades() }
        delay(600)

        // 第 4 步：考试
        onProgress("正在初始化考试安排...")
        runStep { examRepository.getExams() }
        delay(600)

        // 第 5 步：培养方案
        onProgress("正在初始化培养进度...")
        runStep { trainingPlanRepository.getTrainingPlan() }
        delay(600)

        // 第 6 步：消费账单
        onProgress("正在初始化消费账单...")
        runStep { ycardRepository.getAllBills() }
        delay(600)

        // 第 7 步：考勤信息
        onProgress("正在初始化考勤信息...")
        runStep { kqAttendanceRepository.getAttendanceList() }
        delay(400)

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
