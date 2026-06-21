package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.CxChapter
import com.yourname.ahu_plus.data.model.CxCourse
import com.yourname.ahu_plus.data.model.CxJob
import com.yourname.ahu_plus.data.model.CxJobInfo
import com.yourname.ahu_plus.data.model.CxStudyResult
import com.yourname.ahu_plus.data.model.CxStudyUiState
import com.yourname.ahu_plus.data.model.CxTaskProgress
import com.yourname.ahu_plus.data.model.CxTaskStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlin.random.Random

/**
 * 超星学习通自动学习 Repository。
 *
 * 负责视频自动播放、文档/阅读自动完成、章节检测自动答题。
 * 学习进度通过 [studyState] StateFlow 实时汇报到 UI。
 */
class ChaoxingStudyRepository(
    private val cxRepo: ChaoxingRepository,
    private val tikuRepo: ChaoxingTikuRepository,
    private val sessionManager: SessionManager,
    private val notificationRepo: ChaoxingNotificationRepository? = null,
) {
    companion object {
        private const val TAG = "CxStudy"
        private const val MAX_403_RETRY = 2
    }

    private val _studyState = MutableStateFlow(CxStudyUiState())
    val studyState: StateFlow<CxStudyUiState> = _studyState.asStateFlow()

    @Volatile
    private var shouldStop = false

    /** 停止学习 */
    fun stop() {
        shouldStop = true
    }

    // ══════════════════════════════════════════════════════════════
    //  一键学习（入口）
    // ══════════════════════════════════════════════════════════════

    /**
     * 一键学习所有课程（或指定课程列表）。
     *
     * @param courses 要学习的课程列表
     * @param speed 视频播放倍速 (1.0~2.0)
     * @param concurrency 同时学习的章节数
     * @param autoSubmit 是否自动提交答题（true=提交, false=仅保存）
     */
    suspend fun studyAll(
        courses: List<CxCourse>,
        speed: Float = 1.0f,
        concurrency: Int = 4,
        autoSubmit: Boolean = true,
        enabledTaskTypes: Set<String> = setOf("video", "document", "read", "workid"),
    ) {
        shouldStop = false
        _studyState.value = CxStudyUiState(isRunning = true)

        try {
            for (course in courses) {
                if (shouldStop) break
                addLog("开始学习课程: ${course.title}")

                val pointsResult = cxRepo.getCoursePoints(course)
                if (pointsResult.isFailure) {
                    addLog("获取章节失败: ${pointsResult.exceptionOrNull()?.message}")
                    continue
                }

                val points = pointsResult.getOrNull()?.points ?: emptyList()
                addLog("共 ${points.size} 个章节")

                val semaphore = Semaphore(concurrency)

                for (chapter in points) {
                    if (shouldStop) break
                    if (chapter.hasFinished) {
                        addLog("已完成: ${chapter.title}")
                        continue
                    }
                    if (chapter.needUnlock) {
                        addLog("需解锁: ${chapter.title}")
                        continue
                    }

                    // 并发控制
                    semaphore.acquire()
                    try {
                        processChapter(course, chapter, speed, autoSubmit, enabledTaskTypes)
                    } finally {
                        semaphore.release()
                    }
                }
            }

            addLog("所有课程学习任务已完成")

            // 自动签到(Phase 5, 2026-06-20)
            if (sessionManager.getCxAutoSign()) {
                addLog("自动签到: 扫描课程活动...")
                autoSignAllCourses(courses)
            }

            // 推送通知
            notificationRepo?.send("超星学习通: 所有课程已完成")?.onFailure {
                addLog("通知推送失败: ${it.message}")
            }

            _studyState.value = _studyState.value.copy(isRunning = false)
        } catch (e: Exception) {
            Log.e(TAG, "学习异常", e)
            addLog("学习异常: ${e.message}")
            notificationRepo?.send("超星学习通异常: ${e.message}")
            _studyState.value = _studyState.value.copy(isRunning = false, error = e.message)
        }
    }

    /**
     * 自动签到(遍历所有课程的活动列表,对进行中的签到执行签到)。
     */
    private suspend fun autoSignAllCourses(courses: List<CxCourse>) {
        val lat = sessionManager.getCxSignLat()
        val lon = sessionManager.getCxSignLon()
        val address = sessionManager.getCxSignAddress()
        val gesture = sessionManager.getCxSignGesture()
        for (course in courses) {
            if (shouldStop) break
            val actsResult = cxRepo.getActivityList(course)
            val activities = actsResult.getOrNull() ?: continue
            for (act in activities.filter { it.status == 1 }) {
                if (shouldStop) break
                addLog("自动签到: ${act.name} (${act.signType.label})")
                cxRepo.preSign(course, act.id)
                val r = when (act.signType) {
                    com.yourname.ahu_plus.data.model.CxSignType.LOCATION -> {
                        if (lat >= 0 && lon >= 0) cxRepo.signInLocation(course, act.id, lat, lon, address)
                        else { addLog("  ⚠ 未配置经纬度,跳过"); continue }
                    }
                    com.yourname.ahu_plus.data.model.CxSignType.GESTURE -> {
                        if (gesture.isNotBlank()) cxRepo.signInGesture(course, act.id, gesture)
                        else { addLog("  ⚠ 未配置手势码,跳过"); continue }
                    }
                    else -> cxRepo.signNormal(course, act.id)
                }
                r.onSuccess { addLog("  ✓ 签到成功: $it") }
                r.onFailure { addLog("  ✗ 签到失败: ${it.message}") }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  单课程学习
    // ══════════════════════════════════════════════════════════════

    /**
     * 学习单个课程的所有章节。
     */
    suspend fun studyCourse(
        course: CxCourse,
        speed: Float = 1.0f,
        autoSubmit: Boolean = true,
        enabledTaskTypes: Set<String> = setOf("video", "document", "read", "workid"),
    ) {
        shouldStop = false
        _studyState.value = CxStudyUiState(isRunning = true)

        try {
            addLog("开始学习: ${course.title}")
            val pointsResult = cxRepo.getCoursePoints(course)
            if (pointsResult.isFailure) {
                addLog("获取章节失败: ${pointsResult.exceptionOrNull()?.message}")
                _studyState.value = _studyState.value.copy(isRunning = false, error = "获取章节失败")
                return
            }

            val points = pointsResult.getOrNull()?.points ?: emptyList()
            _studyState.value = _studyState.value.copy(totalTasks = points.size)

            for (chapter in points) {
                if (shouldStop) break
                processChapter(course, chapter, speed, autoSubmit, enabledTaskTypes)
            }

            _studyState.value = _studyState.value.copy(isRunning = false)
            addLog("课程学习完成: ${course.title}")
        } catch (e: Exception) {
            Log.e(TAG, "学习异常", e)
            _studyState.value = _studyState.value.copy(isRunning = false, error = e.message)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  章节处理
    // ══════════════════════════════════════════════════════════════

    private suspend fun processChapter(
        course: CxCourse,
        chapter: CxChapter,
        speed: Float,
        autoSubmit: Boolean,
        enabledTaskTypes: Set<String> = setOf("video", "document", "read", "workid"),
    ) {
        addLog("章节: ${chapter.title}")

        // 获取任务点
        val jobsResult = cxRepo.getJobList(course, chapter)
        if (jobsResult.isFailure) {
            addLog("  获取任务点失败: ${jobsResult.exceptionOrNull()?.message}")
            return
        }

        val (jobs, jobInfo) = jobsResult.getOrNull() ?: return

        if (jobs.isEmpty()) {
            if (chapter.jobCount > 0) {
                // 有任务点但全部已通过 → 视为已完成，无需 studyEmptyPage
                addLog("  所有任务已完成")
            } else {
                // 真正的空页面（无任务点）
                cxRepo.studyEmptyPage(course, chapter)
                addLog("  空页面任务完成")
            }
            _studyState.value = _studyState.value.copy(
                completedCount = _studyState.value.completedCount + 1
            )
            return
        }

        for (job in jobs) {
            if (shouldStop) break
            if (job.type !in enabledTaskTypes) {
                addLog("    跳过: ${job.name.ifBlank { job.type }} (${job.type} 未启用)")
                continue
            }
            processJob(course, chapter, job, jobInfo, speed, autoSubmit)
        }

        _studyState.value = _studyState.value.copy(
            completedCount = _studyState.value.completedCount + 1
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  任务点处理
    // ══════════════════════════════════════════════════════════════

    private suspend fun processJob(
        course: CxCourse,
        chapter: CxChapter,
        job: CxJob,
        jobInfo: CxJobInfo,
        speed: Float,
        autoSubmit: Boolean,
    ) {
        val taskTitle = "${chapter.title} / ${job.name.ifBlank { job.type }}"
        val progress = CxTaskProgress(
            courseTitle = course.title,
            chapterTitle = chapter.title,
            job = job,
            status = CxTaskStatus.RUNNING,
        )
        _studyState.value = _studyState.value.copy(currentTask = progress)
        addLog("  任务: $taskTitle [${job.type}]")

        val result = when (job.type) {
            "video" -> studyVideo(course, job, jobInfo, speed)
            "document" -> {
                cxRepo.studyDocument(course, job, jobInfo)
                CxStudyResult.SUCCESS
            }
            "read" -> {
                cxRepo.studyRead(course, job, jobInfo)
                CxStudyResult.SUCCESS
            }
            "workid" -> studyWork(course, job, jobInfo, autoSubmit)
            else -> {
                addLog("    未知任务类型: ${job.type}")
                CxStudyResult.SKIPPED
            }
        }

        val status = if (result.isSuccess()) CxTaskStatus.SUCCESS else CxTaskStatus.FAILED
        val completed = _studyState.value.completedTasks.toMutableList()
        completed.add(progress.copy(status = status, progress = 1f))
        _studyState.value = _studyState.value.copy(
            currentTask = null,
            completedTasks = completed,
        )

        addLog("  ${if (result.isSuccess()) "✓ 完成" else "✗ 失败"}: $taskTitle")
    }

    // ══════════════════════════════════════════════════════════════
    //  视频自动学习
    // ══════════════════════════════════════════════════════════════

    private suspend fun studyVideo(
        course: CxCourse,
        job: CxJob,
        jobInfo: CxJobInfo,
        speed: Float,
    ): CxStudyResult {
        // 1. 获取视频信息
        val infoResult = cxRepo.getVideoInfo(job.objectid)
        if (infoResult.isFailure) {
            addLog("    获取视频信息失败: ${infoResult.exceptionOrNull()?.message}")
            return CxStudyResult.ERROR
        }
        val videoInfo = infoResult.getOrNull()!!
        val duration = videoInfo.duration
        val dtoken = videoInfo.dtoken

        var playTime = job.playTime / 1000  // 已播放秒数
        addLog("    视频时长: ${duration}s, 已播放: ${playTime}s")

        // 2. 尝试瞬间完成 (isdrag=4)
        val instantResult = cxRepo.reportVideoProgress(course, job, jobInfo, dtoken, duration, duration, isdrag = 4)
        if (instantResult.isSuccess && instantResult.getOrNull() == true) {
            addLog("    视频瞬间完成")
            return CxStudyResult.SUCCESS
        }

        // 3. 循环上报进度
        var lastLogTime = playTime
        var waitTime = Random.nextLong(30_000, 90_000)
        var lastIter = System.currentTimeMillis()
        var forbiddenRetry = 0

        while (playTime < duration) {
            if (shouldStop) return CxStudyResult.ERROR

            val now = System.currentTimeMillis()
            val dt = (now - lastIter) * speed
            lastIter = now
            playTime = minOf(duration, playTime + (dt / 1000).toInt())

            // 达到上报间隔
            if (playTime - lastLogTime >= (waitTime / 1000).toInt() || playTime >= duration) {
                val reportResult = cxRepo.reportVideoProgress(
                    course, job, jobInfo, dtoken, duration, playTime
                )

                reportResult.onSuccess { isPassed ->
                    if (isPassed) {
                        addLog("    视频任务完成")
                        return CxStudyResult.SUCCESS
                    }
                }

                reportResult.onFailure { e ->
                    val msg = e.message ?: ""
                    if (msg.contains("403")) {
                        forbiddenRetry++
                        if (forbiddenRetry >= MAX_403_RETRY) {
                            addLog("    403 重试失败，跳过")
                            return CxStudyResult.FORBIDDEN
                        }
                        addLog("    403 错误，刷新会话 (${forbiddenRetry}/$MAX_403_RETRY)")
                        delay(Random.nextLong(2000, 4000))
                        // 尝试刷新视频信息
                        val refreshed = cxRepo.getVideoInfo(job.objectid)
                        if (refreshed.isSuccess) {
                            // 用新的 dtoken 继续
                            return@onFailure
                        }
                    }
                }

                waitTime = Random.nextLong(30_000, 90_000)
                lastLogTime = playTime
            }

            // 更新进度到 UI
            val progress = playTime.toFloat() / duration
            _studyState.value = _studyState.value.copy(
                currentTask = _studyState.value.currentTask?.copy(
                    progress = progress,
                    message = "${playTime}s / ${duration}s"
                )
            )

            delay(1000)  // 每秒检查一次
        }

        return CxStudyResult.SUCCESS
    }

    // ══════════════════════════════════════════════════════════════
    //  章节检测（自动答题）
    // ══════════════════════════════════════════════════════════════

    private suspend fun studyWork(
        course: CxCourse,
        job: CxJob,
        jobInfo: CxJobInfo,
        autoSubmit: Boolean,
    ): CxStudyResult {
        // 1. 获取题目
        val questionsResult = cxRepo.getWorkQuestions(course, job, jobInfo)
        if (questionsResult.isFailure) {
            addLog("    获取题目失败: ${questionsResult.exceptionOrNull()?.message}")
            return CxStudyResult.ERROR
        }

        val workData = questionsResult.getOrNull()!!
        if (workData.questions.isEmpty()) {
            addLog("    无题目")
            return CxStudyResult.SUCCESS
        }

        addLog("    共 ${workData.questions.size} 道题")

        // 2. 逐题查题库 + 填答案
        val formFields = workData.formFields.toMutableMap()
        var foundCount = 0

        for (q in workData.questions) {
            val answer = tikuRepo.query(q)
            val finalAnswer = if (answer != null) {
                foundCount++
                addLog("    [${q.type}] ${q.title.take(30)}... → $answer (题库)")
                answer
            } else {
                // 随机答题
                val randomAns = generateRandomAnswer(q)
                addLog("    [${q.type}] ${q.title.take(30)}... → $randomAns (随机)")
                randomAns
            }

            // 填入表单
            formFields["answer${q.id}"] = finalAnswer
            formFields["answertype${q.id}"] = q.answerField["answertype${q.id}"] ?: ""
        }

        // 3. 覆盖率检查
        val coverage = foundCount.toFloat() / workData.questions.size
        addLog("    题库覆盖率: ${(coverage * 100).toInt()}%")

        // pyFlag: "" = 提交, "1" = 仅保存
        val pyFlag = if (autoSubmit && coverage >= 0.8f) "" else "1"

        // 4. 提交
        val submitData = workData.copy(
            formFields = formFields,
            pyFlag = pyFlag,
        )

        val submitResult = cxRepo.submitWork(submitData)
        submitResult.onSuccess { msg ->
            addLog("    ${if (pyFlag == "") "提交" else "保存"}成功: $msg")
        }
        submitResult.onFailure { e ->
            addLog("    ${if (pyFlag == "") "提交" else "保存"}失败: ${e.message}")
            return CxStudyResult.ERROR
        }

        return CxStudyResult.SUCCESS
    }

    /**
     * 生成随机答案(2026-06-20 Phase 5,移植自 answer.py:random_answer)。
     *
     * 多选题使用智能权重:
     *   - 2 选项: 必选 1
     *   - 3 选项: [0.3, 0.7] 选 1 或 2
     *   - 4 选项: [0.1, 0.5, 0.4] 选 1/2/3
     *   - 5 选项: [0.1, 0.4, 0.3, 0.2] 选 1~4
     */
    private fun generateRandomAnswer(q: com.yourname.ahu_plus.data.model.CxQuestion): String {
        return when (q.type) {
            "single" -> {
                val opts = q.options.split("\n").filter { it.isNotBlank() }
                if (opts.isNotEmpty()) opts.random().take(1) else ""
            }
            "multiple" -> generateWeightedMultipleAnswer(q)
            "judgement" -> if (Random.nextBoolean()) "true" else "false"
            "completion" -> ""
            else -> ""
        }
    }

    private fun generateWeightedMultipleAnswer(q: com.yourname.ahu_plus.data.model.CxQuestion): String {
        val opts = q.options.split("\n").filter { it.isNotBlank() }
        if (opts.isEmpty()) return ""
        val n = opts.size
        val (minN, maxN, weights) = when {
            n <= 1 -> Triple(1, 1, listOf(1.0))
            n == 2 -> Triple(1, 2, listOf(0.5, 0.5))
            n == 3 -> Triple(1, 2, listOf(0.3, 0.7))
            n == 4 -> Triple(1, 3, listOf(0.1, 0.5, 0.4))
            else -> Triple(1, 4, listOf(0.1, 0.4, 0.3, 0.2))
        }
        val sumW = weights.sum()
        val normalized = if (sumW > 0) weights.map { it / sumW } else weights
        val choices = (minN..maxN).toList()
        val selectCount = if (normalized.size == choices.size) {
            val r = Random.nextDouble()
            var acc = 0.0
            var picked = choices.last()
            for (i in choices.indices) {
                acc += normalized[i]
                if (r <= acc) { picked = choices[i]; break }
            }
            picked
        } else {
            choices.random()
        }
        return opts.shuffled().take(selectCount).map { it.take(1) }.sorted().joinToString("")
    }

    // ══════════════════════════════════════════════════════════════
    //  日志
    // ══════════════════════════════════════════════════════════════

    private fun addLog(msg: String) {
        Log.d(TAG, msg)
        val logs = _studyState.value.logs.toMutableList()
        logs.add(msg)
        // 保留最近 200 条
        if (logs.size > 200) logs.removeAt(0)
        _studyState.value = _studyState.value.copy(logs = logs)
    }
}
