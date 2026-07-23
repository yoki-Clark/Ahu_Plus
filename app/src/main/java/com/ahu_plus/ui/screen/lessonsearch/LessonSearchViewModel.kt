package com.ahu_plus.ui.screen.lessonsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.local.DataSnapshotStatus
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.jw.CourseDisplayItem
import com.ahu_plus.data.model.jw.CourseUnit
import com.ahu_plus.data.model.jw.LessonFilterOption
import com.ahu_plus.data.model.jw.LessonRecord
import com.ahu_plus.data.model.jw.LessonScheduleParser
import com.ahu_plus.data.model.jw.LessonSearchFilter
import com.ahu_plus.data.model.jw.LessonSearchMode
import com.ahu_plus.data.model.jw.LessonSearchResponse
import com.ahu_plus.data.model.jw.SemesterInfo
import com.ahu_plus.data.repository.CourseRepository
import com.ahu_plus.data.repository.ErrorClassifier
import com.ahu_plus.data.repository.JwAuthRepository
import com.ahu_plus.data.repository.LessonSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全校开课查询 ViewModel。
 *
 * 交互:选学期 + 关键词(名称/编号两种模式)搜索 + 分页"加载更多" + 客户端"隐藏满员"。
 * 数据:本地优先 —— 冷启动先渲染上次浏览的首屏(browse-all page1),再后台刷新。
 * 反竞态:queryGeneration + isCurrent(requestId),沿用 EmptyClassroomViewModel 套路。
 * 会话:失败经 ErrorClassifier 分类,仅"未加载且需重认证"时置 needsLogin;有缓存不清旧数据。
 */
class LessonSearchViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val lessonSearchRepository: LessonSearchRepository,
    private val courseRepository: CourseRepository,
    private val sessionManager: SessionManager? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonSearchUiState())
    val uiState: StateFlow<LessonSearchUiState> = _uiState.asStateFlow()

    private val gson = GsonProvider.instance
    private var queryGeneration = 0L
    private var queryJob: Job? = null

    private fun beginQuery(): Long {
        queryJob?.cancel()
        return ++queryGeneration
    }

    private fun isCurrent(requestId: Long): Boolean = requestId == queryGeneration

    init {
        val requestId = beginQuery()
        queryJob = viewModelScope.launch {
            // 1) 本地优先:先画上次浏览的首屏(若新鲜)。
            val restored = restoreFromCache(requestId)
            if (!isCurrent(requestId)) return@launch
            // 2) 拉学期列表,确定默认学期(优先本学期,回退缓存学期/首个)。
            val semesterId = resolveSemesters(requestId, prefer = _uiState.value.selectedSemesterId)
            if (!isCurrent(requestId)) return@launch
            // 3) 首屏:未命中缓存→加载;命中且学期一致→后台刷新;学期不同→重新加载。
            val cachedSameSemester = restored && _uiState.value.selectedSemesterId == semesterId
            loadPage(
                requestId = requestId,
                semesterId = semesterId,
                mode = _uiState.value.mode,
                keyword = "",
                page = 1,
                append = false,
                isRefresh = cachedSameSemester,
            )
        }
    }

    /** 拉学期列表并挑默认学期;失败不阻塞(保留 prefer/回退)。返回最终选中的学期 id。 */
    private suspend fun resolveSemesters(requestId: Long, prefer: Int?): Int {
        val result = withContext(Dispatchers.IO) {
            jwAuthRepository.executeWithSessionRetry { courseRepository.getSemesterList() }
        }
        val list = result.getOrNull().orEmpty()
        val chosen = pickDefaultSemester(list, prefer)
        if (isCurrent(requestId)) {
            _uiState.update {
                it.copy(
                    semesters = if (list.isNotEmpty()) list else it.semesters,
                    selectedSemesterId = chosen,
                )
            }
        }
        return chosen
    }

    /** 优先 prefer(若在列表内)> 本学期 DEFAULT_SEMESTER_ID(若在列表内)> 列表首个 > prefer > DEFAULT。 */
    private fun pickDefaultSemester(list: List<SemesterInfo>, prefer: Int?): Int {
        val ids = list.mapNotNull { it.id }
        prefer?.let { if (it in ids) return it }
        if (CourseRepository.DEFAULT_SEMESTER_ID in ids) return CourseRepository.DEFAULT_SEMESTER_ID
        ids.firstOrNull()?.let { return it }
        return prefer ?: CourseRepository.DEFAULT_SEMESTER_ID
    }

    fun selectSemester(semesterId: Int) {
        if (semesterId == _uiState.value.selectedSemesterId) return
        _uiState.update {
            it.copy(selectedSemesterId = semesterId, records = emptyList(), filteredRecords = emptyList())
        }
        runSearch()
    }

    fun onKeywordChange(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }
    }

    fun selectMode(mode: LessonSearchMode) {
        if (mode == _uiState.value.mode) return
        _uiState.update { it.copy(mode = mode) }
        // 已有关键词时切模式立即重搜;空关键词只切 chip,不打网络。
        if (_uiState.value.keyword.isNotBlank()) runSearch()
    }

    /** 点搜索按钮/回车:从第 1 页重新查询当前 semester+mode+keyword。 */
    fun onSearch() = runSearch()

    /** 下拉刷新:重跑当前查询(isRefresh=true,不清旧数据)。 */
    fun onRefresh() = runSearch(isRefresh = true)

    private fun runSearch(isRefresh: Boolean = false) {
        val state = _uiState.value
        val semesterId = state.selectedSemesterId
        val requestId = beginQuery()
        queryJob = viewModelScope.launch {
            loadPage(
                requestId = requestId,
                semesterId = semesterId,
                mode = state.mode,
                keyword = state.keyword,
                page = 1,
                append = false,
                isRefresh = isRefresh,
            )
        }
    }

    /** 加载下一页并追加。沿用当前 generation,新搜索会 cancel 本 job。 */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return
        val requestId = queryGeneration
        val nextPage = state.currentPage + 1
        _uiState.update { it.copy(isLoadingMore = true) }
        queryJob = viewModelScope.launch {
            loadPage(
                requestId = requestId,
                semesterId = state.selectedSemesterId,
                mode = state.mode,
                keyword = state.keyword,
                page = nextPage,
                append = true,
                isRefresh = false,
            )
        }
    }

    /** 客户端"隐藏满员"开关:仅重算 filteredRecords,不打网络(totalRows 仍是服务端全量)。 */
    fun setHideFull(hide: Boolean) {
        _uiState.update { it.copy(hideFull = hide, filteredRecords = applyFilter(it.records, hide)) }
    }

    private fun applyFilter(records: List<LessonRecord>, hideFull: Boolean): List<LessonRecord> =
        if (hideFull) records.filterNot { it.isFull() } else records

    private suspend fun loadPage(
        requestId: Long,
        semesterId: Int,
        mode: LessonSearchMode,
        keyword: String,
        page: Int,
        append: Boolean,
        isRefresh: Boolean,
    ) {
        if (!isCurrent(requestId)) return
        val wasLoaded = _uiState.value.records.isNotEmpty()
        if (!append) {
            _uiState.update {
                if (isRefresh) it.copy(isRefreshing = true)
                else it.copy(isLoading = !wasLoaded, error = null, needsLogin = false)
            }
        }

        // 组合生效筛选：已应用筛选 + 列表页当前 semester/mode/keyword。
        val filter = _uiState.value.appliedFilter.copy(
            semesterId = semesterId,
            mode = mode,
            keyword = keyword,
        )
        val result = withContext(Dispatchers.IO) {
            jwAuthRepository.executeWithSessionRetry {
                lessonSearchRepository.search(filter, page)
            }
        }
        if (!isCurrent(requestId)) return

        result.fold(
            onSuccess = { resp ->
                val newRecords = resp.data.orEmpty()
                val merged = if (append) _uiState.value.records + newRecords else newRecords
                val pageInfo = resp.page
                // 浏览全部(无关键词无筛选)的首屏写缓存(冷启动秒开);带关键词/筛选/翻页不缓存。
                if (!append && keyword.isBlank() && page == 1 && filter.activeCount == 0) {
                    saveBrowseCache(semesterId, resp)
                }
                _uiState.update {
                    if (!isCurrent(requestId)) return@update it
                    val grid = computeGrid(merged, it.canShowGrid, it.gridWeek)
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        records = merged,
                        filteredRecords = applyFilter(merged, it.hideFull),
                        currentPage = pageInfo?.currentPage ?: page,
                        totalPages = pageInfo?.totalPages ?: it.totalPages,
                        totalRows = pageInfo?.totalRows ?: merged.size,
                        error = null,
                        needsLogin = false,
                        dataStatus = DataSnapshotStatus.network(),
                        gridWeek = grid.week,
                        gridMaxWeek = grid.maxWeek,
                        gridDisplayItems = grid.items,
                        unparsedRecords = grid.unparsed,
                    )
                }
            },
            onFailure = { e ->
                _uiState.update {
                    if (!isCurrent(requestId)) return@update it
                    val reauth = ErrorClassifier.shouldReauth(ErrorClassifier.classify(e))
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        error = if (!wasLoaded) (e.message ?: "开课查询失败") else it.error,
                        needsLogin = !wasLoaded && reauth,
                        dataStatus = if (wasLoaded) it.dataStatus?.withFailedRefresh() else it.dataStatus,
                    )
                }
            }
        )
    }

    // ══════════════════════════════════════════════════════
    // 周网格课表计算
    // ══════════════════════════════════════════════════════

    private data class GridState(
        val week: Int,
        val maxWeek: Int,
        val items: List<CourseDisplayItem>,
        val unparsed: List<LessonRecord>,
    )

    /**
     * 从记录集算网格：仅在定位到单班([canShowGrid])时解析；否则返回空网格。
     * week 夹回 [1, maxWeek]（新查询由 applyFilter 预置为 1，翻页保持当前 week）。
     */
    private fun computeGrid(
        records: List<LessonRecord>,
        canShowGrid: Boolean,
        currentWeek: Int,
    ): GridState {
        if (!canShowGrid) return GridState(currentWeek, 1, emptyList(), emptyList())
        val parsed = records.map { it to LessonScheduleParser.parse(it) }
        val maxWeek = parsed.maxOfOrNull { LessonScheduleParser.maxWeek(it.second) }
            ?.coerceAtLeast(1) ?: 1
        val week = currentWeek.coerceIn(1, maxWeek)
        val items = parsed.flatMap { (rec, res) ->
            LessonScheduleParser.displayItemsFor(rec, res, week)
        }
        val unparsed = parsed.filter { it.second.hasUnparsed || !it.second.hasSlots }.map { it.first }
        return GridState(week, maxWeek, items, unparsed)
    }

    /** 重算当前记录在指定周的网格条目（切周/切视图用，不打网络）。 */
    private fun recomputeGrid(week: Int? = null) {
        _uiState.update {
            val grid = computeGrid(
                records = it.records,
                canShowGrid = it.canShowGrid,
                currentWeek = week ?: it.gridWeek,
            )
            it.copy(
                gridWeek = grid.week,
                gridMaxWeek = grid.maxWeek,
                gridDisplayItems = grid.items,
                unparsedRecords = grid.unparsed,
            )
        }
    }

    /** 切列表/课表视图（仅定位到单班时 GRID 生效）。 */
    fun setViewMode(mode: LessonViewMode) {
        if (mode == _uiState.value.viewMode) return
        _uiState.update { it.copy(viewMode = mode) }
        if (mode == LessonViewMode.GRID) recomputeGrid()
    }

    /** 网格切周。 */
    fun selectGridWeek(week: Int) {
        if (week == _uiState.value.gridWeek) return
        recomputeGrid(week)
    }

    /** 供 UI 渲染网格行标（节次时间）。lesson-search 无自带节次表，用全校标准 13 节。 */
    fun gridUnitTimes(): List<CourseUnit> = DEFAULT_UNIT_TIMES

    // ══════════════════════════════════════════════════════
    // 筛选面板：开合 / 草稿编辑 / 级联加载 / 应用·重置
    // ══════════════════════════════════════════════════════

    /** 打开面板：草稿 = 已应用筛选；懒加载学院/开课单位/教学楼选项。 */
    fun openFilterSheet() {
        _uiState.update { it.copy(filterSheetOpen = true, draftFilter = it.appliedFilter) }
        ensureDepartmentOptions()
        ensureMajorDeptOptions()
        val campusId = _uiState.value.draftFilter.campusId
        if (campusId != null) loadBuildings(campusId)
    }

    fun closeFilterSheet() {
        _uiState.update { it.copy(filterSheetOpen = false) }
    }

    /** 编辑草稿（通用）。传入变换函数直接改草稿。 */
    private fun editDraft(transform: (LessonSearchFilter) -> LessonSearchFilter) {
        _uiState.update { it.copy(draftFilter = transform(it.draftFilter)) }
    }

    fun toggleDraftDepartment(id: Long) = editDraft { f ->
        f.copy(departmentIds = f.departmentIds.toggle(id))
    }

    fun setDraftCourseType(id: Long?) = editDraft { it.copy(courseTypeId = id) }

    fun setDraftCampus(id: Long?) {
        editDraft { it.copy(campusId = id, buildingId = null) }
        _uiState.update { it.copy(buildingOptions = emptyList()) }
        if (id != null) loadBuildings(id)
    }

    fun setDraftCompulsory(value: String?) = editDraft { it.copy(compulsory = value) }
    fun setDraftExamMode(id: Long?) = editDraft { it.copy(examModeId = id) }
    fun setDraftTeachLang(id: Long?) = editDraft { it.copy(teachLangId = id) }

    fun toggleDraftWeekday(isoWeekday: Int) = editDraft { f ->
        f.copy(weekdays = f.weekdays.toggle(isoWeekday))
    }

    fun toggleDraftCourseUnit(indexNo: Int) = editDraft { f ->
        f.copy(courseUnitIndexes = f.courseUnitIndexes.toggle(indexNo))
    }

    fun setDraftBuilding(id: Long?) = editDraft { it.copy(buildingId = id) }
    fun setDraftRoomName(text: String) = editDraft { it.copy(roomNameLike = text.ifBlank { null }) }
    fun setDraftCredits(gte: Double?, lte: Double?) = editDraft { it.copy(creditsGte = gte, creditsLte = lte) }

    fun toggleDraftGrade(grade: String) {
        editDraft { f -> f.copy(grades = f.grades.toggle(grade), adminClassId = null) }
        reloadAdminClasses()
    }

    fun toggleDraftMajorDept(id: Long) {
        editDraft { f -> f.copy(majorDeptIds = f.majorDeptIds.toggle(id), adminClassId = null) }
        ensureMajorOptions()
        reloadAdminClasses()
    }

    fun toggleDraftMajor(id: Long) {
        editDraft { f -> f.copy(majorIds = f.majorIds.toggle(id), adminClassId = null) }
        reloadAdminClasses()
    }

    fun setDraftAdminClass(id: Long?) = editDraft { it.copy(adminClassId = id) }

    /** 应用草稿 → 触发查询。定位到单班时预置 GRID 视图，否则回 LIST。 */
    fun applyFilter() {
        val draft = _uiState.value.draftFilter
        _uiState.update {
            it.copy(
                appliedFilter = draft,
                filterSheetOpen = false,
                viewMode = if (draft.isSingleAdminClass) LessonViewMode.GRID else LessonViewMode.LIST,
                records = emptyList(),
                filteredRecords = emptyList(),
                gridDisplayItems = emptyList(),
                unparsedRecords = emptyList(),
                gridWeek = 1,
            )
        }
        runSearch()
    }

    /** 清空草稿（保留学期上下文），不立即查询——等用户再点应用。 */
    fun resetFilterDraft() {
        _uiState.update {
            it.copy(draftFilter = LessonSearchFilter(it.selectedSemesterId))
        }
    }

    /** 清空并立即应用（一键回到无筛选浏览）。 */
    fun clearFilter() {
        _uiState.update {
            it.copy(
                draftFilter = LessonSearchFilter(it.selectedSemesterId),
                appliedFilter = LessonSearchFilter(it.selectedSemesterId),
                filterSheetOpen = false,
                viewMode = LessonViewMode.LIST,
                records = emptyList(),
                filteredRecords = emptyList(),
                gridDisplayItems = emptyList(),
                unparsedRecords = emptyList(),
            )
        }
        runSearch()
    }

    // ── 级联选项加载 ──

    private fun ensureDepartmentOptions() {
        val s = _uiState.value
        if (s.departmentOptions.isNotEmpty() || s.loadingDepartments) return
        _uiState.update { it.copy(loadingDepartments = true) }
        viewModelScope.launch {
            val result = jwAuthRepository.executeWithSessionRetry { lessonSearchRepository.getDepartments() }
            val opts = result.getOrNull().orEmpty()
                .mapNotNull { d -> d.id?.let { id -> d.nameZh?.let { LessonFilterOption(id, it) } } }
            _uiState.update { it.copy(departmentOptions = opts, loadingDepartments = false) }
        }
    }

    private fun ensureMajorDeptOptions() {
        val s = _uiState.value
        if (s.majorDeptOptions.isNotEmpty() || s.loadingMajorDepts) return
        _uiState.update { it.copy(loadingMajorDepts = true) }
        viewModelScope.launch {
            val result = jwAuthRepository.executeWithSessionRetry { lessonSearchRepository.getMajorDepartments() }
            val opts = result.getOrNull().orEmpty().mapNotNull { it.toOption() }
            _uiState.update { it.copy(majorDeptOptions = opts, loadingMajorDepts = false) }
        }
    }

    private fun ensureMajorOptions() {
        val s = _uiState.value
        if (s.majorOptions.isNotEmpty() || s.loadingMajors) return
        _uiState.update { it.copy(loadingMajors = true) }
        viewModelScope.launch {
            val result = jwAuthRepository.executeWithSessionRetry { lessonSearchRepository.getMajors() }
            val opts = result.getOrNull().orEmpty().mapNotNull { it.toOption() }
            _uiState.update { it.copy(majorOptions = opts, loadingMajors = false) }
        }
    }

    /**
     * 年级/开课单位/专业任一变化后，重新拉行政班候选。
     *
     * ⚠ 运行时实测(2026-07-23 授权测试账号)：`search-adminclass` 端点上 **只有 `majors[]` 真正收窄**
     * listing（选专业 → 从 1256 收到个位/十位数）；`grades=`/`departments=` 服务端忽略（恒返回全部 1256，
     * 单数 `grade`/`gradeAssoc` 也无效）。因此年级过滤必须**客户端**按对象 `grade` 字段做。
     * 结论：专业是缩小行政班列表的关键维度；仅选年级时列表仍较大（该年级全部班），需靠客户端 grade 过滤兜住。
     */
    private fun reloadAdminClasses() {
        val draft = _uiState.value.draftFilter
        // 无任何定位维度时不请求（结果会过大且无意义）。
        if (draft.grades.isEmpty() && draft.majorDeptIds.isEmpty() && draft.majorIds.isEmpty()) {
            _uiState.update { it.copy(adminClassOptions = emptyList()) }
            return
        }
        _uiState.update { it.copy(loadingAdminClasses = true) }
        viewModelScope.launch {
            val result = jwAuthRepository.executeWithSessionRetry {
                // majorIds → majors[]（唯一服务端生效维度）；grades/majorDeptIds 传了也被服务端忽略。
                lessonSearchRepository.searchAdminClasses(draft.grades, draft.majorDeptIds, draft.majorIds)
            }
            val raw = result.getOrNull().orEmpty()
            // 客户端按对象 grade 字段过滤（服务端 grades= 不生效）。
            val filtered = if (draft.grades.isEmpty()) raw else raw.filter { it.grade in draft.grades }
            val opts = filtered.mapNotNull { it.toOption() }
            _uiState.update { it.copy(adminClassOptions = opts, loadingAdminClasses = false) }
        }
    }

    private fun loadBuildings(campusId: Long) {
        _uiState.update { it.copy(loadingBuildings = true) }
        viewModelScope.launch {
            val result = jwAuthRepository.executeWithSessionRetry { lessonSearchRepository.getBuildings(campusId) }
            val opts = result.getOrNull().orEmpty().mapNotNull { it.toOption() }
            _uiState.update { it.copy(buildingOptions = opts, loadingBuildings = false) }
        }
    }

    private fun <T> List<T>.toggle(item: T): List<T> =
        if (contains(item)) this - item else this + item

    private fun saveBrowseCache(semesterId: Int, resp: LessonSearchResponse) {
        val sm = sessionManager ?: return
        viewModelScope.launch {
            runCatching {
                val json = gson.toJson(CachedLessonBrowse(semesterId, resp))
                sm.saveLessonSearchJson(json)
            }
        }
    }

    /** 恢复上次浏览首屏(仅浏览全部首页,TTL 内)。命中则填充 records/selectedSemesterId。 */
    private suspend fun restoreFromCache(requestId: Long): Boolean {
        val sm = sessionManager ?: return false
        val json = sm.getLessonSearchJson() ?: return false
        val updatedAt = sm.getLessonSearchUpdatedAt()
        if (DebugClock.nowMillis() - updatedAt > CACHE_TTL_MS) return false
        return try {
            val cached = withContext(Dispatchers.IO) {
                gson.fromJson(json, CachedLessonBrowse::class.java)
            }
            val semesterId = cached?.semesterId ?: return false
            val records = cached.response?.data.orEmpty()
            if (records.isEmpty()) return false
            if (!isCurrent(requestId)) return true
            val pageInfo = cached.response?.page
            _uiState.update {
                if (!isCurrent(requestId)) return@update it
                it.copy(
                    isLoading = false,
                    selectedSemesterId = semesterId,
                    records = records,
                    filteredRecords = applyFilter(records, it.hideFull),
                    currentPage = pageInfo?.currentPage ?: 1,
                    totalPages = pageInfo?.totalPages ?: 1,
                    totalRows = pageInfo?.totalRows ?: records.size,
                    error = null,
                    needsLogin = false,
                    dataStatus = DataSnapshotStatus.cache(updatedAt),
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        /** 开课信息一学期内基本不变,缓存 6 小时够用(比空教室的 5 分钟宽松)。 */
        private const val CACHE_TTL_MS = 6L * 60 * 60 * 1000

        /** 全校标准 13 节次时间（与 CourseRepository.defaultUnitTimes 对齐），仅供网格行标。 */
        private val DEFAULT_UNIT_TIMES: List<CourseUnit> = listOf(
            800 to 845, 850 to 935, 950 to 1035, 1040 to 1125, 1130 to 1215,
            1400 to 1445, 1450 to 1535, 1550 to 1635, 1640 to 1725, 1730 to 1815,
            1900 to 1945, 1950 to 2035, 2040 to 2125,
        ).mapIndexed { i, (start, end) ->
            CourseUnit(
                nameZh = "${i + 1}",
                indexNo = i + 1,
                startTime = start,
                endTime = end,
                dayPart = null,
                name = "${i + 1}",
            )
        }
    }
}

/** 浏览全部首屏缓存包装:记录 semesterId 以便 restore 时对齐当前学期。 */
private data class CachedLessonBrowse(
    val semesterId: Int?,
    val response: LessonSearchResponse?,
)

/** 列表视图 vs 周网格课表视图（仅定位到单个教学班时才允许网格）。 */
enum class LessonViewMode { LIST, GRID }

data class LessonSearchUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val semesters: List<SemesterInfo> = emptyList(),
    val selectedSemesterId: Int = CourseRepository.DEFAULT_SEMESTER_ID,
    val keyword: String = "",
    val mode: LessonSearchMode = LessonSearchMode.NAME,
    val records: List<LessonRecord> = emptyList(),
    val filteredRecords: List<LessonRecord> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalRows: Int = 0,
    val hideFull: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false,
    val dataStatus: DataSnapshotStatus? = null,
    // ── 全量筛选（2026-07-23 升级） ──
    /** 已应用的筛选（点「应用筛选」后落地，驱动实际查询）。 */
    val appliedFilter: LessonSearchFilter = LessonSearchFilter(CourseRepository.DEFAULT_SEMESTER_ID),
    /** 面板编辑中的草稿筛选（点「应用筛选」才 → appliedFilter）。 */
    val draftFilter: LessonSearchFilter = LessonSearchFilter(CourseRepository.DEFAULT_SEMESTER_ID),
    /** 底部筛选面板是否展开。 */
    val filterSheetOpen: Boolean = false,
    // 级联选项列表（懒加载）+ 各自 loading。
    val departmentOptions: List<LessonFilterOption> = emptyList(),
    val majorDeptOptions: List<LessonFilterOption> = emptyList(),
    val majorOptions: List<LessonFilterOption> = emptyList(),
    val adminClassOptions: List<LessonFilterOption> = emptyList(),
    val buildingOptions: List<LessonFilterOption> = emptyList(),
    val loadingDepartments: Boolean = false,
    val loadingMajorDepts: Boolean = false,
    val loadingMajors: Boolean = false,
    val loadingAdminClasses: Boolean = false,
    val loadingBuildings: Boolean = false,
    // ── 课表（周网格）视图 ──
    val viewMode: LessonViewMode = LessonViewMode.LIST,
    /** 网格当前展示的周次（1-based）。 */
    val gridWeek: Int = 1,
    /** 全部时段覆盖的最大周次（周次下拉上界）。 */
    val gridMaxWeek: Int = 1,
    /** 当前周网格条目（已按 gridWeek 过滤）。 */
    val gridDisplayItems: List<CourseDisplayItem> = emptyList(),
    /** 排课文本解析失败、落到网格下方兜底列表的记录。 */
    val unparsedRecords: List<LessonRecord> = emptyList(),
) {
    /** 还有下一页(且当前不是带关键词的空结果)。 */
    val hasMore: Boolean get() = currentPage < totalPages

    /** 当前是否浏览全部(无关键词)。 */
    val isBrowsing: Boolean get() = keyword.isBlank()

    /** 当前选中学期的中文名(用于下拉锚点显示)。 */
    val selectedSemesterName: String?
        get() = semesters.firstOrNull { it.id == selectedSemesterId }?.nameZh

    /** 已激活的筛选维度数（供「筛选」入口徽标）。 */
    val activeFilterCount: Int get() = appliedFilter.activeCount

    /** 恰好定位到单个教学班 → 允许「课表」网格切换。 */
    val canShowGrid: Boolean get() = appliedFilter.isSingleAdminClass

    /** 网格视图生效（既定位到单班、又切到 GRID）。 */
    val isGridActive: Boolean get() = canShowGrid && viewMode == LessonViewMode.GRID
}
