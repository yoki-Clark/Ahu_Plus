package com.ahu_plus.ui.screen.schedule

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.local.DataSnapshotStatus
import com.ahu_plus.data.model.jw.CourseDisplayItem
import com.ahu_plus.ui.components.CompactDataStatusFooter
import com.ahu_plus.data.model.jw.CourseUnit
import com.ahu_plus.data.model.schedule.SchedulePaletteConfig
import com.ahu_plus.data.model.schedule.backgroundOrDefault
import com.ahu_plus.data.model.jw.parseTimeMinutes
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.CourseCardStyle
import com.ahu_plus.ui.theme.CoursePalettes
import com.ahu_plus.ui.theme.ScheduleBackgroundVisuals
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// (CourseColors moved to ui/theme/CourseColors.kt in 2026-06-17 refactor)

// ── 布局常量 (可经设置面板调整) ──────────────────────────
private val TIME_COL_WIDTH = 40.dp
private val HEADER_HEIGHT = 40.dp

private val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 计算第 [targetWeek] 周的周一日期。
 *
 * 锚点:服务器返回的 `currentWeek` 表示"今天是第 currentWeek 周"。
 * 先把今天对齐到本周一(ISO 周一为起点),再按 (targetWeek - currentWeek) 周偏移。
 *
 * 例:今天周三、currentWeek=17,要算第 18 周周一 →
 *     本周一(周三回退到周一)+ (18-17)*7 = 本周一 + 7 天。
 *
 * 2026-06-25:课表头部需叠加具体日期号。
 */
private fun computeWeekMonday(targetWeek: Int, currentWeek: Int, todayDate: LocalDate): LocalDate {
    val todayMonday = todayDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val offsetWeeks = (targetWeek - currentWeek).toLong()
    return todayMonday.plusDays(offsetWeeks * 7)
}

@Composable
fun WeekGrid(
    displayItems: List<CourseDisplayItem>,
    unitTimes: List<CourseUnit>,
    selectedWeek: Int,
    currentWeek: Int,
    onCourseClick: (CourseDisplayItem) -> Unit = {},
    modifier: Modifier = Modifier,
    colWidth: Dp = 64.dp,
    rowHeight: Dp = 56.dp,
    fontScale: Float = 1.0f,
    /** 是否显示周六 (默认 true)。false 时周六列隐藏 */
    showSat: Boolean = true,
    /** 是否显示周日 (默认 true)。false 时周日列隐藏 */
    showSun: Boolean = true,
    /** 时间 tick：传入外部递增的 Int，每分钟 +1，触发当前时间线重算（默认 0） */
    timeTick: Int = 0,
    /**
     * 共享的垂直滚动状态。由外部(ScheduleScreen)创建并同时传给固定时间列,
     * 使时间列与网格体垂直滚动同步,且切换周次(Pager 换页)时滚动位置不丢失。
     * 为 null 时内部自建(向后兼容/预览)。
     */
    sharedVerScroll: ScrollState? = null,
    paletteConfig: SchedulePaletteConfig = SchedulePaletteConfig(),
    dataStatus: DataSnapshotStatus? = null,
    /** 学期第 1 周的起始日期；跨学期课表传入后，表头日期不再按今天反推。 */
    semesterStartDate: LocalDate? = null,
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backgroundVisuals = CoursePalettes.backgroundVisuals(
        paletteConfig, MaterialTheme.colorScheme, darkTheme
    )
    val backgroundConfig = paletteConfig.backgroundOrDefault()
    // 可见的星期列表 (用于头部 + 列计算)。1=周一 ... 7=周日。
    val visibleDays: List<Int> = remember(showSat, showSun) {
        buildList {
            for (d in 1..5) add(d)
            if (showSat) add(6)
            if (showSun) add(7)
        }
    }
    val sortedUnits = remember(unitTimes) {
        unitTimes.filter { it.indexNo != null }.sortedBy { it.indexNo }
    }
    if (sortedUnits.isEmpty()) return

    val today = DebugClock.todayDate()
    val todayDayOfWeek = today.dayOfWeek.value
    val isCurrentWeek = selectedWeek == currentWeek

    val minUnit = sortedUnits.minOf { it.indexNo ?: 1 }
    val maxUnit = sortedUnits.maxOf { it.indexNo ?: 13 }
    val totalRows = maxUnit - minUnit + 1
    val unitMap = sortedUnits.associateBy { it.indexNo }

    val horScroll = rememberScrollState()
    val verScroll = sharedVerScroll ?: rememberScrollState()

    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val viewportColumnWidth = with(density) {
        (viewportWidthPx.toFloat() / visibleDays.size).toDp()
    }
    val effectiveColWidth = maxOf(colWidth, viewportColumnWidth)
    val gridWidth = effectiveColWidth * visibleDays.size
    val bodyHeight = rowHeight * totalRows
    val dayToColIndex: Map<Int, Int> = remember(visibleDays) {
        visibleDays.withIndex().associate { (i, d) -> d to i }
    }
    val lineColor = if (
        backgroundConfig.gridStyle == "classic" && backgroundConfig.showGridLines
    ) backgroundVisuals.gridLine else Color.Transparent
    // I-007: 红线始终显示（不依赖"当前时间在某个节次内"），课前/课间/课后均可定位
    val currentTimeLineY = remember(sortedUnits, isCurrentWeek, rowHeight, minUnit, timeTick) {
        if (!isCurrentWeek) {
            null
        } else {
            val now = DebugClock.nowTime()
            val nowMinutes = now.hour * 60 + now.minute
            // 优先：当前时间在某个节次内 → 精确插值
            sortedUnits.firstNotNullOfOrNull { unit ->
                val unitNo = unit.indexNo ?: return@firstNotNullOfOrNull null
                val start = parseTimeMinutes(unit.startTimeStr()) ?: return@firstNotNullOfOrNull null
                val end = parseTimeMinutes(unit.endTimeStr()) ?: return@firstNotNullOfOrNull null
                if (nowMinutes !in start..end || end <= start) return@firstNotNullOfOrNull null
                val row = unitNo - minUnit
                val fraction = (nowMinutes - start).toFloat() / (end - start)
                rowHeight * row + rowHeight * fraction
            } ?: run {
                // 兜底：用前后节次插值（课前/课间/课后均适用）
                val validUnits = sortedUnits
                    .filter { it.indexNo != null && parseTimeMinutes(it.startTimeStr()) != null }
                    .sortedBy { it.indexNo }
                if (validUnits.isEmpty()) return@remember null
                val firstStart = parseTimeMinutes(validUnits.first().startTimeStr())!!
                val lastEnd = parseTimeMinutes(validUnits.last().endTimeStr())!!
                when {
                    nowMinutes <= firstStart -> {
                        // 课前：第一节开始位置
                        rowHeight * (validUnits.first().indexNo!! - minUnit)
                    }
                    nowMinutes >= lastEnd -> {
                        // 课后：最后一节结束位置
                        rowHeight * (validUnits.last().indexNo!! - minUnit + 1)
                    }
                    else -> {
                        // 在前后节次之间插值
                        validUnits.zipWithNext().firstOrNull { (prev, next) ->
                            val prevEnd = parseTimeMinutes(prev.endTimeStr()) ?: return@firstOrNull false
                            val nextStart = parseTimeMinutes(next.startTimeStr()) ?: return@firstOrNull false
                            nowMinutes in prevEnd until nextStart
                        }?.let { (prev, next) ->
                            val prevEndMin = parseTimeMinutes(prev.endTimeStr())!!
                            val nextStartMin = parseTimeMinutes(next.startTimeStr())!!
                            val prevEndY = rowHeight * (prev.indexNo!! - minUnit + 1)
                            val nextStartY = rowHeight * (next.indexNo!! - minUnit)
                            val gapFraction = if (nextStartMin > prevEndMin)
                                (nowMinutes - prevEndMin).toFloat() / (nextStartMin - prevEndMin) else 0f
                            prevEndY + (nextStartY - prevEndY) * gapFraction
                        }
                    }
                }
            }
        }
    }

    val groupedItems = remember(displayItems) {
        val groups = mutableMapOf<String, MutableList<CourseDisplayItem>>()
        for (item in displayItems) {
            val key = "d${item.weekday}_u${item.startUnit}"
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }
        groups
    }

    // 选中周的周一日期(用于头部日期号)。直接按 (selectedWeek - currentWeek) 偏移今天所在周一。
    val selectedWeekMonday = remember(today, currentWeek, selectedWeek, semesterStartDate) {
        semesterStartDate
            ?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            ?.plusDays((selectedWeek - 1).coerceAtLeast(0) * 7L)
            ?: computeWeekMonday(selectedWeek, currentWeek, today)
    }

    // 注意:左侧"时间列"已上移到 ScheduleScreen 作为固定列(切换周次时不随页面滑动 — 2026-06-25)。
    // 本组件只渲染:① 顶部星期头(含日期号) ② 网格体(课程卡片 + 当前时间线)。
    // 网格体与固定时间列共享 [verScroll],垂直滚动同步。
    Column(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportWidthPx = it.width }
            .background(backgroundVisuals.canvas),
    ) {
        // ── 顶部星期头(随 Pager 换页,日期号随选中周变化)──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
                .horizontalScroll(horScroll)
        ) {
            Row(modifier = Modifier.width(gridWidth)) {
                for (d in visibleDays) {
                    val cellDate = selectedWeekMonday.plusDays((d - 1).toLong())
                    DayHeaderCell(
                        label = DAY_LABELS[d - 1],
                        dateNumber = cellDate.dayOfMonth,
                        isToday = isCurrentWeek && d == todayDayOfWeek,
                        lineColor = lineColor,
                        colWidth = effectiveColWidth,
                        fontScale = fontScale,
                        backgroundVisuals = backgroundVisuals,
                    )
                }
            }
        }

        // ── 网格体 ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verScroll)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bodyHeight)
                    .horizontalScroll(horScroll)
            ) {
                Box(modifier = Modifier.size(gridWidth, bodyHeight)) {
                for (u in minUnit..maxUnit) {
                    val row = u - minUnit
                    for (d in visibleDays) {
                        val isToday = isCurrentWeek && d == todayDayOfWeek
                        val colIdx = dayToColIndex[d] ?: continue
                        val highlightToday = isToday && backgroundConfig.highlightToday
                        val cellColor = when (backgroundConfig.gridStyle) {
                            "clean" -> when {
                                highlightToday -> backgroundVisuals.todayCell
                                backgroundConfig.alternatingRows && row % 2 != 0 ->
                                    backgroundVisuals.oddCell.copy(alpha = backgroundVisuals.oddCell.alpha * 0.42f)
                                else -> Color.Transparent
                            }
                            else -> when {
                                highlightToday -> backgroundVisuals.todayCell
                                !backgroundConfig.alternatingRows || row % 2 == 0 -> backgroundVisuals.evenCell
                                else -> backgroundVisuals.oddCell
                            }
                        }
                        val inset = if (backgroundConfig.gridStyle == "blocks") {
                            backgroundConfig.blockGapDp.coerceIn(0f, 6f).dp
                        } else 0.dp
                        val shape = if (backgroundConfig.gridStyle == "blocks") {
                            RoundedCornerShape(backgroundConfig.blockRadiusDp.coerceIn(0f, 18f).dp)
                        } else RoundedCornerShape(0.dp)
                        var cellModifier = Modifier
                            .offset(effectiveColWidth * colIdx + inset, rowHeight * row + inset)
                            .size(
                                (effectiveColWidth - inset * 2).coerceAtLeast(1.dp),
                                (rowHeight - inset * 2).coerceAtLeast(1.dp),
                            )
                            .clip(shape)
                            .background(cellColor)
                        if (backgroundConfig.gridStyle == "classic" && backgroundConfig.showGridLines) {
                            cellModifier = cellModifier.border(0.5.dp, lineColor, shape)
                        }
                        Box(modifier = cellModifier)
                    }
                }

                for (item in displayItems) {
                    val col = dayToColIndex[item.weekday] ?: continue
                    val rowStart = item.startUnit - minUnit
                    val rowSpan = item.endUnit - item.startUnit + 1
                    if (rowStart < 0 || rowSpan <= 0) continue

                    val groupKey = "d${item.weekday}_u${item.startUnit}"
                    val overlapCount = groupedItems[groupKey]?.size ?: 1
                    val overlapIdx = if (overlapCount > 1) {
                        groupedItems[groupKey]!!.indexOf(item).coerceAtLeast(0)
                    } else {
                        0
                    }
                    val overlapW = effectiveColWidth / overlapCount

                    val x = effectiveColWidth * col + overlapW * overlapIdx + 3.dp
                    val y = rowHeight * rowStart + 3.dp
                    val w = overlapW - 6.dp
                    val h = rowHeight * rowSpan - 6.dp

                    CourseCard(
                        item = item,
                        compact = rowSpan <= 1,
                        onClick = { onCourseClick(item) },
                        modifier = Modifier
                            .offset(x, y)
                            .size(w.coerceAtLeast(0.dp), h.coerceAtLeast(0.dp)),
                        fontScale = fontScale,
                        paletteConfig = paletteConfig,
                    )
                }

                // 当前时间线 — 在课程卡片之后渲染 + zIndex 提升层级，确保浮于课程之上
                if (currentTimeLineY != null) {
                    val todayCol = dayToColIndex[todayDayOfWeek]
                    if (todayCol != null) {
                        Box(
                            modifier = Modifier
                                .offset(effectiveColWidth * todayCol + 5.dp, currentTimeLineY - 4.dp)
                                .size(8.dp, 8.dp)
                                .zIndex(10f)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
                        )
                        Box(
                            modifier = Modifier
                                .offset(effectiveColWidth * todayCol + 10.dp, currentTimeLineY - 1.dp)
                                .size(effectiveColWidth - 14.dp, 2.dp)
                                .zIndex(10f)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                        )
                    }
                }
                }
            }
            dataStatus?.let { status ->
                CompactDataStatusFooter(
                    status = status,
                    modifier = Modifier
                        .height(DATA_STATUS_FOOTER_HEIGHT)
                        .background(backgroundVisuals.canvas),
                )
            }
        }
    }
}

/**
 * 固定时间列(2026-06-25 从 WeekGrid 拆出)。
 *
 * 由 ScheduleScreen 放在 WeekPager 左侧,**不参与** Pager 的横向换页 →
 * 切换周次时时间列保持不动。与各周 WeekGrid 网格体共享 [verScroll] 垂直滚动同步。
 *
 * 顶部对齐一个与星期头同高的"时间"角标,下方时间格与网格行一一对齐。
 */
@Composable
fun FixedTimeColumn(
    unitTimes: List<CourseUnit>,
    rowHeight: Dp,
    fontScale: Float,
    verScroll: ScrollState,
    modifier: Modifier = Modifier,
    paletteConfig: SchedulePaletteConfig = SchedulePaletteConfig(),
    bottomSpacerHeight: Dp = 0.dp,
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backgroundVisuals = CoursePalettes.backgroundVisuals(
        paletteConfig, MaterialTheme.colorScheme, darkTheme
    )
    val backgroundConfig = paletteConfig.backgroundOrDefault()
    val sortedUnits = remember(unitTimes) {
        unitTimes.filter { it.indexNo != null }.sortedBy { it.indexNo }
    }
    if (sortedUnits.isEmpty()) return
    val minUnit = sortedUnits.minOf { it.indexNo ?: 1 }
    val maxUnit = sortedUnits.maxOf { it.indexNo ?: 13 }
    val unitMap = sortedUnits.associateBy { it.indexNo }
    val lineColor = if (
        backgroundConfig.gridStyle == "classic" && backgroundConfig.showGridLines
    ) backgroundVisuals.gridLine else Color.Transparent

    Column(modifier = modifier.width(TIME_COL_WIDTH)) {
        // 角标(与星期头同高)
        Box(
            modifier = Modifier
                .width(TIME_COL_WIDTH)
                .height(HEADER_HEIGHT)
                .background(backgroundVisuals.timeColumn)
                .border(0.5.dp, lineColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "时间",
                fontSize = (12 * fontScale).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 时间格(与网格体共享 verScroll → 同步垂直滚动)
        Column(
            modifier = Modifier
                .width(TIME_COL_WIDTH)
                .fillMaxHeight()
                .verticalScroll(verScroll)
                .background(backgroundVisuals.canvas)
        ) {
            for (u in minUnit..maxUnit) {
                TimeCell(
                    unit = unitMap[u],
                    fallbackUnit = u,
                    lineColor = lineColor,
                    rowHeight = rowHeight,
                    fontScale = fontScale,
                    backgroundColor = backgroundVisuals.timeColumn,
                )
            }
            if (bottomSpacerHeight > 0.dp) {
                Box(Modifier.height(bottomSpacerHeight).width(TIME_COL_WIDTH))
            }
        }
    }
}

internal val DATA_STATUS_FOOTER_HEIGHT = 40.dp

@Composable
private fun DayHeaderCell(
    label: String,
    dateNumber: Int,
    isToday: Boolean,
    lineColor: Color,
    colWidth: Dp,
    fontScale: Float,
    backgroundVisuals: ScheduleBackgroundVisuals,
) {
    Box(
        modifier = Modifier
            .width(colWidth)
            .height(HEADER_HEIGHT)
            .background(
                if (isToday) backgroundVisuals.todayCell else backgroundVisuals.headerCell
            )
            .border(0.5.dp, lineColor),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = (12 * fontScale).sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                lineHeight = (13 * fontScale).sp
            )
            Text(
                text = dateNumber.toString(),
                fontSize = (10 * fontScale).sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = (11 * fontScale).sp
            )
            if (isToday) {
                Text(
                    text = "今天",
                    fontSize = (9 * fontScale).sp,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = (10 * fontScale).sp
                )
            }
        }
    }
}

@Composable
private fun TimeCell(
    unit: CourseUnit?,
    fallbackUnit: Int,
    lineColor: Color,
    rowHeight: Dp,
    fontScale: Float,
    backgroundColor: Color,
) {
    Box(
        modifier = Modifier
            .width(TIME_COL_WIDTH)
            .height(rowHeight)
            .background(backgroundColor)
            .border(0.5.dp, lineColor)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${unit?.indexNo ?: fallbackUnit}",
                fontSize = (13 * fontScale).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val start = unit?.startTimeStr().orEmpty()
            val end = unit?.endTimeStr().orEmpty()
            if (start.isNotBlank()) {
                Text(
                    text = start,
                    fontSize = (9 * fontScale).sp,
                    lineHeight = (11 * fontScale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
            if (end.isNotBlank()) {
                Text(
                    text = end,
                    fontSize = (9 * fontScale).sp,
                    lineHeight = (11 * fontScale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun CourseCard(
    item: CourseDisplayItem,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    fontScale: Float = 1.0f,
    paletteConfig: SchedulePaletteConfig,
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val courseKey = CoursePalettes.courseKey(item.courseCode, item.lessonId, item.courseName)
    val base = CoursePalettes.colorFor(paletteConfig, item.colorIndex, courseKey)
    val style = CourseCardStyle.fromStorage(paletteConfig.cardStyle)
    val visuals = CoursePalettes.cardVisuals(base, style, MaterialTheme.colorScheme, darkTheme)

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = visuals.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (style == CourseCardStyle.OUTLINE) BorderStroke(1.dp, visuals.outline) else null,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (style == CourseCardStyle.OUTLINE) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(visuals.accent)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (style == CourseCardStyle.OUTLINE) 7.dp else 4.dp,
                        end = 4.dp,
                        top = 3.dp,
                        bottom = 3.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = item.courseName,
                    fontSize = (10 * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = visuals.content,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = (12 * fontScale).sp
                )

                if (!item.room.isNullOrBlank()) {
                    Text(
                        text = item.room,
                        fontSize = (9 * fontScale).sp,
                        color = visuals.secondaryContent,
                        softWrap = true,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = (10 * fontScale).sp
                    )
                }

                if (!compact && item.teacherNames.isNotBlank()) {
                    Text(
                        text = item.teacherNames,
                        fontSize = (9 * fontScale).sp,
                        color = visuals.secondaryContent.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = (10 * fontScale).sp
                    )
                }
            }
        }
    }
}
