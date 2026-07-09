package com.ahu_plus.ui.screen.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.model.agenda.AgendaEvent
import com.ahu_plus.data.model.agenda.AgendaSource
import com.ahu_plus.data.model.task.UserTask
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.CourseColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
/** 月视图单格最多显示几条日程标题(超出显示 +N)。 */
private const val MAX_CELL_EVENTS = 3

/**
 * 日程页,两种形态:
 *  - **月视图(展开)**:月历铺满整屏,每格显示日期号 + 该天前几条日程标题。应用列表进入的默认态。
 *  - **周视图(折叠)**:仅选中日所在周一行(紧凑),下方列出该日全部事件。首页进入的默认态。
 *
 * 点某天 → 折叠到周视图并选中;返回键:周视图 → 月视图,月视图 → 退出([onBack])。
 * FAB 添加手动日程;点手动日程可编辑;顶栏设置控制课程/考试是否自动入日程。
 *
 * @param startExpanded 进入时是否为月视图(true = 应用列表入口,false = 首页入口)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    viewModel: AgendaViewModel,
    onBack: () -> Unit,
    startExpanded: Boolean = false,
    openAddOnEnter: Boolean = false,
    onAddConsumed: () -> Unit = {},
) {
    // 每次进入刷新课程/考试缓存快照
    LaunchedEffect(Unit) { viewModel.refresh() }

    val eventsByDate by viewModel.eventsByDate.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val showCourses by viewModel.showCourses.collectAsStateWithLifecycle()
    val showExams by viewModel.showExams.collectAsStateWithLifecycle()

    var showAddSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UserTask?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    // 月历是否展开整月(false = 折叠成选中日所在周一行)。入口决定初值。
    var monthExpanded by remember { mutableStateOf(startExpanded) }
    // 当前展示的月份(展开整月时用);默认选中日所在月
    var visibleMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    // 首页"+"进入时自动弹添加 sheet(消费一次性标志)
    LaunchedEffect(openAddOnEnter) {
        if (openAddOnEnter) {
            editing = null
            showAddSheet = true
            onAddConsumed()
        }
    }

    // 返回键:月视图 → 退出;周视图 → 回月视图(不退出)
    androidx.activity.compose.BackHandler {
        if (monthExpanded) onBack() else monthExpanded = true
    }

    val today = DebugClock.todayDate()
    val selectedEvents = eventsByDate[selectedDate].orEmpty()

    // pager 页号锚点(进页面固定):page=PAGER_MID 对应锚点月/周,偏移即月/周差
    val anchorMonth = remember { YearMonth.from(today) }
    val anchorMonday = remember { mondayOf(today) }

    fun openEdit(event: AgendaEvent) {
        if (event.source != AgendaSource.CUSTOM) return
        val id = event.sourceId ?: return
        editing = viewModel.findTask(id) ?: return
        showAddSheet = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日程") },
                navigationIcon = {
                    IconButton(onClick = { if (monthExpanded) onBack() else monthExpanded = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "日程设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showAddSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加日程")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (monthExpanded) {
                // ── 月视图:横向 pager 翻月 ──
                val pagerState = rememberPagerState(
                    initialPage = PAGER_MID + monthsBetween(anchorMonth, visibleMonth),
                    pageCount = { PAGER_COUNT },
                )
                // 滑动 → 同步 visibleMonth(用于标题)
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        visibleMonth = anchorMonth.plusMonths((page - PAGER_MID).toLong())
                    }
                }
                CalendarHeader(visibleMonth = visibleMonth)
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    MonthGrid(
                        visibleMonth = anchorMonth.plusMonths((page - PAGER_MID).toLong()),
                        today = today,
                        selected = selectedDate,
                        eventsByDate = eventsByDate,
                        onSelect = { date ->
                            viewModel.selectDate(date)
                            monthExpanded = false // 选定 → 折叠成周视图
                        },
                    )
                }
            } else {
                // ── 周视图:横向 pager 翻周 + 下方议程列表 ──
                val weekPagerState = rememberPagerState(
                    initialPage = PAGER_MID + weeksBetween(anchorMonday, mondayOf(selectedDate)),
                    pageCount = { PAGER_COUNT },
                )
                // 滑动翻周 → 选中新周的对应星期(保持 selectedDate 的 dayOfWeek)
                LaunchedEffect(weekPagerState) {
                    snapshotFlow { weekPagerState.currentPage }.collect { page ->
                        val newMonday = anchorMonday.plusWeeks((page - PAGER_MID).toLong())
                        val sameDow = newMonday.plusDays((selectedDate.dayOfWeek.value - 1).toLong())
                        if (sameDow != selectedDate) viewModel.selectDate(sameDow)
                    }
                }
                // 外部改选中日(点日期/月视图落下) → 若跨周,滚 pager 到对应周
                LaunchedEffect(selectedDate) {
                    val target = PAGER_MID + weeksBetween(anchorMonday, mondayOf(selectedDate))
                    if (target != weekPagerState.currentPage) weekPagerState.scrollToPage(target)
                }
                CalendarHeader(visibleMonth = YearMonth.from(selectedDate))
                HorizontalPager(state = weekPagerState) { page ->
                    val monday = anchorMonday.plusWeeks((page - PAGER_MID).toLong())
                    WeekRow(
                        monday = monday,
                        today = today,
                        selected = selectedDate,
                        hasEvents = { date -> eventsByDate[date]?.isNotEmpty() == true },
                        onSelect = { viewModel.selectDate(it) },
                    )
                }
                if (selectedEvents.isEmpty()) {
                    EmptyDay(onAdd = { editing = null; showAddSheet = true })
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { DayHeader(selectedDate, today) }
                        items(selectedEvents, key = { it.id }) { event ->
                            AgendaEventRow(
                                event = event,
                                onClick = { openEdit(event) },
                                onToggle = { viewModel.toggleComplete(event) },
                                onDelete = { viewModel.deleteEvent(event) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddAgendaEventSheet(
            defaultDate = selectedDate,
            initial = editing,
            onDismiss = { showAddSheet = false; editing = null },
            onConfirm = { viewModel.upsertEvent(it) },
        )
    }

    if (showSettings) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("日程设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SettingSwitch("课程自动加入日程", showCourses) { viewModel.setShowCourses(it) }
                SettingSwitch("考试自动加入日程", showExams) { viewModel.setShowExams(it) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── 月历 ─────────────────────────────────────────────

/** 月份标题 + 星期表头(左右翻页靠手势 pager,不再放箭头)。 */
@Composable
private fun CalendarHeader(visibleMonth: YearMonth) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = "${visibleMonth.year} 年 ${visibleMonth.monthValue} 月",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { wd ->
                Text(
                    text = wd,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** 月视图网格:6 行铺满,每格日期号 + 当天前几条日程标题条。 */
@Composable
private fun MonthGrid(
    visibleMonth: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    eventsByDate: Map<LocalDate, List<AgendaEvent>>,
    onSelect: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
        monthWeeks(visibleMonth).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { date ->
                    MonthDayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == visibleMonth,
                        isToday = date == today,
                        isSelected = date == selected,
                        events = eventsByDate[date].orEmpty(),
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** 月视图单格:顶部日期号(今天/选中高亮),下方最多 [MAX_CELL_EVENTS] 条日程标题条。 */
@Composable
private fun MonthDayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<AgendaEvent>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayFg = when {
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        isToday -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .padding(1.5.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 日期号:今天填充主色圆
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = dayFg,
            )
        }
        Spacer(Modifier.height(2.dp))
        // 事件标题条(最多 MAX_CELL_EVENTS 条,超出显示 +N)
        events.take(MAX_CELL_EVENTS).forEach { ev ->
            EventChip(ev)
        }
        if (events.size > MAX_CELL_EVENTS) {
            Text(
                text = "+${events.size - MAX_CELL_EVENTS}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 月视图格内的一条事件小标签:来源色底 + 标题。 */
@Composable
private fun EventChip(event: AgendaEvent) {
    val color = accentOf(event)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 周视图:[monday] 起一周一行(紧凑,圆点指示有无事件)。 */
@Composable
private fun WeekRow(
    monday: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    hasEvents: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
        (0..6).map { monday.plusDays(it.toLong()) }.forEach { date ->
            WeekDayCell(
                date = date,
                isToday = date == today,
                isSelected = date == selected,
                hasEvents = hasEvents(date),
                onClick = { onSelect(date) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 周视图单格:日期号 + 事件圆点。选中填充主色。 */
@Composable
private fun WeekDayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fg = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "周" + WEEKDAY_LABELS[date.dayOfWeek.value - 1],
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.Normal,
            color = fg,
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    if (hasEvents) {
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    } else Color.Transparent
                ),
        )
    }
}

// ── pager 页号映射(以进页面时的锚点为 PAGER_MID,偏移即月/周差,纯函数无漂移) ──
private const val PAGER_MID = 6000
private const val PAGER_COUNT = 12000

private fun mondayOf(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun monthsBetween(from: YearMonth, to: YearMonth): Int =
    ChronoUnit.MONTHS.between(from, to).toInt()

private fun weeksBetween(fromMonday: LocalDate, toMonday: LocalDate): Int =
    ChronoUnit.WEEKS.between(fromMonday, toMonday).toInt()

/** [month] 的完整周网格(周一起,首末补齐前后月,固定 6 行 = 42 天)。 */
private fun monthWeeks(month: YearMonth): List<List<LocalDate>> {
    val first = month.atDay(1)
    val gridStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0 until 6).map { w ->
        (0..6).map { d -> gridStart.plusDays((w * 7 + d).toLong()) }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    val prefix = when (date) {
        today -> "今天 · "
        today.plusDays(1) -> "明天 · "
        else -> ""
    }
    Text(
        text = "$prefix${date.monthValue} 月 ${date.dayOfMonth} 日 周${WEEKDAY_LABELS[date.dayOfWeek.value - 1]}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun AgendaEventRow(
    event: AgendaEvent,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = accentOf(event)
    Surface(
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (event.source == AgendaSource.CUSTOM) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧时间列
            Column(
                modifier = Modifier.width(52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val start = event.startClock()
                if (start != null) {
                    Text(start, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent)
                    event.endClock()?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("全天", style = MaterialTheme.typography.labelMedium, color = accent)
                }
            }

            // 竖色条
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )

            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    // 删除线只对"可勾选完成"的待办生效;考试的 isFinished 不可靠且语义不同
                    textDecoration = if (event.isCheckable && event.completed) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(sourceLabel(event.source), event.location?.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 右侧操作:可勾选项显示勾选框 + 删除;只读项无操作
            if (event.isCheckable) {
                Checkbox(checked = event.completed, onCheckedChange = { onToggle() })
                if (event.source == AgendaSource.CUSTOM) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** 事件来源对应的强调色。 */
@Composable
private fun accentOf(event: AgendaEvent): Color = when (event.source) {
    AgendaSource.COURSE -> CourseColors[event.colorIndex % CourseColors.size]
    AgendaSource.EXAM -> MaterialTheme.colorScheme.error
    AgendaSource.HOMEWORK -> MaterialTheme.colorScheme.tertiary
    AgendaSource.CUSTOM -> MaterialTheme.colorScheme.primary
}

private fun sourceLabel(source: AgendaSource): String = when (source) {
    AgendaSource.COURSE -> "课程"
    AgendaSource.EXAM -> "考试"
    AgendaSource.HOMEWORK -> "作业"
    AgendaSource.CUSTOM -> "日程"
}

@Composable
private fun EmptyDay(onAdd: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("这天还没有安排", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                shape = AhuShapes.Card,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable(onClick = onAdd),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("添加日程", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}
