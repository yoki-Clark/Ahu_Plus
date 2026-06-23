package com.yourname.ahu_plus.ui.screen.chaoxing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.CxActivity
import com.yourname.ahu_plus.data.model.CxChapter
import com.yourname.ahu_plus.data.model.CxCourse
import com.yourname.ahu_plus.data.model.CxCoursePoints
import com.yourname.ahu_plus.data.model.CxCourseProgress
import com.yourname.ahu_plus.data.model.CxHomeworkItem
import com.yourname.ahu_plus.data.model.CxHomeworkListState
import com.yourname.ahu_plus.data.model.CxHomeworkDetailState
import com.yourname.ahu_plus.data.model.CxJob
import com.yourname.ahu_plus.data.model.CxQuestion
import com.yourname.ahu_plus.data.model.CxJobInfo
import com.yourname.ahu_plus.data.model.CxMessage
import com.yourname.ahu_plus.data.model.CxMessageSource
import com.yourname.ahu_plus.data.model.CxSignType
import com.yourname.ahu_plus.data.model.CxStudyUiState
import com.yourname.ahu_plus.data.model.CxWorkData
import com.yourname.ahu_plus.data.repository.ChaoxingRepository
import com.yourname.ahu_plus.data.repository.ChaoxingStudyRepository
import com.yourname.ahu_plus.data.repository.ChaoxingTikuRepository
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections

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
        _settingsState.value = _settingsState.value.copy(speed = v)
        viewModelScope.launch { sessionManager.saveCxSpeed(v) }
    }

    fun updateConcurrency(v: Int) {
        _settingsState.value = _settingsState.value.copy(concurrency = v)
        viewModelScope.launch { sessionManager.saveCxConcurrency(v) }
    }

    fun updateNotOpenAction(v: String) {
        _settingsState.value = _settingsState.value.copy(notOpenAction = v)
        viewModelScope.launch { sessionManager.saveCxNotopenAction(v) }
    }

    fun updateAutoSign(v: Boolean) {
        _settingsState.value = _settingsState.value.copy(autoSign = v)
        viewModelScope.launch { sessionManager.saveCxAutoSign(v) }
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
    }

    fun updateShowOverlay(v: Boolean) {
        _settingsState.value = _settingsState.value.copy(showOverlay = v)
        sessionManager.showStudyOverlay = v
    }

    fun updateHideEndedCourses(v: Boolean) {
        _settingsState.value = _settingsState.value.copy(hideEndedCourses = v)
        viewModelScope.launch { sessionManager.saveCxHideEndedCourses(v) }
        // 重新加载作业列表以应用过滤
        if (_coursesState.value.courses.isNotEmpty()) loadHomework()
    }

    /** 切换单门课程的隐藏状态。key = "${courseId}_${clazzId}" */
    fun toggleHiddenCourse(courseKey: String) {
        val current = _settingsState.value.hiddenCourseKeys.toMutableSet()
        if (current.contains(courseKey)) current.remove(courseKey) else current.add(courseKey)
        _settingsState.value = _settingsState.value.copy(hiddenCourseKeys = current)
        viewModelScope.launch { sessionManager.saveCxHiddenCourses(current) }
        // 过滤课程列表和作业列表
        applyHiddenCourseFilter()
        loadHomework()
    }

    /** 批量更新隐藏课程 */
    fun updateHiddenCourses(keys: Set<String>) {
        _settingsState.value = _settingsState.value.copy(hiddenCourseKeys = keys)
        viewModelScope.launch { sessionManager.saveCxHiddenCourses(keys) }
        applyHiddenCourseFilter()
        loadHomework()
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
        _settingsState.value = _settingsState.value.copy(visitBrushEnabled = v)
        viewModelScope.launch { sessionManager.saveCxVisitBrushEnabled(v) }
    }

    fun updateVisitBrushInterval(v: Int) {
        _settingsState.value = _settingsState.value.copy(visitBrushInterval = v)
        viewModelScope.launch { sessionManager.saveCxVisitBrushInterval(v) }
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
            val chainStr = if (s.tikuType == "DISABLED") "CACHE" else "CACHE,${s.tikuType}"
            Log.d("CxVM", "applyTikuConfig: tikuType=${s.tikuType}, chain=$chainStr, aiKey=${s.aiApiKey.take(8)}...")
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
        viewModelScope.launch {
            _loginState.value = CxLoginState(isLoading = true)
            val result = cxRepo.login(username, password)
            result.onSuccess {
                val valid = cxRepo.validateSession()
                if (valid) {
                    // 保存凭据供自动登录 + 触发云端备份
                    sessionManager.saveCxCredentials(username, password)
                    sessionManager.notifyBackupOnLogin()
                    // 2026-06-23: 首次登录时弹出免责警告;已确认过则不再弹
                    val shouldWarn = !sessionManager.getCxLoginWarningShown()
                    _loginState.value = CxLoginState(isLoggedIn = true, showLoginWarning = shouldWarn)
                    loadCourses()
                } else {
                    _loginState.value = CxLoginState(error = "登录成功但会话验证失败")
                }
            }
            result.onFailure { e ->
                _loginState.value = CxLoginState(error = e.message ?: "登录失败")
            }
        }
    }

    fun checkLogin() {
        viewModelScope.launch {
            if (cxRepo.isLoggedIn()) {
                val valid = cxRepo.validateSession()
                if (valid) {
                    // 2026-06-23: 首次登录警告(自动登录复用同样逻辑)
                    val shouldWarn = !sessionManager.getCxLoginWarningShown()
                    _loginState.value = CxLoginState(isLoggedIn = true, showLoginWarning = shouldWarn)
                    loadCourses()
                } else {
                    // 会话过期 → 尝试自动续期
                    if (sessionManager.hasCxCredentials()) {
                        _loginState.value = CxLoginState(isLoading = true)
                        val success = cxRepo.autoLogin()
                        if (success) {
                            sessionManager.notifyBackupOnLogin()
                            val shouldWarn = !sessionManager.getCxLoginWarningShown()
                            _loginState.value = CxLoginState(isLoggedIn = true, showLoginWarning = shouldWarn)
                            loadCourses()
                        } else {
                            _loginState.value = CxLoginState(error = "自动登录失败，请手动登录")
                        }
                    } else {
                        _loginState.value = CxLoginState(error = "会话已过期，请重新登录")
                    }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            cxRepo.clearCookies()
            sessionManager.clearCxCredentials()
            sessionManager.saveCxHomeworkJson("")  // 清除作业缓存
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
    fun loadMessages() {
        viewModelScope.launch {
            val courses = _coursesState.value.courses
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
                    val activityResult = if (courses.isNotEmpty()) cxRepo.getActivityMessages(courses) else Result.success(emptyList())

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
                    return@launch
                }
            }

            // ── 无缓存：全量拉取 ─────────────────────────────
            val noticeResult = cxRepo.getNoticeList()
            val activityResult = if (courses.isNotEmpty()) cxRepo.getActivityMessages(courses) else Result.success(emptyList())

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
        }
    }

    /**
     * 加载更多收件箱通知（游标分页）。
     */
    fun loadMoreMessages() {
        val current = _messagesState.value
        if (current.isLoadingMore || !current.hasMore || current.lastNoticeId.isBlank()) return

        viewModelScope.launch {
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

    fun loadCourses() {
        viewModelScope.launch {
            _coursesState.value = _coursesState.value.copy(isLoading = true, error = null)
            val result = cxRepo.getCourseList()
            result.onSuccess { courses ->
                val hidden = _settingsState.value.hiddenCourseKeys
                val filtered = courses.filter { "${it.courseId}_${it.clazzId}" !in hidden }
                _coursesState.value = _coursesState.value.copy(
                    isLoading = false, courses = filtered, allCourses = courses, error = null,
                )
                sessionManager.saveCxCoursesJson(gson.toJson(courses))  // 缓存完整列表
                // 后台加载课程进度
                loadCourseProgress(courses)
            }
            result.onFailure { e ->
                _coursesState.value = _coursesState.value.copy(isLoading = false, error = e.message ?: "获取课程列表失败")
            }
        }
    }

    /**
     * 后台并行加载所有课程的任务点进度（本地缓存优先 + 后台刷新）。
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
            _coursesState.value = _coursesState.value.copy(
                courseProgress = progress,
                isProgressLoading = false,
            )
            // 网络成功后缓存
            if (progress.isNotEmpty()) {
                sessionManager.saveCxCoursesProgressJson(gson.toJson(progress))
            }
        } catch (e: Exception) {
            Log.w("CxVM", "加载课程进度失败: ${e.message}")
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
    fun loadSignActivities() {
        viewModelScope.launch {
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
            for (c in courses) {
                val r = cxRepo.getActivityList(c)
                r.onSuccess { merged.addAll(it.filter { a -> a.status == 1 }) }
                r.onFailure { if (firstError == null) firstError = it.message }
            }
            _signState.value = _signState.value.copy(
                isLoading = false,
                activities = merged,
                error = firstError,
            )
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
        viewModelScope.launch {
            val current = _signState.value.signingIds.toMutableSet()
            current.add(activity.id)
            _signState.value = _signState.value.copy(signingIds = current)

            val course = _coursesState.value.courses.firstOrNull {
                it.courseId == activity.courseId && it.clazzId == activity.classId
            } ?: run {
                _signState.value = _signState.value.copy(
                    signingIds = _signState.value.signingIds - activity.id,
                    error = "找不到对应课程",
                )
                return@launch
            }

            // preSign 前置(对 NORMAL/PRE_SIGN 友好,LOCATION/GESTURE 通常也走)
            if (activity.signType == CxSignType.PRE_SIGN || activity.signType == CxSignType.NORMAL) {
                cxRepo.preSign(course, activity.id)
            }

            val result = when (activity.signType) {
                CxSignType.LOCATION -> {
                    val lat = _signState.value.configuredLat
                    val lon = _signState.value.configuredLon
                    val address = _signState.value.configuredAddress
                    if (lat < 0 || lon < 0) {
                        Result.failure<String>(IllegalStateException("请先在设置页配置经纬度"))
                    } else {
                        cxRepo.signInLocation(course, activity.id, lat, lon, address)
                    }
                }
                CxSignType.GESTURE -> {
                    val code = _signState.value.configuredGesture
                    if (code.isBlank()) {
                        Result.failure<String>(IllegalStateException("请先在设置页配置手势码"))
                    } else {
                        cxRepo.signInGesture(course, activity.id, code)
                    }
                }
                else -> cxRepo.signNormal(course, activity.id)
            }

            result.onSuccess { msg ->
                _signState.value = _signState.value.copy(
                    signingIds = _signState.value.signingIds - activity.id,
                    activities = _signState.value.activities.filter { it.id != activity.id },
                    error = if ("成功" in msg || "签到成功" in msg) null else "签到结果: $msg",
                )
            }
            result.onFailure { e ->
                _signState.value = _signState.value.copy(
                    signingIds = _signState.value.signingIds - activity.id,
                    error = "签到失败: ${e.message}",
                )
            }
        }
    }

    fun updateSignLocation(lat: Double, lon: Double, address: String) {
        _signState.value = _signState.value.copy(
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

    fun loadChapterJobs(course: CxCourse, chapter: CxChapter) {
        viewModelScope.launch {
            val current = _detailState.value
            val loading = current.loadingChapters.toMutableSet().apply { add(chapter.id) }
            _detailState.value = current.copy(loadingChapters = loading)

            val result = cxRepo.getJobList(course, chapter)
            val newJobs = current.chapterJobs.toMutableMap()
            val newInfo = current.chapterJobInfo.toMutableMap()
            result.onSuccess { (jobs, info) -> newJobs[chapter.id] = jobs; newInfo[chapter.id] = info }
            result.onFailure { newJobs[chapter.id] = emptyList() }

            loading.remove(chapter.id)
            _detailState.value = current.copy(chapterJobs = newJobs, chapterJobInfo = newInfo, loadingChapters = loading)
        }
    }

    // ════════════════════════════════════════════════════════
    //  课程作业 (2026-06-22)
    // ════════════════════════════════════════════════════════

    fun loadHomework() {
        if (_homeworkState.value.isLoading) return
        _homeworkState.value = _homeworkState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
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
                // 如果课程列表为空，等待加载（最多等待 8 秒）
                if (courses.isEmpty()) {
                    Log.w("CxVM", "loadHomework: 课程列表为空，等待加载...")
                    var waited = 0
                    while (courses.isEmpty() && waited < 40) {
                        delay(200)
                        waited++
                        courses = _coursesState.value.courses
                    }
                    if (courses.isEmpty()) {
                        _homeworkState.value = CxHomeworkListState(error = "请先加载课程列表")
                        return@launch
                    }
                }

                // 过滤用户手动隐藏的课程
                val hidden = _settingsState.value.hiddenCourseKeys
                val visibleCourses = courses.filter { "${it.courseId}_${it.clazzId}" !in hidden }
                Log.d("CxVM", "loadHomework: ${visibleCourses.size}/${courses.size} 门可见课程 (隐藏了 ${hidden.size} 门)")

                // 2. 并发请求所有课程的作业列表
                val allHomework = Collections.synchronizedList(mutableListOf<CxHomeworkItem>())
                val errors = Collections.synchronizedList(mutableListOf<String>())

                kotlinx.coroutines.coroutineScope {
                    visibleCourses.map { course ->
                        async {
                            val courseUrl = course.url.ifBlank {
                                "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction" +
                                    "?courseid=${course.courseId}&clazzid=${course.clazzId}&cpi=${course.cpi}"
                            }
                            val result = cxRepo.getHomeworkList(courseUrl)
                            result.onSuccess { items ->
                                items.forEach { item ->
                                    allHomework.add(item.copy(courseName = course.title))
                                }
                                Log.d("CxVM", "loadHomework: ${course.title.take(16)} → ${items.size} 个作业")
                            }
                            result.onFailure { e ->
                                Log.w("CxVM", "loadHomework: ${course.title.take(16)} 失败: ${e.message}")
                                errors.add("${course.title.take(12)}: ${e.message}")
                            }
                        }
                    }.forEach { it.await() }
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
            } catch (e: Exception) {
                Log.e("CxVM", "loadHomework 异常", e)
                // 网络失败时保留缓存数据
                if (_homeworkState.value.homework.isNotEmpty()) {
                    _homeworkState.value = _homeworkState.value.copy(isLoading = false)
                } else {
                    _homeworkState.value = CxHomeworkListState(
                        isLoading = false,
                        error = e.message ?: "加载作业失败",
                    )
                }
            }
        }
    }

    fun refreshHomework() {
        _homeworkState.value = _homeworkState.value.copy(isLoading = true, error = null)
        loadHomework()
    }

    fun loadHomeworkDetail(work: CxHomeworkItem) {
        _homeworkDetailState.value = CxHomeworkDetailState(isLoading = true, error = null)

        // 1. 优先显示缓存
        viewModelScope.launch {
            val cachedJson = sessionManager.getCxHomeworkDetailJson()
            if (!cachedJson.isNullOrBlank()) {
                try {
                    val cachedWorkData = gson.fromJson(cachedJson, CxWorkData::class.java)
                    if (cachedWorkData.questions.isNotEmpty()) {
                        _homeworkDetailState.value = CxHomeworkDetailState(
                            isLoading = true, workData = cachedWorkData,
                        )
                    }
                } catch (_: Exception) { /* 缓存不兼容 */ }
            }

            // 2. 后台网络刷新
            val result = cxRepo.getHomeworkPage(
                workUrl = work.workUrl,
                courseId = work.courseId,
                classId = work.classId,
                cpi = work.cpi,
            )
            result.onSuccess { workData ->
                _homeworkDetailState.value = CxHomeworkDetailState(workData = workData)
                sessionManager.saveCxHomeworkDetailJson(gson.toJson(workData))
            }
            result.onFailure { e ->
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
        _homeworkDetailState.value = _homeworkDetailState.value.copy(isLoading = true)
        viewModelScope.launch {
            val result = cxRepo.getHomeworkPage(
                workUrl = work.workUrl,
                courseId = work.courseId,
                classId = work.classId,
                cpi = work.cpi,
            )
            result.onSuccess { workData ->
                _homeworkDetailState.value = _homeworkDetailState.value.copy(
                    isLoading = false, workData = workData, error = null,
                )
                sessionManager.saveCxHomeworkDetailJson(gson.toJson(workData))
            }
            result.onFailure { e ->
                _homeworkDetailState.value = _homeworkDetailState.value.copy(
                    isLoading = false,
                    error = e.message ?: "刷新失败",
                )
            }
        }
    }

    fun clearHomeworkDetail() {
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
            // 填充答案：优先用户输入 → 题库 → 随机
            val filledFormFields = workData.formFields.toMutableMap()
            var foundCount = 0
            for (q in workData.questions) {
                val userAns = userAnswers[q.id]?.takeIf { it.isNotBlank() }
                val finalAnswer = if (userAns != null) {
                    foundCount++
                    userAns
                } else {
                    val tikuAns = tikuRepo.query(q)
                    if (tikuAns != null) { foundCount++; tikuAns }
                    else generateRandomAnswerForHomework(q)
                }
                filledFormFields["answer${q.id}"] = finalAnswer
                filledFormFields["answertype${q.id}"] = q.answerField["answertype${q.id}"] ?: ""
            }
            val coverage = if (workData.questions.isNotEmpty()) foundCount.toFloat() / workData.questions.size else 0f
            Log.i("CxVM", "submitHomework: 覆盖率 ${(coverage * 100).toInt()}% ($foundCount/${workData.questions.size})")

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
        }
    }

    /** 作业Tab随机答案生成 (与 ChaoxingStudyRepository.generateRandomAnswer 对齐) */
    private fun generateRandomAnswerForHomework(q: CxQuestion): String {
        return when (q.type) {
            "single" -> {
                val opts = q.options.split("\n").filter { it.isNotBlank() }
                if (opts.isNotEmpty()) opts.random().take(1) else ""
            }
            "multiple" -> {
                val opts = q.options.split("\n").filter { it.isNotBlank() }
                if (opts.size >= 3) opts.shuffled().take(2).map { it.take(1) }.sorted().joinToString("")
                else if (opts.isNotEmpty()) opts.random().take(1) else ""
            }
            "judgement" -> if (kotlin.random.Random.nextBoolean()) "true" else "false"
            "completion" -> "暂未作答"
            "shortanswer" -> "暂未作答"
            else -> ""
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
                speed = s.speed,
                concurrency = s.concurrency,
                autoSubmit = s.submitMode == "auto",
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
                speed = s.speed,
                autoSubmit = s.submitMode == "auto",
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
    val concurrency: Int = 1,            // 2026-06-23: 默认 1 节并发,避免速率过高被检测
    val notOpenAction: String = "retry",
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
