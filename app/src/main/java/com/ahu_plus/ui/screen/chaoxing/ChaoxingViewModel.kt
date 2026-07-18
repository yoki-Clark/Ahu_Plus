package com.ahu_plus.ui.screen.chaoxing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.CxActivity
import com.ahu_plus.data.model.CxChapter
import com.ahu_plus.data.model.CxCourse
import com.ahu_plus.data.model.CxCoursePoints
import com.ahu_plus.data.model.CxCourseProgress
import com.ahu_plus.data.model.CxHomeworkItem
import com.ahu_plus.data.model.CxHomeworkListState
import com.ahu_plus.data.model.CxHomeworkDetailState
import com.ahu_plus.data.model.CxJob
import com.ahu_plus.data.model.CxJobInfo
import com.ahu_plus.data.model.CxMessage
import com.ahu_plus.data.model.CxMessageSource
import com.ahu_plus.data.model.CxSignType
import com.ahu_plus.data.model.CxStudyUiState
import com.ahu_plus.data.model.CxWorkData
import com.ahu_plus.data.repository.ChaoxingRepository
import com.ahu_plus.data.repository.CxAnswerMode
import com.ahu_plus.data.repository.ChaoxingStudyRepository
import com.ahu_plus.data.repository.ChaoxingTikuRepository
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal fun providerChainForTikuType(tikuType: String): String =
    if (tikuType == "DISABLED") "DISABLED" else "CACHE,$tikuType"

class ChaoxingViewModel(
    val cxRepo: ChaoxingRepository,
    private val studyRepo: ChaoxingStudyRepository,
    val tikuRepo: ChaoxingTikuRepository,
    val sessionManager: SessionManager,
) : ViewModel() {

    private val gson = GsonProvider.instance

    // ── 登录状态 ────────────────────────────────────────────
    private val _loginState = MutableStateFlow(CxLoginState())
    val loginState: StateFlow<CxLoginState> = _loginState.asStateFlow()

    // ── 课程列表 + 多选 ─────────────────────────────────────
    private val _coursesState = MutableStateFlow(CxCoursesState())
    val coursesState: StateFlow<CxCoursesState> = _coursesState.asStateFlow()

    // ── 课程详情 ────────────────────────────────────────────
    private val _detailState = MutableStateFlow(CxDetailState())
    val detailState: StateFlow<CxDetailState> = _detailState.asStateFlow()

    // ── 签到中心 (Phase 3, 2026-06-20) ─────────────────────
    private val _signState = MutableStateFlow(CxSignState())
    val signState: StateFlow<CxSignState> = _signState.asStateFlow()

    // ── 即时签到流程 (2026-06-24):点"签到"→检索进行中活动→按类型弹对话框 ──
    private val _signFlowState = MutableStateFlow(CxSignFlowState())
    val signFlowState: StateFlow<CxSignFlowState> = _signFlowState.asStateFlow()

    // ── 题库测试结果 (2026-06-20) ──────────────────────────
    private val _tikuTestResult = MutableStateFlow<String?>(null)
    val tikuTestResult: StateFlow<String?> = _tikuTestResult.asStateFlow()

    // ── 学习进度 ────────────────────────────────────────────
    val studyState: StateFlow<CxStudyUiState> = studyRepo.studyState

    // ── 设置 ────────────────────────────────────────────────
    private val _settingsState = MutableStateFlow(CxSettingsState())
    val settingsState: StateFlow<CxSettingsState> = _settingsState.asStateFlow()

    // ── 消息中心 ─────────────────────────────────────────────
    private val _messagesState = MutableStateFlow(CxMessagesState())
    val messagesState: StateFlow<CxMessagesState> = _messagesState.asStateFlow()

    // ── 课程作业 (2026-06-22) ─────────────────────────────────
    private val _homeworkState = MutableStateFlow(CxHomeworkListState())
    val homeworkState: StateFlow<CxHomeworkListState> = _homeworkState.asStateFlow()

    private val _homeworkDetailState = MutableStateFlow(CxHomeworkDetailState())
    val homeworkDetailState: StateFlow<CxHomeworkDetailState> = _homeworkDetailState.asStateFlow()
    private var homeworkDetailJob: Job? = null
    private var homeworkDetailRequestId = 0L
    private var homeworkDetailWorkId: String? = null

    /** ViewModel-level admission control for UI re-entry and recomposition. */
    private companion object {
        const val COURSES_TTL_MS = 5 * 60 * 1000L
        const val MESSAGES_TTL_MS = 2 * 60 * 1000L
        const val HOMEWORK_TTL_MS = 5 * 60 * 1000L
        const val CHAPTER_JOBS_TTL_MS = 10 * 60 * 1000L
        const val SIGN_ACTIVITIES_TTL_MS = 60 * 1000L
        const val FAILURE_COOLDOWN_MS = 30 * 1000L
        const val AUTH_FAILURE_COOLDOWN_MS = 15 * 1000L
    }

    private val requestLock = Any()
    private var authJob: Job? = null
    private var authFailureAt = 0L
    private var coursesJob: Job? = null
    private var coursesLoadedAt = 0L
    private var coursesFailureAt = 0L
    private var messagesJob: Job? = null
    private var messagesMoreJob: Job? = null
    private var messagesLoadedAt = 0L
    private var messagesActivitiesLoadedAt = 0L
    private var messagesFailureAt = 0L
    private var messagesPendingForce = false
    private var messagesPendingActivities = false
    private var homeworkJob: Job? = null
    private var homeworkLoadedAt = 0L
    private var homeworkFailureAt = 0L
    private var signActivitiesJob: Job? = null
    private var signActivitiesLoadedAt = 0L
    private var signActivitiesFailureAt = 0L
    private var signFlowJob: Job? = null
    private var signSubmitJob: Job? = null
    private var signFlowGeneration = 0L
    private val signJobsInFlight = mutableMapOf<Long, Job>()
    private val signFailureAt = mutableMapOf<Long, Long>()
    private val photoObjectIds = mutableMapOf<Long, String>()
    private val chapterJobsInFlight = mutableMapOf<String, Job>()
    private val chapterJobsLoadedAt = mutableMapOf<String, Long>()
    private val chapterFailureAt = mutableMapOf<String, Long>()

    private fun nowMs(): Long = System.currentTimeMillis()

    private fun inWindow(timestamp: Long, windowMs: Long, now: Long = nowMs()): Boolean =
        timestamp > 0L && now - timestamp < windowMs

    private fun beginHomeworkDetailRequest(workId: String): Long {
        homeworkDetailJob?.cancel()
        homeworkDetailWorkId = workId
        return ++homeworkDetailRequestId
    }

    private fun invalidateHomeworkDetailRequests() {
        homeworkDetailJob?.cancel()
        homeworkDetailJob = null
        homeworkDetailWorkId = null
        homeworkDetailRequestId++
    }

    private fun isCurrentHomeworkDetailRequest(requestId: Long, workId: String): Boolean {
        return requestId == homeworkDetailRequestId && homeworkDetailWorkId == workId
    }

    init {
        cxRepo.loadPersistedCookies()
        loadSettings()
    }

    // ════════════════════════════════════════════════════════
    //  设置
    // ════════════════════════════════════════════════════════

    private fun loadSettings() {
        _settingsState.value = CxSettingsState(
            speed = sessionManager.getCxSpeed(),
            concurrency = sessionManager.getCxConcurrency(),
            notOpenAction = sessionManager.getCxNotopenAction(),
            autoSign = sessionManager.getCxAutoSign(),
            submitMode = sessionManager.getCxSubmitMode(),
            tikuType = sessionManager.getCxTikuType(),
            tikuToken = sessionManager.getCxTokensYanxi().ifEmpty { sessionManager.getCxTikuToken() },
            aiApiKey = sessionManager.getCxAiKey(),
            aiBaseUrl = sessionManager.getCxAiBaseUrl().ifBlank { "https://api.deepseek.com" },
            aiModel = sessionManager.getCxAiModel().let { model ->
                // 2026-06-21: deepseek-chat/reasoner 弃用 → v4-flash
                if (model in listOf("deepseek-chat", "deepseek-reasoner")) {
                    viewModelScope.launch { sessionManager.saveCxAiModel("deepseek-v4-flash") }
                    "deepseek-v4-flash"
                } else model
            },
            enabledTaskTypes = sessionManager.getCxTaskTypes(),
            messagesMergeInbox = sessionManager.getCxMessagesMerge(),
            showOverlay = true,
            hideEndedCourses = sessionManager.getCxHideEndedCourses(),
            hiddenCourseKeys = sessionManager.getCxHiddenCourses(),
            visitBrushEnabled = sessionManager.getCxVisitBrushEnabled(),
            visitBrushInterval = sessionManager.getCxVisitBrushInterval(),
            downloadEnabled = sessionManager.getCxDownloadEnabled(),
        )
        applyTikuConfig()
    }

    fun updateSpeed(v: Float) {
        _settingsState.value = _settingsState.value.copy(speed = 1.0f)
        viewModelScope.launch { sessionManager.saveCxSpeed(v) }
    }

    fun updateConcurrency(v: Int) {
        _settingsState.value = _settingsState.value.copy(concurrency = 1)
        viewModelScope.launch { sessionManager.saveCxConcurrency(v) }
    }

    fun updateNotOpenAction(v: String) {
        _settingsState.value = _settingsState.value.copy(notOpenAction = "continue")
        viewModelScope.launch { sessionManager.saveCxNotopenAction("continue") }
    }

    fun updateAutoSign(v: Boolean) {
        _settingsState.value = _settingsState.value.copy(autoSign = false)
        viewModelScope.launch { sessionManager.saveCxAutoSign(false) }
    }

    fun updateSubmitMode(v: String) {
        _settingsState.value = _settingsState.value.copy(submitMode = v)
        viewModelScope.launch { sessionManager.saveCxSubmitMode(v) }
    }

    fun updateTikuType(v: String) {
        _settingsState.value = _settingsState.value.copy(tikuType = v)
        viewModelScope.launch { sessionManager.saveCxTikuType(v) }
        applyTikuConfig()
    }

    fun updateTikuToken(v: String) {
        _settingsState.value = _settingsState.value.copy(tikuToken = v)
        viewModelScope.launch { sessionManager.saveCxTikuToken(v) }
        applyTikuConfig()
    }

    /**
     * 更新言溪 Token（统一使用 cx_tokens_yanxi key，设置页和 TikuScreen 共用）。
     * 2026-06-21 修复：设置页之前存的 cx_tiku_token 未被 applyTikuConfig 读取。
     */
    fun updateCxTokensYanxi(v: String) {
        _settingsState.value = _settingsState.value.copy(tikuToken = v)
        viewModelScope.launch { sessionManager.saveCxTokensYanxi(v) }
        applyTikuConfig()
    }

    fun updateAiApiKey(v: String) {
        _settingsState.value = _settingsState.value.copy(aiApiKey = v)
        viewModelScope.launch { sessionManager.saveCxAiKey(v) }
        applyTikuConfig()
    }

    fun updateAiBaseUrl(v: String) {
        _settingsState.value = _settingsState.value.copy(aiBaseUrl = v)
        viewModelScope.launch { sessionManager.saveCxAiBaseUrl(v) }
        applyTikuConfig()
    }

    fun updateAiModel(v: String) {
        _settingsState.value = _settingsState.value.copy(aiModel = v)
        viewModelScope.launch { sessionManager.saveCxAiModel(v) }
        applyTikuConfig()
    }

    fun updateMessagesMergeInbox(v: Boolean) {
        _settingsState.value = _settingsState.value.copy(messagesMergeInbox = v)
        viewModelScope.launch { sessionManager.saveCxMessagesMerge(v) }
        if (v) {
            viewModelScope.launch {
                val pending = synchronized(requestLock) { messagesJob }
                pending?.join()
                loadMessages(includeActivities = true)
            }
        }
    }

    fun updateShowOverlay(v: Boolean) {
        _settingsState.value = _settingsState.value.copy(showOverlay = v)
        sessionManager.showStudyOverlay = v
    }

    fun updateHideEndedCourses(v: Boolean) {
        _settingsState.value = _settingsState.value.copy(hideEndedCourses = v)
        viewModelScope.launch { sessionManager.saveCxHideEndedCourses(v) }
        // 重新加载作业列表以应用过滤
        if (_coursesState.value.courses.isNotEmpty()) loadHomework(force = true)
    }

    /** 切换单门课程的隐藏状态。key = "${courseId}_${clazzId}" */
    fun toggleHiddenCourse(courseKey: String) {
        val current = _settingsState.value.hiddenCourseKeys.toMutableSet()
        if (current.contains(courseKey)) current.remove(courseKey) else current.add(courseKey)
        _settingsState.value = _settingsState.value.copy(hiddenCourseKeys = current)
        viewModelScope.launch { sessionManager.saveCxHiddenCourses(current) }
        // 过滤课程列表和作业列表
        applyHiddenCourseFilter()
        loadHomework(force = true)
    }

    /** 批量更新隐藏课程 */
    fun updateHiddenCourses(keys: Set<String>) {
        _settingsState.value = _settingsState.value.copy(hiddenCourseKeys = keys)
        viewModelScope.launch { sessionManager.saveCxHiddenCourses(keys) }
        applyHiddenCourseFilter()
        loadHomework(force = true)
    }

    /** 对已加载的课程列表应用隐藏过滤（从 allCourses 重新计算 visible courses） */
    private fun applyHiddenCourseFilter() {
        val all = _coursesState.value.allCourses
        if (all.isEmpty()) return
        val hidden = _settingsState.value.hiddenCourseKeys
        _coursesState.value = _coursesState.value.copy(
            courses = all.filter { "${it.courseId}_${it.clazzId}" !in hidden },
        )
    }

    fun updateVisitBrushEnabled(v: Boolean) {
        _settingsState.value = _settingsState.value.copy(visitBrushEnabled = false)
        viewModelScope.launch { sessionManager.saveCxVisitBrushEnabled(false) }
    }

    fun updateVisitBrushInterval(v: Int) {
        _settingsState.value = _settingsState.value.copy(visitBrushInterval = 30)
        viewModelScope.launch { sessionManager.saveCxVisitBrushInterval(30) }
    }

    fun updateDownloadEnabled(v: Boolean) {
        _settingsState.value = _settingsState.value.copy(downloadEnabled = v)
        viewModelScope.launch { sessionManager.saveCxDownloadEnabled(v) }
    }

    fun toggleTaskType(type: String) {
        val current = _settingsState.value.enabledTaskTypes.toMutableSet()
        if (current.contains(type)) current.remove(type) else current.add(type)
        _settingsState.value = _settingsState.value.copy(enabledTaskTypes = current)
        viewModelScope.launch { sessionManager.saveCxTaskTypes(current) }
    }

    private fun applyTikuConfig() {
        viewModelScope.launch {
            val s = _settingsState.value
            // ★ 关键修复: 从 tikuType 派生 providerChain,不再读 sessionManager 的旧缓存
            val chainStr = providerChainForTikuType(s.tikuType)
            Log.d("CxVM", "applyTikuConfig: tikuType=${s.tikuType}, chain=$chainStr")
            tikuRepo.configure(
                providerChainStr = chainStr,
                yanxiTokensStr = sessionManager.getCxTokensYanxi().ifEmpty { sessionManager.getCxTikuToken() },
                yanxiDelay = sessionManager.getCxTikuDelay(),
                coverRate = sessionManager.getCxCoverRate(),
                aiApiKey = sessionManager.getCxAiKey().ifEmpty { s.aiApiKey },
                aiBaseUrl = sessionManager.getCxAiBaseUrl().ifEmpty { s.aiBaseUrl },
                aiModel = sessionManager.getCxAiModel().ifEmpty { s.aiModel },
                aiMinInterval = sessionManager.getCxAiMinInterval(),
                siliconflowKey = sessionManager.getCxSiliconflowKey(),
                siliconflowModel = sessionManager.getCxSiliconflowModel(),
                siliconflowEndpoint = sessionManager.getCxSiliconflowEndpoint(),
                likeapiSearch = sessionManager.getCxLikeapiSearch(),
                likeapiVision = sessionManager.getCxLikeapiVision(),
                likeapiModel = sessionManager.getCxLikeapiModel(),
                goAuthorization = sessionManager.getCxGoAuthorization(),
                goMinInterval = sessionManager.getCxGoMinInterval(),
                tikuAdapterUrl = sessionManager.getCxTikuAdapterUrl(),
            )
        }
    }

    fun testLlmConnection() {
        viewModelScope.launch {
            val r = tikuRepo.checkLlmConnection()
            r.onSuccess { msg ->
                _tikuTestResult.value = "✓ LLM 连接测试成功: $msg"
            }
            r.onFailure { e ->
                _tikuTestResult.value = "✗ LLM 连接失败: ${e.message}"
            }
        }
    }

    fun reorderProviderChain(newOrder: List<ChaoxingTikuRepository.TikuType>) {
        val chain = newOrder.joinToString(",") { it.name }
        viewModelScope.launch { sessionManager.saveCxProviderChain(chain) }
        applyTikuConfig()
    }

    // ════════════════════════════════════════════════════════
    //  登录
    // ════════════════════════════════════════════════════════

    fun login(username: String, password: String) {
        synchronized(requestLock) {
            if (authJob?.isActive == true) return
            if (inWindow(authFailureAt, AUTH_FAILURE_COOLDOWN_MS)) {
                _loginState.value = CxLoginState(error = "认证请求暂在冷却中，请稍后再试")
                return
            }
            val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _loginState.value = CxLoginState(isLoading = true)
            val result = cxRepo.login(username, password)
            if (result.isFailure) {
                authFailureAt = nowMs()
                _loginState.value = CxLoginState(error = result.exceptionOrNull()?.message ?: "登录失败")
                return@launch
            }

            when (val validation = cxRepo.validateSessionResult()) {
                Result.success(true) -> {
                    // 保存凭据供用户明确同意的会话续期使用。
                    sessionManager.saveCxCredentials(username, password)
                    val shouldWarn = !sessionManager.getCxLoginWarningShown()
                    _loginState.value = CxLoginState(isLoggedIn = true, showLoginWarning = shouldWarn)
                    loadCourses()
                }
                else -> {
                    authFailureAt = nowMs()
                    _loginState.value = CxLoginState(
                        error = if (validation.isSuccess) {
                            "登录成功但会话已过期"
                        } else {
                            validation.exceptionOrNull()?.message ?: "会话验证失败，请稍后重试"
                        },
                    )
                }
            }
            }
            authJob = job
            job.invokeOnCompletion {
                synchronized(requestLock) {
                    if (authJob === job) authJob = null
                }
            }
            job.start()
        }
    }

    fun checkLogin() {
        synchronized(requestLock) {
            if (authJob?.isActive == true) return
            if (inWindow(authFailureAt, AUTH_FAILURE_COOLDOWN_MS)) {
                _loginState.value = CxLoginState(error = "认证请求暂在冷却中，请稍后再试")
                return
            }
            val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            if (!cxRepo.isLoggedIn()) return@launch

            when (val validation = cxRepo.validateSessionResult()) {
                Result.success(true) -> {
                    val shouldWarn = !sessionManager.getCxLoginWarningShown()
                    _loginState.value = CxLoginState(isLoggedIn = true, showLoginWarning = shouldWarn)
                    loadCourses()
                }
                Result.success(false) -> {
                    // Only an explicit login-page result permits credential replay.
                    if (!sessionManager.hasCxCredentials()) {
                        authFailureAt = nowMs()
                        _loginState.value = CxLoginState(error = "会话已过期，请重新登录")
                        return@launch
                    }
                    _loginState.value = CxLoginState(isLoading = true)
                    if (cxRepo.autoLogin()) {
                        val shouldWarn = !sessionManager.getCxLoginWarningShown()
                        _loginState.value = CxLoginState(isLoggedIn = true, showLoginWarning = shouldWarn)
                        loadCourses()
                    } else {
                        authFailureAt = nowMs()
                        _loginState.value = CxLoginState(error = "自动登录失败，请手动登录")
                    }
                }
                else -> {
                    // Network/risk-control failures must not trigger credential replay.
                    authFailureAt = nowMs()
                    _loginState.value = CxLoginState(
                        error = validation.exceptionOrNull()?.message ?: "会话验证失败，请稍后重试",
                    )
                }
            }
            }
            authJob = job
            job.invokeOnCompletion {
                synchronized(requestLock) {
                    if (authJob === job) authJob = null
                }
            }
            job.start()
        }
    }

    fun logout() {
        synchronized(requestLock) {
            authJob?.cancel()
            authJob = null
            authFailureAt = 0L
            coursesJob?.cancel()
            messagesJob?.cancel()
            messagesMoreJob?.cancel()
            homeworkJob?.cancel()
            signActivitiesJob?.cancel()
            signFlowJob?.cancel()
            signSubmitJob?.cancel()
            signJobsInFlight.values.forEach { it.cancel() }
            chapterJobsInFlight.values.forEach { it.cancel() }
            signJobsInFlight.clear()
            chapterJobsInFlight.clear()
            coursesLoadedAt = 0L
            coursesFailureAt = 0L
            messagesLoadedAt = 0L
            messagesActivitiesLoadedAt = 0L
            messagesFailureAt = 0L
            messagesPendingForce = false
            messagesPendingActivities = false
            homeworkLoadedAt = 0L
            homeworkFailureAt = 0L
            signActivitiesLoadedAt = 0L
            signActivitiesFailureAt = 0L
            signFlowJob = null
            chapterJobsLoadedAt.clear()
            chapterFailureAt.clear()
            signFailureAt.clear()
            photoObjectIds.clear()
        }
        invalidateHomeworkDetailRequests()
        viewModelScope.launch {
            cxRepo.clearCookies()
            sessionManager.clearCxCredentials()
            sessionManager.saveCxHomeworkJson("")  // 清除作业缓存
            sessionManager.saveCxHomeworkDetailJson("")
            _loginState.value = CxLoginState()
            _coursesState.value = CxCoursesState()
            _detailState.value = CxDetailState()
            _signState.value = CxSignState()
            _tikuTestResult.value = null
            _messagesState.value = CxMessagesState()
            _homeworkState.value = CxHomeworkListState()
            _homeworkDetailState.value = CxHomeworkDetailState()
        }
    }

    /**
     * 2026-06-23: 关闭首次登录警告弹窗。
     * 关闭后写回 SessionManager 标志位,下次登录不再弹。
     * 注意:退出登录时不应清除这个标志,否则再次登录会重新弹警告。
     */
    fun dismissLoginWarning() {
        _loginState.value = _loginState.value.copy(showLoginWarning = false)
        viewModelScope.launch { sessionManager.saveCxLoginWarningShown(true) }
    }

    // ════════════════════════════════════════════════════════
    //  消息中心
    // ════════════════════════════════════════════════════════

    /**
     * 加载消息（缓存优先 + 追加更新）。
     *
     * 1. 有缓存 → 立即显示，后台用 cursor 追加新通知 + 刷新活动状态
     * 2. 无缓存 → 全量拉取，写入缓存
     */
    fun loadMessages(
        force: Boolean = false,
        includeActivities: Boolean = _settingsState.value.messagesMergeInbox,
    ) {
        synchronized(requestLock) {
            if (messagesJob?.isActive == true) {
                messagesPendingForce = messagesPendingForce || force
                messagesPendingActivities = messagesPendingActivities || includeActivities
                return
            }
            val now = nowMs()
            val noticesFresh = inWindow(messagesLoadedAt, MESSAGES_TTL_MS, now)
            val activitiesFresh = !includeActivities || inWindow(messagesActivitiesLoadedAt, MESSAGES_TTL_MS, now)
            if (!force && noticesFresh && activitiesFresh) return
            if (!force && inWindow(messagesFailureAt, FAILURE_COOLDOWN_MS, now)) return
            _messagesState.value = _messagesState.value.copy(isLoading = true, error = null)
            val job = viewModelScope.launch {
                try {
            var courses = _coursesState.value.courses
            if (includeActivities && courses.isEmpty()) {
                val pendingCourses = synchronized(requestLock) { coursesJob }
                pendingCourses?.join()
                courses = _coursesState.value.courses
            }
            val activityOnly = !force && includeActivities && noticesFresh && !activitiesFresh
            if (activityOnly) {
                if (courses.isEmpty()) {
                    _messagesState.value = _messagesState.value.copy(isLoading = false)
                    return@launch
                }
                val activities = cxRepo.getActivityMessages(courses).getOrThrow()
                val current = _messagesState.value
                val merged = (current.messages.filter { it.source == CxMessageSource.NOTICE } + activities)
                    .distinctBy { "${it.source}:${it.id}" }
                    .sortedByDescending { it.time }
                _messagesState.value = current.copy(isLoading = false, messages = merged, error = null)
                sessionManager.saveCxMessagesJson(gson.toJson(merged), current.lastNoticeId)
                messagesActivitiesLoadedAt = nowMs()
                messagesFailureAt = 0L
                return@launch
            }
            val fetchedActivities = includeActivities && courses.isNotEmpty()
            val cachedJson = sessionManager.getCxMessagesJson()
            val cachedCursor = sessionManager.getCxMessagesCursor()

            // ── 有缓存：先显示，后台追加 ──────────────────────
            if (!cachedJson.isNullOrBlank()) {
                val cached = try {
                    gson.fromJson(cachedJson, Array<CxMessage>::class.java).toList()
                } catch (_: Exception) { emptyList() }

                if (cached.isNotEmpty()) {
                    // 立即显示缓存
                    _messagesState.value = CxMessagesState(
                        isLoading = false,
                        messages = cached.sortedByDescending { it.time },
                        lastNoticeId = cachedCursor,
                        hasMore = cachedCursor.isNotBlank(),
                    )

                    // 后台追加：新通知(cursor) + 活动刷新
                    val existingNoticeIds = cached.filter { it.source == CxMessageSource.NOTICE }.map { it.id }.toSet()

                    val noticeResult = cxRepo.getNoticeList(cachedCursor)
                    val activityResult = if (fetchedActivities) {
                        cxRepo.getActivityMessages(courses)
                    } else {
                        Result.success(emptyList())
                    }
                    noticeResult.exceptionOrNull()?.let { throw it }
                    activityResult.exceptionOrNull()?.let { throw it }

                    val newNotices = (noticeResult.getOrNull()?.first ?: emptyList())
                        .filter { it.id !in existingNoticeIds }
                    val nextCursor = noticeResult.getOrNull()?.second ?: cachedCursor
                    val activities = activityResult.getOrNull() ?: emptyList()

                    // 合并：旧通知 + 新通知 + 最新活动（活动按 id 去重覆盖）
                    val actIds = activities.map { it.id }.toSet()
                    val oldActivities = cached.filter { it.source == CxMessageSource.ACTIVITY && it.id !in actIds }
                    val merged = (cached.filter { it.source == CxMessageSource.NOTICE } + newNotices + oldActivities + activities)
                        .sortedByDescending { it.time }

                    _messagesState.value = CxMessagesState(
                        isLoading = false,
                        messages = merged,
                        lastNoticeId = nextCursor,
                        hasMore = newNotices.isNotEmpty() || nextCursor.isNotBlank(),
                    )
                    sessionManager.saveCxMessagesJson(gson.toJson(merged), nextCursor)
                    messagesLoadedAt = nowMs()
                    if (fetchedActivities) messagesActivitiesLoadedAt = nowMs()
                    messagesFailureAt = 0L
                    return@launch
                }
            }

            // ── 无缓存：全量拉取 ─────────────────────────────
            val noticeResult = cxRepo.getNoticeList()
            val activityResult = if (fetchedActivities) {
                cxRepo.getActivityMessages(courses)
            } else {
                Result.success(emptyList())
            }
            noticeResult.exceptionOrNull()?.let { throw it }
            activityResult.exceptionOrNull()?.let { throw it }

            val notices = noticeResult.getOrNull()?.first ?: emptyList()
            val nextCursor = noticeResult.getOrNull()?.second ?: ""
            val activities = activityResult.getOrNull() ?: emptyList()

            val merged = (notices + activities).sortedByDescending { it.time }

            _messagesState.value = CxMessagesState(
                isLoading = false,
                messages = merged,
                lastNoticeId = nextCursor,
                hasMore = notices.isNotEmpty() || nextCursor.isNotBlank(),
            )
            sessionManager.saveCxMessagesJson(gson.toJson(merged), nextCursor)
            messagesLoadedAt = nowMs()
            if (fetchedActivities) messagesActivitiesLoadedAt = nowMs()
            messagesFailureAt = 0L
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    messagesFailureAt = nowMs()
                    _messagesState.value = _messagesState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载消息失败",
                    )
                } finally {
                    val pending = synchronized(requestLock) {
                        messagesJob = null
                        val next = messagesPendingForce to messagesPendingActivities
                        messagesPendingForce = false
                        messagesPendingActivities = false
                        next
                    }
                    if (pending.first || pending.second) {
                        loadMessages(force = pending.first, includeActivities = pending.second)
                    }
                }
            }
            messagesJob = job
        }
    }

    /**
     * 加载更多收件箱通知（游标分页）。
     */
    fun loadMoreMessages() {
        val current = _messagesState.value
        if (current.isLoadingMore || !current.hasMore || current.lastNoticeId.isBlank()) return
        synchronized(requestLock) {
            if (messagesJob?.isActive == true || messagesMoreJob?.isActive == true) return
            val job = viewModelScope.launch {
                try {
            _messagesState.value = current.copy(isLoadingMore = true)

            val result = cxRepo.getNoticeList(current.lastNoticeId)
            result.onSuccess { (newNotices, nextCursor) ->
                val existingIds = current.messages.map { it.id }.toSet()
                val fresh = newNotices.filter { it.id !in existingIds }

                val allMessages = (current.messages + fresh).sortedByDescending { it.time }

                _messagesState.value = _messagesState.value.copy(
                    isLoadingMore = false,
                    messages = allMessages,
                    lastNoticeId = nextCursor,
                    hasMore = fresh.isNotEmpty() || nextCursor.isNotBlank(),
                )
                // 更新缓存
                sessionManager.saveCxMessagesJson(gson.toJson(allMessages), nextCursor)
            }
            result.onFailure { e ->
                _messagesState.value = _messagesState.value.copy(
                    isLoadingMore = false,
                    error = "加载更多失败: ${e.message}",
                )
            }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _messagesState.value = _messagesState.value.copy(
                        isLoadingMore = false,
                        error = e.message ?: "加载更多失败",
                    )
                } finally {
                    messagesMoreJob = null
                }
            }
            messagesMoreJob = job
        }
    }

    // ════════════════════════════════════════════════════════
    //  消息已读标记
    // ════════════════════════════════════════════════════════

    /** 标记消息为已读（本地追踪，不影响 API 状态） */
    fun markMessageAsRead(messageId: String) {
        val current = _messagesState.value
        if (messageId !in current.readMessageIds) {
            _messagesState.value = current.copy(
                readMessageIds = current.readMessageIds + messageId
            )
        }
    }

    // ════════════════════════════════════════════════════════
    //  课程列表 + 多选
    // ════════════════════════════════════════════════════════

    fun loadCourses(force: Boolean = false) {
        synchronized(requestLock) {
            if (coursesJob?.isActive == true) return
            val now = nowMs()
            if (!force && inWindow(coursesLoadedAt, COURSES_TTL_MS, now)) return
            if (!force && inWindow(coursesFailureAt, FAILURE_COOLDOWN_MS, now)) return
            val job = viewModelScope.launch {
                try {
            _coursesState.value = _coursesState.value.copy(isLoading = true, error = null)
            val result = cxRepo.getCourseList(forceRefresh = force)
            result.onSuccess { courses ->
                val hidden = _settingsState.value.hiddenCourseKeys
                val filtered = courses.filter { "${it.courseId}_${it.clazzId}" !in hidden }
                _coursesState.value = _coursesState.value.copy(
                    isLoading = false, courses = filtered, allCourses = courses, error = null,
                )
                sessionManager.saveCxCoursesJson(gson.toJson(courses))  // 缓存完整列表
                // 后台加载课程进度
                // Progress is an optional, sequential fan-out. Reuse the current
                // snapshot unless this is an explicit refresh or a new course has
                // no cached entry yet.
                val knownProgress = _coursesState.value.courseProgress
                if (force || courses.any { "${it.courseId}_${it.clazzId}" !in knownProgress }) {
                    loadCourseProgress(courses)
                }
                coursesLoadedAt = nowMs()
                coursesFailureAt = 0L
            }
            result.onFailure { e ->
                coursesFailureAt = nowMs()
                _coursesState.value = _coursesState.value.copy(isLoading = false, error = e.message ?: "获取课程列表失败")
            }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    coursesFailureAt = nowMs()
                    _coursesState.value = _coursesState.value.copy(
                        isLoading = false,
                        error = e.message ?: "获取课程列表失败",
                    )
                } finally {
                    coursesJob = null
                }
            }
            coursesJob = job
        }
    }

    /**
     * 后台串行加载所有课程的任务点进度（本地缓存优先 + 后台刷新）。
     */
    private suspend fun loadCourseProgress(courses: List<CxCourse>) {
        if (courses.isEmpty()) return

        // 1. 优先加载本地缓存 — 立即显示进度条
        val cachedJson = sessionManager.getCxCoursesProgressJson()
        if (!cachedJson.isNullOrBlank()) {
            try {
                val type = TypeToken.getParameterized(
                    Map::class.java, String::class.java, CxCourseProgress::class.java
                ).type
                val cached: Map<String, CxCourseProgress> = gson.fromJson(cachedJson, type)
                if (cached.isNotEmpty()) {
                    _coursesState.value = _coursesState.value.copy(
                        courseProgress = cached,
                        isProgressLoading = true,
                    )
                }
            } catch (_: Exception) { /* 缓存格式不兼容，忽略 */ }
        }

        if (_coursesState.value.courseProgress.isEmpty()) {
            _coursesState.value = _coursesState.value.copy(isProgressLoading = true)
        }

        // 2. 后台网络刷新
        try {
            val progress = cxRepo.getAllCoursesProgress(courses)
            val courseKeys = courses.map { "${it.courseId}_${it.clazzId}" }.toSet()
            val mergedProgress = (_coursesState.value.courseProgress + progress)
                .filterKeys { it in courseKeys }
            _coursesState.value = _coursesState.value.copy(
                courseProgress = mergedProgress,
                isProgressLoading = false,
            )
            // 网络成功后缓存
            if (mergedProgress.isNotEmpty()) {
                sessionManager.saveCxCoursesProgressJson(gson.toJson(mergedProgress))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CxVM", "加载课程进度失败: ${e.javaClass.simpleName}")
            // 网络失败时保留缓存数据
            _coursesState.value = _coursesState.value.copy(isProgressLoading = false)
        }
    }

    fun toggleCourseSelection(courseKey: String) {
        val current = _coursesState.value.selectedCourseIds.toMutableSet()
        if (current.contains(courseKey)) current.remove(courseKey) else current.add(courseKey)
        _coursesState.value = _coursesState.value.copy(selectedCourseIds = current)
    }

    fun selectAllCourses() {
        val all = _coursesState.value.courses.map { it.courseId + "_" + it.clazzId }.toSet()
        _coursesState.value = _coursesState.value.copy(selectedCourseIds = all)
    }

    fun deselectAllCourses() {
        _coursesState.value = _coursesState.value.copy(selectedCourseIds = emptySet())
    }

    fun getSelectedCourses(): List<CxCourse> {
        val ids = _coursesState.value.selectedCourseIds
        return _coursesState.value.courses.filter { ids.contains(it.courseId + "_" + it.clazzId) }
    }

    // ════════════════════════════════════════════════════════
    //  课程详情
    // ════════════════════════════════════════════════════════

    fun loadCourseDetail(course: CxCourse) {
        viewModelScope.launch {
            _detailState.value = CxDetailState(course = course, isLoading = true)
            val result = cxRepo.getCoursePoints(course)
            result.onSuccess { points ->
                _detailState.value = CxDetailState(course = course, coursePoints = points)
            }
            result.onFailure { e ->
                _detailState.value = CxDetailState(course = course, error = e.message ?: "获取章节失败")
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  签到中心 (Phase 3, 2026-06-20)
    // ════════════════════════════════════════════════════════

    /**
     * 加载签到活动列表。
     *
     * 遍历已加载的课程,合并所有 activeList。
     * 同时从 SessionManager 读取已配置的经纬度 / 手势码。
     */
    fun loadSignActivities(force: Boolean = false) {
        synchronized(requestLock) {
            if (signActivitiesJob?.isActive == true) return
            val now = nowMs()
            if (!force && inWindow(signActivitiesLoadedAt, SIGN_ACTIVITIES_TTL_MS, now)) return
            if (!force && inWindow(signActivitiesFailureAt, FAILURE_COOLDOWN_MS, now)) return
            val job = viewModelScope.launch {
                try {
            _signState.value = _signState.value.copy(isLoading = true, error = null)
            val lat = sessionManager.getCxSignLat()
            val lon = sessionManager.getCxSignLon()
            val address = sessionManager.getCxSignAddress()
            val gesture = sessionManager.getCxSignGesture()
            _signState.value = _signState.value.copy(
                configuredLat = lat,
                configuredLon = lon,
                configuredAddress = address,
                configuredGesture = gesture,
            )
            val courses = _coursesState.value.courses
            if (courses.isEmpty()) {
                _signState.value = _signState.value.copy(isLoading = false)
                return@launch
            }
            val merged = mutableListOf<CxActivity>()
            var firstError: String? = null
            var stopAfterFailure = false
            for (c in courses) {
                val r = cxRepo.getActivityList(c)
                r.onSuccess {
                    merged.addAll(it.filter { a -> a.status == 1 && a.type == 2 })
                }
                r.onFailure {
                    if (firstError == null) firstError = it.message
                    if (cxRepo.isTrafficCooldown(it) || cxRepo.isExplicitAuthExpiry(it)) {
                        stopAfterFailure = true
                    }
                }
                if (stopAfterFailure) break
            }
            _signState.value = _signState.value.copy(
                isLoading = false,
                activities = merged,
                error = firstError,
            )
            signActivitiesLoadedAt = if (firstError == null) nowMs() else 0L
            if (firstError == null) signActivitiesFailureAt = 0L else signActivitiesFailureAt = nowMs()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    signActivitiesFailureAt = nowMs()
                    _signState.value = _signState.value.copy(isLoading = false, error = e.message ?: "加载签到失败")
                } finally {
                    signActivitiesJob = null
                }
            }
            signActivitiesJob = job
        }
    }

    /**
     * 一键签到。
     *
     * 根据 activity.signType 自动路由:
     *   - NORMAL / PRE_SIGN → signNormal + preSign
     *   - LOCATION → signInLocation(用配置的经纬度)
     *   - GESTURE → signInGesture(用配置的手势码)
     */
    fun signActivity(activity: CxActivity) {
        if (activity.type != 2 || activity.status != 1) return
        synchronized(requestLock) {
            if (signJobsInFlight[activity.id]?.isActive == true || activity.id in _signState.value.signingIds) return
            if (inWindow(signFailureAt[activity.id] ?: 0L, FAILURE_COOLDOWN_MS)) {
                _signState.value = _signState.value.copy(error = "该签到活动暂不可重试，请稍后再试")
                return
            }
            _signState.value = _signState.value.copy(
                signingIds = _signState.value.signingIds + activity.id,
                error = null,
            )
            val job = viewModelScope.launch {
                try {
                    val course = _coursesState.value.allCourses
                        .ifEmpty { _coursesState.value.courses }
                        .firstOrNull { it.courseId == activity.courseId && it.clazzId == activity.classId }
                    if (course == null) {
                        signFailureAt[activity.id] = nowMs()
                        _signState.value = _signState.value.copy(error = "找不到对应课程")
                        return@launch
                    }

                    if (activity.signType == CxSignType.PRE_SIGN || activity.signType == CxSignType.NORMAL) {
                        val pre = cxRepo.preSign(course, activity.id)
                        if (pre.isFailure) {
                            signFailureAt[activity.id] = nowMs()
                            _signState.value = _signState.value.copy(
                                error = "签到预检查失败: ${pre.exceptionOrNull()?.message.orEmpty()}",
                            )
                            return@launch
                        }
                    }

                    val result = when (activity.signType) {
                        CxSignType.LOCATION -> {
                            val state = _signState.value
                            if (state.configuredLat < 0 || state.configuredLon < 0) {
                                Result.failure<String>(IllegalStateException("请先在设置页配置经纬度"))
                            } else {
                                cxRepo.signInLocation(
                                    course, activity.id, state.configuredLat, state.configuredLon, state.configuredAddress,
                                )
                            }
                        }
                        CxSignType.GESTURE -> {
                            val code = _signState.value.configuredGesture
                            if (code.isBlank()) Result.failure<String>(IllegalStateException("请先在设置页配置手势码"))
                            else cxRepo.signInGesture(course, activity.id, code)
                        }
                        else -> cxRepo.signNormal(course, activity.id)
                    }

                    result.onSuccess { msg ->
                        val confirmed = "成功" in msg || "success" in msg.lowercase() || "already" in msg.lowercase()
                        if (confirmed) {
                            _signState.value = _signState.value.copy(
                                activities = _signState.value.activities.filter { it.id != activity.id },
                                error = null,
                            )
                        } else {
                            signFailureAt[activity.id] = nowMs()
                            _signState.value = _signState.value.copy(error = "签到结果未确认: ${msg.take(80)}")
                        }
                    }
                    result.onFailure { e ->
                        signFailureAt[activity.id] = nowMs()
                        _signState.value = _signState.value.copy(error = "签到失败: ${e.message}")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    signFailureAt[activity.id] = nowMs()
                    _signState.value = _signState.value.copy(error = "签到失败: ${e.message}")
                } finally {
                    _signState.value = _signState.value.copy(signingIds = _signState.value.signingIds - activity.id)
                    signJobsInFlight.remove(activity.id)
                }
            }
            signJobsInFlight[activity.id] = job
        }
    }

    /**
     * 仅从 SessionManager 载入签到配置(经纬度/地址/手势)到 signState,不拉网络。
     * 供设置页展示当前配置使用;签到中心则用 [loadSignActivities] 顺带刷新。
     */
    fun loadSignConfig() {
        _signState.value = _signState.value.copy(
            configuredLat = sessionManager.getCxSignLat(),
            configuredLon = sessionManager.getCxSignLon(),
            configuredAddress = sessionManager.getCxSignAddress(),
            configuredGesture = sessionManager.getCxSignGesture(),
        )
    }

    fun updateSignLocation(lat: Double, lon: Double, address: String) {        _signState.value = _signState.value.copy(
            configuredLat = lat, configuredLon = lon, configuredAddress = address,
        )
        viewModelScope.launch {
            sessionManager.saveCxSignLat(lat)
            sessionManager.saveCxSignLon(lon)
            sessionManager.saveCxSignAddress(address)
        }
    }

    fun updateSignGesture(code: String) {
        _signState.value = _signState.value.copy(configuredGesture = code)
        viewModelScope.launch { sessionManager.saveCxSignGesture(code) }
    }

    // ════════════════════════════════════════════════════════
    //  即时签到流程 (2026-06-24)
    //  用户点"签到" → 检索进行中活动 → preSign 判类型 → 按类型分发
    // ════════════════════════════════════════════════════════

    /**
     * 启动即时签到:检索所有课程的进行中签到活动,逐个 preSign 判定真实类型,
     * 汇总成待处理队列。无活动则提示。
     */
    fun startSignFlow() {
        synchronized(requestLock) {
            if (signFlowJob?.isActive == true || inWindow(signActivitiesFailureAt, FAILURE_COOLDOWN_MS)) return
            val generation = ++signFlowGeneration
            val job = viewModelScope.launch {
                try {
            if (generation != signFlowGeneration) return@launch
            _signFlowState.value = CxSignFlowState(isSearching = true)
            val courses = _coursesState.value.courses
            if (courses.isEmpty()) {
                _signFlowState.value = CxSignFlowState(message = "请先在课程页加载课程后再签到")
                return@launch
            }
            val pending = mutableListOf<CxSignTask>()
            for (course in courses) {
                val activityResult = cxRepo.getActivityList(course)
                if (activityResult.isFailure) {
                    val error = activityResult.exceptionOrNull()
                        ?: IllegalStateException("activity list failed")
                    throw error
                }
                val acts = activityResult.getOrNull().orEmpty()
                for (act in acts.filter { it.status == 1 && it.type == 2 }) {
                    // 真实类型以 preSign 为准
                    val pre = cxRepo.preSign(course, act.id)
                    if (pre.isSuccess) {
                        pending.add(CxSignTask(course = course, activity = act, signType = pre.getOrNull()?.signType ?: act.signType))
                    } else {
                        val error = pre.exceptionOrNull() ?: IllegalStateException("preSign failed")
                        throw error
                    }
                }
            }
            if (pending.isEmpty()) {
                _signFlowState.value = CxSignFlowState(message = "当前没有进行中的签到")
                return@launch
            }
            if (generation == signFlowGeneration) {
                _signFlowState.value = CxSignFlowState(tasks = pending, currentIndex = 0)
            }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    signActivitiesFailureAt = nowMs()
                    _signFlowState.value = CxSignFlowState(error = e.message ?: "查找签到失败")
                } finally {
                    signFlowJob = null
                }
            }
            signFlowJob = job
        }
    }

    /** 关闭签到流程(取消或全部完成) */
    fun dismissSignFlow() {
        synchronized(requestLock) {
            signFlowGeneration++
            signFlowJob?.cancel()
            signSubmitJob?.cancel()
            signFlowJob = null
            signSubmitJob = null
            photoObjectIds.clear()
        }
        _signFlowState.value = CxSignFlowState()
    }

    /** 清除一次性提示消息 */
    fun clearSignFlowMessage() {
        _signFlowState.value = _signFlowState.value.copy(message = null)
    }

    /** 跳过当前签到任务,前进到下一个 */
    fun skipCurrentSignTask() {
        val s = _signFlowState.value
        val next = s.currentIndex + 1
        if (next >= s.tasks.size) {
            _signFlowState.value = CxSignFlowState(message = "已处理完所有签到")
        } else {
            _signFlowState.value = s.copy(currentIndex = next, submitting = false, error = null)
        }
    }

    /**
     * 提交当前签到任务。[params] 携带各类型所需输入(经纬度/手势/签到码/enc/objectId)。
     * 成功后自动前进到下一个待签任务。
     */
    fun submitCurrentSign(params: SignParams) {
        synchronized(requestLock) {
            val state = _signFlowState.value
            val task = state.current ?: return
            if (state.submitting || signSubmitJob?.isActive == true) return
            val generation = signFlowGeneration
            _signFlowState.value = state.copy(submitting = true, error = null)
            val job = viewModelScope.launch {
                try {
                    if (generation != signFlowGeneration) return@launch
                    handleSignResult(task, submitSignParams(task, params))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _signFlowState.value = _signFlowState.value.copy(submitting = false, error = "签到失败: ${e.message}")
                } finally {
                    signSubmitJob = null
                }
            }
            signSubmitJob = job
        }
    }

    private suspend fun submitSignParams(task: CxSignTask, params: SignParams): Result<String> = when (params) {
        is SignParams.Normal -> cxRepo.signNormal(task.course, task.activity.id)
        is SignParams.Location -> cxRepo.signInLocation(task.course, task.activity.id, params.lat, params.lon, params.address)
        is SignParams.Gesture -> cxRepo.signInGesture(task.course, task.activity.id, params.code)
        is SignParams.SignCode -> cxRepo.signInSignCode(task.course, task.activity.id, params.code)
        is SignParams.QrCode -> cxRepo.signInQrCode(task.course, task.activity.id, params.enc, params.lat, params.lon, params.address)
        is SignParams.Photo -> cxRepo.signInPhoto(task.course, task.activity.id, params.objectId)
    }

    private fun handleSignResult(task: CxSignTask, result: Result<String>) {
        result.onSuccess { msg ->
            val ok = "成功" in msg || "success" in msg.lowercase() || "already" in msg.lowercase()
            if (ok) {
                photoObjectIds.remove(task.activity.id)
                val state = _signFlowState.value
                val next = state.currentIndex + 1
                if (next >= state.tasks.size) {
                    _signFlowState.value = CxSignFlowState(message = "签到完成 ✓")
                } else {
                    _signFlowState.value = state.copy(currentIndex = next, submitting = false, error = null)
                }
            } else {
                _signFlowState.value = _signFlowState.value.copy(submitting = false, error = "签到结果未确认: ${msg.take(80)}")
            }
        }
        result.onFailure { e ->
            _signFlowState.value = _signFlowState.value.copy(submitting = false, error = "签到失败: ${e.message}")
        }
    }

    /** 拍照签到:先上传图片拿 objectId,再提交 */
    fun submitPhotoSign(imageBytes: ByteArray, fileName: String, mimeType: String) {
        synchronized(requestLock) {
            val state = _signFlowState.value
            val task = state.current ?: return
            if (state.submitting || signSubmitJob?.isActive == true) return
            val generation = signFlowGeneration
            _signFlowState.value = state.copy(submitting = true, error = null)
            val job = viewModelScope.launch {
                try {
                    if (generation != signFlowGeneration) return@launch
                    val objectId = photoObjectIds[task.activity.id] ?: run {
                        val uploaded = cxRepo.uploadHomeworkFile(imageBytes, fileName, mimeType)
                        val id = uploaded.getOrElse {
                            _signFlowState.value = _signFlowState.value.copy(
                                submitting = false,
                                error = "图片上传失败: ${it.message}",
                            )
                            return@launch
                        }
                        photoObjectIds[task.activity.id] = id
                        id
                    }
                    if (generation == signFlowGeneration) {
                        handleSignResult(task, submitSignParams(task, SignParams.Photo(objectId)))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _signFlowState.value = _signFlowState.value.copy(submitting = false, error = "签到失败: ${e.message}")
                } finally {
                    signSubmitJob = null
                }
            }
            signSubmitJob = job
        }
    }

    // ── 自定义签到位置 (2026-06-24) ──────────────────────────
    private val _customLocations = MutableStateFlow<List<com.ahu_plus.data.model.CustomSignLocation>>(emptyList())
    val customLocations: StateFlow<List<com.ahu_plus.data.model.CustomSignLocation>> = _customLocations.asStateFlow()

    /** 从持久化加载自定义位置(进入位置选择器时调用) */
    fun loadCustomLocations() {
        _customLocations.value = sessionManager.getCustomSignLocations()
    }

    /** 添加一个自定义位置(name 去重,同名覆盖) */
    fun addCustomLocation(name: String, lat: Double, lon: Double) {
        val trimmed = name.trim().ifBlank { "自定义位置" }
        val list = _customLocations.value.filter { it.name != trimmed } +
            com.ahu_plus.data.model.CustomSignLocation(trimmed, lat, lon)
        _customLocations.value = list
        viewModelScope.launch { sessionManager.saveCustomSignLocations(list) }
    }

    /** 删除一个自定义位置 */
    fun removeCustomLocation(loc: com.ahu_plus.data.model.CustomSignLocation) {
        val list = _customLocations.value.filter { it != loc }
        _customLocations.value = list
        viewModelScope.launch { sessionManager.saveCustomSignLocations(list) }
    }

    fun loadChapterJobs(course: CxCourse, chapter: CxChapter, force: Boolean = false) {
        val key = "${course.courseId}_${course.clazzId}_${chapter.id}"
        synchronized(requestLock) {
            if (chapterJobsInFlight[key]?.isActive == true) return
            if (!force && inWindow(chapterJobsLoadedAt[key] ?: 0L, CHAPTER_JOBS_TTL_MS)) return
            if (!force && inWindow(chapterFailureAt[key] ?: 0L, FAILURE_COOLDOWN_MS)) return
            val current = _detailState.value
            _detailState.value = current.copy(loadingChapters = current.loadingChapters + chapter.id)
            val job = viewModelScope.launch {
                try {
                    val result = cxRepo.getJobList(course, chapter, forceRefresh = force)
                    val state = _detailState.value
                    val newJobs = state.chapterJobs.toMutableMap()
                    val newInfo = state.chapterJobInfo.toMutableMap()
                    result.onSuccess { (jobs, info) ->
                        newJobs[chapter.id] = jobs
                        newInfo[chapter.id] = info
                        chapterJobsLoadedAt[key] = nowMs()
                        chapterFailureAt.remove(key)
                    }
                    result.onFailure { e ->
                        chapterFailureAt[key] = nowMs()
                        _detailState.value = _detailState.value.copy(error = e.message)
                    }
                    _detailState.value = _detailState.value.copy(
                        chapterJobs = newJobs,
                        chapterJobInfo = newInfo,
                        loadingChapters = _detailState.value.loadingChapters - chapter.id,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    chapterFailureAt[key] = nowMs()
                    _detailState.value = _detailState.value.copy(
                        loadingChapters = _detailState.value.loadingChapters - chapter.id,
                        error = e.message ?: "获取任务点失败",
                    )
                } finally {
                    chapterJobsInFlight.remove(key)
                }
            }
            chapterJobsInFlight[key] = job
        }
    }

    // ════════════════════════════════════════════════════════
    //  课程作业 (2026-06-22)
    // ════════════════════════════════════════════════════════

    fun loadHomework(force: Boolean = false) {
        synchronized(requestLock) {
            if (homeworkJob?.isActive == true) return
            val now = nowMs()
            if (!force && inWindow(homeworkLoadedAt, HOMEWORK_TTL_MS, now)) return
            if (!force && inWindow(homeworkFailureAt, FAILURE_COOLDOWN_MS, now)) return
        _homeworkState.value = _homeworkState.value.copy(isLoading = true, error = null)

        val job = viewModelScope.launch {
            try {
                // 1. 优先加载缓存 (快速显示)
                val cachedJson = sessionManager.getCxHomeworkJson()
                if (!cachedJson.isNullOrBlank() && _homeworkState.value.homework.isEmpty()) {
                    try {
                        val type = TypeToken.getParameterized(
                            List::class.java, CxHomeworkItem::class.java
                        ).type
                        val cached: List<CxHomeworkItem> = gson.fromJson(cachedJson, type)
                        if (cached.isNotEmpty()) {
                            _homeworkState.value = CxHomeworkListState(
                                isLoading = true,
                                homework = cached,
                            )
                        }
                    } catch (_: Exception) { /* 缓存格式不兼容，忽略 */ }
                }

                var courses = _coursesState.value.courses
                // If the course request is already in flight, wait for that single request.
                if (courses.isEmpty()) {
                    val pendingCourses = synchronized(requestLock) { coursesJob }
                    pendingCourses?.join()
                    courses = _coursesState.value.courses
                    if (courses.isEmpty()) {
                        _homeworkState.value = CxHomeworkListState(error = "请先加载课程列表")
                        return@launch
                    }
                }

                // 过滤用户手动隐藏的课程
                val hidden = _settingsState.value.hiddenCourseKeys
                val visibleCourses = courses.filter { "${it.courseId}_${it.clazzId}" !in hidden }
                Log.d("CxVM", "loadHomework: ${visibleCourses.size}/${courses.size} 门可见课程 (隐藏了 ${hidden.size} 门)")

                // Keep one course request in flight at a time; the repository also caches
                // the short-lived course response, so repeated tab entry is cheap.
                val allHomework = mutableListOf<CxHomeworkItem>()
                val errors = mutableListOf<String>()
                var stopAfterFailure = false
                for (course in visibleCourses) {
                    val courseUrl = course.url.ifBlank {
                        "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction" +
                            "?courseid=${course.courseId}&clazzid=${course.clazzId}&cpi=${course.cpi}"
                    }
                    val result = cxRepo.getHomeworkList(courseUrl)
                    result.onSuccess { items ->
                        items.forEach { item -> allHomework += item.copy(courseName = course.title) }
                        Log.d("CxVM", "loadHomework: ${items.size} 个作业")
                    }
                    result.onFailure { e ->
                        Log.w("CxVM", "loadHomework 请求失败: ${e.javaClass.simpleName}")
                        errors += "${course.title.take(12)}: ${e.message}"
                        stopAfterFailure = true
                    }
                    if (stopAfterFailure) break
                }

                // 过滤已结束课程（启用时：只保留有未完成/待审批作业的课程）
                val hideEnded = _settingsState.value.hideEndedCourses
                val activeStatusSet = setOf("未交", "待做", "待审批", "已提交", "待批阅", "未完成")
                val filtered = if (hideEnded) {
                    val activeCourses = allHomework
                        .filter { hw -> activeStatusSet.any { it in hw.status } }
                        .map { it.courseName }
                        .toSet()
                    allHomework.filter { it.courseName in activeCourses }
                } else {
                    allHomework
                }

                val sorted = filtered.sortedBy { it.status != "未交" }
                _homeworkState.value = CxHomeworkListState(
                    isLoading = false,
                    homework = sorted,
                    error = if (sorted.isEmpty() && errors.isNotEmpty()) {
                        "部分课程加载失败: ${errors.take(3).joinToString("; ")}"
                    } else null,
                )

                // 3. 网络成功后缓存
                if (sorted.isNotEmpty()) {
                    sessionManager.saveCxHomeworkJson(gson.toJson(sorted))
                }
                homeworkLoadedAt = if (errors.isEmpty()) nowMs() else 0L
                if (errors.isEmpty()) homeworkFailureAt = 0L else homeworkFailureAt = nowMs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CxVM", "loadHomework 异常: ${e.javaClass.simpleName}")
                // 网络失败时保留缓存数据
                if (_homeworkState.value.homework.isNotEmpty()) {
                    _homeworkState.value = _homeworkState.value.copy(isLoading = false)
                } else {
                    _homeworkState.value = CxHomeworkListState(
                        isLoading = false,
                        error = e.message ?: "加载作业失败",
                    )
                }
                homeworkFailureAt = nowMs()
            } finally {
                homeworkJob = null
            }
        }
        homeworkJob = job
        }
    }

    fun refreshHomework() {
        loadHomework(force = true)
    }

    fun loadHomeworkDetail(work: CxHomeworkItem) {
        val requestId = beginHomeworkDetailRequest(work.workId)
        _homeworkDetailState.value = CxHomeworkDetailState(isLoading = true, error = null)

        // 1. 优先显示缓存
        homeworkDetailJob = viewModelScope.launch {
            val cachedJson = sessionManager.getCxHomeworkDetailJson()
            if (!cachedJson.isNullOrBlank()) {
                val cachedWorkData = decodeCxHomeworkDetailCache(cachedJson, work.workId)
                if (cachedWorkData != null && cachedWorkData.questions.isNotEmpty()) {
                    if (isCurrentHomeworkDetailRequest(requestId, work.workId)) {
                        _homeworkDetailState.value = CxHomeworkDetailState(
                            isLoading = true, workData = cachedWorkData,
                        )
                    }
                }
            }

            // 2. 后台网络刷新
            val result = cxRepo.getHomeworkPage(
                workUrl = work.workUrl,
                courseId = work.courseId,
                classId = work.classId,
                cpi = work.cpi,
            )
            result.onSuccess { workData ->
                if (!isCurrentHomeworkDetailRequest(requestId, work.workId)) return@onSuccess
                _homeworkDetailState.value = CxHomeworkDetailState(workData = workData)
                sessionManager.saveCxHomeworkDetailJson(encodeCxHomeworkDetailCache(work.workId, workData))
            }
            result.onFailure { e ->
                if (!isCurrentHomeworkDetailRequest(requestId, work.workId)) return@onFailure
                if (_homeworkDetailState.value.workData == null) {
                    _homeworkDetailState.value = CxHomeworkDetailState(
                        error = e.message ?: "加载作业详情失败",
                    )
                } else {
                    // 有缓存时静默失败
                    _homeworkDetailState.value = _homeworkDetailState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun refreshHomeworkDetail(work: CxHomeworkItem) {
        val canReuseCurrentData = homeworkDetailWorkId == work.workId
        val requestId = beginHomeworkDetailRequest(work.workId)
        _homeworkDetailState.value = if (canReuseCurrentData) {
            _homeworkDetailState.value.copy(isLoading = true, error = null)
        } else {
            CxHomeworkDetailState(isLoading = true)
        }
        homeworkDetailJob = viewModelScope.launch {
            val result = cxRepo.getHomeworkPage(
                workUrl = work.workUrl,
                courseId = work.courseId,
                classId = work.classId,
                cpi = work.cpi,
            )
            result.onSuccess { workData ->
                if (!isCurrentHomeworkDetailRequest(requestId, work.workId)) return@onSuccess
                _homeworkDetailState.value = _homeworkDetailState.value.copy(
                    isLoading = false, workData = workData, error = null,
                )
                sessionManager.saveCxHomeworkDetailJson(encodeCxHomeworkDetailCache(work.workId, workData))
            }
            result.onFailure { e ->
                if (!isCurrentHomeworkDetailRequest(requestId, work.workId)) return@onFailure
                _homeworkDetailState.value = _homeworkDetailState.value.copy(
                    isLoading = false,
                    error = e.message ?: "刷新失败",
                )
            }
        }
    }

    fun clearHomeworkDetail() {
        invalidateHomeworkDetailRequests()
        _homeworkDetailState.value = CxHomeworkDetailState()
    }

    fun submitHomework(
        work: CxHomeworkItem,
        workData: CxWorkData,
        formActionUrl: String,
        userAnswers: Map<String, String> = emptyMap(),
    ) {
        if (_homeworkDetailState.value.isSubmitting) return
        if (formActionUrl.isBlank()) {
            _homeworkDetailState.value = _homeworkDetailState.value.copy(error = "无法提交：缺少表单地址")
            return
        }
        _homeworkDetailState.value = _homeworkDetailState.value.copy(
            isSubmitting = true, submitResult = null, error = null,
        )

        viewModelScope.launch {
            try {
                // Unknown answers are a hard stop. Never submit a guessed or empty answer.
                val filledFormFields = workData.formFields.toMutableMap()
                var foundCount = 0
                for (q in workData.questions) {
                    val userAns = userAnswers[q.id]?.takeIf { it.isNotBlank() }
                    val finalAnswer = userAns ?: tikuRepo.query(q)
                    if (finalAnswer.isNullOrBlank()) {
                        _homeworkDetailState.value = _homeworkDetailState.value.copy(
                            isSubmitting = false,
                            error = "题库未命中“${q.title.take(24)}”，请补充答案后再提交",
                        )
                        return@launch
                    }
                    foundCount++
                    filledFormFields["answer${q.id}"] = finalAnswer
                    filledFormFields["answertype${q.id}"] = q.answerField["answertype${q.id}"] ?: ""
                }
                Log.i("CxVM", "submitHomework: 已确认答案 $foundCount/${workData.questions.size}")

                val result = cxRepo.submitHomework(
                    workData.copy(formFields = filledFormFields, pyFlag = ""),
                    formActionUrl,
                )
                result.onSuccess { msg ->
                    _homeworkDetailState.value = _homeworkDetailState.value.copy(
                        isSubmitting = false, submitResult = msg.ifBlank { "提交成功" },
                    )
                    sessionManager.saveCxHomeworkDetailJson("")  // 清除缓存
                    refreshHomework()
                }
                result.onFailure { e ->
                    _homeworkDetailState.value = _homeworkDetailState.value.copy(
                        isSubmitting = false,
                        error = e.message ?: "提交失败",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _homeworkDetailState.value = _homeworkDetailState.value.copy(
                    isSubmitting = false,
                    error = e.message ?: "提交失败",
                )
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  学习控制
    // ════════════════════════════════════════════════════════

    fun studyCourses(courses: List<CxCourse>) {
        if (studyState.value.isRunning) {
            studyRepo.stop()
        }
        val s = _settingsState.value
        viewModelScope.launch {
            studyRepo.studyAll(
                courses = courses,
                speed = 1.0f,
                concurrency = 1,
                answerMode = CxAnswerMode.fromSetting(s.submitMode),
                enabledTaskTypes = s.enabledTaskTypes,
            )
        }
    }

    fun studySingleCourse(course: CxCourse) {
        if (studyState.value.isRunning) {
            studyRepo.stop()
        }
        val s = _settingsState.value
        viewModelScope.launch {
            studyRepo.studyCourse(
                course = course,
                speed = 1.0f,
                answerMode = CxAnswerMode.fromSetting(s.submitMode),
                enabledTaskTypes = s.enabledTaskTypes,
            )
        }
    }

    fun stopStudy() { studyRepo.stop() }
}

// ══════════════════════════════════════════════════════════════
//  数据类
// ══════════════════════════════════════════════════════════════

data class CxLoginState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val showLoginWarning: Boolean = false,    // 2026-06-23: 首次登录成功后弹出免责警告
)

data class CxCoursesState(
    val isLoading: Boolean = false,
    val courses: List<CxCourse> = emptyList(),           // 过滤后的可见课程
    val allCourses: List<CxCourse> = emptyList(),         // 全部课程（含隐藏的，供管理对话框使用）
    val selectedCourseIds: Set<String> = emptySet(),
    val error: String? = null,
    /** 课程进度索引: key = "${courseId}_${clazzId}" */
    val courseProgress: Map<String, CxCourseProgress> = emptyMap(),
    /** 是否正在加载进度 */
    val isProgressLoading: Boolean = false,
)

data class CxDetailState(
    val course: CxCourse? = null,
    val isLoading: Boolean = false,
    val coursePoints: CxCoursePoints? = null,
    val chapterJobs: Map<String, List<CxJob>> = emptyMap(),
    val chapterJobInfo: Map<String, CxJobInfo> = emptyMap(),
    val loadingChapters: Set<String> = emptySet(),
    val error: String? = null,
)

data class CxSettingsState(
    val speed: Float = 1.0f,             // 2026-06-23: 默认 1x 倍速
    val concurrency: Int = 1,            // 固定串行，控制请求量
    val notOpenAction: String = "continue",
    val autoSign: Boolean = false,
    val submitMode: String = "auto",         // auto / save / skip
    val tikuType: String = "CACHE",          // DISABLED / CACHE / YANXI / AI
    val tikuToken: String = "",
    val aiApiKey: String = "",
    val aiBaseUrl: String = "https://api.deepseek.com",
    val aiModel: String = "deepseek-v4-flash",
    val enabledTaskTypes: Set<String> = setOf("video", "document", "read", "workid", "audio", "live"),
    val messagesMergeInbox: Boolean = false, // 是否将活动通知集合为收件箱
    val showOverlay: Boolean = true,         // 2026-06-22: 后台学习时是否显示悬浮窗
    val hideEndedCourses: Boolean = true,    // 2026-06-22: 隐藏已结束课程的作业
    val hiddenCourseKeys: Set<String> = emptySet(),  // 用户手动隐藏的课程 key
    val visitBrushEnabled: Boolean = false,     // 2026-06-23: 默认关闭刷访问次数
    val visitBrushInterval: Int = 30,           // 访问间隔（秒）
    val downloadEnabled: Boolean = false,       // 课程资源自动下载
)

/** 签到 UI 状态(2026-06-20 集成 Phase 3) */
data class CxSignState(
    val isLoading: Boolean = false,
    val activities: List<CxActivity> = emptyList(),
    val signingIds: Set<Long> = emptySet(),         // 正在签到的活动 id
    val error: String? = null,
    /** 配置的位置坐标(从 SessionManager 加载) */
    val configuredLat: Double = -1.0,
    val configuredLon: Double = -1.0,
    val configuredAddress: String = "",
    val configuredGesture: String = "",
)

/** 即时签到流程中的单个待处理任务 */
data class CxSignTask(
    val course: CxCourse,
    val activity: CxActivity,
    val signType: CxSignType,
)

/**
 * 即时签到流程状态(2026-06-24)。
 *
 * - [isSearching] 正在检索进行中活动
 * - [tasks] 待处理任务队列;[currentIndex] 当前处理到第几个
 * - [message] 一次性提示(无活动/全部完成),非 null 时 UI 弹 Snackbar 后清除
 */
data class CxSignFlowState(
    val isSearching: Boolean = false,
    val tasks: List<CxSignTask> = emptyList(),
    val currentIndex: Int = 0,
    val submitting: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    /** 当前待处理任务(队列已空则 null) */
    val current: CxSignTask? get() = tasks.getOrNull(currentIndex)
    /** 是否有对话框需要展示 */
    val hasActiveTask: Boolean get() = current != null
}

/** 各签到类型提交所需参数 */
sealed interface SignParams {
    data object Normal : SignParams
    data class Location(val lat: Double, val lon: Double, val address: String) : SignParams
    data class Gesture(val code: String) : SignParams
    data class SignCode(val code: String) : SignParams
    data class QrCode(val enc: String, val lat: Double = -1.0, val lon: Double = -1.0, val address: String = "") : SignParams
    data class Photo(val objectId: String) : SignParams
}

/** 消息中心 UI 状态(2026-06-21) */
data class CxMessagesState(
    val isLoading: Boolean = false,
    val messages: List<CxMessage> = emptyList(),
    val lastNoticeId: String = "",           // 收件箱游标
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    /** 用户在 app 内点开过的消息 ID（本地已读追踪） */
    val readMessageIds: Set<String> = emptySet(),
)

internal data class CxHomeworkDetailCache(
    val workId: String,
    val workData: CxWorkData,
)

internal fun encodeCxHomeworkDetailCache(workId: String, workData: CxWorkData): String {
    return GsonProvider.instance.toJson(CxHomeworkDetailCache(workId, workData))
}

internal fun decodeCxHomeworkDetailCache(json: String, expectedWorkId: String): CxWorkData? {
    return runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        val cachedWorkId = root.get("workId")?.asString ?: return@runCatching null
        if (cachedWorkId != expectedWorkId) return@runCatching null
        val workData = root.get("workData") ?: return@runCatching null
        GsonProvider.instance.fromJson(workData, CxWorkData::class.java)
    }.getOrNull()
}
