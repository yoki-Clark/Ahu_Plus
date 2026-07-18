package com.ahu_plus.data.repository

import android.util.Log
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.CxChapter
import com.ahu_plus.data.model.CxCourse
import com.ahu_plus.data.model.CxJob
import com.ahu_plus.data.model.CxJobInfo
import com.ahu_plus.data.model.CxStudyResult
import com.ahu_plus.data.model.CxStudyUiState
import com.ahu_plus.data.model.CxTaskProgress
import com.ahu_plus.data.model.CxTaskStatus
import com.ahu_plus.data.network.ChaoxingAuthExpiredException
import com.ahu_plus.data.network.ChaoxingForbiddenException
import com.ahu_plus.data.network.ChaoxingRateLimitedException
import com.ahu_plus.data.network.ChaoxingRiskChallengeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CxAnswerMode(
    val settingValue: String,
    val shouldQueryAnswers: Boolean,
    val shouldSubmit: Boolean,
) {
    AUTO("auto", shouldQueryAnswers = true, shouldSubmit = true),
    SAVE("save", shouldQueryAnswers = true, shouldSubmit = false),
    SKIP("skip", shouldQueryAnswers = false, shouldSubmit = false),
    ;

    companion object {
        /** Unknown or missing values fail closed and do not issue answer requests. */
        fun fromSetting(value: String?): CxAnswerMode {
            val normalized = value?.trim().orEmpty()
            return entries.firstOrNull {
                it.settingValue.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true)
            } ?: SKIP
        }
    }
}

/**
 * 超星学习通自动学习 Repository。
 *
 * 负责按真实时长串行播放视频，并按显式答题策略处理章节检测。会瞬时标记
 * 完成的文档、阅读、音频和直播端点在执行层禁用。
 * 学习进度通过 [studyState] StateFlow 实时汇报到 UI。
 */
class ChaoxingStudyRepository(
    private val cxRepo: ChaoxingRepository,
    private val tikuRepo: ChaoxingTikuRepository,
    private val sessionManager: SessionManager,
    private val notificationRepo: ChaoxingNotificationRepository? = null,
    private val context: android.content.Context? = null,
) {
    companion object {
        private const val TAG = "CxStudy"
        private const val CODE_FORBIDDEN = 403
        private const val CODE_RATE_LIMITED = 429
        private const val CODE_RISK_CHALLENGE = 460
    }

    private val _studyState = MutableStateFlow(CxStudyUiState())
    val studyState: StateFlow<CxStudyUiState> = _studyState.asStateFlow()

    @Volatile
    private var shouldStop = false

    private val runLock = Any()
    private var runOwner: Any? = null
    private val downloadedResourceKeys = mutableSetOf<String>()

    @Volatile
    private var studyJob: kotlinx.coroutines.Job? = null

    /** 停止学习 */
    fun stop() {
        val job = synchronized(runLock) {
            shouldStop = true
            studyJob
        }
        job?.cancel()
        _studyState.value = _studyState.value.copy(isRunning = false, currentTask = null)
    }

    private fun acquireRun(job: Job?): Any? {
        val owner = job ?: Any()
        return synchronized(runLock) {
            if (runOwner != null) null else owner.also {
                runOwner = it
                studyJob = job
                shouldStop = false
                downloadedResourceKeys.clear()
            }
        }
    }

    private fun releaseRun(owner: Any) {
        synchronized(runLock) {
            if (runOwner === owner) {
                runOwner = null
                studyJob = null
                downloadedResourceKeys.clear()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  一键学习（入口）
    // ══════════════════════════════════════════════════════════════

    /**
     * 一键学习所有课程（或指定课程列表）。
     *
     * @param courses 要学习的课程列表
     * @param speed 兼容旧配置的速度参数；执行层固定为 1.0x
     * @param concurrency 兼容旧配置的并发参数；执行层固定为串行
     * @param answerMode 答题策略；未知配置由 [CxAnswerMode.fromSetting] 按不答题处理
     */
    suspend fun studyAll(
        courses: List<CxCourse>,
        speed: Float = 1.0f,
        concurrency: Int = 1,
        answerMode: CxAnswerMode = CxAnswerMode.SKIP,
        enabledTaskTypes: Set<String> = setOf("video", "document", "read", "workid", "audio", "live"),
    ) {
        val runningJob = kotlin.coroutines.coroutineContext[Job]
        val runOwner = acquireRun(runningJob)
        if (runOwner == null) {
            addLog("已有学习任务正在运行，本次启动已拒绝")
            return
        }
        _studyState.value = CxStudyUiState(isRunning = true)

        // Keep the parameter for settings/Intent compatibility, but never create
        // concurrent requests from a stale preference.
        if (concurrency != 1) {
            addLog("并发参数已兼容保留，但自动学习固定顺序执行（请求并发=1）")
        }

        try {
            for (course in courses) {
                if (shouldStop) break
                addLog("开始学习课程: ${course.title}")

                val pointsResult = cxRepo.getCoursePoints(course)
                if (pointsResult.isFailure) {
                    val error = pointsResult.exceptionOrNull()
                        ?: IllegalStateException("course points request failed")
                    addLog("获取章节失败: ${safeErrorLabel(error)}")
                    throwIfRestriction(error)
                    throw error
                }

                val points = pointsResult.getOrNull()?.points ?: emptyList()
                // 累加任务点总数（jobCount 来自 API 响应，无需额外请求）
                _studyState.value = _studyState.value.copy(
                    totalTasks = _studyState.value.totalTasks + points.sumOf { it.jobCount }
                )
                addLog("共 ${points.size} 个章节")

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

                    processChapter(course, chapter, speed, answerMode, enabledTaskTypes)
                }
            }

            if (shouldStop) throw CancellationException("Study stopped")

            addLog("本次自动学习任务已结束")

            // 推送通知
            notificationRepo?.send("超星学习通: 本次自动学习任务已结束")?.onFailure {
                addLog("通知推送失败: ${safeErrorLabel(it)}")
            }

        } catch (e: CancellationException) {
            Log.i(TAG, "学习已停止")
            addLog("学习已停止")
            throw e
        } catch (e: Exception) {
            val errorLabel = safeErrorLabel(e)
            Log.e(TAG, "学习异常: $errorLabel")
            addLog("学习异常: $errorLabel")
            _studyState.value = _studyState.value.copy(error = errorLabel)
            notificationRepo?.send("超星学习通异常: $errorLabel")
            throw e
        } finally {
            releaseRun(runOwner)
            _studyState.value = _studyState.value.copy(isRunning = false, currentTask = null)
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
        answerMode: CxAnswerMode = CxAnswerMode.SKIP,
        enabledTaskTypes: Set<String> = setOf("video", "document", "read", "workid", "audio", "live"),
    ) {
        val runningJob = kotlin.coroutines.coroutineContext[Job]
        val runOwner = acquireRun(runningJob)
        if (runOwner == null) {
            addLog("已有学习任务正在运行，本次启动已拒绝")
            return
        }
        _studyState.value = CxStudyUiState(isRunning = true)

        try {
            addLog("开始学习: ${course.title}")
            val pointsResult = cxRepo.getCoursePoints(course)
            if (pointsResult.isFailure) {
                val error = pointsResult.exceptionOrNull()
                addLog("获取章节失败: ${safeErrorLabel(error)}")
                throwIfRestriction(error)
                _studyState.value = _studyState.value.copy(isRunning = false, error = "获取章节失败")
                return
            }

            val points = pointsResult.getOrNull()?.points ?: emptyList()
            _studyState.value = _studyState.value.copy(
                totalTasks = points.sumOf { it.jobCount }
            )

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
                processChapter(course, chapter, speed, answerMode, enabledTaskTypes)
            }

            if (shouldStop) throw CancellationException("Study stopped")
            _studyState.value = _studyState.value.copy(isRunning = false)
            addLog("课程学习完成: ${course.title}")
        } catch (e: CancellationException) {
            addLog("学习已停止")
            throw e
        } catch (e: Exception) {
            val errorLabel = safeErrorLabel(e)
            Log.e(TAG, "学习异常: $errorLabel")
            _studyState.value = _studyState.value.copy(isRunning = false, error = errorLabel)
            if (isRestrictionFailure(e)) throw e
        } finally {
            releaseRun(runOwner)
            _studyState.value = _studyState.value.copy(isRunning = false, currentTask = null)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  章节处理
    // ══════════════════════════════════════════════════════════════

    private suspend fun processChapter(
        course: CxCourse,
        chapter: CxChapter,
        speed: Float,
        answerMode: CxAnswerMode,
        enabledTaskTypes: Set<String> = setOf("video", "document", "read", "workid", "audio", "live"),
    ) {
        addLog("章节: ${chapter.title}")

        // 获取任务点
        val jobsResult = cxRepo.getJobList(course, chapter)
        if (jobsResult.isFailure) {
            val error = jobsResult.exceptionOrNull()
                ?: IllegalStateException("job list request failed")
            addLog("  获取任务点失败: ${safeErrorLabel(error)}")
            throwIfRestriction(error)
            throw error
        }

        val (jobs, jobInfo) = jobsResult.getOrNull() ?: return

        if (jobs.isEmpty()) {
            val done = if (chapter.jobCount > 0 && chapter.hasFinished) {
                addLog("  所有任务已完成")
                chapter.jobCount
            } else if (chapter.jobCount > 0) {
                throw IllegalStateException("incomplete chapter returned no task cards")
            } else {
                // 真正的空页面（无任务点）
                // Do not call studentstudyAjax for an empty chapter in background mode.
                addLog("  空章节跳过（不会发送访问/完成上报）")
                0
            }
            synchronized(_studyState) {
                _studyState.value = _studyState.value.copy(
                    completedCount = _studyState.value.completedCount + done
                )
            }
            return
        }

        // 优先级排序：短任务（文档/阅读/音频/直播）先于长任务（答题/视频）
        val priorityOrder = mapOf(
            "document" to 0, "read" to 1, "audio" to 2, "live" to 3, "workid" to 4, "video" to 5
        )
        val sortedJobs = jobs.sortedBy { priorityOrder[it.type] ?: 99 }
        // 已通过的任务点数 = 本章总数 - 当前未通过数
        val prePassedCount = (chapter.jobCount - jobs.size).coerceAtLeast(0)
        var succeededCount = 0

        for (job in sortedJobs) {
            if (shouldStop) break
            if (job.type !in enabledTaskTypes) {
                addLog("    跳过: ${job.name.ifBlank { job.type }} (${job.type} 未启用)")
                continue
            }
            val result = processJob(course, chapter, job, jobInfo, speed, answerMode)
            // 仅真正完成的任务计入完成数（失败/仅保存未提交/受限不计）
            if (result.isSuccess()) {
                succeededCount++
            } else if (result != CxStudyResult.SKIPPED) {
                throw IllegalStateException("study task failed: ${result.name}")
            }
        }

        // 自动下载章节资源（如已启用）
        if (sessionManager.getCxDownloadEnabled() && context != null && !shouldStop) {
            val resourcesResult = cxRepo.getCourseResources(course, chapter)
            if (resourcesResult.isFailure) {
                val error = resourcesResult.exceptionOrNull()
                    ?: IllegalStateException("resource list request failed")
                addLog("    获取章节资源失败: ${safeErrorLabel(error)}")
                throwIfRestriction(error)
                throw error
            }
            val resources = resourcesResult.getOrNull().orEmpty()
            for (res in resources) {
                if (shouldStop) break
                val resourceKey = res.preview.trim().ifBlank { res.name.trim() }
                val firstInRun = synchronized(runLock) { downloadedResourceKeys.add(resourceKey) }
                if (!firstInRun) {
                    addLog("    重复资源已跳过")
                    continue
                }
                addLog("    下载章节资源")
                val downloadResult = cxRepo.downloadResource(context, res.preview, res.name)
                if (downloadResult.isFailure) {
                    val error = downloadResult.exceptionOrNull()
                        ?: IllegalStateException("resource download failed")
                    addLog("    下载失败: ${safeErrorLabel(error)}")
                    throwIfRestriction(error)
                    throw error
                } else {
                    addLog("    ✓ 资源已保存")
                }
            }
        }

        synchronized(_studyState) {
            _studyState.value = _studyState.value.copy(
                completedCount = _studyState.value.completedCount + prePassedCount + succeededCount
            )
        }
        if (succeededCount > 0) cxRepo.invalidateJobListCache(course, chapter.id)
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
        answerMode: CxAnswerMode,
    ): CxStudyResult {
        val taskTitle = "${chapter.title} / ${job.name.ifBlank { job.type }}"
        val progress = CxTaskProgress(
            courseTitle = course.title,
            chapterTitle = chapter.title,
            job = job,
            status = CxTaskStatus.RUNNING,
        )
        _studyState.value = _studyState.value.copy(currentTask = progress)
        addLog("  任务: $taskTitle [${job.type}]")

        // 失败原因（供 UI message 展示）
        var failMsg = ""
        val result = if (job.type in setOf("document", "read", "audio", "live")) {
            // These endpoints mark completion immediately and are not a substitute
            // for a real foreground playback/read event. Keep them visible in the
            // UI, but do not emit synthetic completion requests from this runner.
            failMsg = "后台自动学习已跳过即时完成任务"
            addLog("    $failMsg (${job.type})")
            CxStudyResult.SKIPPED
        } else when (job.type) {
            "video" -> studyVideo(course, job, jobInfo, speed)
            "workid" -> if (answerMode.shouldQueryAnswers) {
                studyWork(course, job, jobInfo, answerMode)
            } else {
                failMsg = "答题策略为不答题，已跳过"
                addLog("    $failMsg")
                CxStudyResult.SKIPPED
            }
            else -> {
                addLog("    未知任务类型: ${job.type}")
                CxStudyResult.SKIPPED
            }
        }

        // 三态映射：成功 / 跳过(未提交·未启用) / 失败
        val status = when {
            result.isSuccess() -> CxTaskStatus.SUCCESS
            result == CxStudyResult.SKIPPED -> CxTaskStatus.SKIPPED
            else -> CxTaskStatus.FAILED
        }
        // 跳过类(不答题或仅保存未提交)的提示消息
        val uiMsg = when {
            result == CxStudyResult.SKIPPED && job.type == "workid" && !answerMode.shouldQueryAnswers -> "不答题"
            result == CxStudyResult.SKIPPED && job.type == "workid" -> "仅保存未提交"
            result == CxStudyResult.FORBIDDEN -> "403 受限"
            failMsg.isNotBlank() -> failMsg
            else -> ""
        }
        synchronized(_studyState) {
            _studyState.value = _studyState.value.copy(
                currentTask = null,
                completedTasks = _studyState.value.completedTasks +
                    progress.copy(status = status, progress = 1f, message = uiMsg),
            )
        }

        val mark = when (status) {
            CxTaskStatus.SUCCESS -> "✓ 完成"
            CxTaskStatus.SKIPPED -> "→ 未提交/跳过"
            else -> "✗ 失败"
        }
        addLog("  $mark: $taskTitle")
        return result
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
            val error = infoResult.exceptionOrNull()
            addLog("    获取视频信息失败: ${safeErrorLabel(error)}")
            throwIfRestriction(error)
            return CxStudyResult.ERROR
        }
        val videoInfo = infoResult.getOrNull()!!
        if (videoInfo.duration <= 0) {
            addLog("    视频时长无效，停止该任务（duration=${videoInfo.duration}）")
            return CxStudyResult.ERROR
        }
        val duration = videoInfo.duration      // 秒
        val dtoken = videoInfo.dtoken

        // 原仓库: play_time = int(_job["playTime"]) // 1000 （毫秒→秒）
        // CxJob.playTime is expressed in seconds. Clamp stale values rather than
        // rewinding a completed item to an artificial progress marker.
        val serverPlayTime = job.playTime.coerceIn(0, duration)
        addLog("    视频时长: ${duration}s, 服务器记录: ${serverPlayTime}s")

        // 2. Never send an instant-completion/fake-drag request. Progress starts at
        // the server-recorded position and advances only through the paced loop.
        // 3. Continue from the recorded position; an end marker is sent at most once.
        val startSec = serverPlayTime
        val effectiveSpeed = speed.coerceIn(0.1f, 1.0f)
        if (speed != effectiveSpeed) {
            addLog("    倍速参数已限制为 ${effectiveSpeed}x，避免加速请求突发")
        }
        var playTime = startSec.toDouble()      // Double 累积，对齐 Python float

        // 4. Main loop. The server-provided interval is the only heartbeat cadence.
        var lastLogTime = startSec
        var lastIter = System.nanoTime()
        val reportIntervalSec = CxVideoReportPolicy.intervalSeconds(jobInfo.reportTimeInterval)
        var finalReportSent = false
        var reportCount = 0

        while (true) {
            if (shouldStop) return CxStudyResult.ERROR

            // 时间推进 = 真实流逝时间 × 倍速
            val nowNs = System.nanoTime()
            playTime += (nowNs - lastIter).toDouble() / 1_000_000_000.0 * effectiveSpeed
            lastIter = nowNs
            if (playTime > duration) playTime = duration.toDouble()
            val curSec = playTime.toInt()

            // 更新进度条
            _studyState.value = _studyState.value.copy(
                currentTask = _studyState.value.currentTask?.copy(
                    progress = (playTime / duration).toFloat(),
                    message = "${curSec}s / ${duration}s"
                )
            )

            // Report at the configured interval, plus one terminal report.
            val atEnd = curSec >= duration
            if (CxVideoReportPolicy.shouldReport(
                    currentSec = curSec,
                    lastReportedSec = lastLogTime,
                    durationSec = duration,
                    finalReportSent = finalReportSent,
                    intervalSec = reportIntervalSec,
                )) {
                reportCount++
                addLog("    [#$reportCount] 上报 ${curSec}s / ${duration}s")
                val (passed, code) = videoProgressLog(
                    course, job, jobInfo, dtoken, duration, curSec,
                    isdrag = 3,
                )
                if (passed) {
                    addLog("    ✓ 视频任务完成")
                    return CxStudyResult.SUCCESS
                }
                if (code == CODE_FORBIDDEN || code == CODE_RATE_LIMITED || code == CODE_RISK_CHALLENGE) {
                    shouldStop = true
                    addLog("    检测到限制响应 code=$code，停止整次学习，不再重试")
                    throw ChaoxingStudyRestrictionException(code, null)
                }
                if (code != 200) {
                    addLog("    上报异常 code=$code")
                    return CxStudyResult.ERROR
                }
                lastLogTime = curSec
                if (atEnd) {
                    finalReportSent = true
                    // A 200 response without isPassed is terminal for this video.
                    addLog("    结尾上报未通过，停止该视频任务")
                    return CxStudyResult.ERROR
                }
            }

            delay(500)
        }
    }

    /**
     * 原仓库 video_progress_log 的移植。
     * 上报视频进度，返回 (isPassed, statusCode)。
     */
    private suspend fun videoProgressLog(
        course: CxCourse,
        job: CxJob,
        jobInfo: CxJobInfo,
        dtoken: String,
        duration: Int,
        playingTime: Int,
        isdrag: Int = 3,
    ): Pair<Boolean, Int> {
        if ("courseId" in job.otherinfo) {
            addLog("    otherinfo 包含 courseId，异常")
            return Pair(false, 500)
        }

        val result = cxRepo.reportVideoProgress(course, job, jobInfo, dtoken, duration, playingTime, isdrag)
        return result.fold(
            onSuccess = { Pair(it, 200) },
            onFailure = { e ->
                throwIfRestriction(e)
                Pair(false, restrictionCode(e) ?: 500)
            },
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  章节检测（自动答题）
    // ══════════════════════════════════════════════════════════════

    private suspend fun studyWork(
        course: CxCourse,
        job: CxJob,
        jobInfo: CxJobInfo,
        answerMode: CxAnswerMode,
    ): CxStudyResult {
        if (!answerMode.shouldQueryAnswers) {
            addLog("    答题策略为不答题，已跳过")
            return CxStudyResult.SKIPPED
        }

        // 1. 获取题目
        val questionsResult = cxRepo.getWorkQuestions(course, job, jobInfo)
        if (questionsResult.isFailure) {
            val error = questionsResult.exceptionOrNull()
            addLog("    获取题目失败: ${safeErrorLabel(error)}")
            throwIfRestriction(error)
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
            if (answer == null) {
                addLog("    [${q.type}] 未命中题库，跳过作业，不提交未知答案")
                return CxStudyResult.SKIPPED
            }
            foundCount++
            addLog("    [${q.type}] 题库命中")
            val finalAnswer = answer

            // 填入表单
            formFields["answer${q.id}"] = finalAnswer
            formFields["answertype${q.id}"] = q.answerField["answertype${q.id}"] ?: ""
        }

        // 3. 覆盖率检查
        val coverage = foundCount.toFloat() / workData.questions.size
        addLog("    题库覆盖率: ${(coverage * 100).toInt()}%")

        val shouldSubmit = answerMode.shouldSubmit && coverage >= 0.8f

        // 4. 两步提交（模拟浏览器：先保存，再提交）
        if (shouldSubmit) {
            // 第一步：保存答案 (pyFlag="1")
            val saveResult = cxRepo.submitWork(workData.copy(formFields = formFields, pyFlag = "1"))
            if (saveResult.isFailure) {
                val error = saveResult.exceptionOrNull()
                addLog("    保存失败: ${safeErrorLabel(error)}")
                throwIfRestriction(error)
                return CxStudyResult.ERROR
            }
            addLog("    ✓ 保存成功")
            delay(2000) // 等待服务器处理保存完成后再提交
            // 第二步：提交 (pyFlag="")
            val submitResult = cxRepo.submitWork(workData.copy(formFields = formFields, pyFlag = ""))
            if (submitResult.isFailure) {
                val error = submitResult.exceptionOrNull()
                addLog("    提交失败: ${safeErrorLabel(error)}")
                throwIfRestriction(error)
                return CxStudyResult.ERROR
            }
            addLog("    ✓ 提交成功")
            return CxStudyResult.SUCCESS
        } else {
            // 覆盖率不足或关闭自动提交：仅保存草稿，未真正提交（任务未完成）
            val submitData = workData.copy(formFields = formFields, pyFlag = "1")
            val submitResult = cxRepo.submitWork(submitData)
            submitResult.onFailure { e ->
                addLog("    保存失败: ${safeErrorLabel(e)}")
                throwIfRestriction(e)
                return CxStudyResult.ERROR
            }
            val reason = if (!answerMode.shouldSubmit) "答题策略为仅保存" else "题库覆盖率 ${(coverage * 100).toInt()}% < 80%"
            addLog("    ⚠ 仅保存未提交（$reason），需手动确认")
            // 返回 SKIPPED：UI 标记为"未提交"，不计入完成数
            return CxStudyResult.SKIPPED
        }
    }

    /**
     * A restriction is terminal for one automatic run.  Network code may expose a
     * typed traffic exception, while older endpoints still return a plain message;
     * handle both without retrying or probing alternate parameters.
     */
    private fun restrictionCode(error: Throwable?): Int? {
        var current = error
        while (current != null) {
            when (current) {
                is ChaoxingRateLimitedException -> return CODE_RATE_LIMITED
                is ChaoxingRiskChallengeException -> return CODE_RISK_CHALLENGE
                is ChaoxingForbiddenException,
                is ChaoxingAuthExpiredException -> return CODE_FORBIDDEN
            }
            current = current.cause
        }

        val message = buildString {
            var node = error
            var depth = 0
            while (node != null && depth++ < 4) {
                if (isNotEmpty()) append(' ')
                append(node.message.orEmpty())
                node = node.cause
            }
        }.lowercase()
        return when {
            "429" in message || "too many request" in message || "rate limit" in message ||
                "频率限制" in message || "请求过于频繁" in message -> CODE_RATE_LIMITED
            "403" in message || "forbidden" in message || "401" in message ||
                "auth" in message && "expired" in message || "认证过期" in message -> CODE_FORBIDDEN
            "captcha" in message || "验证码" in message || "risk" in message ||
                "风控" in message || "安全验证" in message || "访问限制" in message ||
                "blocked" in message || "access denied" in message -> CODE_RISK_CHALLENGE
            else -> null
        }
    }

    private fun isRestrictionFailure(error: Throwable?): Boolean = restrictionCode(error) != null

    private fun restrictionException(error: Throwable?): Exception? {
        var current = error
        while (current != null) {
            when (current) {
                is ChaoxingRateLimitedException,
                is ChaoxingRiskChallengeException,
                is ChaoxingForbiddenException,
                is ChaoxingAuthExpiredException -> return current
            }
            current = current.cause
        }
        val code = restrictionCode(error) ?: return null
        return ChaoxingStudyRestrictionException(code, error)
    }

    private fun throwIfRestriction(error: Throwable?) {
        val restriction = restrictionException(error) ?: return
        shouldStop = true
        addLog("检测到访问限制，已停止整次学习")
        throw restriction
    }

    private fun safeErrorLabel(error: Throwable?): String {
        val code = restrictionCode(error)
        return if (code != null) "访问限制($code)" else error?.javaClass?.simpleName.orEmpty().ifBlank { "未知错误" }
    }

    // ══════════════════════════════════════════════════════════════
    //  日志
    // ══════════════════════════════════════════════════════════════

    private fun addLog(msg: String) {
        // Keep detailed progress in the in-app state only. Android system logs must
        // not contain course names, question text, answers, URLs, tokens, or paths.
        Log.d(TAG, "study state updated")
        synchronized(_studyState) {
            val logs = _studyState.value.logs.toMutableList().apply {
                add(msg)
                if (size > 200) removeAt(0)
            }
            _studyState.value = _studyState.value.copy(logs = logs)
        }
    }
}

internal class ChaoxingStudyRestrictionException(
    val statusCode: Int,
    cause: Throwable?,
) : Exception("Chaoxing study stopped by traffic restriction ($statusCode)", cause)

/** Pure scheduling rules kept separate so request-budget tests do not need Android. */
internal object CxVideoReportPolicy {
    const val DEFAULT_REPORT_INTERVAL_SEC: Int = 60
    const val MIN_REPORT_INTERVAL_SEC: Int = 60

    fun intervalSeconds(serverValue: Int): Int =
        (serverValue.takeIf { it > 0 } ?: DEFAULT_REPORT_INTERVAL_SEC)
            .coerceAtLeast(MIN_REPORT_INTERVAL_SEC)

    fun shouldReport(
        currentSec: Int,
        lastReportedSec: Int,
        durationSec: Int,
        finalReportSent: Boolean,
        intervalSec: Int,
    ): Boolean {
        if (durationSec <= 0 || finalReportSent) return false
        val atEnd = currentSec >= durationSec
        return atEnd || currentSec - lastReportedSec >= intervalSeconds(intervalSec)
    }
}
