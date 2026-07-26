package com.ahu_plus.ui.screen.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ahu_plus.ui.components.AhuIconBox
import com.ahu_plus.data.home.AppHubCardStyle
import com.ahu_plus.data.home.AppHubColumns
import com.ahu_plus.data.home.AppHubDensity
import com.ahu_plus.data.home.AppHubGroupMode
import com.ahu_plus.data.home.AppHubLayoutConfig
import com.ahu_plus.data.home.AppHubSortMode
import com.ahu_plus.data.home.AppRegistry
import com.ahu_plus.data.home.AppSpec
import com.ahu_plus.ui.theme.AhuShapes
import kotlin.math.roundToInt

/**
 * 应用页排版设置子页。
 *
 * 通过 [onConfigChange] 即时回写 [AppHubLayoutConfig];调用方(MainScreen)负责持久化
 * 并把新配置回灌到 AppHub,做到设置即所见。预览区复用 AppHub 真实磁贴 [AppHubTile]。
 *
 * @param config       当前配置(已 normalize)
 * @param recentKeys   最近使用 key,供预览 RECENT 排序
 * @param usageCounts  使用次数,供预览 FREQUENCY 排序
 * @param onConfigChange 任一控件变更时回调新配置
 * @param onBack        返回上一页
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppHubSettingsScreen(
    config: AppHubLayoutConfig,
    recentKeys: List<String>,
    usageCounts: Map<String, Int>,
    onConfigChange: (AppHubLayoutConfig) -> Unit,
    onBack: () -> Unit,
) {
    // 本地态即时响应,同时向上回写
    var local by remember { mutableStateOf(config) }
    val apply: (AppHubLayoutConfig) -> Unit = { next ->
        val normalized = next.normalize(AppRegistry.allKeys())
        local = normalized
        onConfigChange(normalized)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用页设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { apply(AppHubLayoutConfig.Default) },
                        enabled = local != AppHubLayoutConfig.Default,
                    ) {
                        Icon(
                            Icons.Filled.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("重置")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "preview") { LayoutPreview(local, recentKeys, usageCounts) }

            item(key = "columns") {
                SettingGroup("列数") {
                    val compactLocked = local.cardStyle == AppHubCardStyle.COMPACT
                    ChipRow(
                        options = AppHubColumns.entries,
                        selected = local.columns,
                        label = { it.label() },
                        enabled = !compactLocked,
                        onSelect = { apply(local.copy(columns = it)) },
                    )
                    if (compactLocked) {
                        HintText("紧凑列表样式固定单列")
                    }
                }
            }

            item(key = "cardStyle") {
                SettingGroup("卡片样式") {
                    ChipRow(
                        options = AppHubCardStyle.entries,
                        selected = local.cardStyle,
                        label = { it.label() },
                        onSelect = { apply(local.copy(cardStyle = it)) },
                    )
                    HintText(local.cardStyle.hint())
                }
            }

            item(key = "density") {
                SettingGroup("显示密度") {
                    ChipRow(
                        options = AppHubDensity.entries,
                        selected = local.density,
                        label = { it.label() },
                        onSelect = { apply(local.copy(density = it)) },
                    )
                }
            }

            item(key = "grouping") {
                SettingGroup("分组方式") {
                    ChipRow(
                        options = AppHubGroupMode.entries,
                        selected = local.groupMode,
                        label = { it.label() },
                        onSelect = { apply(local.copy(groupMode = it)) },
                    )
                }
            }

            item(key = "sorting") {
                SettingGroup("排序方式") {
                    ChipRow(
                        options = AppHubSortMode.entries,
                        selected = local.sortMode,
                        label = { it.label() },
                        onSelect = { mode ->
                            // 首次切到自定义:用当前顺序播种,便于在此基础上拖拽
                            val seeded = if (mode == AppHubSortMode.CUSTOM && local.customOrder.isEmpty()) {
                                local.copy(
                                    sortMode = mode,
                                    customOrder = AppRegistry.orderedForCustomEditing(local).map { it.key },
                                )
                            } else {
                                local.copy(sortMode = mode)
                            }
                            apply(seeded)
                        },
                    )
                    HintText(local.sortMode.hint())
                }
            }

            if (local.sortMode == AppHubSortMode.CUSTOM) {
                item(key = "reorder") {
                    SettingGroup("拖拽排序") {
                        HintText("长按拖动调整顺序,隐藏的应用不在此列表")
                        Spacer(Modifier.height(8.dp))
                        ReorderList(
                            order = AppRegistry.orderedForCustomEditing(local),
                            onMove = { from, to ->
                                val keys = AppRegistry.orderedForCustomEditing(local)
                                    .map { it.key }
                                    .toMutableList()
                                if (from in keys.indices && to in keys.indices) {
                                    val moved = keys.removeAt(from)
                                    keys.add(to, moved)
                                    apply(local.copy(customOrder = keys))
                                }
                            },
                        )
                    }
                }
            }

            item(key = "toggles") {
                SettingGroup("显示项") {
                    ToggleRow(
                        title = "显示图标",
                        description = "关闭后磁贴只显示文字名称",
                        checked = local.showIcons,
                        onChange = { apply(local.copy(showIcons = it)) },
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = "分组标题",
                        description = "在每个分类上方显示标题",
                        checked = local.showSectionHeaders,
                        onChange = { apply(local.copy(showSectionHeaders = it)) },
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = "搜索栏",
                        description = "在顶部显示搜索入口",
                        checked = local.showSearchBar,
                        onChange = { apply(local.copy(showSearchBar = it)) },
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = "第三方服务",
                        description = "在应用页展示集市 / 学习通 / WeLearn 入口",
                        checked = local.showThirdPartyServices,
                        onChange = { apply(local.copy(showThirdPartyServices = it)) },
                    )
                }
            }

            item(key = "visibility") {
                SettingGroup("隐藏应用") {
                    HintText("关闭开关即从应用页隐藏,可随时恢复")
                    Spacer(Modifier.height(4.dp))
                    VisibilityList(
                        hiddenKeys = local.hiddenKeys,
                        onToggle = { key, visible ->
                            val next = if (visible) local.hiddenKeys - key else local.hiddenKeys + key
                            apply(local.copy(hiddenKeys = next))
                        },
                    )
                }
            }
        }
    }
}
// ── 通用控件 ───────────────────────────────────────────────────────

@Composable
private fun SettingGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                enabled = enabled,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}
// ── 实时预览 ───────────────────────────────────────────────────────

@Composable
private fun LayoutPreview(
    config: AppHubLayoutConfig,
    recentKeys: List<String>,
    usageCounts: Map<String, Int>,
) {
    val sections = remember(config, recentKeys, usageCounts) {
        AppRegistry.arrange(config, recentKeys, usageCounts)
    }
    val cols = when {
        config.cardStyle == AppHubCardStyle.COMPACT -> 1
        config.columns == AppHubColumns.ONE -> 1
        config.columns == AppHubColumns.THREE -> 3
        config.columns == AppHubColumns.ADAPTIVE -> 3
        else -> 2
    }
    val gap = if (config.density == AppHubDensity.COMPACT) 8.dp else 12.dp

    Surface(
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "预览",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // 只预览前两个分区、每区前若干应用,避免预览过长
            var shown = 0
            val maxShown = 8
            sections.forEach { section ->
                if (shown >= maxShown) return@forEach
                val title = section.title
                if (config.showSectionHeaders && title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                val take = section.apps.take(maxShown - shown)
                shown += take.size
                take.chunked(cols).forEach { rowApps ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = gap),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        rowApps.forEach { spec ->
                            Box(modifier = Modifier.weight(1f)) {
                                AppHubTile(
                                    title = spec.title,
                                    icon = spec.icon,
                                    iconColor = spec.tint,
                                    iconBackground = spec.gradient,
                                    cardStyle = config.cardStyle,
                                    density = config.density,
                                    showIcon = config.showIcons,
                                    onClick = {},
                                )
                            }
                        }
                        // 补齐空位,保持列宽一致
                        repeat(cols - rowApps.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (shown == 0) {
                Text(
                    text = "当前设置下没有可显示的应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
// ── 拖拽排序列表 ───────────────────────────────────────────────────

private val REORDER_ROW_HEIGHT = 56.dp

/**
 * 手写长按拖拽重排。固定行高、无行间距,拖拽中不修改列表,仅用 translationY 做视觉位移,
 * 松手时才回调 [onMove]。整体是定高 Column,嵌在外层 LazyColumn 的单个 item 里。
 */
@Composable
private fun ReorderList(
    order: List<AppSpec>,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val rowPx = with(LocalDensity.current) { REORDER_ROW_HEIGHT.toPx() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val moveState = rememberUpdatedState(onMove)

    // 拖拽中目标落点 = 起始下标 + 位移换算的行数
    val targetIndex: Int? = draggingIndex?.let { start ->
        (start + (dragOffsetY / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = REORDER_ROW_HEIGHT * order.size),
    ) {
        order.forEachIndexed { index, spec ->
            key(spec.key) {
                val dragging = index == draggingIndex
                // 非拖拽行:当拖拽项越过自己时,让位移动一行
                val displacement: Float = when {
                    dragging -> dragOffsetY
                    draggingIndex == null || targetIndex == null -> 0f
                    else -> {
                        val from = draggingIndex!!
                        val to = targetIndex
                        when {
                            from < index && index <= to -> -rowPx
                            to <= index && index < from -> rowPx
                            else -> 0f
                        }
                    }
                }
                ReorderRow(
                    spec = spec,
                    dragging = dragging,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = displacement },
                    onDragStart = {
                        draggingIndex = index
                        dragOffsetY = 0f
                    },
                    onDrag = { delta -> dragOffsetY += delta },
                    onDragEnd = {
                        val start = draggingIndex
                        val end = start?.let {
                            (it + (dragOffsetY / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                        }
                        draggingIndex = null
                        dragOffsetY = 0f
                        if (start != null && end != null && start != end) {
                            moveState.value(start, end)
                        }
                    },
                    onMoveUp = { if (index > 0) moveState.value(index, index - 1) },
                    onMoveDown = { if (index < order.lastIndex) moveState.value(index, index + 1) },
                )
            }
        }
    }
}

@Composable
private fun ReorderRow(
    spec: AppSpec,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(REORDER_ROW_HEIGHT)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("上移") { onMoveUp(); true },
                    CustomAccessibilityAction("下移") { onMoveDown(); true },
                )
            },
        color = if (dragging) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
        tonalElevation = if (dragging) 4.dp else 0.dp,
        shadowElevation = if (dragging) 4.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AhuIconBox(imageVector = spec.icon, tint = spec.tint, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = spec.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "拖动排序,可用无障碍自定义操作上移或下移",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(24.dp)
                    .pointerInput(spec.key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                        )
                    },
            )
        }
    }
}
// ── 隐藏 / 显示应用 ─────────────────────────────────────────────────

@Composable
private fun VisibilityList(
    hiddenKeys: Set<String>,
    onToggle: (key: String, visible: Boolean) -> Unit,
) {
    Column {
        AppRegistry.grouped().forEach { (group, specs) ->
            Text(
                text = group,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
            specs.forEach { spec ->
                val visible = spec.key !in hiddenKeys
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(spec.key, !visible) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = if (visible) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = spec.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (visible) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = visible,
                        onCheckedChange = { onToggle(spec.key, it) },
                    )
                }
            }
        }
    }
}

// ── 枚举中文标签 / 说明 ─────────────────────────────────────────────

private fun AppHubColumns.label(): String = when (this) {
    AppHubColumns.ONE -> "单列"
    AppHubColumns.TWO -> "两列"
    AppHubColumns.THREE -> "三列"
    AppHubColumns.ADAPTIVE -> "自适应"
}

private fun AppHubCardStyle.label(): String = when (this) {
    AppHubCardStyle.HORIZONTAL -> "横向"
    AppHubCardStyle.VERTICAL -> "竖向"
    AppHubCardStyle.COMPACT -> "紧凑列表"
}

private fun AppHubCardStyle.hint(): String = when (this) {
    AppHubCardStyle.HORIZONTAL -> "图标在左、名称在右"
    AppHubCardStyle.VERTICAL -> "图标在上、名称居中,适合多列密铺"
    AppHubCardStyle.COMPACT -> "单行列表,信息密度最高"
}

private fun AppHubDensity.label(): String = when (this) {
    AppHubDensity.COMFORTABLE -> "宽松"
    AppHubDensity.COMPACT -> "紧凑"
}

private fun AppHubGroupMode.label(): String = when (this) {
    AppHubGroupMode.BY_CATEGORY -> "按分类"
    AppHubGroupMode.FLAT -> "不分组"
}

private fun AppHubSortMode.label(): String = when (this) {
    AppHubSortMode.DEFAULT -> "默认"
    AppHubSortMode.NAME -> "名称"
    AppHubSortMode.RECENT -> "最近使用"
    AppHubSortMode.FREQUENCY -> "使用频率"
    AppHubSortMode.CUSTOM -> "自定义"
}

private fun AppHubSortMode.hint(): String = when (this) {
    AppHubSortMode.DEFAULT -> "按内置顺序排列"
    AppHubSortMode.NAME -> "按名称拼音排列"
    AppHubSortMode.RECENT -> "最近打开的应用排在前面"
    AppHubSortMode.FREQUENCY -> "打开次数多的应用排在前面"
    AppHubSortMode.CUSTOM -> "手动拖拽决定顺序"
}
