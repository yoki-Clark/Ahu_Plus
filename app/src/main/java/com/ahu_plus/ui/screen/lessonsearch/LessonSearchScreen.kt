package com.ahu_plus.ui.screen.lessonsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
        containerColor = MaterialTheme.colorScheme.background
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
                // ── 选择/搜索区域（始终可见）──────────────────
                item { SearchArea(uiState = uiState, viewModel = viewModel) }

                // ── 筛选入口 + 视图切换 ────────────────────────
                item { FilterEntryRow(uiState = uiState, viewModel = viewModel) }

                // ── 结果区域 ─────────────────────────────────
                when {
                    uiState.isLoading && uiState.records.isEmpty() -> {
                        item { CenteredLoader(modifier = Modifier.fillMaxWidth().height(200.dp)) }
                    }

                    uiState.error != null && uiState.records.isEmpty() -> {
                        item {
                            CenteredError(
                                message = uiState.error ?: "加载失败",
                                onRetry = if (uiState.needsLogin) onNeedsLogin else viewModel::onRefresh,
                                actionLabel = if (uiState.needsLogin) "去登录" else "重试",
                                modifier = Modifier.fillMaxWidth().height(200.dp)
                            )
                        }
                    }

                    // 网格课表视图（定位到单班且切到 GRID）
                    uiState.isGridActive -> {
                        item(key = "grid") {
                            LessonGridArea(uiState = uiState, viewModel = viewModel)
                        }
                        if (uiState.unparsedRecords.isNotEmpty()) {
                            item(key = "unparsed-header") {
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
                    }

                    uiState.filteredRecords.isEmpty() && !uiState.isLoading -> {
                        item {
                            CenteredMessage(
                                text = if (uiState.hideFull && uiState.records.isNotEmpty()) "均已满员" else "未找到开课",
                                modifier = Modifier.fillMaxWidth().height(200.dp)
                            )
                        }
                    }

                    else -> {
                        item {
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

                uiState.dataStatus?.let { status ->
                    item(key = "data-status") {
                        DataStatusFooter(status = status)
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // ── 底部筛选面板 ─────────────────────────────────
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

// ── 筛选入口 + 列表/课表视图切换 ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
        if (uiState.activeFilterCount > 0) {
            Text(
                text = "清空",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clickable { viewModel.clearFilter() }
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // 定位到单个教学班时，允许在列表/课表间切换
        if (uiState.canShowGrid) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = uiState.viewMode == LessonViewMode.LIST,
                    onClick = { viewModel.setViewMode(LessonViewMode.LIST) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {}
                ) {
                    Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "列表", modifier = Modifier.size(18.dp))
                }
                SegmentedButton(
                    selected = uiState.viewMode == LessonViewMode.GRID,
                    onClick = { viewModel.setViewMode(LessonViewMode.GRID) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {}
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "课表", modifier = Modifier.size(18.dp))
                }
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




