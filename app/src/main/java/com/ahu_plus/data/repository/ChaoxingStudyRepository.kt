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
import com.ahu_plus.data.model.CxVideoInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val context: android.content.Context? = null,
) {
    companion object {
        private const val TAG = "CxStudy"
        private const val MAX_403_RETRY = 3
    }

    private val _studyState = MutableStateFlow(CxStudyUiState())
    val studyState: StateFlow<CxStudyUiState> = _studyState.asStateFlow()

    @Volatile
    private var shouldStop = false

    @Volatile
    private var studyJob: kotlinx.coroutines.Job? = null

    /** 停止学习 */
    fun stop() {
        shouldStop = true
        studyJob?.cancel()
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
        enabledTaskTypes: Set<String> = setOf("video", "document", "read", "workid", "audio", "live"),
    ) {
        // 保存当前协程的 Job,供 stop() 取消
        studyJob = kotlin.coroutines.coroutineContext[Job]
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

                    processChapter(course, chapter, speed, autoSubmit, enabledTaskTypes)
                }
            }

            addLog("所有课程学习任务已完成")

            // 自动签到(Phase 5, 2026-06-20)
            if (sessionManager.getCxAutoSign()) {
                addLog("自动签到: 扫描课程活动...")
                autoSignAllCourses(courses)
            }

            // 刷访问次数（学习完成后）
            if (sessionManager.getCxVisitBrushEnabled() && !shouldStop) {
                addLog("刷访问计数: 开始...")
                brushVisitCounts(courses, sessionManager.getCxVisitBrushInterval())
                addLog("刷访问计数: 完成")
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
                // 真实子类型以 preSign 响应为准(activelist 的 type 不可靠)
                val signType = cxRepo.preSign(course, act.id).getOrNull()?.signType ?: act.signType
                addLog("自动签到: ${act.name} (${signType.label})")
                val r = when (signType) {
                    com.ahu_plus.data.model.CxSignType.LOCATION -> {
                        if (lat >= 0 && lon >= 0) cxRepo.signInLocation(course, act.id, lat, lon, address)
                        else { addLog("  ⚠ 未配置经纬度,跳过"); continue }
                    }
                    com.ahu_plus.data.model.CxSignType.GESTURE -> {
                        if (gesture.isNotBlank()) cxRepo.signInGesture(course, act.id, gesture)
                        else { addLog("  ⚠ 未配置手势码,跳过"); continue }
                    }
                    // 拍照/二维码需即时交互,自动签到无法处理,跳过留给前台手动签
                    com.ahu_plus.data.model.CxSignType.PHOTO,
                    com.ahu_plus.data.model.CxSignType.QRCODE -> {
                        addLog("  ⚠ ${signType.label}需手动操作,请到签到中心处理"); continue
                    }
                    com.ahu_plus.data.model.CxSignType.SIGNCODE -> {
                        addLog("  ⚠ 签到码需手动输入,请到签到中心处理"); continue
                    }
                    else -> cxRepo.signNormal(course, act.id)
                }
                r.onSuccess { addLog("  ✓ 签到成功: $it") }
                r.onFailure { addLog("  ✗ 签到失败: ${it.message}") }
            }
        }
    }

    /**
     * 刷课程访问次数：遍历课程所有章节，间隔调用 API 模拟访问以提升"学习次数"统计。
     */
    private suspend fun brushVisitCounts(courses: List<CxCourse>, intervalSec: Int) {
        for (course in courses) {
            if (shouldStop) break
            val pointsResult = cxRepo.getCoursePoints(course)
            val points = pointsResult.getOrNull()?.points ?: continue
            for (chapter in points) {
                if (shouldStop) break
                if (chapter.needUnlock) continue
                cxRepo.brushVisitCount(course, chapter)
                addLog("    刷访问: ${chapter.title}")
                delay(intervalSec * 1000L)
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
        enabledTaskTypes: Set<String> = setOf("video", "document", "read", "workid", "audio", "live"),
    ) {
        studyJob = kotlin.coroutines.coroutineContext[Job]
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
        enabledTaskTypes: Set<String> = setOf("video", "document", "read", "workid", "audio", "live"),
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
            val done = if (chapter.jobCount > 0) {
                // 有任务点但全部已通过 → 视为已完成
                addLog("  所有任务已完成")
                chapter.jobCount
            } else {
                // 真正的空页面（无任务点）
                cxRepo.studyEmptyPage(course, chapter)
                addLog("  空页面任务完成")
                1
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
            val result = processJob(course, chapter, job, jobInfo, speed, autoSubmit)
            // 仅真正完成的任务计入完成数（失败/仅保存未提交/受限不计）
            if (result.isSuccess()) succeededCount++
        }

        // 自动下载章节资源（如已启用）
        if (sessionManager.getCxDownloadEnabled() && context != null && !shouldStop) {
            val resources = cxRepo.getCourseResources(course, chapter).getOrNull() ?: emptyList()
            for (res in resources) {
                if (shouldStop) break
                addLog("    下载: ${res.name}")
                cxRepo.downloadResource(context, res.preview, res.name).onSuccess { path ->
                    addLog("    ✓ 已保存: $path")
                }.onFailure { e ->
                    addLog("    ✗ 下载失败: ${e.message}")
                }
            }
        }

        synchronized(_studyState) {
            _studyState.value = _studyState.value.copy(
                completedCount = _studyState.value.completedCount + prePassedCount + succeededCount
            )
        }
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
        val result = when (job.type) {
            "video" -> studyVideo(course, job, jobInfo, speed)
            "document" -> {
                cxRepo.studyDocument(course, job, jobInfo).fold(
                    onSuccess = { CxStudyResult.SUCCESS },
                    onFailure = { e -> failMsg = e.message ?: "文档任务失败"; addLog("    $failMsg"); CxStudyResult.ERROR },
                )
            }
            "read" -> {
                cxRepo.studyRead(course, job, jobInfo).fold(
                    onSuccess = { CxStudyResult.SUCCESS },
                    onFailure = { e -> failMsg = e.message ?: "阅读任务失败"; addLog("    $failMsg"); CxStudyResult.ERROR },
                )
            }
            "audio" -> {
                cxRepo.studyAudio(course, job, jobInfo).fold(
                    onSuccess = { CxStudyResult.SUCCESS },
                    onFailure = { e -> failMsg = e.message ?: "音频任务失败"; addLog("    $failMsg"); CxStudyResult.ERROR },
                )
            }
            "live" -> {
                cxRepo.studyLive(course, job, jobInfo).fold(
                    onSuccess = { CxStudyResult.SUCCESS },
                    onFailure = { e -> failMsg = e.message ?: "直播任务失败"; addLog("    $failMsg"); CxStudyResult.ERROR },
                )
            }
            "workid" -> studyWork(course, job, jobInfo, autoSubmit)
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
        // 跳过类(如答题仅保存未提交)的提示消息
        val uiMsg = when {
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
            addLog("    获取视频信息失败: ${infoResult.exceptionOrNull()?.message}")
            return CxStudyResult.ERROR
        }
        val videoInfo = infoResult.getOrNull()!!
        val duration = videoInfo.duration      // 秒
        var dtoken = videoInfo.dtoken

        // 原仓库: play_time = int(_job["playTime"]) // 1000 （毫秒→秒）
        val serverPlayTime = job.playTime / 1000
        addLog("    视频时长: ${duration}s, 服务器记录: ${serverPlayTime}s")

        // 2. 原仓库: 先用 isdrag=4 试瞬间完成
        val (firstPassed, _) = videoProgressLog(course, job, jobInfo, dtoken, duration, duration, isdrag = 4)
        if (firstPassed) {
            addLog("    视频瞬间完成")
            return CxStudyResult.SUCCESS
        }

        // 3. 确定起始进度：未完成 → 回退到 10% 重走渐进上报
        val startSec = if (serverPlayTime >= duration) (duration * 0.1).toInt() else serverPlayTime
        if (serverPlayTime >= duration) addLog("    瞬间未通过，从 10% 重新渐进上报")
        var playTime = startSec.toDouble()      // Double 累积，对齐 Python float

        // 4. 主循环（原仓库: while not passed）
        var lastLogTime = startSec
        var lastIter = System.nanoTime()
        // 视频心跳间隔:从 30~90s 改为 5~10s。
        // 原因:真实用户 1.0x 看视频每 5~10s 一次进度上报,30~90s 跳进度被反作弊识别为
        // "跳着看"+配合 isdrag=4 = 典型工具指纹;改 5~10s 后 playingTime 增量也变小,
        // 单 IP 时间聚类曲线接近真人。
        var waitTime = Random.nextInt(5, 11)
        var forbiddenRetry = 0
        var reportCount = 0

        while (true) {
            if (shouldStop) return CxStudyResult.ERROR

            // 时间推进 = 真实流逝时间 × 倍速
            val nowNs = System.nanoTime()
            playTime += (nowNs - lastIter).toDouble() / 1_000_000_000.0 * speed
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

            // 上报条件：距上次上报超过间隔，或已播放完毕（原仓库: play_time == duration）
            val atEnd = curSec >= duration
            if (curSec - lastLogTime >= waitTime || atEnd) {
                reportCount++
                addLog("    [#$reportCount] 上报 ${curSec}s / ${duration}s")
                val (passed, code) = videoProgressLog(
                    course, job, jobInfo, dtoken, duration, curSec,
                    isdrag = if (curSec >= duration) 4 else 3,
                )
                if (passed) {
                    addLog("    ✓ 视频任务完成")
                    return CxStudyResult.SUCCESS
                }
                if (code == 403) {
                    if (forbiddenRetry >= MAX_403_RETRY) {
                        addLog("    403 重试失败，跳过")
                        return CxStudyResult.FORBIDDEN
                    }
                    forbiddenRetry++
                    addLog("    403 错误，刷新 dtoken (${forbiddenRetry}/$MAX_403_RETRY)")
                    delay(2000L * (1 shl (forbiddenRetry - 1))) // 指数退避: 2s→4s→8s
                    val refreshed = _refreshVideoStatus(job.objectid)
                    if (refreshed != null) {
                        dtoken = refreshed.dtoken
                        continue
                    }
                }
                if (code != 200) {
                    addLog("    上报异常 code=$code")
                    return CxStudyResult.ERROR
                }
                // 每次上报后重置下一次间隔,同样 5~10s(同 L464)
                waitTime = Random.nextInt(5, 11)
                lastLogTime = curSec
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
        // 原仓库限速 2s 一次
        delay(Random.nextLong(500, 2500))

        if ("courseId" in job.otherinfo) {
            addLog("    otherinfo 包含 courseId，异常")
            return Pair(false, 500)
        }

        val result = cxRepo.reportVideoProgress(course, job, jobInfo, dtoken, duration, playingTime, isdrag)
        return result.fold(
            onSuccess = { Pair(it, 200) },
            onFailure = { e ->
                val msg = e.message ?: ""
                when {
                    msg.contains("403") -> Pair(false, 403)
                    else -> Pair(false, 500)
                }
            },
        )
    }

    /**
     * 原仓库 _refresh_video_status 的移植。
     * 刷新视频状态（获取最新的 dtoken / duration / playTime）。
     */
    private suspend fun _refreshVideoStatus(objectId: String): CxVideoInfo? {
        return cxRepo.getVideoInfo(objectId).getOrNull()
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

        val shouldSubmit = autoSubmit && coverage >= 0.8f

        // 4. 两步提交（模拟浏览器：先保存，再提交）
        if (shouldSubmit) {
            // 第一步：保存答案 (pyFlag="1")
            val saveResult = cxRepo.submitWork(workData.copy(formFields = formFields, pyFlag = "1"))
            if (saveResult.isFailure) {
                addLog("    保存失败: ${saveResult.exceptionOrNull()?.message}")
                return CxStudyResult.ERROR
            }
            addLog("    ✓ 保存成功")
            delay(2000) // 等待服务器处理保存完成后再提交
            // 第二步：提交 (pyFlag="")
            val submitResult = cxRepo.submitWork(workData.copy(formFields = formFields, pyFlag = ""))
            if (submitResult.isFailure) {
                addLog("    提交失败: ${submitResult.exceptionOrNull()?.message}")
                return CxStudyResult.ERROR
            }
            addLog("    ✓ 提交成功: ${submitResult.getOrNull()}")
            return CxStudyResult.SUCCESS
        } else {
            // 覆盖率不足或关闭自动提交：仅保存草稿，未真正提交（任务未完成）
            val submitData = workData.copy(formFields = formFields, pyFlag = "1")
            val submitResult = cxRepo.submitWork(submitData)
            submitResult.onFailure { e ->
                addLog("    保存失败: ${e.message}")
                return CxStudyResult.ERROR
            }
            val reason = if (!autoSubmit) "已关闭自动提交" else "题库覆盖率 ${(coverage * 100).toInt()}% < 80%"
            addLog("    ⚠ 仅保存未提交（$reason），需手动确认")
            // 返回 SKIPPED：UI 标记为"未提交"，不计入完成数
            return CxStudyResult.SKIPPED
        }
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
    private fun generateRandomAnswer(q: com.ahu_plus.data.model.CxQuestion): String {
        return when (q.type) {
            "single" -> {
                val opts = q.options.split("\n").filter { it.isNotBlank() }
                if (opts.isNotEmpty()) opts.random().take(1) else ""
            }
            "multiple" -> generateWeightedMultipleAnswer(q)
            "judgement" -> if (Random.nextBoolean()) "true" else "false"
            "completion" -> "暂未作答"
            "shortanswer" -> "暂未作答"
            else -> ""
        }
    }

    private fun generateWeightedMultipleAnswer(q: com.ahu_plus.data.model.CxQuestion): String {
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
        synchronized(_studyState) {
            val logs = _studyState.value.logs.toMutableList().apply {
                add(msg)
                if (size > 200) removeAt(0)
            }
            _studyState.value = _studyState.value.copy(logs = logs)
        }
    }
}
