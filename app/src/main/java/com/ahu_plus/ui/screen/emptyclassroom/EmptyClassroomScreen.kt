package com.ahu_plus.ui.screen.emptyclassroom

import com.ahu_plus.ui.components.CenteredLoader
import com.ahu_plus.ui.components.CenteredError
import com.ahu_plus.ui.components.CenteredMessage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.AhuUnitTimes
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.repository.FreeRoomResult
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.components.DataStatusFooter
import com.ahu_plus.ui.theme.AhuGreen
import com.ahu_plus.ui.theme.AhuOrange
import com.ahu_plus.ui.theme.AhuRed
import kotlinx.coroutines.delay
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val FreeGreen = AhuGreen
private val FreeOrange = AhuOrange
private val FreeRed = AhuRed
private val BarBackground = Color(0xFFE0E0E0)
private val PastUnitGray = Color(0xFFBDBDBD)
private val BusyUnitGray = Color(0xFFEEEEEE)
private val NowIndicatorColor = Color(0xFF1976D2)  // Material Blue 700,对比更强

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmptyClassroomScreen(
    viewModel: EmptyClassroomViewModel,
    onBack: () -> Unit,
    onNeedsLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 单例 60 秒 tick:驱动「当前时间指示条」位置刷新 + 节次跨越自动刷新
    // 提升自原来的 per-card LaunchedEffect,避免 20+ 个房间起 20+ 个循环。
    var minuteTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            minuteTick++
        }
    }

    // 节次跨越自动刷新:仅在今天且已选教学楼时,节次变更触发 onRefresh。
    LaunchedEffect(minuteTick, uiState.isSelectedDateToday, uiState.hasBuildingSelected) {
        if (uiState.isSelectedDateToday && uiState.hasBuildingSelected) {
            val nowUnit = AhuUnitTimes.getCurrentUnit(DebugClock.nowTime())
            if (nowUnit != uiState.currentUnit) {
                viewModel.onRefresh()
            }
        }
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("空教室查询") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::onRefresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // ── 选择区域（始终可见）──────────────────────
            item { SelectorArea(uiState = uiState, viewModel = viewModel) }

            // ── 结果区域 ─────────────────────────────────
            when {
                uiState.isLoading && uiState.rooms.isEmpty() -> {
                    item { CenteredLoader(modifier = Modifier.fillMaxWidth().height(200.dp)) }
                }

                uiState.error != null && uiState.rooms.isEmpty() -> {
                    item {
                        CenteredError(
                            message = uiState.error ?: "加载失败",
                            onRetry = if (uiState.needsLogin) onNeedsLogin else viewModel::onRefresh,
                            actionLabel = if (uiState.needsLogin) "去登录" else "重试",
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        )
                    }
                }

                !uiState.hasBuildingSelected -> {
                    item {
                        CenteredMessage(
                            text = "请选择校区和教学楼",
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        )
                    }
                }

                uiState.filteredRooms.isEmpty() && !uiState.isLoading -> {
                    item {
                        CenteredMessage(
                            text = when {
                                uiState.isSelectedDateToday && uiState.currentUnit == null ->
                                    "今天所有课程已结束"
                                uiState.selectedFloor != null ->
                                    "该楼层暂无空闲教室"
                                !uiState.isSelectedDateToday ->
                                    "所选日期暂无空闲教室"
                                else -> "当前教学楼暂无空闲教室"
                            },
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        )
                    }
                }

                else -> {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "共 ${uiState.filteredRooms.size} 间空闲教室",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(
                        items = uiState.filteredRooms,
                        key = { it.room.id }
                    ) { result ->
                        RoomResultCard(
                            result = result,
                            currentUnit = uiState.currentUnit,
                            isToday = uiState.isSelectedDateToday,
                            continuousMode = uiState.continuousFree,
                            minuteTick = minuteTick
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
            uiState.dataStatus?.let { status ->
                item(key = "data-status") {
                    DataStatusFooter(status = status)
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ── 选择区域 ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorArea(
    uiState: EmptyClassroomUiState,
    viewModel: EmptyClassroomViewModel
) {
    if (uiState.presets.isNotEmpty() || uiState.hasBuildingSelected) {
        Text(
            text = "快捷方案",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            uiState.presets.forEach { preset ->
                val currentOffset = ChronoUnit.DAYS.between(
                    DebugClock.todayDate(),
                    uiState.selectedDate,
                ).toInt()
                val selected = preset.campusId == uiState.selectedCampusId &&
                    preset.buildingId == uiState.selectedBuildingId &&
                    preset.floor == uiState.selectedFloor &&
                    preset.dayOffset == currentOffset &&
                    preset.continuousFree == uiState.continuousFree
                InputChip(
                    selected = selected,
                    onClick = { viewModel.applyPreset(preset) },
                    label = { Text(preset.title) },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.deletePreset(preset.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "删除${preset.title}",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
            if (uiState.hasBuildingSelected) {
                FilledTonalButton(onClick = viewModel::saveCurrentPreset) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("保存当前")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Header 文本三态:今天-有当前节 / 今天-课程已结束 / 非今天-全天
    val unitInfo = when {
        !uiState.isSelectedDateToday -> "全天"
        uiState.currentUnit != null -> {
            val timeRange = AhuUnitTimes.formatUnitTime(uiState.currentUnit)
            "当前第 ${uiState.currentUnit} 节 ($timeRange)"
        }
        else -> "课程已结束"
    }
    val headerText = "${uiState.selectedDate.format(dateFormatter)} · $unitInfo"
    Text(
        text = headerText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )

    // 日期选择按钮 (今天/明天/+30天)
    var showDatePicker by remember { mutableStateOf(false) }
    val today = DebugClock.todayDate()
    val tomorrow = today.plusDays(1)
    val dayAfterTomorrow = today.plusDays(2)
    val dateButtonLabel = buildString {
        append(uiState.selectedDate.format(dateFormatter))
        when (uiState.selectedDate) {
            today -> append(" (今天)")
            tomorrow -> append(" (明天)")
            dayAfterTomorrow -> append(" (后天)")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(onClick = { showDatePicker = true }) {
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(dateButtonLabel)
        }
        // 明天/后天快捷选择 (2026-06-25:右侧两个 chip,不显示具体日期号)
        FilterChip(
            selected = uiState.selectedDate == tomorrow,
            onClick = { viewModel.selectDate(tomorrow) },
            label = { Text("明天") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        FilterChip(
            selected = uiState.selectedDate == dayAfterTomorrow,
            onClick = { viewModel.selectDate(dayAfterTomorrow) },
            label = { Text("后天") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }

    if (showDatePicker) {
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val maxMillis = today.plusDays(30).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis in todayMillis..maxMillis

                override fun isSelectableYear(year: Int): Boolean = year == today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.selectedDateMillis?.let { millis ->
                        java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    if (picked != null && !picked.isBefore(today)) {
                        viewModel.selectDate(picked)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            // 2026-06-25: Material3 默认中文显示 "星期一/二/..." 含 "星" 字。
            // Material3 内部 WeekDays 用 DayOfWeek.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
            // 渲染星期缩写 — 对简体中文该 API 直接给出 "一/二/三/四/五/六/日"。
            // 默认全屏 (FULL_STANDALONE) 给出 "星期一" 含 "星" 字,但 Material3 WeekDays
            // 实际取 NARROW_STANDALONE (CalendarModel.android.kt:71),所以只要 locale 是
            // 简体中文就应显示单字。检查确认在部分 ROM 上 FULL 落到 UI — 故兜底用
            // Configuration override 强制 Locale.SIMPLIFIED_CHINESE。
            val originalConfig = LocalConfiguration.current
            val overrideConfig = remember(originalConfig) {
                android.content.res.Configuration(originalConfig).apply {
                    setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
                    setLocales(android.os.LocaleList(java.util.Locale.SIMPLIFIED_CHINESE))
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides overrideConfig
            ) {
                DatePicker(state = state)
            }
        }
    }

    // 校区选择
    Text(
        text = "校区",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        uiState.availableCampuses.forEach { campus ->
            FilterChip(
                selected = campus.id == uiState.selectedCampusId,
                onClick = { viewModel.selectCampus(campus.id) },
                label = { Text(campus.nameZh) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }

    // 教学楼选择
    if (uiState.selectedCampusId != null && uiState.buildings.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "教学楼",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.buildings.forEach { building ->
                FilterChip(
                    selected = building.id == uiState.selectedBuildingId,
                    onClick = { viewModel.selectBuilding(building.id) },
                    label = { Text(building.nameZh) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }

    // 楼层过滤
    if (uiState.availableFloors.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "楼层",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.selectedFloor == null,
                onClick = { viewModel.selectFloor(null) },
                label = { Text("全部") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            uiState.availableFloors.forEach { floor ->
                FilterChip(
                    selected = floor == uiState.selectedFloor,
                    onClick = { viewModel.selectFloor(floor) },
                    label = { Text("${floor}F") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }

    // 2026-06-25: "连续空闲"排序开关。
    // 设计而非 bug:开启后按最长连续空闲段长度降序排,用户更在意"能连坐多少节"。
    // 每次进入页面默认关闭,需手动开启。
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "连续空闲",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "开启后按最长连续空闲段长度重排序(例如中午有课,后面的就不算)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = uiState.continuousFree,
            onCheckedChange = { viewModel.setContinuousFree(it) }
        )
    }
}

// ── 单间教室卡片 ─────────────────────────────────────────────

@Composable
private fun RoomResultCard(
    result: FreeRoomResult,
    currentUnit: Int?,
    isToday: Boolean = true,
    continuousMode: Boolean = false,
    @Suppress("UNUSED_PARAMETER") minuteTick: Int = 0
) {
    val continuousCount = continuousFreeCount(result)
    val freeColor = when {
        result.freeUnitsCount >= 4 -> FreeGreen
        result.freeUnitsCount >= 2 -> FreeOrange
        else -> FreeRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 第一行：教室名 + 空闲时长标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.room.nameZh,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FreeDurationChip(
                    unitCount = result.freeUnitsCount,
                    segmentCount = result.freeSegments.size,
                    continuousMode = continuousMode,
                    continuousCount = continuousCount,
                    color = freeColor
                )
            }

            // 第二行：类型 + 楼层 + 座位
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val roomType = result.room.roomType?.nameZh
                if (!roomType.isNullOrBlank()) {
                    Text(
                        text = roomType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                result.room.floor?.let { floor ->
                    Text(
                        text = "${floor}F",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                result.room.seats?.let { seats ->
                    if (seats > 0) {
                        Text(
                            text = "${seats}座",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!result.room.remark.isNullOrBlank()) {
                    Text(
                        text = result.room.remark,
                        style = MaterialTheme.typography.bodySmall,
                        color = FreeGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 第三行：空闲时间段文字
            if (result.freeTimeRange.isNotEmpty()) {
                Text(
                    text = result.freeTimeRange,
                    style = MaterialTheme.typography.bodySmall,
                    color = freeColor,
                    fontWeight = FontWeight.Medium
                )
            }

            // 第四行：可视化空闲条 (条高度 14dp 留出顶部三角箭头空间)
            FreeTimeBar(
                freeSegments = result.freeSegments,
                currentUnit = currentUnit,
                freeColor = freeColor,
                isToday = isToday,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
            )
        }
    }
}

// ── 空闲时长标签 ─────────────────────────────────────────────

@Composable
private fun FreeDurationChip(
    unitCount: Int,
    segmentCount: Int,
    continuousMode: Boolean,
    continuousCount: Int,
    color: Color
) {
    // 连续空闲模式:主标签显示"连坐 N 节",副标签括号显示全天总空闲数(若不同)。
    val text = when {
        continuousMode && unitCount > continuousCount ->
            "连坐 $continuousCount 节 (全天 $unitCount)"
        continuousMode ->
            "连坐 $continuousCount 节"
        segmentCount > 1 ->
            "空闲 $unitCount 节 ($segmentCount 段)"
        else ->
            "空闲 $unitCount 节"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ── 可视化空闲时间条 ─────────────────────────────────────────

/**
 * 13 节次全宽可视化条。
 *
 * 视觉:
 * - 每节一个圆角矩形;空闲=品牌色,过去空闲=半透明品牌色,过去占用=深灰,占用=浅灰。
 * - 当前时间竖线 (仅今天): 加粗到 3dp 蓝色竖线 + 顶端 6×4dp 实心三角箭头,顶部上方延出 4dp。
 * - 多段空闲支持: 根据 [freeSegments] (折叠后的 IntRange 列表) 渲染多个绿色块。
 *
 * 数据来源: [FreeRoomResult.freeSegments] = [AhuUnitTimes.collapseToSegments] 输出。
 *
 * tick 由顶层 [EmptyClassroomScreen] 注入 ([minuteTick]),避免每个 card 单独跑 60s 循环。
 */
@Composable
private fun FreeTimeBar(
    freeSegments: List<IntRange>,
    currentUnit: Int?,
    freeColor: Color,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    val totalUnits = AhuUnitTimes.totalUnits()

    val nowLineFraction: Float? = remember(isToday, currentUnit) {
        if (!isToday) return@remember null
        val now = DebugClock.nowTime()
        val nowMin = now.hour * 60 + now.minute
        val unit = currentUnit ?: return@remember null
        val times = AhuUnitTimes.UNIT_TO_TIME[unit] ?: return@remember null
        val startMin = parseTimeMinutes(times.first) ?: return@remember null
        val endMin = parseTimeMinutes(times.second) ?: return@remember null
        if (nowMin !in startMin..endMin) return@remember null
        (nowMin - startMin).toFloat() / (endMin - startMin).toFloat()
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width
        val barHeight = size.height
        val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        // 百分比布局(总宽 100%):
        //   左右各 5% 边距 + 13 节 × 6% + 小空隙 2% ×2 + 大空隙 4% ×2 = 100%
        //   每节 6% 槽位内再留 0.2% × 2 内边距 → 实际填充 5.6%
        //   小空隙:2|3(上午内)、7|8(下午内);大空隙:5|6(上午↔下午)、10|11(下午↔晚上)
        val leftMargin = barWidth * 0.05f
        val rightMargin = barWidth * 0.05f
        val unitW = barWidth * 0.06f        // 每节槽位 6%
        val cellPad = barWidth * 0.002f     // 每节内左右各 0.2% 内边距
        val unitFillW = unitW - cellPad * 2 // 实际填充 5.6%
        val smallGap = barWidth * 0.02f
        val bigGap = barWidth * 0.04f
        val gapAfterUnit: Map<Int, Float> = mapOf(
            2 to smallGap,
            5 to bigGap,
            7 to smallGap,
            10 to bigGap
        )

        // 预计算每节起点 X(左边距 + 之前节次宽 + 之前 gap)
        val unitStartX = FloatArray(totalUnits + 1) { 0f }
        run {
            var x = leftMargin
            for (u in 1..totalUnits) {
                unitStartX[u] = x
                x += unitW + (gapAfterUnit[u] ?: 0f)
            }
            // 校验:最后一节右端 + 右边距 ≈ barWidth
            require(kotlin.math.abs(
                unitStartX[totalUnits] + unitW + rightMargin - barWidth
            ) < 0.5f) { "百分比布局总和 ≠ 100%" }
        }

        // 1. 绘制 13 节次方块
        for (unit in 1..totalUnits) {
            // 多段空闲: 任一区间包含 unit 即视为空闲
            val isFree = freeSegments.any { unit in it }
            val isPast = isToday && currentUnit != null && unit < currentUnit

            val color = when {
                isPast && !isFree -> PastUnitGray
                isPast && isFree -> freeColor.copy(alpha = 0.3f)
                isFree -> freeColor
                else -> BusyUnitGray
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(unitStartX[unit] + cellPad, 0f),
                size = Size(unitFillW, barHeight),
                cornerRadius = cornerRadius
            )
        }

        // 2. 当前时间竖线 + 三角箭头 (仅今天 + 有当前节次)
        if (nowLineFraction != null && currentUnit != null) {
            // 当前时间线走整个 6% 槽位宽度,不受 0.2% 内边距影响
            val lineX = unitStartX[currentUnit] + unitW * nowLineFraction

            // 2a. 加粗蓝色竖线 (3dp)
            drawLine(
                color = NowIndicatorColor,
                start = Offset(lineX, 0f),
                end = Offset(lineX, barHeight),
                strokeWidth = 3.dp.toPx()
            )

            // 2b. 顶端三角箭头 (从条顶部向上延伸 4dp,宽 6dp)
            val arrowHalfWidth = 3.dp.toPx()
            val arrowHeight = 4.dp.toPx()
            val arrowPath = Path().apply {
                moveTo(lineX - arrowHalfWidth, 0f)
                lineTo(lineX + arrowHalfWidth, 0f)
                lineTo(lineX, -arrowHeight)
                close()
            }
            drawPath(path = arrowPath, color = NowIndicatorColor)
        }
    }
}

private fun parseTimeMinutes(s: String): Int? {
    val parts = s.split(":")
    if (parts.size < 2) return null
    return (parts[0].toIntOrNull() ?: return null) * 60 + (parts[1].toIntOrNull() ?: return null)
}

// ── 通用状态组件已移至 ui/components/CenteredComponents.kt ──
