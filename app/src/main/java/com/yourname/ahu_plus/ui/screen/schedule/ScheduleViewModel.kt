package com.yourname.ahu_plus.ui.screen.schedule

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.debug.DebugClock
import com.yourname.ahu_plus.data.local.CourseNoteRepository
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.course.AssessmentPlan
import com.yourname.ahu_plus.data.model.course.RecordEntry
import com.yourname.ahu_plus.data.model.course.RecordType
import com.yourname.ahu_plus.data.model.jw.CourseActivity
import com.yourname.ahu_plus.data.model.jw.CourseDisplayItem
import com.yourname.ahu_plus.data.model.jw.CourseUnit
import com.yourname.ahu_plus.data.model.jw.GetDataLesson
import com.yourname.ahu_plus.data.model.jw.SemesterInfo
import com.yourname.ahu_plus.data.model.jw.UserScheduleItem
import com.yourname.ahu_plus.data.model.task.HomeworkRecord
import com.yourname.ahu_plus.data.model.task.RecentTaskItem
import com.yourname.ahu_plus.data.model.task.RecentTaskSource
import com.yourname.ahu_plus.data.model.task.UserTask
import com.yourname.ahu_plus.data.model.KqAttendanceRecord
import com.yourname.ahu_plus.data.repository.AssessmentRepository
import com.yourname.ahu_plus.data.repository.CourseRepository
import com.yourname.ahu_plus.data.repository.ExamRepository
import com.yourname.ahu_plus.data.repository.HomeworkRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.KqAttendanceRepository
import com.yourname.ahu_plus.data.repository.RecordRepository
import com.yourname.ahu_plus.data.repository.SessionExpiredException
import com.yourname.ahu_plus.data.repository.UserTaskRepository
import com.yourname.ahu_plus.ui.widget.TodayScheduleWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.util.UUID

class ScheduleViewModel(
    application: Application,
    private val jwAuthRepository: JwAuthRepository,
    private val courseRepository: CourseRepository,
    private val noteRepository: CourseNoteRepository,
    private val sessionManager: SessionManager? = null,
    private val assessmentRepository: AssessmentRepository,
    private val recordRepository: RecordRepository,
    private val homeworkRepository: HomeworkRepository,
    private val userTaskRepository: UserTaskRepository,
    private val examRepository: ExamRepository,
    private val kqAttendanceRepository: KqAttendanceRepository,
) : AndroidViewModel(application) {

    private val gson = com.yourname.ahu_plus.data.GsonProvider.instance
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    // ── 课程详情 sheet 内联状态 (按 lessonId+week 跟踪) ─────
    private val _courseNote = MutableStateFlow("")
    private val _slotNote = MutableStateFlow("")
    private val _assessmentPlan = MutableStateFlow<AssessmentPlan?>(null)
    private val _courseRecords = MutableStateFlow<List<RecordEntry>>(emptyList())
    private val _courseAttendance = MutableStateFlow<List<KqAttendanceRecord>>(emptyList())
    var courseNote: StateFlow<String> = _courseNote.asStateFlow()
        private set
    var slotNote: StateFlow<String> = _slotNote.asStateFlow()
        private set
    var assessmentPlan: StateFlow<AssessmentPlan?> = _assessmentPlan.asStateFlow()
        private set
    var courseRecords: StateFlow<List<RecordEntry>> = _courseRecords.asStateFlow()
        private set
    /** 当前课程匹配到的教务考勤记录 (kqcard.ahu.edu.cn) */
    var courseAttendance: StateFlow<List<KqAttendanceRecord>> = _courseAttendance.asStateFlow()
        private set

    // ── 设置变更触发器 (Profile 页改开关时 bump) ───────
    private val _settingsTicker = MutableStateFlow(0)

    // ── 跨 Repository 缓存(从 SessionManager / kq Repo 反序列化) ─────
    // 这两个原本是 `MutableStateFlow(loadXxxCached())` 内联表达式 — 由于属性只初始化一次,
    // 后续考勤/考试缓存更新 UI 看不到。这里抽出为字段,onRefresh / 外部刷新成功时
    // 调用 reload 重新读 cache 触发下游 combine 重算。
    private val _kqAttendanceCache = MutableStateFlow(loadKqAttendanceCached())
    private val _examsCache = MutableStateFlow(loadExamsSnapshot())

    /** 重新从持久层读 kqcard 考勤 / 考试缓存,driver 首页"今日考勤"和"近期任务"刷新 */
    private fun reloadCrossRepoCaches() {
        _kqAttendanceCache.value = loadKqAttendanceCached()
        _examsCache.value = loadExamsSnapshot()
    }

    /**
     * 首页今日课程考勤状态。
     * key = "课程名"，value = status (1=正常/已签到, 2=迟到, 3=缺勤)。
     * 由 kqcard 缓存数据按今日日期 + 课程名匹配产出。
     */
    val todayCourseAttendance: StateFlow<Map<String, Int>> = combine(
        _uiState,
        _settingsTicker,
        _kqAttendanceCache,
    ) { state, _, cached ->
        val today = DebugClock.todayDate()
        val todayStr = today.toString()
        val currentWeek = state.currentWeek
        val result = mutableMapOf<String, Int>()
        for (rec in cached) {
            val checkDate = rec.accountBean?.checkdate ?: continue
            if (checkDate != todayStr) continue
            val recWeek = rec.accountBean?.week
            if (recWeek != null && recWeek != currentWeek) continue
            val status = rec.classWaterBean?.status ?: continue
            val name = rec.subjectBean?.sName?.ifBlank { null }
                ?: rec.subjectBean?.sSimple?.ifBlank { null } ?: continue
            result[name] = status
        }
        result
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 一次性读取 kqcard 考勤缓存 */
    private fun loadKqAttendanceCached(): List<KqAttendanceRecord> {
        return kqAttendanceRepository.readCached()?.records ?: emptyList()
    }

    // ── 首页近期任务 (跨源合并) ───────────────────────
    val recentTasks: StateFlow<List<RecentTaskItem>> = combine(
        homeworkRepository.homework,
        userTaskRepository.tasks,
        _settingsTicker,
    ) { hw, tasks, _ -> hw to tasks }
        .combine(_examsCache) { (hw, tasks), exams ->
            val sm = sessionManager
            val showCompleted = sm?.getShowCompletedTasks() ?: false
            val showCompletedExams = sm?.getShowCompletedExams() ?: true
            buildRecentTasks(hw, tasks, exams, showCompleted, showCompletedExams)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // 从持久化恢复课表布局偏好
        val sm = sessionManager
        if (sm != null) {
            _uiState.update {
                it.copy(
                    colWidthDp = sm.getScheduleColWidth(),
                    rowHeightDp = sm.getScheduleRowHeight(),
                    fontScale = sm.getScheduleFontScale(),
                    showSat = sm.getShowSat(),
                    showSun = sm.getShowSun(),
                    pagerEnabled = sm.getPagerEnabled(),
                    resetOnEnter = sm.getResetOnEnter(),
                    showCompletedTasks = sm.getShowCompletedTasks(),
                    showCompletedExams = sm.getShowCompletedExams(),
                )
            }
        }
        // 优先加载本地缓存，再尝试静默网络刷新
        viewModelScope.launch {
            // 先加载用户自定义课表
            loadUserItems()
            // 后台加载学期列表(不影响缓存秒显示)
            launch { loadSemesterList() }
            val cached = loadFromCache()
            if (cached) {
                // 有缓存数据 → 后台静默刷新
                launch { loadScheduleData(isRefresh = true) }
            } else {
                // 无缓存 → 主动加载
                loadScheduleData(isRefresh = false)
            }
        }
    }

    /**
     * 加载本学生可用学期列表(HTML allSemesters 解析)。
     * 失败静默 — SemesterChips 空态不渲染即可。
     */
    private suspend fun loadSemesterList() {
        // 网络请求必须在 IO 线程,否则触发 NetworkOnMainThreadException
        withContext(Dispatchers.IO) {
            // 先确保 JW 已认证(否则 course-table 页面 302 跳登录)
            val authResult = jwAuthRepository.authenticate()
            if (authResult.isFailure) {
                Log.w(TAG, "学期列表加载跳过: 认证失败 ${authResult.exceptionOrNull()?.message}")
                return@withContext
            }
            courseRepository.getSemesterList().onSuccess { list ->
                _uiState.update { it.copy(availableSemesters = list) }
            }.onFailure { e ->
                Log.w(TAG, "学期列表加载失败(非致命): ${e.message}")
            }
        }
    }

    /** 加载用户自定义课表条目 */
    private fun loadUserItems() {
        val sm = sessionManager ?: return
        val json = sm.getUserScheduleJson() ?: return
        try {
            val items = gson.fromJson(json, Array<UserScheduleItem>::class.java).toList()
            _uiState.update { it.copy(userScheduleItems = items) }
        } catch (_: Exception) { Log.w(TAG, "Failed to parse user schedule items") }
    }

    /** 持久化用户自定义课表条目 */
    private fun saveUserItems() {
        val sm = sessionManager ?: return
        viewModelScope.launch {
            try {
                val json = gson.toJson(_uiState.value.userScheduleItems)
                sm.saveUserScheduleJson(json)
                TodayScheduleWidgetUpdater.updateAll(getApplication())
            } catch (_: Exception) { Log.w(TAG, "Failed to save user schedule items") }
        }
    }

    /** 添加用户自定义课表条目 */
    fun addUserScheduleItem(item: UserScheduleItem) {
        _uiState.update {
            it.copy(userScheduleItems = it.userScheduleItems + item)
        }
        saveUserItems()
        rebuildDisplayItems()
    }

    /** 删除用户自定义课表条目 */
    fun removeUserScheduleItem(id: String) {
        _uiState.update {
            it.copy(userScheduleItems = it.userScheduleItems.filter { i -> i.id != id })
        }
        saveUserItems()
        rebuildDisplayItems()
    }

    fun onToggleAddCourse() {
        _uiState.update { it.copy(showAddCourse = !it.showAddCourse) }
    }

    /** 从 SessionManager 恢复已缓存的课表 JSON */
    private suspend fun loadFromCache(): Boolean {
        val sm = sessionManager ?: return false
        val json = sm.getScheduleJson() ?: return false
        return try {
            withContext(Dispatchers.IO) {
                val data = gson
                    .fromJson(json, com.yourname.ahu_plus.data.model.jw.ScheduleData::class.java)
                val displayItems = buildDisplayItems(
                    activities = data.activities,
                    selectedWeek = data.currentWeek,
                    lessons = data.lessons
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        studentName = data.studentName,
                        className = data.className,
                        department = data.department,
                        credits = data.credits,
                        allActivities = data.activities,
                        displayItems = displayItems,
                        unitTimes = data.unitTimes,
                        semester = data.semester,
                        currentWeek = data.currentWeek,
                        selectedWeek = data.currentWeek,
                        weekIndices = data.weekIndices,
                        lessons = data.lessons,
                        // 本地缓存始终是本学期数据(写缓存策略仅 DEFAULT_SEMESTER_ID) → 同步更新
                        currentSemesterCurrentWeek = data.currentWeek,
                    )
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** courseCode → colorIndex 映射 (从 allActivities 构建一次,跨周稳定) */
    private var colorMap: Map<String, Int> = emptyMap()

    /** 为任意周计算 displayItems (供 WeekPager 各页使用) */
    fun buildDisplayItemsForWeek(week: Int): List<CourseDisplayItem> {
        val data = _uiState.value
        if (colorMap.isEmpty() && data.allActivities.isNotEmpty()) {
            colorMap = CourseRepository.buildColorMap(data.allActivities)
        }
        return buildDisplayItems(data.allActivities, week, data.lessons)
    }

    /** 合并教务课程与用户自定义条目的 displayItems,应用 showSat/showSun 过滤 */
    private fun buildDisplayItems(
        activities: List<CourseActivity>,
        selectedWeek: Int,
        lessons: List<GetDataLesson>?,
        showSat: Boolean = _uiState.value.showSat,
        showSun: Boolean = _uiState.value.showSun,
    ): List<CourseDisplayItem> {
        if (colorMap.isEmpty() && activities.isNotEmpty()) {
            colorMap = CourseRepository.buildColorMap(activities)
        }
        val systemItems = CourseRepository.toDisplayItems(activities, selectedWeek, lessons, colorMap)
        val userItems = _uiState.value.userScheduleItems
            .filter { it.weeks.contains(selectedWeek) }
            .map { it.toDisplayItem() }
        val all = (systemItems + userItems).sortedWith(compareBy({ it.weekday }, { it.startUnit }))
        return all.filter { item ->
            when (item.weekday) {
                6 -> showSat
                7 -> showSun
                else -> true
            }
        }
    }

    private fun rebuildDisplayItems() {
        val data = _uiState.value
        val items = buildDisplayItems(
            activities = data.allActivities,
            selectedWeek = data.selectedWeek,
            lessons = data.lessons
        )
        _uiState.update { it.copy(displayItems = items) }
    }

    /**
     * 加载指定学期课表。
     *
     * @param isRefresh true=静默刷新(不显示 loading)，false=主动加载
     * @param semesterId 目标学期 ID;默认 = 当前选中的学期
     *
     * **缓存策略**: 仅本学期 ([CourseRepository.DEFAULT_SEMESTER_ID]) 才写入本地缓存;
     * 其他学期按需加载(用户切换学期时实时拉取,不持久化)。
     */
    private suspend fun loadScheduleData(
        isRefresh: Boolean = false,
        semesterId: Int = _uiState.value.selectedSemesterId,
    ) {
        val isCurrentSemester = semesterId == CourseRepository.DEFAULT_SEMESTER_ID
        if (!isRefresh) {
            _uiState.update {
                it.copy(
                    // 非本学期切换需要显示 loading;本学期首屏也显示 loading
                    isLoading = it.allActivities.isEmpty() || !isCurrentSemester,
                    isLoadingSemester = !isCurrentSemester,
                    error = null,
                )
            }
        } else {
            _uiState.update { it.copy(isRefreshing = true) }
        }
        val wasLoaded = _uiState.value.allActivities.isNotEmpty()
        try {
            withContext(Dispatchers.IO) {
                // Step 1: 认证
                val authResult = jwAuthRepository.authenticate()
                if (authResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingSemester = false,
                            // 仅当本地无数据时才触发 needsLogin
                            needsLogin = !wasLoaded,
                            error = if (!wasLoaded) "教务处认证失败: ${authResult.exceptionOrNull()?.message}" else null
                        )
                    }
                    return@withContext
                }

                // Step 2: 获取课表(传入学期 ID)
                val result = courseRepository.getSchedule(semesterId)
                result.fold(
                    onSuccess = { data ->
                        // ★ 仅本学期写入本地缓存(其他学期按需加载)
                        if (isCurrentSemester) {
                            val sm = sessionManager
                            if (sm != null) {
                                try {
                                    val json = com.yourname.ahu_plus.data.GsonProvider.instance.toJson(data)
                                    sm.saveScheduleJson(json)
                                    TodayScheduleWidgetUpdater.updateAll(getApplication())
                                } catch (e: Exception) { Log.w(TAG, "Failed to cache schedule JSON: ${e.message}") }
                            }
                        }

                        val displayItems = buildDisplayItems(
                            activities = data.activities,
                            selectedWeek = data.currentWeek,
                            lessons = data.lessons
                        )
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoadingSemester = false,
                                error = null,
                                needsLogin = false,
                                studentName = data.studentName,
                                className = data.className,
                                department = data.department,
                                credits = data.credits,
                                allActivities = data.activities,
                                displayItems = displayItems,
                                unitTimes = data.unitTimes,
                                semester = data.semester,
                                currentWeek = data.currentWeek,
                                selectedWeek = data.currentWeek,
                                weekIndices = data.weekIndices,
                                lessons = data.lessons,
                                // 仅本学期的"当前周"会持久化,其他学期不影响
                                currentSemesterCurrentWeek = if (isCurrentSemester)
                                    data.currentWeek
                                else it.currentSemesterCurrentWeek,
                            )
                        }
                    },
                    onFailure = { e ->
                        // 2026-06-23: SessionExpiredException 时尝试后台静默重连 + 重试一次
                        if (e is SessionExpiredException) {
                            val retry = retryAfterSilentReauth(semesterId, isCurrentSemester)
                            if (retry != null) {
                                applyScheduleData(retry, isCurrentSemester, wasLoaded)
                                return@fold
                            }
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoadingSemester = false,
                                error = if (!wasLoaded) "课表加载失败: ${e.message}" else it.error
                            )
                        }
                    }
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingSemester = false,
                    error = if (!wasLoaded) "未知错误: ${e.message}" else it.error
                )
            }
        } finally {
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * 切换到指定学期。
     *
     * - 切到本学期 → 优先用本地缓存(秒显示),后端静默刷新
     * - 切到其他学期 → 清空旧数据,网络拉取(无缓存,按需加载)
     *
     * 切换时同时清空 activities/lessons/weekIndices/unitTimes 等学期相关字段,
     * 并重置 colorMap(不同学期的课程代码映射不同)。
     */
    fun selectSemester(semesterId: Int) {
        val current = _uiState.value.selectedSemesterId
        if (semesterId == current) return
        Log.d(TAG, "切换学期: $current → $semesterId")

        // 重置跨学期状态
        colorMap = emptyMap()
        _uiState.update {
            it.copy(
                selectedSemesterId = semesterId,
                // 清空学期相关展示态
                allActivities = emptyList(),
                displayItems = emptyList(),
                unitTimes = emptyList(),
                lessons = null,
                semester = null,
                currentWeek = 1,
                selectedWeek = 1,
                weekIndices = emptyList(),
                studentName = null,
                className = null,
                department = null,
                credits = null,
                error = null,
                needsLogin = false,
                // 关闭可能存在的详情 sheet
                selectedCourseDetail = null,
                // ★ currentSemesterCurrentWeek 不重置 — 跨学期跳转"今"按钮要用到
            )
        }

        if (semesterId == CourseRepository.DEFAULT_SEMESTER_ID) {
            // 切回本学期 → 优先用本地缓存
            viewModelScope.launch {
                val cached = loadFromCache()
                if (cached) {
                    launch { loadScheduleData(isRefresh = true, semesterId = semesterId) }
                } else {
                    loadScheduleData(isRefresh = false, semesterId = semesterId)
                }
            }
        } else {
            // 其他学期 → 网络按需加载
            viewModelScope.launch { loadScheduleData(isRefresh = false, semesterId = semesterId) }
        }
    }

    // ── 周切换 ─────────────────────────────────────

    fun onPreviousWeek() {
        val current = _uiState.value.selectedWeek
        val minWeek = _uiState.value.weekIndices.minOrNull() ?: 1
        if (current > minWeek) setSelectedWeek(current - 1)
    }

    fun onNextWeek() {
        val current = _uiState.value.selectedWeek
        val maxWeek = _uiState.value.weekIndices.maxOrNull() ?: 20
        if (current < maxWeek) setSelectedWeek(current + 1)
    }

    fun onWeekSelected(week: Int) {
        setSelectedWeek(week)
    }

    fun onRefresh() {
        viewModelScope.launch {
            // 跨 Repository 缓存(考勤 / 考试)可能在外部刷新过 — 重读一次让首页今日卡片同步
            reloadCrossRepoCaches()
            loadScheduleData(isRefresh = true)
        }
    }

    // ── 课表显示设置 ─────────────────────────────────

    fun onToggleSettings() {
        _uiState.update { it.copy(showSettings = !it.showSettings) }
    }

    fun onColWidthChanged(value: Float) {
        _uiState.update { it.copy(colWidthDp = value) }
        viewModelScope.launch { sessionManager?.saveScheduleColWidth(value) }
    }

    fun onRowHeightChanged(value: Float) {
        _uiState.update { it.copy(rowHeightDp = value) }
        viewModelScope.launch { sessionManager?.saveScheduleRowHeight(value) }
    }

    fun onFontScaleChanged(value: Float) {
        _uiState.update { it.copy(fontScale = value) }
        viewModelScope.launch { sessionManager?.saveScheduleFontScale(value) }
    }

    fun onResetSettings() {
        _uiState.update {
            it.copy(
                colWidthDp = 64f, rowHeightDp = 56f, fontScale = 1.0f,
                showSat = true, showSun = true,
                pagerEnabled = true, resetOnEnter = true,
                showCompletedTasks = false,
                showCompletedExams = false,
            )
        }
        viewModelScope.launch {
            sessionManager?.saveScheduleColWidth(64f)
            sessionManager?.saveScheduleRowHeight(56f)
            sessionManager?.saveScheduleFontScale(1.0f)
            sessionManager?.setShowSat(true)
            sessionManager?.setShowSun(true)
            sessionManager?.setPagerEnabled(true)
            sessionManager?.setResetOnEnter(true)
            sessionManager?.setShowCompletedTasks(false)
            sessionManager?.setShowCompletedExams(false)
        }
        rebuildDisplayItems()
    }

    // ── 课表显示设置 (5 个 Boolean) ──────────────────────

    fun setShowSat(value: Boolean) {
        _uiState.update { it.copy(showSat = value) }
        viewModelScope.launch { sessionManager?.setShowSat(value) }
        rebuildDisplayItems()
    }

    fun setShowSun(value: Boolean) {
        _uiState.update { it.copy(showSun = value) }
        viewModelScope.launch { sessionManager?.setShowSun(value) }
        rebuildDisplayItems()
    }

    fun setPagerEnabled(value: Boolean) {
        _uiState.update { it.copy(pagerEnabled = value) }
        viewModelScope.launch { sessionManager?.setPagerEnabled(value) }
    }

    fun setResetOnEnter(value: Boolean) {
        _uiState.update { it.copy(resetOnEnter = value) }
        viewModelScope.launch { sessionManager?.setResetOnEnter(value) }
    }

    fun setShowCompletedTasks(value: Boolean) {
        _uiState.update { it.copy(showCompletedTasks = value) }
        viewModelScope.launch {
            sessionManager?.setShowCompletedTasks(value)
            _settingsTicker.value++
        }
    }

    fun setShowCompletedExams(value: Boolean) {
        _uiState.update { it.copy(showCompletedExams = value) }
        viewModelScope.launch {
            sessionManager?.setShowCompletedExams(value)
            _settingsTicker.value++
        }
    }

    /** 计算当前课表状态在指定 weekday (6=周六, 7=周日) 上有课的所有节次 */
    fun affectedWeekendClasses(weekday: Int): List<AffectedClass> {
        val data = _uiState.value
        return data.allActivities
            .filter { it.weekday == weekday }
            .mapNotNull { act ->
                val firstWeek = act.weekIndexes?.minOrNull() ?: return@mapNotNull null
                val lastWeek = act.weekIndexes?.maxOrNull() ?: return@mapNotNull null
                if (firstWeek == lastWeek) "第 $firstWeek 周"
                else "第 $firstWeek-$lastWeek 周"
                act.courseName?.let { name ->
                    AffectedClass(
                        label = "$name (${act.weeksStr ?: "${firstWeek}-${lastWeek}"})",
                        weekday = weekday,
                    )
                }
            }
            .distinct()
    }

    fun setSelectedWeek(week: Int) {
        val data = _uiState.value
        val items = buildDisplayItems(
            activities = data.allActivities,
            selectedWeek = week,
            lessons = data.lessons
        )
        _uiState.update { it.copy(selectedWeek = week, displayItems = items) }
    }

    /**
     * "今"按钮: 跳转回本学期的本周课表。
     *
     * 如果当前查看的是其他学期,先切回 DEFAULT_SEMESTER_ID (触发 selectSemester 异步加载),
     * 然后在数据到达时 setSelectedWeek 到 currentSemesterCurrentWeek;
     * 如果已是本学期,直接 setSelectedWeek(currentSemesterCurrentWeek)。
     *
     * 解决用户报告的"看其他学期课表时点今会跳到该学期的当前周"bug。
     */
    fun jumpToCurrentWeek() {
        val data = _uiState.value
        if (data.selectedSemesterId != CourseRepository.DEFAULT_SEMESTER_ID) {
            // 切回本学期;本学期的当前周在 loadScheduleData 完成后会自动同步 setSelectedWeek
            selectSemester(CourseRepository.DEFAULT_SEMESTER_ID)
            // 同步把 selectedWeek 也设为本学期当前周(即使数据尚未加载,等数据到达后
            // loadScheduleData 会基于 currentSemesterCurrentWeek 触发 setSelectedWeek)
            _uiState.update { it.copy(selectedWeek = data.currentSemesterCurrentWeek) }
        } else {
            setSelectedWeek(data.currentSemesterCurrentWeek)
        }
    }

    /** 进入 ScheduleScreen 时调用: 若开关开启且不在当前周,跳到当前周 */
    fun applyEnterReset() {
        if (!_uiState.value.resetOnEnter) return
        val data = _uiState.value
        if (data.selectedWeek != data.currentWeek && data.currentWeek >= 1) {
            setSelectedWeek(data.currentWeek)
        }
    }

    // ── 课程详情 + 5 折叠 section 数据订阅 ───────────────

    fun onCourseClicked(item: CourseDisplayItem) {
        val courseCode = item.courseCode.orEmpty()
        val lessonId = item.lessonId.toString()
        val week = _uiState.value.selectedWeek
        val weekday = item.weekday
        _uiState.update {
            it.copy(
                selectedCourseDetail = CourseDetailUiModel(
                    item = item,
                    lessonDetail = item.lessonDetail,
                    currentWeek = week,
                )
            )
        }
        // 启动 Flow 订阅 (collect 在 viewModelScope,直到 sheet 关闭)
        courseNoteJob?.cancel()
        courseNoteJob = viewModelScope.launch {
            // 课程备注
            launch {
                noteRepository.observeCourseNote(courseCode).collect { _courseNote.value = it }
            }
            // 此节课备注
            launch {
                noteRepository.observeSlotNote(lessonId, week).collect { _slotNote.value = it }
            }
            // 考核方案
            launch {
                assessmentRepository.observe(lessonId).collect { _assessmentPlan.value = it }
            }
            // 该课程代码下的所有记录 (作业等)
            launch {
                recordRepository.recordsForCourse(courseCode).collect { records ->
                    _courseRecords.value = records
                }
            }
            // 教务考勤记录 (kqcard) — 全部周次
            launch {
                _courseAttendance.value = matchAttendanceForCourse(
                    courseName = item.courseName,
                )
            }
        }
    }

    fun onDismissSheet() {
        courseNoteJob?.cancel()
        courseNoteJob = null
        _courseNote.value = ""
        _slotNote.value = ""
        _assessmentPlan.value = null
        _courseRecords.value = emptyList()
        _courseAttendance.value = emptyList()
        _uiState.update { it.copy(selectedCourseDetail = null) }
    }

    // 保存课程备注
    fun saveCourseNote(text: String) {
        val cc = _uiState.value.selectedCourseDetail?.item?.courseCode.orEmpty()
        if (cc.isBlank()) return
        viewModelScope.launch { noteRepository.saveCourseNote(cc, text) }
    }

    // 保存此节课备注
    fun saveSlotNote(text: String) {
        val detail = _uiState.value.selectedCourseDetail ?: return
        val lessonId = detail.item.lessonId.toString()
        viewModelScope.launch { noteRepository.saveSlotNote(lessonId, detail.currentWeek, text) }
    }

    // ── 考核方案 (Step 4 完整版;此版本留空接口) ─────────

    fun saveAssessment(plan: AssessmentPlan) {
        viewModelScope.launch { assessmentRepository.save(plan) }
    }

    fun addAssessmentImage(uri: android.net.Uri): Unit {} // TODO: Step 4 接入
    fun removeAssessmentImage(path: String): Unit {} // TODO: Step 4 接入

    // ── 记录 (作业) ───────────────────────────────────

    /** 不依赖 selectedCourseDetail 的添加作业 (2026-06-17 Bug4 修复)。 */
    fun addQuickHomeworkForToday(text: String, deadline: Long?) {
        val today = DebugClock.todayDate()
        val todayWd = today.dayOfWeek.value
        val now = DebugClock.nowTime()
        val currentWeek = _uiState.value.currentWeek
        val items = _uiState.value.allActivities
            .filter { it.weekday == todayWd && (it.weekIndexes ?: emptyList()).contains(currentWeek) }
            .mapNotNull { act ->
                val start = act.startUnit ?: return@mapNotNull null
                val end = act.endUnit ?: start
                val startStr = act.startTime?.takeIf { it.isNotBlank() }
                    ?: _uiState.value.unitTimes.firstOrNull { it.indexNo == start }?.startTimeStr()
                val endStr = act.endTime?.takeIf { it.isNotBlank() }
                    ?: _uiState.value.unitTimes.firstOrNull { it.indexNo == end }?.endTimeStr()
                if (startStr == null || endStr == null) return@mapNotNull null
                val startMin = com.yourname.ahu_plus.data.model.jw.parseTimeMinutes(startStr) ?: return@mapNotNull null
                val endMin = com.yourname.ahu_plus.data.model.jw.parseTimeMinutes(endStr) ?: return@mapNotNull null
                val nowMin = now.hour * 60 + now.minute
                Quad(act, startMin, endMin, nowMin - startMin)
            }
        val target = items.firstOrNull { (_, s, e, elapsed) -> elapsed in 0..(e - s) }
            ?: items.firstOrNull { it.elapsed < 0 }
            ?: return
        val (act, _, _, _) = target
        val id = java.util.UUID.randomUUID().toString()
        val entry = com.yourname.ahu_plus.data.model.course.RecordEntry(
            id = id,
            lessonId = act.lessonId?.toString().orEmpty(),
            courseCode = act.courseCode.orEmpty(),
            courseName = act.courseName.orEmpty(),
            week = currentWeek,
            weekday = todayWd,
            startUnit = act.startUnit ?: 0,
            type = com.yourname.ahu_plus.data.model.course.RecordType.HOMEWORK,
            text = text,
            deadline = deadline,
        )
        val hw = com.yourname.ahu_plus.data.model.task.HomeworkRecord(
            id = id,
            lessonId = act.lessonId?.toString().orEmpty(),
            courseCode = act.courseCode.orEmpty(),
            courseName = act.courseName.orEmpty(),
            title = text,
            notes = null,
            deadline = deadline,
        )
        viewModelScope.launch {
            recordRepository.upsert(entry)
            homeworkRepository.upsert(hw)
        }
    }

    fun addHomework(text: String, deadline: Long?) {
        val detail = _uiState.value.selectedCourseDetail ?: return
        val id = UUID.randomUUID().toString()
        val item = detail.item
        val entry = RecordEntry(
            id = id,
            lessonId = item.lessonId.toString(),
            courseCode = item.courseCode.orEmpty(),
            courseName = item.courseName,
            week = detail.currentWeek,
            weekday = item.weekday,
            startUnit = item.startUnit,
            type = RecordType.HOMEWORK,
            text = text,
            deadline = deadline,
        )
        val hw = HomeworkRecord(
            id = id,
            lessonId = item.lessonId.toString(),
            courseCode = item.courseCode.orEmpty(),
            courseName = item.courseName,
            title = text,
            notes = null,
            deadline = deadline,
        )
        viewModelScope.launch {
            recordRepository.upsert(entry)
            homeworkRepository.upsert(hw)
        }
    }

    private var courseNoteJob: kotlinx.coroutines.Job? = null

    fun toggleRecordCompleted(recordId: String) {
        viewModelScope.launch {
            val snapshot = recordRepository.records.first()
            val current = snapshot.values.flatten().firstOrNull { it.id == recordId } ?: return@launch
            val newCompleted = !current.completed
            recordRepository.setCompleted(recordId, newCompleted)
            // 同步 HomeworkRecord 状态
            homeworkRepository.setCompleted(recordId, newCompleted)
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            recordRepository.delete(recordId)
            homeworkRepository.delete(recordId)
        }
    }

    // ── 首页近期任务 (用户自定义待办) ─────────────────

    fun addUserTask(title: String, subtitle: String?, dueAt: Long?) {
        viewModelScope.launch {
            userTaskRepository.upsert(
                UserTask(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    subtitle = subtitle,
                    dueAt = dueAt,
                )
            )
        }
    }

    fun toggleUserTask(taskId: String) {
        viewModelScope.launch { userTaskRepository.setCompleted(taskId, !isTaskCompleted(taskId)) }
    }

    private fun isTaskCompleted(taskId: String): Boolean {
        // 通过当前 recentTasks 派生 (UserTask 段)
        return recentTasks.value.firstOrNull { it.id == "task:$taskId" }?.isCompleted ?: false
    }

    fun deleteUserTask(taskId: String) {
        viewModelScope.launch { userTaskRepository.delete(taskId) }
    }

    /**
     * 统一处理"近期任务"勾选 (Bug3 修复)。
     * 根据 item.source 路由到对应 Repository,并同步 RecordEntry。
     */
    fun toggleRecentTask(item: com.yourname.ahu_plus.data.model.task.RecentTaskItem) {
        when (item.source) {
            com.yourname.ahu_plus.data.model.task.RecentTaskSource.USER_TASK -> {
                val id = item.id.removePrefix("task:")
                viewModelScope.launch { userTaskRepository.setCompleted(id, !item.isCompleted) }
            }
            com.yourname.ahu_plus.data.model.task.RecentTaskSource.HOMEWORK -> {
                val id = item.id.removePrefix("hw:")
                viewModelScope.launch {
                    homeworkRepository.setCompleted(id, !item.isCompleted)
                    recordRepository.setCompleted(id, !item.isCompleted)
                }
            }
            com.yourname.ahu_plus.data.model.task.RecentTaskSource.EXAM -> {
                // 考试不可勾选,忽略
            }
        }
    }

    // ── 教务考勤匹配 ──────────────────────────────────

    /**
     * 从 kqcard 缓存中匹配某门课的全部考勤记录 (跨所有周次)。
     *
     * 匹配规则: 课程名模糊匹配 (contains, 忽略空格)
     */
    private fun matchAttendanceForCourse(
        courseName: String,
    ): List<KqAttendanceRecord> {
        val cached = kqAttendanceRepository.readCached() ?: return emptyList()
        val normalizedName = courseName.replace("\\s".toRegex(), "")
        return cached.records.filter { rec ->
            val recName = (rec.subjectBean?.sName ?: rec.subjectBean?.sSimple ?: "")
                .replace("\\s".toRegex(), "")
            recName.contains(normalizedName, ignoreCase = true)
                    || normalizedName.contains(recName, ignoreCase = true)
        }
    }

    // ── 工具方法 ─────────────────────────────────────

    /** 加载考试缓存快照 (一次性,首页近期任务用) */
    private fun loadExamsSnapshot(): List<com.yourname.ahu_plus.data.model.jw.Exam> {
        val sm = sessionManager ?: return emptyList()
        val json = sm.getExamsJson() ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<com.yourname.ahu_plus.data.model.jw.Exam>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    /** 合并作业 + 考试 + 用户任务 → 近期任务列表 */
    private fun buildRecentTasks(
        homeworks: List<HomeworkRecord>,
        tasks: List<UserTask>,
        exams: List<com.yourname.ahu_plus.data.model.jw.Exam>,
        showCompleted: Boolean,
        showCompletedExams: Boolean,
    ): List<RecentTaskItem> {
        val hwItems = homeworks.map { hw ->
            RecentTaskItem(
                id = "hw:${hw.id}",
                source = RecentTaskSource.HOMEWORK,
                title = "${hw.courseName.ifBlank { "作业" }} · ${hw.title}",
                subtitle = hw.notes,
                dueAt = hw.deadline,
                isCompleted = hw.completed,
            )
        }
        val taskItems = tasks.map { t ->
            RecentTaskItem(
                id = "task:${t.id}",
                source = RecentTaskSource.USER_TASK,
                title = t.title,
                subtitle = t.subtitle,
                dueAt = t.dueAt,
                isCompleted = t.completed,
            )
        }
        val examItems = exams.mapNotNull { e ->
            val epoch = parseExamStartMillis(e.examTime) ?: return@mapNotNull null
            RecentTaskItem(
                id = "exam:${e.id}",
                source = RecentTaskSource.EXAM,
                title = e.displayCourse,
                subtitle = e.displayLocation + " · " + e.displayTime,
                dueAt = epoch,
                isCompleted = e.isFinished,
                payload = e.id,
            )
        }
        val merged = (hwItems + taskItems + examItems)
            .let { items ->
                val filtered = if (showCompleted) items
                else items.filter { !it.isCompleted && (it.source != RecentTaskSource.EXAM || !it.isCompleted) }
                // 2026-06-17 Bug2: 过滤已考完考试 (isFinished == true)
                if (showCompletedExams) filtered
                else filtered.filter { it.source != RecentTaskSource.EXAM || !it.isCompleted }
            }
        // 2026-06-17 Bug3: 已完成沉底,未完成按 dueAt 升序
        return merged.sortedWith(
            compareBy<RecentTaskItem> { it.isCompleted }         // 未完成在前
                .thenBy { it.dueAt == null }                     // 有截止在前
                .thenBy { it.dueAt ?: Long.MAX_VALUE }           // 截止早在前
        )
    }

    /**
     * 解析 Exam.examTime 字符串为 epoch millis。
     * 格式如 "2026-05-24 14:00~15:40" — 只取第一段。
     */
    private fun parseExamStartMillis(examTime: String): Long? {
        val regex = Regex("""(\d{4}-\d{2}-\d{2})\s+(\d{1,2}:\d{2})""")
        val match = regex.find(examTime) ?: return null
        val (date, time) = match.destructured
        return runCatching {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            sdf.parse("$date $time")?.time
        }.getOrNull()
    }

    private companion object {
        private const val TAG = "ScheduleVM"
    }

    /** 2026-06-23: SessionExpiredException 后尝试静默重连+重试一次。 */
    private suspend fun retryAfterSilentReauth(
        semesterId: Int,
        @Suppress("UNUSED_PARAMETER") isCurrentSemester: Boolean
    ): com.yourname.ahu_plus.data.model.jw.ScheduleData? {
        return try {
            jwAuthRepository.clearCookies()
            val authOk = jwAuthRepository.authenticate().isSuccess
            if (!authOk) {
                Log.w(TAG, "retryAfterSilentReauth: 静默重连失败,放弃重试")
                return null
            }
            courseRepository.getSchedule(semesterId).getOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "retryAfterSilentReauth 异常: ${e.message}")
            null
        }
    }

    /** 应用重连+重试后的课表数据。 */
    private suspend fun applyScheduleData(
        data: com.yourname.ahu_plus.data.model.jw.ScheduleData,
        isCurrentSemester: Boolean,
        @Suppress("UNUSED_PARAMETER") wasLoaded: Boolean
    ) {
        if (isCurrentSemester) {
            val sm = sessionManager
            if (sm != null) {
                try {
                    val json = com.yourname.ahu_plus.data.GsonProvider.instance.toJson(data)
                    sm.saveScheduleJson(json)
                    getApplication<android.app.Application>().let { app ->
                        TodayScheduleWidgetUpdater.updateAll(app)
                    }
                } catch (e: Exception) { Log.w(TAG, "Failed to cache schedule JSON: ${e.message}") }
            }
        }
        val displayItems = buildDisplayItems(
            activities = data.activities,
            selectedWeek = data.currentWeek,
            lessons = data.lessons
        )
        _uiState.update {
            it.copy(
                isLoading = false,
                isLoadingSemester = false,
                error = null,
                needsLogin = false,
                studentName = data.studentName,
                className = data.className,
                department = data.department,
                credits = data.credits,
                allActivities = data.activities,
                displayItems = displayItems,
                unitTimes = data.unitTimes,
                semester = data.semester,
                currentWeek = data.currentWeek,
                selectedWeek = data.currentWeek,
                weekIndices = data.weekIndices,
                lessons = data.lessons,
                currentSemesterCurrentWeek = if (isCurrentSemester) data.currentWeek else it.currentSemesterCurrentWeek,
            )
        }
    }
}

/** 受影响的周末课程 (供 ConfirmHideWeekendDialog 展示) */
data class AffectedClass(
    val label: String,
    val weekday: Int,
)

/** 四元组 (addQuickHomeworkForToday 内部用) */
private data class Quad(
    val act: CourseActivity,
    val start: Int,
    val end: Int,
    val elapsed: Int,
)

data class ScheduleUiState(
    val needsLogin: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,  // I-008: 下拉刷新状态
    val error: String? = null,
    val studentName: String? = null,
    val className: String? = null,
    val department: String? = null,
    val credits: Double? = null,
    val allActivities: List<CourseActivity> = emptyList(),
    val displayItems: List<CourseDisplayItem> = emptyList(),
    val unitTimes: List<CourseUnit> = emptyList(),
    val semester: SemesterInfo? = null,
    val currentWeek: Int = 1,
    val selectedWeek: Int = 1,
    val weekIndices: List<Int> = emptyList(),
    val lessons: List<GetDataLesson>? = null,

    /** 当前展示的课程详情 (null 表示 BottomSheet 不显示) */
    val selectedCourseDetail: CourseDetailUiModel? = null,

    // ── 用户自定义课表 ──────────────────────────────
    val userScheduleItems: List<UserScheduleItem> = emptyList(),
    val showAddCourse: Boolean = false,

    // ── 课表显示设置 ─────────────────────────────────
    val colWidthDp: Float = 64f,
    val rowHeightDp: Float = 56f,
    val fontScale: Float = 1.0f,
    val showSettings: Boolean = false,

    // ── 课表显示设置 2.0 (2026-06-17) ─────────────────
    val showSat: Boolean = true,
    val showSun: Boolean = true,
    val pagerEnabled: Boolean = true,
    val resetOnEnter: Boolean = true,
    val showCompletedTasks: Boolean = false,
    val showCompletedExams: Boolean = false,

    // ── 多学期切换 (2026-06-21) ─────────────────────
    /** 本学生可用学期列表(从 course-table HTML 解析) */
    val availableSemesters: List<SemesterInfo> = emptyList(),
    /** 当前选中的学期 ID;默认 = 本学期 DEFAULT_SEMESTER_ID */
    val selectedSemesterId: Int = com.yourname.ahu_plus.data.repository.CourseRepository.DEFAULT_SEMESTER_ID,
    /** 切换到非本学期时的 loading 状态(区别于首屏 isLoading) */
    val isLoadingSemester: Boolean = false,
    /**
     * 本学期 (DEFAULT_SEMESTER_ID) 的"当前周"。
     *
     * 当用户查看其他学期时, `currentWeek` 会被覆写为那个学期的当前周,
     * 但本学期的当前周需要持久保留,才能让"今"按钮在跨学期场景下正确跳转。
     *
     * 仅在 DEFAULT_SEMESTER_ID 学期数据加载成功时更新。
     */
    val currentSemesterCurrentWeek: Int = 1,
)

/**
 * 课程详情 UI 模型。
 *
 * 把 [CourseDisplayItem] + [GetDataLesson] 增强数据 + 当前周次 合并为一个不可变快照,
 * 供 [CourseDetailSheet] 渲染。
 */
data class CourseDetailUiModel(
    val item: CourseDisplayItem,
    val lessonDetail: GetDataLesson?,
    val currentWeek: Int = 1,
)
