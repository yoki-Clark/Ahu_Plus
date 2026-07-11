package com.ahu_plus.ui.screen.schedule

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import com.ahu_plus.data.model.schedule.SchedulePaletteConfig
import com.ahu_plus.data.model.schedule.ScheduleBackgroundConfig
import com.ahu_plus.data.model.schedule.backgroundOrDefault
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.CourseCardStyle
import com.ahu_plus.ui.theme.CoursePalettes
import coil.compose.AsyncImage
import kotlin.math.roundToInt

private data class SliderDragPreview(
    val label: String,
    val value: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val steps: Int,
    val boundsInRoot: Rect,
)

/**
 * 课表显示设置 BottomSheet (2026-06-17 重构)。
 *
 * 包含两个可折叠 group:
 *  - 外观设置 (colWidth / rowHeight / fontScale 三个 slider)
 *  - 显示设置 (是否展示周六/周日 / 是否支持左右滑动切换周 / 进入课表是否重置为本周 / 是否展示已完成任务)
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsSheet(
    colWidthDp: Float,
    rowHeightDp: Float,
    fontScale: Float,
    showSat: Boolean,
    showSun: Boolean,
    pagerEnabled: Boolean,
    resetOnEnter: Boolean,
    showOtherSemesters: Boolean,
    onColWidthChanged: (Float) -> Unit,
    onRowHeightChanged: (Float) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onShowSatChanged: (Boolean) -> Unit,
    onShowSunChanged: (Boolean) -> Unit,
    onPagerEnabledChanged: (Boolean) -> Unit,
    onResetOnEnterChanged: (Boolean) -> Unit,
    onShowOtherSemestersChanged: (Boolean) -> Unit,
    paletteConfig: SchedulePaletteConfig,
    onPalettePresetChanged: (String) -> Unit,
    onCardStyleChanged: (String) -> Unit,
    onCustomPaletteColorChanged: (Int, String) -> Unit,
    onBackgroundConfigChanged: (ScheduleBackgroundConfig) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    /** 课程提醒设置需要读写偏好;为 null 时隐藏该分区(如预览) */
    sessionManager: com.ahu_plus.data.local.SessionManager? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val backgroundConfig = paletteConfig.backgroundOrDefault()
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeSliderPreview by remember { mutableStateOf<SliderDragPreview?>(null) }
    var sheetRootPosition by remember { mutableStateOf(Offset.Zero) }
    val isAppearanceSliderDragging = activeSliderPreview != null
    val contentScrollIsolation = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(0f, available.y)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity(0f, available.y)
        }
    }
    val backgroundImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onBackgroundConfigChanged(
                backgroundConfig.copy(
                    backgroundMode = "image",
                    backgroundImageUri = uri.toString(),
                )
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isAppearanceSliderDragging) Color.Transparent else MaterialTheme.colorScheme.surface,
        scrimColor = if (isAppearanceSliderDragging) Color.Transparent
        else MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        tonalElevation = if (isAppearanceSliderDragging) 0.dp else 1.dp,
        dragHandle = {
            if (!isAppearanceSliderDragging) BottomSheetDefaults.DragHandle()
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .onGloballyPositioned { sheetRootPosition = it.positionInRoot() },
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isAppearanceSliderDragging) 0f else 1f)
                .navigationBarsPadding(),
        ) {
            // 标题行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "课表设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "当前：${CoursePalettes.preset(paletteConfig.presetId).name} · ${CourseCardStyle.fromStorage(paletteConfig.cardStyle).label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onReset) { Text("恢复默认") }
            }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                listOf("卡片", "背景", "布局", "行为", "提醒").forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(contentScrollIsolation)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {

            if (selectedTab == 0) {
                SettingsPageTitle("课程卡片", "选择整套配色和卡片呈现方式")
                SchedulePalettePreview(config = paletteConfig)

                Text(
                    text = "配色方案",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                CoursePalettes.presets.chunked(2).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowPresets.forEach { preset ->
                            PalettePresetCard(
                                name = preset.name,
                                description = preset.description,
                                colors = if (preset.id == "custom") {
                                    CoursePalettes.colors(paletteConfig.copy(presetId = "custom"))
                                } else preset.colors,
                                selected = paletteConfig.presetId == preset.id,
                                onClick = { onPalettePresetChanged(preset.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowPresets.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                Text(
                    text = "卡片质感",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CourseCardStyle.entries.forEach { style ->
                        FilterChip(
                            selected = paletteConfig.cardStyle == style.storageValue,
                            onClick = { onCardStyleChanged(style.storageValue) },
                            label = { Text(style.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (paletteConfig.presetId == "custom") {
                    CustomPaletteEditor(
                        config = paletteConfig,
                        onColorChanged = onCustomPaletteColorChanged,
                    )
                }

                Text(
                    text = "单门课程可在课程详情右上角单独指定颜色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (selectedTab == 1) {
                SettingsPageTitle("网格与画布", "调整空白区域的结构、颜色和背景图片")
                ScheduleBackgroundPreview(
                    paletteConfig = paletteConfig,
                    backgroundConfig = backgroundConfig,
                )

                Text(
                    text = "网格样式",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "classic" to "经典网格",
                        "blocks" to "块状分区",
                        "clean" to "极简留白",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = backgroundConfig.gridStyle == value,
                            onClick = {
                                onBackgroundConfigChanged(backgroundConfig.copy(gridStyle = value))
                            },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (backgroundConfig.gridStyle == "classic") {
                    SettingsSwitchRow(
                        title = "显示网格线",
                        subtitle = "关闭后保留行列底色，但不绘制分隔线",
                        checked = backgroundConfig.showGridLines,
                        onCheckedChange = {
                            onBackgroundConfigChanged(backgroundConfig.copy(showGridLines = it))
                        },
                    )
                }
                SettingsSwitchRow(
                    title = "交替行底色",
                    subtitle = "相邻节次使用轻微不同的背景明度",
                    checked = backgroundConfig.alternatingRows,
                    onCheckedChange = {
                        onBackgroundConfigChanged(backgroundConfig.copy(alternatingRows = it))
                    },
                )
                SettingsSwitchRow(
                    title = "突出今日列",
                    subtitle = "为今天所在列增加主题色底纹",
                    checked = backgroundConfig.highlightToday,
                    onCheckedChange = {
                        onBackgroundConfigChanged(backgroundConfig.copy(highlightToday = it))
                    },
                )

                if (backgroundConfig.gridStyle == "blocks") {
                    SettingSlider(
                        label = "块间距",
                        value = backgroundConfig.blockGapDp,
                        valueRange = 0f..6f,
                        steps = 5,
                        valueText = "${"%.1f".format(backgroundConfig.blockGapDp)} dp",
                        onValueChange = {
                            onBackgroundConfigChanged(backgroundConfig.copy(blockGapDp = it))
                        },
                        onDragPreviewChanged = { activeSliderPreview = it },
                    )
                    SettingSlider(
                        label = "块圆角",
                        value = backgroundConfig.blockRadiusDp,
                        valueRange = 0f..18f,
                        steps = 8,
                        valueText = "${backgroundConfig.blockRadiusDp.toInt()} dp",
                        onValueChange = {
                            onBackgroundConfigChanged(backgroundConfig.copy(blockRadiusDp = it))
                        },
                        onDragPreviewChanged = { activeSliderPreview = it },
                    )
                }

                Text(
                    text = "画布背景",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "palette" to "跟随配色",
                        "solid" to "纯色",
                        "image" to "背景图",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = backgroundConfig.backgroundMode == value,
                            onClick = {
                                if (value == "image" && backgroundConfig.backgroundImageUri.isBlank()) {
                                    backgroundImageLauncher.launch(arrayOf("image/*"))
                                } else {
                                    onBackgroundConfigChanged(backgroundConfig.copy(backgroundMode = value))
                                }
                            },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                when (backgroundConfig.backgroundMode) {
                    "solid" -> SolidBackgroundPicker(
                        selected = backgroundConfig.backgroundColor,
                        onSelect = {
                            onBackgroundConfigChanged(backgroundConfig.copy(backgroundColor = it))
                        },
                    )
                    "image" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { backgroundImageLauncher.launch(arrayOf("image/*")) },
                                modifier = Modifier.weight(1f),
                            ) { Text(if (backgroundConfig.backgroundImageUri.isBlank()) "选择图片" else "更换图片") }
                            if (backgroundConfig.backgroundImageUri.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        onBackgroundConfigChanged(
                                            backgroundConfig.copy(
                                                backgroundMode = "palette",
                                                backgroundImageUri = "",
                                            )
                                        )
                                    }
                                ) { Text("移除") }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = backgroundConfig.imageScale == "crop",
                                onClick = {
                                    onBackgroundConfigChanged(backgroundConfig.copy(imageScale = "crop"))
                                },
                                label = { Text("铺满裁切") },
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = backgroundConfig.imageScale == "fit",
                                onClick = {
                                    onBackgroundConfigChanged(backgroundConfig.copy(imageScale = "fit"))
                                },
                                label = { Text("完整显示") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        SettingSlider(
                            label = "图片亮度",
                            value = backgroundConfig.imageOpacity,
                            valueRange = 0.1f..1f,
                            steps = 8,
                            valueText = "${(backgroundConfig.imageOpacity * 100).toInt()}%",
                            onValueChange = {
                                onBackgroundConfigChanged(backgroundConfig.copy(imageOpacity = it))
                            },
                            onDragPreviewChanged = { activeSliderPreview = it },
                        )
                        SettingSlider(
                            label = "可读性遮罩",
                            value = backgroundConfig.overlayStrength,
                            valueRange = 0f..0.9f,
                            steps = 8,
                            valueText = "${(backgroundConfig.overlayStrength * 100).toInt()}%",
                            onValueChange = {
                                onBackgroundConfigChanged(backgroundConfig.copy(overlayStrength = it))
                            },
                            onDragPreviewChanged = { activeSliderPreview = it },
                        )
                        SettingSlider(
                            label = "空白格不透明度",
                            value = backgroundConfig.cellOpacity,
                            valueRange = 0.08f..1f,
                            steps = 10,
                            valueText = "${(backgroundConfig.cellOpacity * 100).toInt()}%",
                            onValueChange = {
                                onBackgroundConfigChanged(backgroundConfig.copy(cellOpacity = it))
                            },
                            onDragPreviewChanged = { activeSliderPreview = it },
                        )
                    }
                }
            }

            if (selectedTab == 2) {
                SettingsPageTitle("布局与尺寸", "调整课表密度以及需要展示的日期范围")
                Text(
                    text = "拖动滑块时设置页会暂时隐藏，松手后自动恢复",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                SettingSlider(
                    label = "列宽",
                    value = colWidthDp,
                    valueRange = 48f..80f,
                    steps = 15,
                    valueText = "${colWidthDp.toInt()} dp",
                    onValueChange = onColWidthChanged,
                    onDragPreviewChanged = { activeSliderPreview = it },
                )
                SettingSlider(
                    label = "行高",
                    value = rowHeightDp,
                    valueRange = 44f..72f,
                    steps = 13,
                    valueText = "${rowHeightDp.toInt()} dp",
                    onValueChange = onRowHeightChanged,
                    onDragPreviewChanged = { activeSliderPreview = it },
                )
                SettingSlider(
                    label = "字体缩放",
                    value = fontScale,
                    valueRange = 0.75f..1.5f,
                    steps = 14,
                    valueText = "×${"%.2f".format(fontScale)}",
                    onValueChange = onFontScaleChanged,
                    onDragPreviewChanged = { activeSliderPreview = it },
                )
                SettingsSwitchRow(
                    title = "展示周六",
                    subtitle = "关闭后周六列隐藏 (如有课会弹确认)",
                    checked = showSat,
                    onCheckedChange = onShowSatChanged,
                )
                SettingsSwitchRow(
                    title = "展示周日",
                    subtitle = "关闭后周日列隐藏 (如有课会弹确认)",
                    checked = showSun,
                    onCheckedChange = onShowSunChanged,
                )
                SettingsSwitchRow(
                    title = "显示其他学期课表行",
                    subtitle = "关闭后顶部学期切换行隐藏,仅显示本学期",
                    checked = showOtherSemesters,
                    onCheckedChange = onShowOtherSemestersChanged,
                )
            }

            if (selectedTab == 3) {
                SettingsPageTitle("操作行为", "设置进入课表和切换周次时的交互方式")
                SettingsSwitchRow(
                    title = "左右滑动切换周",
                    subtitle = "开启后可在课表内左右滑动切换周次",
                    checked = pagerEnabled,
                    onCheckedChange = onPagerEnabledChanged,
                )
                SettingsSwitchRow(
                    title = "进入课表重置为本周",
                    subtitle = "每次从首页进入课表时自动跳到当前周",
                    checked = resetOnEnter,
                    onCheckedChange = onResetOnEnterChanged,
                )
            }

            if (selectedTab == 4) {
                SettingsPageTitle("课程提醒", "上课前通过系统通知提醒时间和教室")
                if (sessionManager != null) {
                    CourseReminderSettings(sessionManager = sessionManager)
                } else {
                    Text(
                        text = "当前预览环境无法读取课程提醒设置。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
        }
        activeSliderPreview?.let { preview ->
            SliderDragOverlay(
                preview = preview,
                sheetRootPosition = sheetRootPosition,
            )
        }
        }
    }
}

@Composable
private fun SettingsPageTitle(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScheduleBackgroundPreview(
    paletteConfig: SchedulePaletteConfig,
    backgroundConfig: ScheduleBackgroundConfig,
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val visuals = CoursePalettes.backgroundVisuals(
        paletteConfig, MaterialTheme.colorScheme, darkTheme
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(AhuShapes.Card)
            .background(visuals.canvas),
    ) {
        if (backgroundConfig.backgroundMode == "image" &&
            backgroundConfig.backgroundImageUri.isNotBlank()
        ) {
            AsyncImage(
                model = backgroundConfig.backgroundImageUri,
                contentDescription = null,
                contentScale = if (backgroundConfig.imageScale == "fit") ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(126.dp),
                alpha = backgroundConfig.imageOpacity.coerceIn(0.1f, 1f),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .background(
                        MaterialTheme.colorScheme.background.copy(
                            alpha = backgroundConfig.overlayStrength.coerceIn(0f, 0.9f)
                        )
                    )
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(
                if (backgroundConfig.gridStyle == "blocks") backgroundConfig.blockGapDp.dp else 0.dp
            ),
        ) {
            repeat(3) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(
                        if (backgroundConfig.gridStyle == "blocks") backgroundConfig.blockGapDp.dp else 0.dp
                    ),
                ) {
                    repeat(5) { col ->
                        val isToday = col == 2 && backgroundConfig.highlightToday
                        val cellColor = when (backgroundConfig.gridStyle) {
                            "clean" -> when {
                                isToday -> visuals.todayCell
                                backgroundConfig.alternatingRows && row % 2 == 1 ->
                                    visuals.oddCell.copy(alpha = visuals.oddCell.alpha * 0.42f)
                                else -> Color.Transparent
                            }
                            else -> when {
                                isToday -> visuals.todayCell
                                !backgroundConfig.alternatingRows || row % 2 == 0 -> visuals.evenCell
                                else -> visuals.oddCell
                            }
                        }
                        val shape = if (backgroundConfig.gridStyle == "blocks") {
                            RoundedCornerShape(backgroundConfig.blockRadiusDp.dp)
                        } else RoundedCornerShape(0.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(shape)
                                .background(cellColor)
                                .then(
                                    if (backgroundConfig.gridStyle == "classic" && backgroundConfig.showGridLines) {
                                        Modifier.border(0.5.dp, visuals.gridLine, shape)
                                    } else Modifier
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SolidBackgroundPicker(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = listOf(
        Color(0xFFF7FAFF), Color(0xFFFFF8F2), Color(0xFFF5FBF7), Color(0xFFFAF6FF),
        Color(0xFFF4F1EA), Color(0xFFEFF4F8), Color(0xFF161B24), Color(0xFF20231F),
        Color(0xFF221E27), Color(0xFF1D2428), Color(0xFF2A2521), Color(0xFF262A32),
    )
    Text(
        text = "背景颜色",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    colors.chunked(6).forEach { rowColors ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            rowColors.forEach { color ->
                val storage = CoursePalettes.toStorage(color)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            if (selected.equals(storage, ignoreCase = true)) 3.dp else 1.dp,
                            if (selected.equals(storage, ignoreCase = true)) {
                                MaterialTheme.colorScheme.primary
                            } else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        )
                        .clickable { onSelect(storage) },
                )
            }
        }
    }
}

@Composable
private fun SchedulePalettePreview(config: SchedulePaletteConfig) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = CoursePalettes.colors(config)
    val background = CoursePalettes.backgroundVisuals(config, MaterialTheme.colorScheme, darkTheme)
    val style = CourseCardStyle.fromStorage(config.cardStyle)
    val labels = listOf("高等数学", "大学英语", "数据结构")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(AhuShapes.Card)
            .background(background.canvas)
            .border(1.dp, background.gridLine, AhuShapes.Card)
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            labels.forEachIndexed { index, label ->
                val visuals = CoursePalettes.cardVisuals(
                    palette[index], style, MaterialTheme.colorScheme, darkTheme
                )
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (index == 1) 88.dp else 68.dp),
                    shape = AhuShapes.Card,
                    colors = CardDefaults.cardColors(containerColor = visuals.container),
                    border = if (style == CourseCardStyle.OUTLINE) {
                        androidx.compose.foundation.BorderStroke(1.dp, visuals.outline)
                    } else null,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(Modifier.fillMaxHeight()) {
                        if (style == CourseCardStyle.OUTLINE) {
                            Box(Modifier.fillMaxHeight().width(4.dp).background(visuals.accent))
                        }
                        Column(Modifier.padding(7.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = visuals.content,
                                maxLines = 2,
                            )
                            Text(
                                text = if (index == 1) "博学南楼" else "逸夫图书馆",
                                style = MaterialTheme.typography.labelSmall,
                                color = visuals.secondaryContent,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PalettePresetCard(
    name: String,
    description: String,
    colors: List<Color>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, AhuShapes.Card)
                else Modifier
            ),
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                colors.take(5).forEach { color ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(color))
                }
            }
        }
    }
}

@Composable
private fun CustomPaletteEditor(
    config: SchedulePaletteConfig,
    onColorChanged: (Int, String) -> Unit,
) {
    var selectedSlot by remember { mutableStateOf(0) }
    val colors = CoursePalettes.colors(config.copy(presetId = "custom"))

    Text(
        text = "编辑颜色槽位",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        colors.forEachIndexed { index, color ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color)
                    .then(
                        if (selectedSlot == index) Modifier.border(
                            2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)
                        ) else Modifier
                    )
                    .clickable { selectedSlot = index },
            )
        }
    }
    Text(
        text = "正在编辑第 ${selectedSlot + 1} 个颜色",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    CoursePalettes.customColorBank.chunked(8).forEach { rowColors ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            rowColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable {
                            onColorChanged(selectedSlot, CoursePalettes.toStorage(color))
                        },
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.padding(horizontal = 4.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderDragOverlay(
    preview: SliderDragPreview,
    sheetRootPosition: Offset,
) {
    val density = LocalDensity.current
    val width = with(density) { preview.boundsInRoot.width.toDp() }
    val height = with(density) { preview.boundsInRoot.height.toDp() }
    val x = (preview.boundsInRoot.left - sheetRootPosition.x).roundToInt()
    val y = (preview.boundsInRoot.top - sheetRootPosition.y).roundToInt()
    Slider(
        value = preview.value.coerceIn(preview.valueRange),
        onValueChange = {},
        valueRange = preview.valueRange,
        steps = preview.steps,
        modifier = Modifier
            .offset { IntOffset(x, y) }
            .size(width, height),
    )
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onDragPreviewChanged: ((SliderDragPreview?) -> Unit)? = null,
) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = {
                if (!isDragging) {
                    isDragging = true
                }
                onDragPreviewChanged?.invoke(
                    SliderDragPreview(
                        label = label,
                        value = it,
                        valueRange = valueRange,
                        steps = steps,
                        boundsInRoot = sliderBoundsInRoot,
                    )
                )
                onValueChange(it)
            },
            onValueChangeFinished = {
                if (isDragging) {
                    isDragging = false
                    onDragPreviewChanged?.invoke(null)
                }
            },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.onGloballyPositioned {
                sliderBoundsInRoot = it.boundsInRoot()
            },
        )
    }
}
