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
import androidx.compose.material3.CircularProgressIndicator
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
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.model.jw.LessonFilterOption
import com.ahu_plus.data.model.jw.LessonInlineOptions

/**
 * 全校开课查询的底部筛选面板。分两段：
 *  1. 教学班定位（级联）：年级 → 开课单位 → 专业 → 行政班。定位到单班后可切「课表」网格。
 *  2. 开课筛选：学院/类型/性质/校区/教学楼/考核/语言/星期/节次/学分区间/教室关键字。
 *
 * 编辑的是 VM 的 draftFilter；点「应用筛选」才落地为 appliedFilter 并查询。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LessonFilterSheet(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel,
) {
    val draft = uiState.draftFilter
    val currentYear = remember { DebugClock.todayDate().year }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("筛选", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        // ═══ 教学班定位（级联） ═══
        SectionTitle("教学班定位")
        Text(
            "选到具体行政班后，可切换到「课表」查看该班周网格课表。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 年级（多选 chip）
        MultiChipGroup(
            label = "年级",
            options = LessonInlineOptions.grades(currentYear).map { LessonFilterOption(it.toLong(), it) },
            selectedIds = draft.grades.mapNotNull { it.toLongOrNull() }.toSet(),
            onToggle = { viewModel.toggleDraftGrade(it.toString()) },
        )

        // 开课单位（多选 chip，来自 API）
        LoadingMultiChipGroup(
            label = "开课单位",
            loading = uiState.loadingMajorDepts,
            options = uiState.majorDeptOptions,
            selectedIds = draft.majorDeptIds.toSet(),
            onToggle = viewModel::toggleDraftMajorDept,
        )

        // 专业（多选 chip，选开课单位后加载）
        if (draft.majorDeptIds.isNotEmpty() || uiState.majorOptions.isNotEmpty()) {
            LoadingMultiChipGroup(
                label = "专业",
                loading = uiState.loadingMajors,
                options = uiState.majorOptions,
                selectedIds = draft.majorIds.toSet(),
                onToggle = viewModel::toggleDraftMajor,
            )
        }

        // 行政班（单选下拉，定位钥匙）
        OptionDropdown(
            label = "行政班（教学班）",
            loading = uiState.loadingAdminClasses,
            options = uiState.adminClassOptions,
            selectedId = draft.adminClassId,
            onSelect = viewModel::setDraftAdminClass,
            emptyHint = "先选年级/开课单位/专业",
        )

        HorizontalDivider()

        // ═══ 开课筛选 ═══
        FilterSheetOpenCourseSection(uiState = uiState, viewModel = viewModel)

        // ═══ 操作 ═══
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::resetFilterDraft,
                modifier = Modifier.weight(1f)
            ) { Text("重置") }
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

/** 开课筛选段：学院/类型/性质/校区/教学楼/考核/语言/星期/节次/学分/教室。 */
@Composable
private fun FilterSheetOpenCourseSection(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel,
) {
    val draft = uiState.draftFilter
    SectionTitle("开课筛选")

    // 开课学院（多选 chip，来自 getDepartments）
    LoadingMultiChipGroup(
        label = "开课学院",
        loading = uiState.loadingDepartments,
        options = uiState.departmentOptions,
        selectedIds = draft.departmentIds.toSet(),
        onToggle = viewModel::toggleDraftDepartment,
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

    // 学分区间
    CreditRangeRow(
        gte = draft.creditsGte,
        lte = draft.creditsLte,
        onChange = viewModel::setDraftCredits,
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

/** 带 loading 的多选 chip 组（级联 API 选项）。 */
@Composable
private fun LoadingMultiChipGroup(
    label: String,
    loading: Boolean,
    options: List<LessonFilterOption>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            ChipGroupLabel(label)
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(start = 8.dp).heightIn(max = 16.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        if (options.isEmpty() && !loading) {
            Text(
                "暂无选项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MultiChipGroup(label = "", options = options, selectedIds = selectedIds, onToggle = onToggle)
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
