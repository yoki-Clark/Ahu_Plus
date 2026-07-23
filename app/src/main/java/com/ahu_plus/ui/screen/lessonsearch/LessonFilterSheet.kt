package com.ahu_plus.ui.screen.lessonsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.model.jw.LessonFilterOption
import com.ahu_plus.data.model.jw.LessonInlineOptions

/**
 * 全校开课查询「开课列表」模式的开课属性筛选面板。
 * 分两组：课程属性（学院/类型/性质/考核/语言/学分）｜上课安排（校区/教学楼/星期/节次/教室）。
 * 编辑 draftFilter；点「应用筛选」落地为 appliedFilter 并查询。
 * 教学班定位（年级→专业→行政班）已移到「班级课表」模式常驻区，不在本面板。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LessonFilterSheet(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel,
) {
    val draft = uiState.draftFilter
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("筛选", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        // 滚动筛选区占据剩余空间，底部操作行始终可见（不再被挤出屏幕）。
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterSheetOpenCourseSection(uiState = uiState, viewModel = viewModel)
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::resetFilterDraft,
                modifier = Modifier.weight(1f)
            ) { Text("清空") }
            Button(
                onClick = viewModel::applyFilter,
                modifier = Modifier.weight(1f)
            ) {
                Text("应用筛选" + if (draft.activeCount > 0) "（${draft.activeCount}）" else "")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** 开课属性筛选段。二级分组：课程属性 ｜ 上课安排。 */
@Composable
private fun FilterSheetOpenCourseSection(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel,
) {
    val draft = uiState.draftFilter

    // ═══ 课程属性 ═══
    SectionTitle("课程属性")

    // 开课学院（可搜索多选，来自 getDepartments，约 50 项）
    SearchableMultiSelectField(
        label = "开课学院",
        loading = uiState.loadingDepartments,
        options = uiState.departmentOptions,
        selectedIds = draft.departmentIds.toSet(),
        onToggle = viewModel::toggleDraftDepartment,
        onClear = viewModel::clearDraftDepartments,
        emptyHint = "全部学院",
    )

    // 课程类型（单选 chip，内嵌）
    SingleChipGroup(
        label = "课程类型",
        options = LessonInlineOptions.COURSE_TYPES,
        selectedId = draft.courseTypeId,
        onSelect = viewModel::setDraftCourseType,
    )

    // 课程性质（单选 chip，枚举字符串）
    StringSingleChipGroup(
        label = "课程性质",
        options = LessonInlineOptions.COMPULSORY,
        selectedValue = draft.compulsory,
        onSelect = viewModel::setDraftCompulsory,
    )

    // 考核方式（单选 chip）
    SingleChipGroup(
        label = "考核方式",
        options = LessonInlineOptions.EXAM_MODES,
        selectedId = draft.examModeId,
        onSelect = viewModel::setDraftExamMode,
    )

    // 授课语言（单选 chip）
    SingleChipGroup(
        label = "授课语言",
        options = LessonInlineOptions.TEACH_LANGS,
        selectedId = draft.teachLangId,
        onSelect = viewModel::setDraftTeachLang,
    )

    // 学分区间
    CreditRangeRow(
        gte = draft.creditsGte,
        lte = draft.creditsLte,
        onChange = viewModel::setDraftCredits,
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

    // ═══ 上课安排 ═══
    SectionTitle("上课安排")

    // 校区（单选 chip，内嵌；联动教学楼）
    SingleChipGroup(
        label = "校区",
        options = LessonInlineOptions.CAMPUSES,
        selectedId = draft.campusId,
        onSelect = viewModel::setDraftCampus,
    )

    // 教学楼（单选下拉，选校区后加载）
    if (draft.campusId != null) {
        OptionDropdown(
            label = "教学楼",
            loading = uiState.loadingBuildings,
            options = uiState.buildingOptions,
            selectedId = draft.buildingId,
            onSelect = viewModel::setDraftBuilding,
            emptyHint = "该校区暂无教学楼数据",
        )
    }

    // 星期（多选 chip，ISO 周一=1..周日=7）
    val weekdayLabels = listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")
    MultiChipGroup(
        label = "星期",
        options = weekdayLabels.map { LessonFilterOption(it.first.toLong(), "周${it.second}") },
        selectedIds = draft.weekdays.map { it.toLong() }.toSet(),
        onToggle = { viewModel.toggleDraftWeekday(it.toInt()) },
    )

    // 节次（多选 chip，1..13）
    MultiChipGroup(
        label = "节次",
        options = (1..13).map { LessonFilterOption(it.toLong(), "第${it}节") },
        selectedIds = draft.courseUnitIndexes.map { it.toLong() }.toSet(),
        onToggle = { viewModel.toggleDraftCourseUnit(it.toInt()) },
    )

    // 教室关键字
    var roomText by remember(draft.roomNameLike) { mutableStateOf(draft.roomNameLike.orEmpty()) }
    OutlinedTextField(
        value = roomText,
        onValueChange = {
            roomText = it
            viewModel.setDraftRoomName(it)
        },
        label = { Text("教室关键字") },
        placeholder = { Text("如 博北、A101") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

// ── 复用组件 ─────────────────────────────────────────────────

/** 多选 chip 组（Long id）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiChipGroup(
    label: String,
    options: List<LessonFilterOption>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    if (options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChipGroupLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                FilterChip(
                    selected = opt.id in selectedIds,
                    onClick = { onToggle(opt.id) },
                    label = { Text(opt.nameZh) },
                )
            }
        }
    }
}

/** 单选 chip 组（Long id，再点已选=取消）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleChipGroup(
    label: String,
    options: List<LessonFilterOption>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChipGroupLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                FilterChip(
                    selected = opt.id == selectedId,
                    onClick = { onSelect(if (opt.id == selectedId) null else opt.id) },
                    label = { Text(opt.nameZh) },
                )
            }
        }
    }
}

/** 单选 chip 组（String 枚举值，如 compulsory）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StringSingleChipGroup(
    label: String,
    options: List<Pair<String, String>>,
    selectedValue: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChipGroupLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, name) ->
                FilterChip(
                    selected = value == selectedValue,
                    onClick = { onSelect(if (value == selectedValue) null else value) },
                    label = { Text(name) },
                )
            }
        }
    }
}

@Composable
private fun ChipGroupLabel(label: String) {
    if (label.isBlank()) return
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 单选下拉（选项多时用；含「全部」项清空选择）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionDropdown(
    label: String,
    loading: Boolean,
    options: List<LessonFilterOption>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    emptyHint: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.id == selectedId }?.nameZh
    val enabled = options.isNotEmpty() && !loading
    val anchorText = when {
        loading -> "加载中…"
        selectedName != null -> selectedName
        options.isEmpty() -> emptyHint
        else -> "全部"
    }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = anchorText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("全部") },
                onClick = { expanded = false; onSelect(null) }
            )
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.nameZh) },
                    onClick = { expanded = false; onSelect(opt.id) }
                )
            }
        }
    }
}

/** 学分区间输入（两个数字框）。 */
@Composable
private fun CreditRangeRow(
    gte: Double?,
    lte: Double?,
    onChange: (Double?, Double?) -> Unit,
) {
    var gteText by remember(gte) { mutableStateOf(gte?.let { formatCredit(it) } ?: "") }
    var lteText by remember(lte) { mutableStateOf(lte?.let { formatCredit(it) } ?: "") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChipGroupLabel("学分区间")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = gteText,
                onValueChange = {
                    gteText = it
                    onChange(it.toDoubleOrNull(), lteText.toDoubleOrNull())
                },
                label = { Text("最低") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = lteText,
                onValueChange = {
                    lteText = it
                    onChange(gteText.toDoubleOrNull(), it.toDoubleOrNull())
                },
                label = { Text("最高") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun formatCredit(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
