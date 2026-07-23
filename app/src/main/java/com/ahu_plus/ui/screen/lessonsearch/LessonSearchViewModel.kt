package com.ahu_plus.ui.screen.lessonsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.local.DataSnapshotStatus
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.jw.CourseDisplayItem
import com.ahu_plus.data.model.jw.CourseUnit
import com.ahu_plus.data.model.jw.LessonAdminClass
import com.ahu_plus.data.model.jw.LessonFilterOption
import com.ahu_plus.data.model.jw.LessonMajorNode
import com.ahu_plus.data.model.jw.LessonInlineOptions
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        val cur = _uiState.value
        if (semesterId == cur.selectedSemesterId) return
        _uiState.update {
            it.copy(
                selectedSemesterId = semesterId,
                records = emptyList(), filteredRecords = emptyList(),
                gridDisplayItems = emptyList(), unparsedRecords = emptyList(),
            )
        }
        // 班级课表模式未定位到行政班时不查询（会误浏览全部）；已定位则重查该班当学期开课。
        if (cur.screenMode == LessonScreenMode.COURSE_LIST || cur.appliedFilter.adminClassId != null) {
            runSearch()
        }
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

    /** 打开开课属性筛选面板：草稿 = 已应用筛选；懒加载开课学院/教学楼选项。 */
    fun openFilterSheet() {
        _uiState.update { it.copy(filterSheetOpen = true, draftFilter = it.appliedFilter) }
        ensureDepartmentOptions()
        val campusId = _uiState.value.draftFilter.campusId
        if (campusId != null) loadBuildings(campusId)
    }

    // ══════════════════════════════════════════════════════
    // 顶层模式切换 + 班级课表级联（即时生效，不走 draft）
    // ══════════════════════════════════════════════════════

    /**
     * 切换开课列表 / 班级课表模式：清理不属于目标模式的筛选维度并重查。
     *  - → COURSE_LIST：清级联定位（年级/专业/行政班），回列表浏览。
     *  - → CLASS_SCHEDULE：清开课属性筛选，进入三级定位（未选到行政班前不查询，显示空态）。
     */
    fun setScreenMode(mode: LessonScreenMode) {
        val cur = _uiState.value
        if (mode == cur.screenMode) return
        val cleared = when (mode) {
            LessonScreenMode.COURSE_LIST -> cur.appliedFilter.copy(
                grades = emptyList(), majorDeptIds = emptyList(),
                majorIds = emptyList(), adminClassId = null,
            )
            LessonScreenMode.CLASS_SCHEDULE -> cur.appliedFilter.copy(
                departmentIds = emptyList(), courseTypeId = null, campusId = null,
                compulsory = null, examModeId = null, teachLangId = null,
                weekdays = emptyList(), courseUnitIndexes = emptyList(),
                buildingId = null, roomNameLike = null, creditsGte = null, creditsLte = null,
                keyword = "",
            )
        }
        _uiState.update {
            it.copy(
                screenMode = mode,
                appliedFilter = cleared,
                draftFilter = cleared,
                keyword = if (mode == LessonScreenMode.CLASS_SCHEDULE) "" else it.keyword,
                filterSheetOpen = false,
                viewMode = LessonViewMode.LIST,
                records = emptyList(),
                filteredRecords = emptyList(),
                gridDisplayItems = emptyList(),
                unparsedRecords = emptyList(),
                gridWeek = 1,
                error = null,
                needsLogin = false,
            )
        }
        when (mode) {
            LessonScreenMode.COURSE_LIST -> runSearch()
            LessonScreenMode.CLASS_SCHEDULE -> {
                ensureMajorDeptOptions()
                cleared.majorDeptIds.firstOrNull()?.let { loadMajorsForDepartment(it) }
                if (cleared.majorIds.isNotEmpty()) reloadAdminClasses()
                if (cleared.adminClassId != null) runSearch() // 保留了行政班：直接出课表
            }
        }
    }

    /**
     * 班级课表：选学院（级联第 1 环，取自开课单位列表）。
     * 变更后清专业/行政班/年级过滤与预探班数，并按学院拉专业候选。
     */
    fun selectClassDepartment(id: Long?) {
        _uiState.update {
            it.copy(
                appliedFilter = it.appliedFilter.copy(
                    majorDeptIds = id?.let { d -> listOf(d) } ?: emptyList(),
                    majorIds = emptyList(),
                    adminClassId = null,
                ),
                scopedMajorNodes = emptyList(),
                majorClassCounts = emptyMap(),
                rawAdminClasses = emptyList(),
                records = emptyList(), filteredRecords = emptyList(),
                gridDisplayItems = emptyList(), unparsedRecords = emptyList(),
            )
        }
        if (id != null) loadMajorsForDepartment(id)
    }

    /** 班级课表：选专业（级联第 2 环，服务端唯一收窄维度）。变更后清行政班并拉行政班候选。 */
    fun selectClassMajor(id: Long?) {
        _uiState.update {
            it.copy(
                appliedFilter = it.appliedFilter.copy(
                    majorIds = id?.let { m -> listOf(m) } ?: emptyList(),
                    adminClassId = null,
                ),
                rawAdminClasses = emptyList(),
                records = emptyList(), filteredRecords = emptyList(),
                gridDisplayItems = emptyList(), unparsedRecords = emptyList(),
            )
        }
        reloadAdminClasses()
    }

    /**
     * 班级课表：选年级（可选客户端过滤，收窄行政班列表）。
     * 服务端 `search-adminclass` 忽略 grades，故这里**不发网络**，仅改过滤条件；
     * [LessonSearchUiState.adminClassOptions] 会按新年级从已加载的 [rawAdminClasses] 重算。
     */
    fun selectClassGrade(grade: String?) {
        _uiState.update {
            it.copy(
                appliedFilter = it.appliedFilter.copy(
                    grades = grade?.let { g -> listOf(g) } ?: emptyList(),
                    adminClassId = null,
                ),
                records = emptyList(), filteredRecords = emptyList(),
                gridDisplayItems = emptyList(), unparsedRecords = emptyList(),
            )
        }
    }

    /** 班级课表：选行政班（定位钥匙）→ 立即查询该班全部开课并预置 GRID 视图。 */
    fun selectClassAdminClass(id: Long?) {
        _uiState.update {
            it.copy(
                appliedFilter = it.appliedFilter.copy(adminClassId = id),
                viewMode = if (id != null) LessonViewMode.GRID else LessonViewMode.LIST,
                records = emptyList(), filteredRecords = emptyList(),
                gridDisplayItems = emptyList(), unparsedRecords = emptyList(),
                gridWeek = 1,
            )
        }
        if (id != null) runSearch()
    }

    /** 展开学院下拉时确保学院候选已加载。 */
    fun ensureClassDepartmentOptions() = ensureMajorDeptOptions()

    /** 移除某个生效开课属性筛选（chip × ）→ 立即重查。 */
    fun removeAppliedFilter(dimension: LessonFilterDimension) {
        _uiState.update {
            val next = clearFilterDimension(it.appliedFilter, dimension)
            it.copy(appliedFilter = next, draftFilter = next, records = emptyList(), filteredRecords = emptyList())
        }
        runSearch()
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

    fun clearDraftDepartments() = editDraft { it.copy(departmentIds = emptyList()) }

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

    /**
     * 应用开课属性筛选草稿 → 触发查询（开课列表模式）。
     * 保留级联定位维度（本模式恒为空，但不误清），回列表视图。
     */
    fun applyFilter() {
        val draft = _uiState.value.draftFilter
        _uiState.update {
            it.copy(
                appliedFilter = draft,
                filterSheetOpen = false,
                viewMode = LessonViewMode.LIST,
                records = emptyList(),
                filteredRecords = emptyList(),
                gridDisplayItems = emptyList(),
                unparsedRecords = emptyList(),
                gridWeek = 1,
            )
        }
        runSearch()
    }

    /** 清空开课属性草稿（保留学期/级联上下文），不立即查询——等用户再点应用。 */
    fun resetFilterDraft() {
        _uiState.update {
            val kept = it.appliedFilter.copy(
                departmentIds = emptyList(), courseTypeId = null, campusId = null,
                compulsory = null, examModeId = null, teachLangId = null,
                weekdays = emptyList(), courseUnitIndexes = emptyList(),
                buildingId = null, roomNameLike = null, creditsGte = null, creditsLte = null,
            )
            it.copy(draftFilter = kept)
        }
    }

    /** 清空开课属性筛选并立即应用（一键回到无筛选浏览；只清属性，保留级联/学期）。 */
    fun clearFilter() {
        _uiState.update {
            val kept = it.appliedFilter.copy(
                departmentIds = emptyList(), courseTypeId = null, campusId = null,
                compulsory = null, examModeId = null, teachLangId = null,
                weekdays = emptyList(), courseUnitIndexes = emptyList(),
                buildingId = null, roomNameLike = null, creditsGte = null, creditsLte = null,
            )
            it.copy(
                draftFilter = kept,
                appliedFilter = kept,
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

    /** 加载学院候选（开课单位列表；学院→专业级联第 1 环）。 */
    private fun ensureMajorDeptOptions() {
        val s = _uiState.value
        if (s.majorDeptOptions.isNotEmpty() || s.loadingMajorDepts) return
        _uiState.update { it.copy(loadingMajorDepts = true) }
        viewModelScope.launch {
            val result = jwAuthRepository.executeWithSessionRetry { lessonSearchRepository.getMajorDepartments() }
            val opts = result.getOrNull().orEmpty().mapNotNull { node ->
                node.id?.let { id -> LessonFilterOption(id, node.displayName.ifBlank { node.nameZh.orEmpty() }) }
            }.filter { it.nameZh.isNotBlank() }
            _uiState.update { it.copy(majorDeptOptions = opts, loadingMajorDepts = false) }
        }
    }

    /**
     * 选定学院后按学院拉专业候选，并后台并发预探每个专业的行政班数（僵尸专业过滤 + 班数标注）。
     * 防竞态：以 [LessonSearchFilter.majorDeptIds] 为准，学院已切换则丢弃本次结果。
     */
    private fun loadMajorsForDepartment(departmentId: Long) {
        _uiState.update { it.copy(loadingMajors = true, scopedMajorNodes = emptyList(), majorClassCounts = emptyMap()) }
        viewModelScope.launch {
            val result = jwAuthRepository.executeWithSessionRetry {
                lessonSearchRepository.getMajorsByDepartment(departmentId)
            }
            val nodes = result.getOrNull().orEmpty().filter { it.id != null && it.displayName.isNotBlank() }
            _uiState.update { st ->
                if (st.appliedFilter.majorDeptIds.firstOrNull() != departmentId) st
                else st.copy(scopedMajorNodes = nodes, loadingMajors = false)
            }
            probeMajorClassCounts(departmentId, nodes.mapNotNull { it.id })
        }
    }

    /**
     * 后台并发预探每个专业的行政班数：`search-adminclass?majors[]=<id>` 的返回条数即该专业班数。
     * 只回填成功探到的计数（失败/未探到的专业不进 map → UI 不隐藏、也不标注，避免误杀）。
     * 防竞态：学院已切换（[LessonSearchFilter.majorDeptIds] 变化）则丢弃回填。
     */
    private fun probeMajorClassCounts(departmentId: Long, majorIds: List<Long>) {
        if (majorIds.isEmpty()) return
        _uiState.update { it.copy(probingMajorCounts = true) }
        viewModelScope.launch {
            val counts: Map<Long, Int> = majorIds.map { id ->
                async {
                    val r = jwAuthRepository.executeWithSessionRetry {
                        lessonSearchRepository.searchAdminClasses(majorIds = listOf(id))
                    }
                    r.getOrNull()?.let { id to it.size }
                }
            }.awaitAll().filterNotNull().toMap()
            _uiState.update { st ->
                if (st.appliedFilter.majorDeptIds.firstOrNull() != departmentId) st
                else st.copy(majorClassCounts = st.majorClassCounts + counts, probingMajorCounts = false)
            }
        }
    }

    /**
     * 选定专业后拉行政班候选（缓存整份未过滤列表到 [LessonSearchUiState.rawAdminClasses]）。
     *
     * ⚠ 运行时实测(2026-07-23 授权测试账号)：`search-adminclass` 端点上 **只有 `majors[]` 真正收窄**
     * listing；`grades=`/`departments=` 服务端忽略。故年级只作客户端过滤（在 UiState.adminClassOptions
     * 里按对象 `grade` 字段收窄），这里不带 grades，也不预过滤，整份缓存下来供切换年级时零网络重算。
     */
    private fun reloadAdminClasses() {
        val f = _uiState.value.appliedFilter
        // 未选专业时不请求（学院级列表过大且无意义；专业是唯一服务端收窄维度）。
        if (f.majorIds.isEmpty()) {
            _uiState.update { it.copy(rawAdminClasses = emptyList(), loadingAdminClasses = false) }
            return
        }
        val reqMajorIds = f.majorIds
        _uiState.update { it.copy(loadingAdminClasses = true) }
        viewModelScope.launch {
            val result = jwAuthRepository.executeWithSessionRetry {
                lessonSearchRepository.searchAdminClasses(majorIds = reqMajorIds)
            }
            val raw = result.getOrNull().orEmpty()
            _uiState.update { st ->
                // 防竞态：专业已变则丢弃本次结果。
                if (st.appliedFilter.majorIds != reqMajorIds) st
                else st.copy(rawAdminClasses = raw, loadingAdminClasses = false)
            }
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

/** 列表视图 vs 周网格课表视图（仅班级课表模式定位到单班时才允许网格）。 */
enum class LessonViewMode { LIST, GRID }

/**
 * 顶层任务模式（2026-07-23 双模式重构）：
 *  - [COURSE_LIST] 开课列表：关键词 + 开课属性筛选 → 卡片列表。
 *  - [CLASS_SCHEDULE] 班级课表：年级→专业→行政班三级定位 → 该班周网格。
 * 两模式共用学期上下文与同一搜索端点；切模式清理不属于该模式的筛选维度。
 */
enum class LessonScreenMode { COURSE_LIST, CLASS_SCHEDULE }

/**
 * 开课属性筛选维度（用于「生效筛选」可移除 chip）。
 * 仅覆盖开课列表模式的服务端属性维度；班级课表级联用常驻下拉，不走 chip。
 */
enum class LessonFilterDimension {
    DEPARTMENTS, COURSE_TYPE, CAMPUS, COMPULSORY, EXAM_MODE, TEACH_LANG,
    WEEKDAYS, COURSE_UNITS, BUILDING, ROOM, CREDITS,
}

/** 一枚生效筛选 chip：维度 + 展示文案。点 × 调 [LessonSearchViewModel.removeAppliedFilter]。 */
data class ActiveFilterChip(val dimension: LessonFilterDimension, val label: String)

/**
 * 从筛选中清掉某个开课属性维度（纯函数，与 [buildActiveFilterChips] 对称，便于单测）。
 * CAMPUS 联动清 building（教学楼依赖校区）。
 */
internal fun clearFilterDimension(
    filter: LessonSearchFilter,
    dimension: LessonFilterDimension,
): LessonSearchFilter = when (dimension) {
    LessonFilterDimension.DEPARTMENTS -> filter.copy(departmentIds = emptyList())
    LessonFilterDimension.COURSE_TYPE -> filter.copy(courseTypeId = null)
    LessonFilterDimension.CAMPUS -> filter.copy(campusId = null, buildingId = null)
    LessonFilterDimension.COMPULSORY -> filter.copy(compulsory = null)
    LessonFilterDimension.EXAM_MODE -> filter.copy(examModeId = null)
    LessonFilterDimension.TEACH_LANG -> filter.copy(teachLangId = null)
    LessonFilterDimension.WEEKDAYS -> filter.copy(weekdays = emptyList())
    LessonFilterDimension.COURSE_UNITS -> filter.copy(courseUnitIndexes = emptyList())
    LessonFilterDimension.BUILDING -> filter.copy(buildingId = null)
    LessonFilterDimension.ROOM -> filter.copy(roomNameLike = null)
    LessonFilterDimension.CREDITS -> filter.copy(creditsGte = null, creditsLte = null)
}

/**
 * 从已应用筛选构造「生效筛选」chip 列表（纯函数，便于单测）。
 * 需要 [departmentOptions]/[buildingOptions] 解析 id→名；未加载到名时降级为计数文案。
 */
internal fun buildActiveFilterChips(
    filter: LessonSearchFilter,
    departmentOptions: List<LessonFilterOption>,
    buildingOptions: List<LessonFilterOption>,
): List<ActiveFilterChip> {
    val chips = mutableListOf<ActiveFilterChip>()
    if (filter.departmentIds.isNotEmpty()) {
        val names = filter.departmentIds.mapNotNull { id -> departmentOptions.firstOrNull { it.id == id }?.nameZh }
        val label = when {
            names.isEmpty() -> "学院 ${filter.departmentIds.size} 项"
            names.size == 1 -> names.first()
            else -> "${names.first()} 等 ${filter.departmentIds.size} 个学院"
        }
        chips += ActiveFilterChip(LessonFilterDimension.DEPARTMENTS, label)
    }
    filter.courseTypeId?.let { id ->
        chips += ActiveFilterChip(
            LessonFilterDimension.COURSE_TYPE,
            LessonInlineOptions.COURSE_TYPES.firstOrNull { it.id == id }?.nameZh ?: "课程类型",
        )
    }
    filter.campusId?.let { id ->
        chips += ActiveFilterChip(
            LessonFilterDimension.CAMPUS,
            LessonInlineOptions.CAMPUSES.firstOrNull { it.id == id }?.nameZh ?: "校区",
        )
    }
    filter.compulsory?.takeIf { it.isNotBlank() }?.let { value ->
        chips += ActiveFilterChip(
            LessonFilterDimension.COMPULSORY,
            LessonInlineOptions.COMPULSORY.firstOrNull { it.first == value }?.second ?: "性质",
        )
    }
    filter.examModeId?.let { id ->
        chips += ActiveFilterChip(
            LessonFilterDimension.EXAM_MODE,
            LessonInlineOptions.EXAM_MODES.firstOrNull { it.id == id }?.nameZh ?: "考核",
        )
    }
    filter.teachLangId?.let { id ->
        chips += ActiveFilterChip(
            LessonFilterDimension.TEACH_LANG,
            LessonInlineOptions.TEACH_LANGS.firstOrNull { it.id == id }?.nameZh ?: "语言",
        )
    }
    if (filter.weekdays.isNotEmpty()) {
        val names = filter.weekdays.sorted().joinToString("/") { WEEKDAY_LABELS[it] ?: it.toString() }
        chips += ActiveFilterChip(LessonFilterDimension.WEEKDAYS, "周$names")
    }
    if (filter.courseUnitIndexes.isNotEmpty()) {
        val units = filter.courseUnitIndexes.sorted().joinToString(",")
        chips += ActiveFilterChip(LessonFilterDimension.COURSE_UNITS, "第 $units 节")
    }
    filter.buildingId?.let { id ->
        chips += ActiveFilterChip(
            LessonFilterDimension.BUILDING,
            buildingOptions.firstOrNull { it.id == id }?.nameZh ?: "教学楼",
        )
    }
    filter.roomNameLike?.takeIf { it.isNotBlank() }?.let { room ->
        chips += ActiveFilterChip(LessonFilterDimension.ROOM, "教室:$room")
    }
    if (filter.creditsGte != null || filter.creditsLte != null) {
        val lo = filter.creditsGte?.let { formatCreditLabel(it) } ?: ""
        val hi = filter.creditsLte?.let { formatCreditLabel(it) } ?: ""
        val label = when {
            lo.isNotEmpty() && hi.isNotEmpty() -> "$lo~$hi 学分"
            lo.isNotEmpty() -> "≥$lo 学分"
            else -> "≤$hi 学分"
        }
        chips += ActiveFilterChip(LessonFilterDimension.CREDITS, label)
    }
    return chips
}

private val WEEKDAY_LABELS = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")

private fun formatCreditLabel(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

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
    /** 顶层任务模式：开课列表 / 班级课表。 */
    val screenMode: LessonScreenMode = LessonScreenMode.COURSE_LIST,
    // 级联选项列表（懒加载）+ 各自 loading。
    val departmentOptions: List<LessonFilterOption> = emptyList(),
    val buildingOptions: List<LessonFilterOption> = emptyList(),
    val loadingDepartments: Boolean = false,
    val loadingMajors: Boolean = false,
    val loadingAdminClasses: Boolean = false,
    val loadingBuildings: Boolean = false,
    // ── 班级课表：学院→专业→行政班级联（2026-07-23 重构） ──
    /** 学院候选（开课单位列表；级联第 1 环）。 */
    val majorDeptOptions: List<LessonFilterOption> = emptyList(),
    val loadingMajorDepts: Boolean = false,
    /** 选定学院后按学院收窄的专业节点（未过滤僵尸项的原始列表）。 */
    val scopedMajorNodes: List<LessonMajorNode> = emptyList(),
    /** 各专业 id → 行政班数（后台预探回填；缺失=未探到/探测失败，不隐藏也不标注）。 */
    val majorClassCounts: Map<Long, Int> = emptyMap(),
    /** 正在后台预探专业班数。 */
    val probingMajorCounts: Boolean = false,
    /** 选定专业后行政班的完整未过滤列表（年级过滤在客户端从此列表重算，零网络）。 */
    val rawAdminClasses: List<LessonAdminClass> = emptyList(),
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

    /** 已激活的开课属性筛选维度数（供「筛选」入口徽标；仅开课列表模式有意义）。 */
    val activeFilterCount: Int get() = appliedFilter.activeCount

    /** 是否处于班级课表模式。 */
    val isClassScheduleMode: Boolean get() = screenMode == LessonScreenMode.CLASS_SCHEDULE

    /** 班级课表模式下已定位到单个行政班 → 有课表可展示 / 允许列表·网格切换。 */
    val canShowGrid: Boolean get() = isClassScheduleMode && appliedFilter.isSingleAdminClass

    /** 网格视图生效（既定位到单班、又切到 GRID）。 */
    val isGridActive: Boolean get() = canShowGrid && viewMode == LessonViewMode.GRID

    /** 班级课表模式：当前选中的年级（单选，取 grades 首个）。 */
    val selectedGrade: String? get() = appliedFilter.grades.firstOrNull()

    /** 班级课表模式：当前选中的学院 id（单选，取 majorDeptIds 首个）。 */
    val selectedMajorDeptId: Long? get() = appliedFilter.majorDeptIds.firstOrNull()

    /** 当前选中学院的中文名（下拉锚点显示）。 */
    val selectedMajorDeptName: String?
        get() = selectedMajorDeptId?.let { id -> majorDeptOptions.firstOrNull { it.id == id }?.nameZh }

    /** 班级课表模式：当前选中的专业 id（单选，取 majorIds 首个）。 */
    val selectedMajorId: Long? get() = appliedFilter.majorIds.firstOrNull()

    /**
     * 专业候选（学院收窄后）：预探到 0 班的僵尸专业隐藏；探到 N(>0) 的标注「· N个班」；
     * 未探到的（缺 count）原样保留、不标注（避免因探测失败误杀真实专业）。
     */
    val majorOptions: List<LessonFilterOption>
        get() = scopedMajorNodes.mapNotNull { node ->
            val id = node.id ?: return@mapNotNull null
            val count = majorClassCounts[id]
            if (count == 0) return@mapNotNull null // 探到 0 班 → 僵尸专业，隐藏
            val base = node.displayName.ifBlank { node.nameZh.orEmpty() }
            if (base.isBlank()) return@mapNotNull null
            val label = if (count != null && count > 0) "$base · ${count}个班" else base
            LessonFilterOption(id, label)
        }

    /** 当前选中专业的中文名（下拉锚点显示；含「· N个班」标注）。 */
    val selectedMajorName: String?
        get() = selectedMajorId?.let { id -> majorOptions.firstOrNull { it.id == id }?.nameZh }

    /** 行政班候选：从 [rawAdminClasses] 按选中年级客户端过滤（服务端 grades= 不生效）。 */
    val adminClassOptions: List<LessonFilterOption>
        get() {
            val grade = selectedGrade
            val src = if (grade == null) rawAdminClasses else rawAdminClasses.filter { it.grade == grade }
            return src.mapNotNull { it.toOption() }
        }

    /** 当前选中行政班的中文名（下拉锚点显示）。 */
    val selectedAdminClassName: String?
        get() = appliedFilter.adminClassId?.let { id -> adminClassOptions.firstOrNull { it.id == id }?.nameZh }

    /** 生效筛选 chip（开课列表模式，可移除）。 */
    val activeFilterChips: List<ActiveFilterChip>
        get() = buildActiveFilterChips(appliedFilter, departmentOptions, buildingOptions)
}
