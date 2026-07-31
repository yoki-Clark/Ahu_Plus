package com.ahu_plus.ui.screen.lessonsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.model.jw.LessonInlineOptions
import com.ahu_plus.data.model.jw.LessonRecord
import com.ahu_plus.data.model.jw.LessonSearchMode
import com.ahu_plus.ui.components.AhuPullToRefreshBox
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.components.CenteredError
import com.ahu_plus.ui.components.CenteredLoader
import com.ahu_plus.ui.components.CenteredMessage
import com.ahu_plus.ui.components.DataStatusFooter
import com.ahu_plus.ui.theme.AhuRed
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.AhuSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonSearchScreen(
    viewModel: LessonSearchViewModel,
    onBack: () -> Unit,
    onNeedsLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("开课查询") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        AhuPullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::onRefresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AhuSpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── 顶层模式切换（始终可见）──────────────────
                item(key = "mode") { ModeSegmentedRow(uiState = uiState, viewModel = viewModel) }

                if (uiState.isClassScheduleMode) {
                    classScheduleItems(uiState, viewModel, onNeedsLogin)
                } else {
                    courseListItems(uiState, viewModel, onNeedsLogin)
                }

                uiState.dataStatus?.let { status ->
                    item(key = "data-status") {
                        DataStatusFooter(status = status)
                    }
                }
                item(key = "bottom-spacer") { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // ── 底部筛选面板（仅开课列表模式的开课属性筛选）─────────
            if (uiState.filterSheetOpen) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = viewModel::closeFilterSheet,
                    sheetState = sheetState,
                ) {
                    LessonFilterSheet(uiState = uiState, viewModel = viewModel)
                }
            }
        }
    }
}

// ── 顶层模式切换：开课列表 | 班级课表 ──────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSegmentedRow(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        SegmentedButton(
            selected = uiState.screenMode == LessonScreenMode.COURSE_LIST,
            onClick = { viewModel.setScreenMode(LessonScreenMode.COURSE_LIST) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text("开课列表") }
        SegmentedButton(
            selected = uiState.screenMode == LessonScreenMode.CLASS_SCHEDULE,
            onClick = { viewModel.setScreenMode(LessonScreenMode.CLASS_SCHEDULE) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text("班级课表") }
    }
}

// ══════════════════════════════════════════════════════════════
// 模式 A：开课列表
// ══════════════════════════════════════════════════════════════

private fun LazyListScope.courseListItems(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel,
    onNeedsLogin: () -> Unit,
) {
    item(key = "search-area") { SearchArea(uiState = uiState, viewModel = viewModel) }
    item(key = "filter-entry") { FilterEntryRow(uiState = uiState, viewModel = viewModel) }
    if (uiState.activeFilterChips.isNotEmpty()) {
        item(key = "active-chips") { ActiveFilterChipsRow(uiState = uiState, viewModel = viewModel) }
    }
    resultItems(uiState, viewModel, onNeedsLogin)
}

/** 开课列表结果区（loading/error/empty/列表 + 加载更多）。 */
private fun LazyListScope.resultItems(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel,
    onNeedsLogin: () -> Unit,
) {
    when {
        uiState.isLoading && uiState.records.isEmpty() -> {
            item(key = "loading") { CenteredLoader(modifier = Modifier.fillMaxWidth().height(200.dp)) }
        }

        uiState.error != null && uiState.records.isEmpty() -> {
            item(key = "error") {
                CenteredError(
                    message = uiState.error ?: "加载失败",
                    onRetry = if (uiState.needsLogin) onNeedsLogin else viewModel::onRefresh,
                    actionLabel = if (uiState.needsLogin) "去登录" else "重试",
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }
        }

        uiState.filteredRecords.isEmpty() && !uiState.isLoading -> {
            item(key = "empty") {
                CenteredMessage(
                    text = if (uiState.hideFull && uiState.records.isNotEmpty()) "均已满员" else "未找到开课",
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }
        }

        else -> {
            item(key = "count") {
                Spacer(modifier = Modifier.height(4.dp))
                val countText = buildString {
                    append("共 ${uiState.totalRows} 条开课")
                    if (uiState.hideFull && uiState.filteredRecords.size < uiState.records.size) {
                        append("（已隐藏满员，剩 ${uiState.filteredRecords.size} 条）")
                    }
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(
                items = uiState.filteredRecords,
                key = { it.id ?: it.code ?: it.hashCode() }
            ) { record ->
                LessonCard(record = record)
            }
            if (uiState.hasMore) {
                item(key = "load-more") {
                    LoadMoreArea(
                        isLoadingMore = uiState.isLoadingMore,
                        onLoadMore = viewModel::loadMore
                    )
                }
            }
        }
    }
}

// ── 筛选入口（仅开课列表模式）────────────────────────────────

@Composable
private fun FilterEntryRow(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgedBox(
            badge = {
                if (uiState.activeFilterCount > 0) {
                    Badge { Text(uiState.activeFilterCount.toString()) }
                }
            }
        ) {
            OutlinedButton(onClick = viewModel::openFilterSheet) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("筛选")
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

// ── 生效筛选 chip 行（可逐个移除）──────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterChipsRow(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        uiState.activeFilterChips.forEach { chip ->
            InputChip(
                selected = false,
                onClick = { viewModel.removeAppliedFilter(chip.dimension) },
                label = { Text(chip.label) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "移除",
                        modifier = Modifier.size(16.dp)
                    )
                },
            )
        }
        Text(
            text = "清空",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .clickable { viewModel.clearFilter() }
        )
    }
}

// ══════════════════════════════════════════════════════════════
// 模式 B：班级课表（年级→专业→行政班 三级定位 → 周网格）
// ══════════════════════════════════════════════════════════════

private fun LazyListScope.classScheduleItems(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel,
    onNeedsLogin: () -> Unit,
) {
    item(key = "cs-semester") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SemesterDropdown(uiState = uiState, viewModel = viewModel)
            ClassLocatorSection(uiState = uiState, viewModel = viewModel)
        }
    }

    when {
        // 未定位到行政班：显示引导空态（以 adminClassId 为准，不依赖名字解析）。
        uiState.appliedFilter.adminClassId == null -> {
            item(key = "cs-guide") { ClassScheduleGuide() }
        }

        uiState.isLoading && uiState.records.isEmpty() -> {
            item(key = "cs-loading") { CenteredLoader(modifier = Modifier.fillMaxWidth().height(200.dp)) }
        }

        uiState.error != null && uiState.records.isEmpty() -> {
            item(key = "cs-error") {
                CenteredError(
                    message = uiState.error ?: "加载失败",
                    onRetry = if (uiState.needsLogin) onNeedsLogin else viewModel::onRefresh,
                    actionLabel = if (uiState.needsLogin) "去登录" else "重试",
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }
        }

        else -> {
            // 视图切换（列表 | 课表）——仅本模式、定位到单班时可见。
            item(key = "cs-view-toggle") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "共 ${uiState.totalRows} 门课",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    ViewModeToggle(uiState = uiState, viewModel = viewModel)
                }
            }

            if (uiState.isGridActive) {
                item(key = "cs-grid") { LessonGridArea(uiState = uiState, viewModel = viewModel) }
                if (uiState.unparsedRecords.isNotEmpty()) {
                    item(key = "cs-unparsed-header") {
                        Text(
                            text = "以下课程时间无法排入网格（${uiState.unparsedRecords.size} 条）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(
                        items = uiState.unparsedRecords,
                        key = { "u-${it.id ?: it.code ?: it.hashCode()}" }
                    ) { record -> LessonCard(record = record) }
                }
            } else {
                if (uiState.filteredRecords.isEmpty()) {
                    item(key = "cs-empty") {
                        CenteredMessage(
                            text = "该班暂无开课",
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                    }
                } else {
                    items(
                        items = uiState.filteredRecords,
                        key = { it.id ?: it.code ?: it.hashCode() }
                    ) { record -> LessonCard(record = record) }
                }
            }
        }
    }
}

/** 班级课表模式引导空态：解释学院→专业→行政班定位与年级过滤。 */
@Composable
private fun ClassScheduleGuide() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "选学院 → 专业 → 行政班，查看该班周网格课表",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "选专业后可用年级进一步收窄行政班",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 视图切换段按钮：列表 | 课表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeToggle(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel
) {
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(
            selected = uiState.viewMode == LessonViewMode.GRID,
            onClick = { viewModel.setViewMode(LessonViewMode.GRID) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {}
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = "课表", modifier = Modifier.size(18.dp))
        }
        SegmentedButton(
            selected = uiState.viewMode == LessonViewMode.LIST,
            onClick = { viewModel.setViewMode(LessonViewMode.LIST) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {}
        ) {
            Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "列表", modifier = Modifier.size(18.dp))
        }
    }
}

/** 三级定位：学院（可搜索单选）→ 专业（可搜索单选，含班数标注）→ 行政班（定位钥匙），年级为可选客户端过滤。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassLocatorSection(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel
) {
    val currentYear = remember { DebugClock.todayDate().year }
    val grades = remember(currentYear) { LessonInlineOptions.grades(currentYear) }

    // 进入本模式即预拉学院候选（懒加载，命中缓存直接返回）。
    LaunchedEffect(Unit) { viewModel.ensureClassDepartmentOptions() }

    // 学院（可搜索单选，级联第 1 环）
    SearchableSingleSelectField(
        label = "学院",
        selectedName = uiState.selectedMajorDeptName,
        loading = uiState.loadingMajorDepts,
        options = uiState.majorDeptOptions,
        selectedId = uiState.selectedMajorDeptId,
        onSelect = viewModel::selectClassDepartment,
        emptyHint = "选择开课学院",
        includeAllOption = false,
    )

    // 专业（可搜索单选，学院收窄后；0 班僵尸专业已隐藏，真实专业标注「· N个班」）
    SearchableSingleSelectField(
        label = if (uiState.probingMajorCounts) "专业（正在核对班级数…）" else "专业",
        selectedName = uiState.selectedMajorName,
        loading = uiState.loadingMajors,
        options = uiState.majorOptions,
        selectedId = uiState.selectedMajorId,
        onSelect = viewModel::selectClassMajor,
        emptyHint = if (uiState.selectedMajorDeptId == null) "先选学院" else "该学院无可选专业",
        enabled = uiState.selectedMajorDeptId != null && uiState.majorOptions.isNotEmpty(),
        includeAllOption = false,
    )

    // 年级（固定小列表，内嵌下拉；可选，仅客户端收窄行政班列表）
    GradeDropdown(
        selectedGrade = uiState.selectedGrade,
        grades = grades,
        onSelect = viewModel::selectClassGrade,
    )

    // 行政班（可搜索单选，定位钥匙；候选数在面板标题显示）
    SearchableSingleSelectField(
        label = "行政班（教学班）",
        selectedName = uiState.selectedAdminClassName,
        loading = uiState.loadingAdminClasses,
        options = uiState.adminClassOptions,
        selectedId = uiState.appliedFilter.adminClassId,
        onSelect = viewModel::selectClassAdminClass,
        emptyHint = when {
            uiState.selectedMajorId == null -> "先选学院和专业"
            uiState.rawAdminClasses.isEmpty() -> "该专业无行政班"
            else -> "该年级无匹配行政班"
        },
        enabled = uiState.adminClassOptions.isNotEmpty(),
        includeAllOption = false,
    )
}

/** 年级下拉（固定小列表，含「全部」清空项）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeDropdown(
    selectedGrade: String?,
    grades: List<String>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedGrade?.let { "${it}级" } ?: "全部年级",
            onValueChange = {},
            readOnly = true,
            label = { Text("年级") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("全部年级") },
                onClick = { expanded = false; onSelect(null) }
            )
            grades.forEach { grade ->
                DropdownMenuItem(
                    text = { Text("${grade}级") },
                    onClick = { expanded = false; onSelect(grade) }
                )
            }
        }
    }
}

// ── 学期下拉（两模式共用）─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemesterDropdown(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    val semesterEnabled = uiState.semesters.isNotEmpty()
    val anchorText = uiState.selectedSemesterName
        ?: if (semesterEnabled) "选择学期" else "加载中"
    ExposedDropdownMenuBox(
        expanded = expanded && semesterEnabled,
        onExpandedChange = { if (semesterEnabled) expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = anchorText,
            onValueChange = {},
            readOnly = true,
            enabled = semesterEnabled,
            label = { Text("学期") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && semesterEnabled,
            onDismissRequest = { expanded = false }
        ) {
            uiState.semesters.forEach { semester ->
                DropdownMenuItem(
                    text = { Text(semester.nameZh ?: "未命名学期") },
                    onClick = {
                        expanded = false
                        semester.id?.let { viewModel.selectSemester(it) }
                    }
                )
            }
        }
    }
}

// ── 选择/搜索区域 ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchArea(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 学期下拉
        SemesterDropdown(uiState = uiState, viewModel = viewModel)

        // 2. 搜索框
        OutlinedTextField(
            value = uiState.keyword,
            onValueChange = viewModel::onKeywordChange,
            singleLine = true,
            placeholder = { Text("搜索课程/教学班") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.onSearch() }),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.keyword.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.onKeywordChange("")
                            viewModel.onSearch()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "清除")
                        }
                    }
                    IconButton(onClick = { viewModel.onSearch() }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // 3. 搜索模式切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LessonSearchMode.entries.forEach { mode ->
                FilterChip(
                    selected = uiState.mode == mode,
                    onClick = { viewModel.selectMode(mode) },
                    label = { Text(mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        // 4. 隐藏满员
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "隐藏满员",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = uiState.hideFull,
                onCheckedChange = viewModel::setHideFull
            )
        }
    }
}

// ── 加载更多 ─────────────────────────────────────────────────

@Composable
private fun LoadMoreArea(
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoadingMore) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            FilledTonalButton(
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("加载更多")
            }
        }
    }
}

// ── 单条开课卡片 ─────────────────────────────────────────────

@Composable
private fun LessonCard(record: LessonRecord) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(AhuSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 标题行：课程名 + 学分
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = record.courseName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                record.course?.credits?.let { credits ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${formatCredits(credits)}学分",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 教学班名称
            record.nameZh?.takeIf { it.isNotBlank() }?.let { className ->
                Text(
                    text = className,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 元信息："· "连接：课程号 / 课程性质 / 类型 / 考核方式
            val meta = listOfNotNull(
                record.course?.code?.takeIf { it.isNotBlank() },
                record.courseProperty?.nameZh?.takeIf { it.isNotBlank() },
                record.courseType?.nameZh?.takeIf { it.isNotBlank() },
                record.examMode?.nameZh?.takeIf { it.isNotBlank() }
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 教师
            val teachers = record.teacherNames()
            if (teachers.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "教师：$teachers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 开课学院
            record.openDepartment?.nameZh?.takeIf { it.isNotBlank() }?.let { dept ->
                Text(
                    text = "开课学院：$dept",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 时间地点
            val schedule = record.scheduleZh()
            if (schedule.isNotBlank()) {
                Text(
                    text = schedule,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = "时间地点待定",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 容量
            val full = record.isFull()
            Text(
                text = buildString {
                    append("已选 ${record.stdCount ?: "-"}/${record.limitCount ?: "-"}")
                    if (full) append(" 满员")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (full) AhuRed else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (full) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/** 学分展示：整数去掉小数点（2.0 → 2），否则保留原值。 */
private fun formatCredits(credits: Double): String =
    if (credits % 1.0 == 0.0) credits.toInt().toString() else credits.toString()




