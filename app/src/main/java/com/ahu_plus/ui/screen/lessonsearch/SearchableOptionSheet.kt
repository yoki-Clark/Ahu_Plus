package com.ahu_plus.ui.screen.lessonsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.model.jw.LessonFilterOption

/**
 * 可搜索单选选择器：只读锚点点击弹出底部选择面板（搜索框 + 候选数 + 勾选列表）。
 * 用于行政班这类候选可能很多、需搜索定位的单选场景。选中即回填并关闭。
 *
 * @param label 锚点标签（如「行政班（教学班）」）。
 * @param selectedName 已选项中文名（null 显示 [emptyHint] 或「全部」）。
 * @param loading 候选加载中。
 * @param options 候选列表。
 * @param selectedId 当前选中 id。
 * @param onSelect 选择回调（传 null = 清空/全部）。
 * @param emptyHint 候选为空时锚点提示。
 * @param enabled 锚点是否可点（如未选专业时禁用）。
 * @param includeAllOption 是否在列表顶部提供「全部」清空项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchableSingleSelectField(
    label: String,
    selectedName: String?,
    loading: Boolean,
    options: List<LessonFilterOption>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    emptyHint: String,
    enabled: Boolean = true,
    includeAllOption: Boolean = true,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val anchorText = when {
        loading -> "加载中…"
        selectedName != null -> selectedName
        !enabled -> emptyHint
        options.isEmpty() -> emptyHint
        else -> "全部"
    }
    val clickable = enabled && !loading && options.isNotEmpty()

    PickerAnchor(
        label = label,
        text = anchorText,
        enabled = clickable,
        onClick = { sheetOpen = true },
    )

    if (sheetOpen) {
        OptionPickerSheet(
            title = label,
            options = options,
            onDismiss = { sheetOpen = false },
            leadingItem = if (includeAllOption) {
                {
                    PickerRow(
                        text = "全部",
                        selected = selectedId == null,
                        single = true,
                        onClick = { onSelect(null); sheetOpen = false },
                    )
                    HorizontalDivider()
                }
            } else null,
        ) { opt ->
            PickerRow(
                text = opt.nameZh,
                selected = opt.id == selectedId,
                single = true,
                onClick = { onSelect(opt.id); sheetOpen = false },
            )
        }
    }
}

/**
 * 可搜索多选选择器：锚点显示「已选 N 项」，弹出面板勾选多个。
 * 用于开课学院这类候选较多的多选场景，替代 chip 墙。变更即时回调 [onToggle]（编辑 draft）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchableMultiSelectField(
    label: String,
    loading: Boolean,
    options: List<LessonFilterOption>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit,
    emptyHint: String,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val selectedNames = options.filter { it.id in selectedIds }.map { it.nameZh }
    val anchorText = when {
        loading && options.isEmpty() -> "加载中…"
        selectedIds.isEmpty() -> emptyHint
        selectedNames.size == 1 -> selectedNames.first()
        selectedNames.size > 1 -> "${selectedNames.first()} 等 ${selectedIds.size} 项"
        else -> "已选 ${selectedIds.size} 项"
    }
    val clickable = !(loading && options.isEmpty()) && options.isNotEmpty()

    PickerAnchor(
        label = label,
        text = anchorText,
        enabled = clickable,
        onClick = { sheetOpen = true },
    )

    if (sheetOpen) {
        OptionPickerSheet(
            title = label,
            options = options,
            onDismiss = { sheetOpen = false },
            headerAction = if (selectedIds.isNotEmpty()) {
                { TextButton(onClick = onClear) { Text("清空") } }
            } else null,
        ) { opt ->
            PickerRow(
                text = opt.nameZh,
                selected = opt.id in selectedIds,
                single = false,
                onClick = { onToggle(opt.id) },
            )
        }
    }
}

// ── 内部组件 ─────────────────────────────────────────────────

/** 只读锚点：外观同 OutlinedTextField，透明层拦截点击弹面板（避免 disabled 灰态影响可读性）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerAnchor(
    label: String,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (enabled) {
            // 透明覆盖层拦截点击（OutlinedTextField readOnly 仍会抢焦点）。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onClick),
            )
        }
    }
}

/**
 * 通用选择底部面板：标题（含候选数）+ 可选头部动作 + 搜索框 + 过滤后的行列表。
 * [content] 收到「按关键词过滤后的候选」，由调用方渲染成单选/多选行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionPickerSheet(
    title: String,
    options: List<LessonFilterOption>,
    onDismiss: () -> Unit,
    headerAction: (@Composable () -> Unit)? = null,
    leadingItem: (@Composable () -> Unit)? = null,
    itemContent: @Composable (LessonFilterOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, options) {
        val q = query.trim()
        if (q.isEmpty()) options else options.filter { it.nameZh.contains(q, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "候选 ${filtered.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                headerAction?.let {
                    Spacer(Modifier.width(4.dp))
                    it()
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("搜索") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (filtered.isEmpty()) {
                Text(
                    text = "无匹配候选",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    leadingItem?.let { lead -> item(key = "__leading__") { lead() } }
                    items(items = filtered, key = { it.id }) { opt ->
                        itemContent(opt)
                    }
                }
            }
        }
    }
}

/** 单条选择行：单选用 RadioButton，多选用 Checkbox；整行可点。 */
@Composable
private fun PickerRow(
    text: String,
    selected: Boolean,
    single: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 单选走 selectable(Role.RadioButton),多选走 toggleable(Role.Checkbox);
            // 控件本身交出回调,整行作为唯一语义节点。
            .then(
                if (single) {
                    Modifier.selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = onClick,
                    )
                } else {
                    Modifier.toggleable(
                        value = selected,
                        role = Role.Checkbox,
                        onValueChange = { onClick() },
                    )
                }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (single) {
            RadioButton(selected = selected, onClick = null)
        } else {
            Checkbox(checked = selected, onCheckedChange = null)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}

