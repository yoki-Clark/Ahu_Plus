package com.ahu_plus.ui.screen.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Room
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.model.JwcNotice
import com.ahu_plus.data.model.jw.CourseDisplayItem
import com.ahu_plus.data.model.jw.CourseUnit
import com.ahu_plus.data.model.jw.formatTime
import com.ahu_plus.data.model.jw.parseTimeMinutes
import com.ahu_plus.data.repository.CourseRepository
import com.ahu_plus.data.home.AppRegistry
import com.ahu_plus.data.home.AppSpec
import com.ahu_plus.ui.components.AhuCard
import com.ahu_plus.ui.components.AhuIconBox
import com.ahu_plus.ui.components.AhuSectionTitle
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.components.LoginRequiredCard
import com.ahu_plus.ui.screen.schedule.ScheduleUiState
import com.ahu_plus.ui.screen.schedule.ScheduleViewModel
import com.ahu_plus.ui.theme.AhuBlue
import com.ahu_plus.ui.theme.AhuGreen
import com.ahu_plus.ui.theme.AhuIndigo
import com.ahu_plus.ui.theme.AhuOrange
import com.ahu_plus.ui.theme.AhuRed
import com.ahu_plus.ui.theme.AhuTeal
import com.ahu_plus.ui.theme.AhuViolet
import com.ahu_plus.ui.theme.AhuGradient
import com.ahu_plus.ui.screen.home.FavoritesPickerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ScheduleViewModel,
    noticeViewModel: JwcNoticeViewModel,
    onOpenSchedule: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenNoticeList: () -> Unit,
    onOpenGrade: () -> Unit,
    onOpenExam: () -> Unit,
    onOpenBathroom: () -> Unit,
    onOpenAc: () -> Unit,
    onOpenLighting: () -> Unit,
    onOpenInternet: () -> Unit,
    onOpenCardAnalytics: () -> Unit,
    onOpenTrainingPlan: () -> Unit = {},
    onOpenEmptyClassroom: () -> Unit = {},
    onOpenAppHub: () -> Unit = {},
    onOpenRegisteredApp: (String) -> Unit = {},
    onOpenWeather: () -> Unit = {},
    recentApps: List<String> = emptyList(),
    onRecordApp: (String) -> Unit = {},
    favoriteIds: List<String> = emptyList(),
    onFavoriteIdsChange: (List<String>) -> Unit = {},
    onAddTodayHomework: () -> Unit = {},
    agendaEventsByDate: Map<java.time.LocalDate, List<com.ahu_plus.data.model.agenda.AgendaEvent>> = emptyMap(),
    onOpenAgenda: () -> Unit = {},
    onAddAgenda: () -> Unit = {},
    onNeedsLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val noticeUiState by noticeViewModel.uiState.collectAsStateWithLifecycle()
    val todayAttendance by viewModel.todayCourseAttendance.collectAsStateWithLifecycle()

    // 订阅 WeatherManager.feed (公开数据, 首页每次进入由 MainScreen 触发 refresh)
    val app = LocalContext.current.applicationContext as com.ahu_plus.AhuPlusApplication
    val weatherFeed by app.weatherManager.feed.collectAsStateWithLifecycle()

    // 2026-06-17 Bug4 修复: 首页添加作业对话框
    var showTodayHomeworkDialog by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("安大 Plus") },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.onRefresh()
                            noticeViewModel.loadNotices()
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 2026-07-06 P0: LazyListState.Saver 让 SaveableStateHolder 跨分支剔除时恢复滚动位置。
            val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.needsLogin) {
                    item {
                        LoginRequiredCard(
                            onLogin = onNeedsLogin,
                            title = "登录后同步课表",
                            description = "当前仍可使用日程、天气和其他公开功能",
                        )
                    }
                }

                item {
                    // 方案 B: 走 CourseRepository.toDisplayItems() —— 与完整课表
                    // 同一管道,自动按 selectedWeek 过滤 weekIndexes,避免首页小课表
                    // 把"今天 weekday 有、但当前周次不上"的课误显示成"今日课程"。
                    com.ahu_plus.ui.screen.dashboard.TodayCourseCard(
                        uiState = com.ahu_plus.ui.screen.dashboard.TodayCourseUiState(
                            todayItems = CourseRepository.toDisplayItems(
                                activities = uiState.allActivities,
                                selectedWeek = uiState.currentWeek,
                                getDataLessons = uiState.lessons,
                            ),
                            unitTimes = uiState.unitTimes,
                            currentWeek = uiState.currentWeek,
                        ),
                        onOpenSchedule = onOpenSchedule,
                        onRefresh = viewModel::onRefresh,
                        onAddHomework = { showTodayHomeworkDialog = true },
                        todayAttendance = todayAttendance,
                        weather = weatherFeed,
                        weatherManager = app.weatherManager,
                        onOpenWeather = {
                            onRecordApp(AppRegistry.KEY_WEATHER)
                            onOpenWeather()
                        },
                    )
                }

                item {
                    AgendaCard(
                        eventsByDate = agendaEventsByDate,
                        onOpenAgenda = onOpenAgenda,
                        onAdd = onAddAgenda,
                    )
                }

                item {
                    FavoritesDock(
                        favoriteIds = favoriteIds,
                        onFavoriteIdsChange = onFavoriteIdsChange,
                        onOpenSchedule = { onRecordApp("schedule"); onOpenSchedule() },
                        onOpenCard = { onRecordApp("card"); onOpenCard() },
                        onOpenGrade = { onRecordApp("grade"); onOpenGrade() },
                        onOpenExam = { onRecordApp("exam"); onOpenExam() },
                        onOpenNoticeList = { onRecordApp("noticeList"); onOpenNoticeList() },
                        onOpenBathroom = { onRecordApp("bathroom"); onOpenBathroom() },
                        onOpenAc = { onRecordApp("ac"); onOpenAc() },
                        onOpenLighting = { onRecordApp("lighting"); onOpenLighting() },
                        onOpenInternet = { onRecordApp("internet"); onOpenInternet() },
                        onOpenCardAnalytics = { onRecordApp("cardAnalytics"); onOpenCardAnalytics() },
                        onOpenTrainingPlan = { onRecordApp("trainingPlan"); onOpenTrainingPlan() },
                        onOpenEmptyClassroom = { onRecordApp("emptyClassroom"); onOpenEmptyClassroom() },
                        onOpenAppHub = onOpenAppHub,
                        onOpenRegisteredApp = { appKey ->
                            onRecordApp(appKey)
                            onOpenRegisteredApp(appKey)
                        },
                    )
                }

                item {
                    JwcNoticeSection(
                        uiState = noticeUiState,
                        onRefresh = noticeViewModel::loadNotices,
                        onToggleNotice = noticeViewModel::toggleNotice,
                        onOpenMore = onOpenNoticeList
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            val wafChallengeUrl = noticeUiState.wafChallengeUrl
            if (wafChallengeUrl != null) {
                key(noticeUiState.wafBootstrapKey) {
                    JwcWafBootstrap(
                        url = wafChallengeUrl,
                        onCookie = noticeViewModel::onWafCookieCaptured,
                        onError = noticeViewModel::onWafBootstrapError,
                    )
                }
            }
        }
    }

    // 2026-06-17 Bug4 修复: 首页添加作业对话框
    if (showTodayHomeworkDialog) {
        com.ahu_plus.ui.screen.schedule.components.AddHomeworkDialog(
            courseName = "今日作业",
            onDismiss = { showTodayHomeworkDialog = false },
            onConfirm = { text, deadline ->
                viewModel.addQuickHomeworkForToday(text, deadline)
                showTodayHomeworkDialog = false
            },
        )
    }
}

@Composable
private fun JwcNoticeSection(
    uiState: JwcNoticeUiState,
    onRefresh: () -> Unit,
    onToggleNotice: (JwcNotice) -> Unit,
    onOpenMore: () -> Unit
) {
    AhuCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AhuIconBox(
                    imageVector = Icons.Filled.Campaign,
                    tint = AhuOrange
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = "教务通告",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "来自教务处通知公告",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onOpenMore) {
                    Text("更多")
                }
            }

            when {
                uiState.isLoading && uiState.notices.isEmpty() -> {
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "正在加载通告...",
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                uiState.error != null && uiState.notices.isEmpty() -> {
                    Column(
                        modifier = Modifier.padding(top = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = uiState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        FilledTonalButton(onClick = onRefresh) {
                            Text("重新加载")
                        }
                    }
                }

                uiState.notices.isEmpty() -> {
                    Text(
                        text = "暂无通告",
                        modifier = Modifier.padding(top = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        uiState.notices.forEachIndexed { index, notice ->
                            if (index > 0) HorizontalDivider()
                            JwcNoticeItem(
                                notice = notice,
                                isExpanded = uiState.expandedUrl == notice.url,
                                detailState = uiState.details[notice.url],
                                onClick = { onToggleNotice(notice) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JwcNoticeItem(
    notice: JwcNotice,
    isExpanded: Boolean,
    detailState: NoticeDetailState?,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (isExpanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (notice.date.isNotBlank()) {
                    Text(
                        text = notice.date,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 2.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (detailState) {
                    NoticeDetailState.Loading, null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "正在展开详情...",
                                modifier = Modifier.padding(start = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is NoticeDetailState.Success -> {
                        Surface(
                            shape = AhuShapes.Card,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = detailState.detail.content.ifBlank { "详情页暂无正文内容" },
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is NoticeDetailState.Error -> {
                        Text(
                            text = detailState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownChip(
    course: CourseDisplayItem,
    unitTimes: List<CourseUnit>
) {
    val minutes = courseStartMinutes(course, unitTimes) ?: return

    // 每 30 秒 tick 一次驱动倒计时；用 tick 当 remember 的 key,
    // 比 @Suppress("UNUSED_EXPRESSION") 更可靠 — 不依赖编译器保留无意义读取。
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }

    val now = remember(tick) { DebugClock.nowTime() }
    val diff = minutes - (now.hour * 60 + now.minute)
    val (label, color) = when {
        diff < 0 -> "已开始" to Color(0xFFFFCDD2)
        diff < 60 -> "${diff} 分钟后" to Color(0xFFFFCDD2)
        diff < 180 -> "${diff / 60} 小时后" to Color(0xFFFFE0B2)
        else -> "${diff / 60}h${diff % 60}m" to Color.White.copy(alpha = 0.78f)
    }
    Surface(
        shape = AhuShapes.Card,
        color = color.copy(alpha = 0.25f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CourseSummary(
    course: CourseDisplayItem,
    unitTimes: List<CourseUnit>
) {
    val timeText = courseTimeText(course, unitTimes)
    val roomText = course.room?.takeIf { it.isNotBlank() } ?: "教室待定"
    val teacherText = course.teacherNames.takeIf { it.isNotBlank() } ?: "教师待定"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 课程名 —— 整张卡的视觉焦点
        Text(
            text = course.courseName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        // 时间 / 地点 —— 同一行带图标
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoIconText(
                icon = Icons.Filled.AccessTime,
                text = timeText,
                tint = Color.White
            )
            InfoIconText(
                icon = Icons.Filled.LocationOn,
                text = roomText,
                tint = Color.White
            )
        }
        // 教师 —— 单独一行，弱化
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = teacherText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InfoIconText(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint.copy(alpha = 0.75f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

// 应用注册表：app key → (title, icon, color, onClick)
//
// 2026-06-22 重构：静态元数据（key/title/icon/color/group）抽到 [AppRegistry]，
// 这里只保留 Composable 消费者专用的 onClick 回调组装。
private data class AppEntry(
    val key: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color, val onClick: () -> Unit
)

@Composable
private fun AppDock(
    onOpenSchedule: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenGrade: () -> Unit,
    onOpenExam: () -> Unit,
    onOpenNoticeList: () -> Unit,
    onOpenBathroom: () -> Unit,
    onOpenAc: () -> Unit,
    onOpenLighting: () -> Unit,
    onOpenInternet: () -> Unit,
    onOpenCardAnalytics: () -> Unit,
    onOpenTrainingPlan: () -> Unit = {},
    onOpenEmptyClassroom: () -> Unit = {},
    onOpenAppHub: () -> Unit = {},
    recentApps: List<String> = emptyList()
) {
    // 回调表：app key → onClick。AppRegistry 只管元数据(无回调),
    // 回调在 Composable 内组装以便注入 onRecordApp / onOpenSchedule 等。
    val clickMap = mapOf(
        AppRegistry.KEY_SCHEDULE to onOpenSchedule,
        AppRegistry.KEY_GRADE to onOpenGrade,
        AppRegistry.KEY_EXAM to onOpenExam,
        AppRegistry.KEY_CARD to onOpenCard,
        AppRegistry.KEY_NOTICE_LIST to onOpenNoticeList,
        AppRegistry.KEY_BATHROOM to onOpenBathroom,
        AppRegistry.KEY_AC to onOpenAc,
        AppRegistry.KEY_LIGHTING to onOpenLighting,
        AppRegistry.KEY_INTERNET to onOpenInternet,
        AppRegistry.KEY_CARD_ANALYTICS to onOpenCardAnalytics,
        AppRegistry.KEY_TRAINING_PLAN to onOpenTrainingPlan,
        AppRegistry.KEY_EMPTY_CLASSROOM to onOpenEmptyClassroom,
    )
    // 拼接最近使用的 AppEntry(由 AppRegistry 提供元数据,clickMap 提供回调)
    val displayApps = remember(recentApps) {
        com.ahu_plus.data.home.AppRegistry
            .pickRecent(recentApps, maxCount = 3)
            .mapNotNull { spec ->
                val onClick = clickMap[spec.key] ?: return@mapNotNull null
                AppEntry(spec.key, spec.title, spec.icon, spec.tint, onClick)
            }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AhuSectionTitle(text = "最近使用", modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenAppHub) {
                Text("更多")
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            displayApps.forEach { app ->
                AppDockItem(
                    title = app.title,
                    iconColor = app.color,
                    onClick = app.onClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(app.icon, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun AppDockItem(
    title: String,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(AhuShapes.IconBox)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides iconColor
                ) {
                    icon()
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private fun isNotStarted(
    course: CourseDisplayItem,
    unitTimes: List<CourseUnit>
): Boolean {
    val minutes = courseStartMinutes(course, unitTimes) ?: return true
    val now = DebugClock.nowTime()
    return minutes >= now.hour * 60 + now.minute
}

private fun courseTimeText(
    course: CourseDisplayItem,
    unitTimes: List<CourseUnit>
): String {
    val unitMap = unitTimes.associateBy { it.indexNo }
    val start = course.startTime?.takeIf { it.isNotBlank() }
        ?: unitMap[course.startUnit]?.startTimeStr()
    val end = course.endTime?.takeIf { it.isNotBlank() }
        ?: unitMap[course.endUnit]?.endTimeStr()

    return when {
        !start.isNullOrBlank() && !end.isNullOrBlank() -> "${formatTime(start)}-${formatTime(end)}"
        !start.isNullOrBlank() -> formatTime(start)
        else -> "第 ${course.startUnit}-${course.endUnit} 节"
    }
}

private fun courseStartMinutes(
    course: CourseDisplayItem,
    unitTimes: List<CourseUnit>
): Int? {
    val text = course.startTime?.takeIf { it.isNotBlank() }
        ?: unitTimes.firstOrNull { it.indexNo == course.startUnit }?.startTimeStr()
    return parseTimeMinutes(text)
}

// ── 首页"我的收藏"应用栏 ─────────────────────────────────────
//
// 2026-07-06 重构 v4:拖拽引擎整体换成"容器级手势 + 浮层跟手 + move 语义"。
//
// 旧引擎(v2/v3)的死结,逐条根治:
//  1. 只能同列纵向移:targetFlat = targetRow*COLS + sourceCol，sourceCol 恒等于源列,
//     导致左上永远换不到右上。→ 现在双轴 hit-test,(row,col) 都由手指算,任意格↔任意格。
//  2. 只跟踪 dragOffsetY 单轴,横拖卡片不动。→ 现在 rawOffset:Offset 双轴。
//  3. swap 后手动 dragOffsetY = fingerY - targetCenterY 补偿,而 targetCenter 用的是换位前
//     的旧 anchor(布局要下一帧才更新)→ 快拖漂移/跳位。→ 现在被拖项视觉位移每帧从
//     fingerPos 现算(纯 derived),reorder 逻辑绝不回写 offset,零漂移。
//  4. 每卡片各挂 detectDragGesturesAfterLongPress + onGloballyPositioned 上报全局 anchor,
//     reorder 后节点身份漂移、anchor 表用 flat-index 复用错乱;且 zIndex 只在 Row 内生效,
//     跨行拖拽会被下一行裁剪。→ 手势收敛到容器一处,只测"一个格子尺寸"用网格数学推所有坐标;
//     被拖项提到**顶层浮层**绝对定位,永不被裁。
//  5. NULL_KEY 的 swap 分支是死代码(displayIds 从不含 NULL_KEY)。→ 删除,空位纯尾部 UI。
//
// 数据契约不变:favoriteIds (List<String>) 由 SessionManager 持久化,本组件只持本地 editing/
// picker/drag 状态,自身不写盘。drop 时 onFavoriteIdsChange(displayIds) 持久化。
//
// ponytail: 6 固定上限 + 2行×3列固定几何,不上 LazyVerticalGrid / reorderable 库
// (它们为可变长 Lazy 列表设计,固定小网格用纯坐标数学更短更稳)。

private const val FAVORITES_MAX = 6
private const val FAVORITES_COLS = 3
private val FAVORITES_SPACING = 10.dp

/**
 * 手指坐标(相对网格内容区左上)→ 命中的 flat index。
 *
 * 旧引擎在这一步栽跟头(targetCol 恒等于 sourceCol,只能同列纵移)。这里 col/row 都由手指
 * 独立算,任意格↔任意格。越界一律 clamp:横向超出 → 首/末列;纵向落到空位区 → 最后真实行;
 * 最终再 clamp 到 [0, size-1],保证永远命中一个真实项。
 *
 * 抽成顶层纯函数以便 FavoritesGridIndexTest 覆盖(组件里的坐标数学最易回归)。
 */
internal fun favIndexAt(
    x: Float,
    y: Float,
    slotW: Int,
    slotH: Int,
    gapPx: Float,
    rowCount: Int,
    size: Int,
): Int {
    if (slotW == 0 || size == 0) return 0
    val col = (x / (slotW + gapPx)).toInt().coerceIn(0, FAVORITES_COLS - 1)
    val row = (y / (slotH + gapPx)).toInt().coerceIn(0, rowCount - 1)
    return (row * FAVORITES_COLS + col).coerceIn(0, size - 1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesDock(
    favoriteIds: List<String>,
    onFavoriteIdsChange: (List<String>) -> Unit,
    onOpenAppHub: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenGrade: () -> Unit,
    onOpenExam: () -> Unit,
    onOpenNoticeList: () -> Unit,
    onOpenBathroom: () -> Unit,
    onOpenAc: () -> Unit,
    onOpenLighting: () -> Unit,
    onOpenInternet: () -> Unit,
    onOpenCardAnalytics: () -> Unit,
    onOpenTrainingPlan: () -> Unit,
    onOpenEmptyClassroom: () -> Unit,
    onOpenRegisteredApp: (String) -> Unit,
) {
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var pickerVisible by remember { mutableStateOf(false) }

    // 回调表 — 复用 AppRegistry 静态元数据 + 调用方提供的 openXxx,本组件无业务路由权。
    val clickMap = mapOf(
        AppRegistry.KEY_SCHEDULE to onOpenSchedule,
        AppRegistry.KEY_GRADE to onOpenGrade,
        AppRegistry.KEY_EXAM to onOpenExam,
        AppRegistry.KEY_CARD to onOpenCard,
        AppRegistry.KEY_NOTICE_LIST to onOpenNoticeList,
        AppRegistry.KEY_BATHROOM to onOpenBathroom,
        AppRegistry.KEY_AC to onOpenAc,
        AppRegistry.KEY_LIGHTING to onOpenLighting,
        AppRegistry.KEY_INTERNET to onOpenInternet,
        AppRegistry.KEY_CARD_ANALYTICS to onOpenCardAnalytics,
        AppRegistry.KEY_TRAINING_PLAN to onOpenTrainingPlan,
        AppRegistry.KEY_EMPTY_CLASSROOM to onOpenEmptyClassroom,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AhuSectionTitle(text = "收藏应用", modifier = Modifier.weight(1f))
            if (favoriteIds.isNotEmpty()) {
                TextButton(onClick = { isEditing = !isEditing }) {
                    Text(if (isEditing) "完成" else "编辑")
                }
            }
            TextButton(onClick = onOpenAppHub) {
                Text("更多")
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (favoriteIds.isEmpty()) {
            EmptyFavoritesHint(onTap = { pickerVisible = true })
        } else {
            FavoritesGrid(
                favoriteIds = favoriteIds,
                editing = isEditing,
                onEnterEditing = { isEditing = true },
                onReorder = onFavoriteIdsChange,
                onRemove = { id -> onFavoriteIdsChange(favoriteIds - id) },
                onOpen = { id -> clickMap[id]?.invoke() ?: onOpenRegisteredApp(id) },
                onAddTap = { pickerVisible = true },
            )
        }
    }

    if (pickerVisible) {
        FavoritesPickerSheet(
            favoriteIds = favoriteIds,
            onConfirm = onFavoriteIdsChange,
            onDismiss = {
                pickerVisible = false
                isEditing = false
            },
        )
    }
}

/**
 * 收藏应用网格 + 拖拽重排引擎。
 *
 * 坐标系:一律用「网格内容区左上角」为原点的**相对坐标**(不碰 positionInRoot 全局坐标)。
 * 只需测量一个格子的尺寸(slotSize),配合固定列数/间距即可推出任意 flat index 的中心:
 *   centerOf(i) = ( col*(w+gap)+w/2 , row*(h+gap)+h/2 )   其中 col=i%3, row=i/3
 *
 * 手势(单一容器级 detectDragGesturesAfterLongPress):
 *   - 长按命中 → 若未编辑先调 onEnterEditing;indexAt(按下点) 定源;记 startCenter = centerOf(源);rawOffset = 0
 *   - 拖动 → rawOffset += delta(双轴);target = indexAt(startCenter + rawOffset)
 *            若 target != dragging → displayIds = move(from,to);dragging = target
 *   - 抬手 → displayIds 变了就 onReorder 落盘;清状态
 *
 * 被拖项 **不** 靠 translation 留在原格(会被相邻行裁剪),而是提到网格顶层用绝对 offset
 * 单独画一份浮层跟手;原格渲染成半透明占位(占位随 dragging 实时移动 = 落点预览)。
 */
@Composable
private fun FavoritesGrid(
    favoriteIds: List<String>,
    editing: Boolean,
    onEnterEditing: () -> Unit,
    onReorder: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAddTap: () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { FAVORITES_SPACING.toPx() }

    // 拖拽中的视觉顺序;favoriteIds 变化(外部增删)时重置。
    var displayIds by remember(favoriteIds) { mutableStateOf(favoriteIds) }
    // 按 **id** 跟踪被拖项,不按槽位下标:下标随重排移动,拿它当身份 → 浮层会显示成"手指
    // 路过的那格的项"(拖课表经过成绩就变成绩)。id 是稳定标识,抓起时定死,重排只挪位置。
    var draggedId by remember { mutableStateOf<String?>(null) }
    var startCenter by remember { mutableStateOf(Offset.Zero) }
    var rawOffset by remember { mutableStateOf(Offset.Zero) }
    // 单个格子尺寸(px),由任一格子 onSizeChanged 上报一次;拖拽几何全靠它。
    var slotSize by remember { mutableStateOf(IntSize.Zero) }
    // 每次拖动结束 +1 → 换掉手势 pointerInput 的 key,强制协程带最新闭包重建(见 FavoriteItem)。
    var dragRestart by remember { mutableStateOf(0) }
    val latestFavoriteIds by rememberUpdatedState(favoriteIds)

    val dragging = draggedId != null
    val rowCount = ((displayIds.size + FAVORITES_COLS - 1) / FAVORITES_COLS).coerceAtLeast(1)

    fun centerOf(index: Int): Offset {
        val col = index % FAVORITES_COLS
        val row = index / FAVORITES_COLS
        val w = slotSize.width.toFloat()
        val h = slotSize.height.toFloat()
        return Offset(col * (w + gapPx) + w / 2f, row * (h + gapPx) + h / 2f)
    }

    fun indexAt(pos: Offset): Int =
        favIndexAt(pos.x, pos.y, slotSize.width, slotSize.height, gapPx, rowCount, displayIds.size)

    // jiggle 相位(三角波,±0.8°):editing && !dragging 时循环,拖拽时平滑收到 0。
    val jigglePhase = remember { Animatable(0f) }
    LaunchedEffect(editing, dragging) {
        if (editing && !dragging) {
            while (true) {
                jigglePhase.animateTo(1f, tween(420, easing = LinearEasing))
                jigglePhase.animateTo(0f, tween(420, easing = LinearEasing))
            }
        } else {
            jigglePhase.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
        }
    }
    val jiggleRotation = (jigglePhase.value - 0.5f) * 1.6f

    // 编辑态补足尾部"+"空位(纯 UI,不参与拖拽命中)。
    val emptySlots = if (editing) (FAVORITES_MAX - displayIds.size).coerceAtLeast(0) else 0
    val totalSlots = displayIds.size + emptySlots
    val gridRows = (totalSlots + FAVORITES_COLS - 1) / FAVORITES_COLS

    Box {
        Column(verticalArrangement = Arrangement.spacedBy(FAVORITES_SPACING)) {
            for (rowIdx in 0 until gridRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FAVORITES_SPACING),
                ) {
                    for (colIdx in 0 until FAVORITES_COLS) {
                        val flatIdx = rowIdx * FAVORITES_COLS + colIdx
                        val id = displayIds.getOrNull(flatIdx)
                        val cellModifier = Modifier
                            .weight(1f)
                            .then(
                                // 只需任意一个格子上报尺寸即可。
                                if (flatIdx == 0) {
                                    Modifier.onSizeChanged { slotSize = it }
                                } else Modifier
                            )
                        when {
                            id == null -> {
                                if (flatIdx < totalSlots) {
                                    EmptySlotCard(onTap = onAddTap, modifier = cellModifier)
                                } else {
                                    Spacer(cellModifier)
                                }
                            }
                            else -> {
                                val spec = AppRegistry.byId(id)
                                if (spec == null) {
                                    Spacer(cellModifier)
                                } else {
                                    // 被拖项:原格渲染成半透明占位(落点预览),真身在浮层。
                                    // 手势挂在真实 item 上(物理槽位 = call-site,reorder 后节点
                                    // 身份不变,协程不断);空位不挂 → "+"的 tap 得以保留。
                                    // onDrag 只用帧间 delta(坐标系无关),onDragStart 用 centerOf(槽)
                                    // 定位,均不依赖 item 自身的全局坐标。
                                    FavoriteItem(
                                        spec = spec,
                                        editing = editing,
                                        placeholder = id == draggedId,
                                        jiggleRotation = jiggleRotation,
                                        restartKey = dragRestart,
                                        onClick = { onOpen(id) },
                                        onRemove = { onRemove(id) },
                                        onDragStart = {
                                            // 若未编辑,先切换到编辑状态
                                            if (!editing) onEnterEditing()
                                            // 记住被拖项的 id(稳定身份),不是槽位下标。
                                            // 注意:这里的闭包变量 `id` 被 pointerInput 协程冻结成
                                            // 抓起前的旧值,不能用;flatIdx 是 call-site 常量 = 物理
                                            // 槽,displayIds 是活 state → displayIds[flatIdx] 才是
                                            // 该槽此刻真正显示的项。
                                            draggedId = displayIds.getOrNull(flatIdx)
                                            startCenter = centerOf(flatIdx)
                                            rawOffset = Offset.Zero
                                        },
                                        onDrag = { amount ->
                                            val from = displayIds.indexOf(draggedId)
                                            if (from < 0) return@FavoriteItem
                                            rawOffset += amount
                                            val target = indexAt(startCenter + rawOffset)
                                            if (target != from && target in displayIds.indices) {
                                                displayIds = displayIds.toMutableList()
                                                    .apply { add(target, removeAt(from)) }
                                            }
                                        },
                                        onDragEnd = {
                                            if (displayIds != latestFavoriteIds) onReorder(displayIds)
                                            draggedId = null
                                            rawOffset = Offset.Zero
                                            dragRestart++
                                        },
                                        onDragCancel = {
                                            draggedId = null
                                            rawOffset = Offset.Zero
                                            displayIds = latestFavoriteIds
                                            dragRestart++
                                        },
                                        modifier = cellModifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 拖拽浮层:被拖项真身,绝对定位跟手,永不被行裁剪 ──
        // 身份直接取 draggedId(不从 displayIds[下标] 反查) → 浮层图标恒为被拖项。
        if (dragging) {
            val spec = draggedId?.let { AppRegistry.byId(it) }
            if (spec != null) {
                // 视觉位移每帧现算:手指位置 - 当前所在格中心。reorder 后 center 变小 → 卡片仍贴手指。
                val fingerPos = startCenter + rawOffset
                val topLeft = fingerPos - Offset(slotSize.width / 2f, slotSize.height / 2f)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(topLeft.x.toInt(), topLeft.y.toInt()) }
                        .width(with(density) { slotSize.width.toDp() })
                        .zIndex(1f)
                        .graphicsLayer {
                            scaleX = 1.08f
                            scaleY = 1.08f
                            shadowElevation = 20f
                            alpha = 0.95f
                        },
                ) {
                    AppDockItem(
                        title = spec.title,
                        iconColor = spec.tint,
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(spec.icon, contentDescription = null)
                    }
                }
            }
        }

    }
}

@Composable
private fun FavoriteItem(
    spec: AppSpec,
    editing: Boolean,
    placeholder: Boolean,
    jiggleRotation: Float,
    restartKey: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 拖拽真身在父组件浮层(绝对定位,不被行裁剪),这里 placeholder=true 时把原格淡成
    // 占位(落点预览)。手势挂在本项:长按后把双轴 delta 交给父组件重排。
    //
    // pointerInput 的 key 含 restartKey:detectDragGesturesAfterLongPress 会把回调 lambda
    // 冻结进协程,key 不变则协程跨重组不重启,一直用第一次那份捕获了过时 displayIds/flatIdx
    // 的旧闭包 → "第二次拖动不动/错乱"。父组件每次拖动结束 bump restartKey → 协程带最新闭包
    // 重建,于是每次拖动都等价于"第一次"。
    //
    // 2026-07-07: 移除 editing 作为 pointerInput key,允许非编辑态下长按触发编辑+拖拽。
    Box(
        modifier = modifier
            .pointerInput(restartKey) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            }
            .graphicsLayer {
                if (placeholder) {
                    alpha = 0.3f
                } else if (editing) {
                    rotationZ = jiggleRotation
                    shadowElevation = 2f
                }
            },
    ) {
        // 编辑态下点卡片本身 = 无操作,删除只通过 × 角标;非编辑态点击 = 打开。
        AppDockItem(
            title = spec.title,
            iconColor = spec.tint,
            onClick = if (editing) { {} } else onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(spec.icon, contentDescription = null)
        }
        if (editing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyFavoritesHint(onTap: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "添加常用应用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptySlotCard(onTap: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape = AhuShapes.Card,
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(AhuShapes.IconBox)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "添加",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
