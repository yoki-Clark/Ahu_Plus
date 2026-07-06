package com.yourname.ahu_plus.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.yourname.ahu_plus.AhuPlusApplication
import com.yourname.ahu_plus.MainActivity
import com.yourname.ahu_plus.data.debug.DebugClock
import com.yourname.ahu_plus.data.local.AppThemeMode
import com.yourname.ahu_plus.data.local.CourseNoteRepository
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.repository.AdwmhCardRepository
import com.yourname.ahu_plus.data.repository.KqAttendanceRepository
import com.yourname.ahu_plus.data.repository.CardRepository
import com.yourname.ahu_plus.data.repository.CasAuthRepository
import com.yourname.ahu_plus.data.repository.CourseRepository
import com.yourname.ahu_plus.data.repository.ExamRepository
import com.yourname.ahu_plus.data.repository.FinanceRepository
import com.yourname.ahu_plus.data.repository.GradeRepository
import com.yourname.ahu_plus.data.repository.JwcNoticeRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.MarketRepository
import com.yourname.ahu_plus.data.repository.StudentInfoRepository
import com.yourname.ahu_plus.data.repository.YcardRepository
import com.yourname.ahu_plus.ui.screen.apps.AppHubScreen
import com.yourname.ahu_plus.ui.screen.chaoxing.ChaoxingTabScreen
import com.yourname.ahu_plus.ui.screen.welearn.WeLearnCourseDetailScreen
import com.yourname.ahu_plus.ui.screen.welearn.WeLearnMainScreen
import com.yourname.ahu_plus.ui.screen.welearn.WeLearnStudyScreen
import com.yourname.ahu_plus.ui.screen.welearn.WeLearnViewModel
import com.yourname.ahu_plus.ui.screen.chaoxing.ChaoxingViewModel
import com.yourname.ahu_plus.ui.screen.dashboard.DashboardScreen
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeListScreen
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeListViewModel
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeViewModel
import com.yourname.ahu_plus.ui.screen.exam.ExamScreen
import com.yourname.ahu_plus.ui.screen.exam.ExamViewModel
import com.yourname.ahu_plus.ui.screen.exam.ExamPredictionScreen
import com.yourname.ahu_plus.ui.screen.exam.ExamPredictionViewModel
import com.yourname.ahu_plus.ui.screen.grade.GradeScreen
import com.yourname.ahu_plus.ui.screen.grade.GradeViewModel
import com.yourname.ahu_plus.ui.screen.home.HomeViewModel
import com.yourname.ahu_plus.ui.screen.market.MarketScreen
import com.yourname.ahu_plus.ui.screen.market.MarketViewModel
import com.yourname.ahu_plus.ui.screen.profile.AttendanceViewModel
import com.yourname.ahu_plus.ui.screen.profile.FinanceViewModel
import com.yourname.ahu_plus.ui.screen.profile.ProfileScreen
import com.yourname.ahu_plus.ui.screen.profile.StudentInfoViewModel
import com.yourname.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomScreen
import com.yourname.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomViewModel
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleScreen
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleViewModel
import com.yourname.ahu_plus.ui.screen.trainingplan.TrainingPlanScreen
import com.yourname.ahu_plus.ui.screen.trainingplan.TrainingPlanViewModel
import com.yourname.ahu_plus.ui.screen.weather.WeatherScreen
import com.yourname.ahu_plus.ui.screen.weather.WeatherViewModel

private const val TAB_HOME = 0
private const val TAB_MARKET = 1
private const val TAB_CHAOXING = 2
private const val TAB_WELEARN = 3
private const val TAB_APPS = 4
private const val TAB_PROFILE = 5

private const val HOME_DASHBOARD = 0
private const val HOME_SCHEDULE = 1
private const val HOME_NOTICE_LIST = 2
private const val HOME_GRADE = 3
private const val HOME_EXAM = 4
private const val HOME_BILLS = 5
private const val HOME_TRAINING_PLAN = 6
private const val HOME_EMPTY_CLASSROOM = 7
private const val HOME_EXAM_PREDICTION = 8
private const val HOME_WEATHER = 9
private const val HOME_AGENDA = 10

/** WeLearn 内部三段式导航 (2026-06-28 新增 CourseDetailScreen) */
private sealed class WeLearnNav {
    object Main : WeLearnNav()
    data class Detail(val course: com.yourname.ahu_plus.data.model.WeLearnCourse) : WeLearnNav()
    data class Study(val course: com.yourname.ahu_plus.data.model.WeLearnCourse, val unitFilter: IntArray? = null) : WeLearnNav()
}

/**
 * 2026-07-06 P0: WeLearnNav 的 Saver — 让 `welearnScreen` 跨 Tab/分支剔除恢复。
 *
 * 存 [typeInt, cid, unitFilterList?];Detail/Study 恢复时 course 字段只回填 cid,name/per
 * 暂时为空,等用户下拉或 vm.loadCourseTree 完成后续数据补全。这是进程死亡后的已知 trade-off,
 * P0 不做 VM 加 `getCourseByCid` 同步 course 元信息(改动 4 个文件,延后 P1)。
 */
private val WeLearnNavSaver: Saver<WeLearnNav, List<Any?>> = Saver(
    save = { nav ->
        when (nav) {
            WeLearnNav.Main -> listOf(0)
            is WeLearnNav.Detail -> listOf(1, nav.course.cid)
            is WeLearnNav.Study -> listOf(2, nav.course.cid, nav.unitFilter?.toList())
        }
    },
    restore = { saved ->
        val cid = saved.getOrNull(1) as? String ?: ""
        when (saved.getOrNull(0) as? Int) {
            0 -> WeLearnNav.Main
            1 -> WeLearnNav.Detail(
                com.yourname.ahu_plus.data.model.WeLearnCourse(cid = cid, name = "", per = 0)
            )
            2 -> WeLearnNav.Study(
                com.yourname.ahu_plus.data.model.WeLearnCourse(cid = cid, name = "", per = 0),
                (saved.getOrNull(2) as? List<*>)?.filterIsInstance<Int>()?.toIntArray()
            )
            else -> WeLearnNav.Main
        }
    }
)

@Composable
fun MainScreen(
    sessionManager: SessionManager,
    cardRepository: CardRepository,
    casAuthRepository: CasAuthRepository,
    jwAuthRepository: JwAuthRepository,
    courseRepository: CourseRepository,
    ycardRepository: YcardRepository,
    marketRepository: MarketRepository,
    jwcNoticeRepository: JwcNoticeRepository,
    studentInfoRepository: StudentInfoRepository,
    courseNoteRepository: CourseNoteRepository,
    gradeRepository: GradeRepository,
    examRepository: ExamRepository,
    financeRepository: FinanceRepository,
    attendanceRepository: KqAttendanceRepository,
    adwmhCardRepository: AdwmhCardRepository,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    /** 仅清除会话并跳转登录(保留凭据/集市token等本地数据) */
    onReauth: () -> Unit,
    /** 完全退出登录(清除所有本地数据) */
    onLogout: () -> Unit,
    /** 首次登录初始化消息流 (LoginViewModel emit → MainScreen 订阅 → 底部 Snackbar 1 秒) */
    initMessageFlow: kotlinx.coroutines.flow.MutableSharedFlow<String>? = null,
    /** 通知/widget deep-link 目标(MainActivity.DEEP_LINK_*) */
    deepLink: String? = null,
    /** deep-link 跳转完成后回调,清空上游 deepLink 状态 */
    onDeepLinkConsumed: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var homePage by rememberSaveable { mutableIntStateOf(HOME_DASHBOARD) }
    // 首页"日程"卡片右上 + → 进日程页并自动弹添加 sheet(一次性)
    var agendaOpenAdd by rememberSaveable { mutableStateOf(false) }

    // 首次登录初始化冒泡 — SnackbarHost
    val initSnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(initMessageFlow) {
        if (initMessageFlow != null) {
            initMessageFlow.collect { msg ->
                initSnackbarHostState.showSnackbar(
                    message = msg,
                    duration = androidx.compose.material3.SnackbarDuration.Short,
                    withDismissAction = false,
                )
            }
        }
    }

    // 跨 Tab 跳转目标:Dashboard 常用应用点击「浴室/空调/照明/网费」时使用
    // 切到「我的」Tab 并把 scrollTarget 透传给 ProfileScreen,滚动到对应卡片后清空
    var profileScrollTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var profileSubPage by rememberSaveable { mutableStateOf<String?>(null) }
    var openCardAnalytics by rememberSaveable { mutableStateOf(false) }
    // B-001: 跨 Tab 跳转时记录"上一页 Tab"，按返回键可回到原 Tab
    var previousTab by rememberSaveable { mutableStateOf<Int?>(null) }
    // B-002: 双击返回键退出
    var backPressedTime by remember { mutableLongStateOf(0L) }

    // 首页"最近使用"追踪 (mutableStateOf 保证 recordRecentApp 后 UI 立即刷新)
    var recentApps by remember { mutableStateOf(sessionManager.getRecentApps()) }
    // 首页"我的收藏"应用列表 (mutableStateOf 保证 onFavoriteIdsChange 后 UI 立即刷新)
    var favoriteIds by remember { mutableStateOf(sessionManager.getFavoriteAppIds()) }
    // 使用帮助首开弹窗：本会话内只弹一次，标记后即时生效（避免同会话二次进入重弹）
    var guideIntroSeen by remember { mutableStateOf(sessionManager.getGuideIntroSeen()) }
    val scope = rememberCoroutineScope()
    val recordApp: (String) -> Unit = remember {
        { appKey: String -> scope.launch { sessionManager.recordRecentApp(appKey); recentApps = sessionManager.getRecentApps() } }
    }
    val onFavoriteIdsChange: (List<String>) -> Unit = remember {
        { ids: List<String> ->
            favoriteIds = ids
            scope.launch { sessionManager.saveFavoriteAppIds(ids) }
        }
    }

    val context = LocalContext.current

    // 通知/widget deep-link → 跳转到目标页。deepLink 变化即触发(冷启动 + onNewIntent)。
    LaunchedEffect(deepLink) {
        when (deepLink) {
            MainActivity.DEEP_LINK_SCHEDULE -> {
                selectedTab = TAB_HOME
                homePage = HOME_SCHEDULE
            }
            MainActivity.DEEP_LINK_GRADE -> {
                selectedTab = TAB_HOME
                homePage = HOME_GRADE
            }
            MainActivity.DEEP_LINK_AGENDA -> {
                selectedTab = TAB_HOME
                homePage = HOME_AGENDA
            }
            MainActivity.DEEP_LINK_CHAOXING -> selectedTab = TAB_CHAOXING
            MainActivity.DEEP_LINK_WELEARN -> selectedTab = TAB_WELEARN
            else -> return@LaunchedEffect
        }
        onDeepLinkConsumed()
    }

    // 系统返回键: 子页面回退 → 跨 Tab 回退 → 双击退出
    BackHandler {
        when {
            // 1. 我的 Tab 子页面 → 我的主页 (ProfileScreen 内部 BackHandler 先拦截,这里兜底)
            profileSubPage != null -> profileSubPage = null
            // 2. 预测页 → 考试页（精确回退，不回 Dashboard）
            homePage == HOME_EXAM_PREDICTION -> homePage = HOME_EXAM
            // 3. 首页其他子页面 → Dashboard
            homePage != HOME_DASHBOARD -> homePage = HOME_DASHBOARD
            // 3. 跨 Tab 跳转过来的 → 回到上一页 Tab
            previousTab != null -> {
                selectedTab = previousTab!!
                previousTab = null
                profileScrollTarget = null
                openCardAnalytics = false
            }
            // 4. 顶层页: 双击返回键退出
            else -> {
                val now = DebugClock.nowMillis()
                if (now - backPressedTime < 1500) {
                    (context as? Activity)?.finish()
                } else {
                    backPressedTime = now
                    Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val app = LocalContext.current.applicationContext as AhuPlusApplication
    // 用 ViewModelProvider.Factory 注入 Repository,Activity 重建 / 进程死亡后能复用 VM,
    // 避免之前 `remember { XxxViewModel(...) }` 写法在重建时丢失所有 VM 状态。
    val factory = remember(app) {
        MainScreenViewModelFactory(
            application = app,
            sessionManager = sessionManager,
            cardRepository = cardRepository,
            casAuthRepository = casAuthRepository,
            jwAuthRepository = jwAuthRepository,
            courseRepository = courseRepository,
            ycardRepository = ycardRepository,
            marketRepository = marketRepository,
            jwcNoticeRepository = jwcNoticeRepository,
            studentInfoRepository = studentInfoRepository,
            courseNoteRepository = courseNoteRepository,
            gradeRepository = gradeRepository,
            examRepository = examRepository,
            financeRepository = financeRepository,
            attendanceRepository = attendanceRepository,
            adwmhCardRepository = adwmhCardRepository,
        )
    }
    val cardViewModel: com.yourname.ahu_plus.ui.screen.home.HomeViewModel = viewModel(factory = factory)
    val scheduleViewModel: com.yourname.ahu_plus.ui.screen.schedule.ScheduleViewModel =
        viewModel(factory = factory)
    val marketViewModel: MarketViewModel = viewModel(factory = factory)
    val marketUiState by marketViewModel.uiState.collectAsStateWithLifecycle()
    // 第三方服务聚合 (集市 + 学习通):每个 Tab 可见 = parent 总开关 && 对应子开关
    // parent 总开关需 5s 弹窗确认;子开关可独立切换;关闭 parent 后即使 selectedTab 残留也降级到首页
    val thirdPartyEnabled = marketUiState.thirdPartyServicesEnabled
    val marketVisible = thirdPartyEnabled && marketUiState.marketChildEnabled
    val chaoxingVisible = thirdPartyEnabled && marketUiState.chaoxingChildEnabled
    val welearnVisible = thirdPartyEnabled && marketUiState.welearnChildEnabled
    val jwcNoticeViewModel: com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeViewModel =
        viewModel(factory = factory)
    val jwcNoticeListViewModel: com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeListViewModel =
        viewModel(factory = factory)
    val studentInfoViewModel: StudentInfoViewModel = viewModel(factory = factory)
    val gradeViewModel: GradeViewModel = viewModel(factory = factory)
    val examViewModel: ExamViewModel = viewModel(factory = factory)
    val financeViewModel: FinanceViewModel = viewModel(factory = factory)
    val attendanceViewModel: AttendanceViewModel = viewModel(factory = factory)
    val trainingPlanViewModel: TrainingPlanViewModel = viewModel(factory = factory)
    val emptyClassroomViewModel: com.yourname.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomViewModel =
        viewModel(factory = factory)
    val chaoxingViewModel: ChaoxingViewModel = viewModel(factory = factory)
    val weLearnViewModel: WeLearnViewModel = viewModel(factory = factory)
    val weatherViewModel: WeatherViewModel = viewModel(factory = factory)
    val agendaViewModel: com.yourname.ahu_plus.ui.screen.agenda.AgendaViewModel = viewModel(factory = factory)
    val agendaEventsByDate by agendaViewModel.eventsByDate.collectAsStateWithLifecycle()

    // 每次进入首页时触发一次天气刷新(用户要求)。常驻 1h Coroutine 兜底。
    LaunchedEffect(selectedTab) {
        if (selectedTab == TAB_HOME) {
            weatherViewModel.refresh()
        }
    }
    val scheduleUiState by scheduleViewModel.uiState.collectAsStateWithLifecycle()
    val showBottomNavigation = selectedTab != TAB_MARKET || (
        marketUiState.selectedTopic == null &&
            !marketUiState.showCompose &&
            !marketUiState.showSettings &&
            !marketUiState.showHotTopics &&
            !marketUiState.showNotices
        )

    Scaffold(
        // 顶层 Scaffold 不消耗系统栏 inset:顶部由各内层屏 (DashboardScreen/ScheduleScreen/
        // GradeScreen/AppHubScreen/ChaoxingTabScreen/...) 自己的 Scaffold + AhuTopAppBar
        // 自适应处理,底部由 NavigationBar 自带 navigationBarsPadding 自动处理。
        // 这样不会出现"双重状态栏空白"。
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(initSnackbarHostState) },
        bottomBar = {
            if (showBottomNavigation) NavigationBar(
                tonalElevation = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                NavigationBarItem(
                    selected = selectedTab == TAB_HOME,
                    onClick = {
                        selectedTab = TAB_HOME
                        homePage = HOME_DASHBOARD
                    },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == TAB_HOME) Icons.Filled.Home
                            else Icons.Outlined.Home,
                            contentDescription = "首页"
                        )
                    },
                    label = {
                        Text(
                            "首页",
                            fontWeight = if (selectedTab == TAB_HOME) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
                if (marketVisible) {
                    NavigationBarItem(
                        selected = selectedTab == TAB_MARKET,
                        onClick = { selectedTab = TAB_MARKET },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == TAB_MARKET) Icons.Filled.Storefront
                                else Icons.Outlined.Storefront,
                                contentDescription = "集市"
                            )
                        },
                        label = {
                            Text(
                                "集市",
                                fontWeight = if (selectedTab == TAB_MARKET) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )
                }
                if (chaoxingVisible) {
                    NavigationBarItem(
                        selected = selectedTab == TAB_CHAOXING,
                        onClick = { selectedTab = TAB_CHAOXING },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == TAB_CHAOXING) Icons.Filled.School
                                else Icons.Outlined.School,
                                contentDescription = "学习通"
                            )
                        },
                        label = {
                            Text(
                                "学习通",
                                fontWeight = if (selectedTab == TAB_CHAOXING) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )
                }
                // WeLearn 随行课堂 (2026-06-27 新增, 2026-06-28 移入第三方服务)
                if (welearnVisible) {
                    NavigationBarItem(
                        selected = selectedTab == TAB_WELEARN,
                        onClick = { selectedTab = TAB_WELEARN },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == TAB_WELEARN) Icons.Filled.LibraryBooks
                                else Icons.Outlined.LibraryBooks,
                                contentDescription = "WeLearn"
                            )
                        },
                        label = {
                            Text(
                                "WeLearn",
                                fontWeight = if (selectedTab == TAB_WELEARN) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )
                }
                NavigationBarItem(
                    selected = selectedTab == TAB_APPS,
                    onClick = { selectedTab = TAB_APPS },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == TAB_APPS) Icons.Filled.Apps
                            else Icons.Outlined.Apps,
                            contentDescription = "应用"
                        )
                    },
                    label = {
                        Text(
                            "应用",
                            fontWeight = if (selectedTab == TAB_APPS) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == TAB_PROFILE,
                    onClick = { selectedTab = TAB_PROFILE },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == TAB_PROFILE) Icons.Filled.Person
                            else Icons.Outlined.Person,
                            contentDescription = "我的"
                        )
                    },
                    label = {
                        Text(
                            "我的",
                            fontWeight = if (selectedTab == TAB_PROFILE) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                (!marketVisible && selectedTab == TAB_MARKET) ||
                    (!chaoxingVisible && selectedTab == TAB_CHAOXING) ||
                    (!welearnVisible && selectedTab == TAB_WELEARN) -> {
                    // 第三方服务对应 Tab 被禁用 (parent 关或对应子开关关) — 降级到首页
                    DashboardScreen(
                        viewModel = scheduleViewModel,
                        noticeViewModel = jwcNoticeViewModel,
                        onOpenSchedule = { homePage = HOME_SCHEDULE },
                        onOpenCard = { homePage = HOME_BILLS },
                        onOpenNoticeList = { homePage = HOME_NOTICE_LIST },
                        onOpenGrade = { homePage = HOME_GRADE },
                        onOpenExam = { homePage = HOME_EXAM },
                        onOpenBathroom = {
                            previousTab = selectedTab
                            selectedTab = TAB_PROFILE
                            profileScrollTarget = "bathroom"
                        },
                        onOpenAc = {
                            previousTab = selectedTab
                            selectedTab = TAB_PROFILE
                            profileScrollTarget = "ac"
                        },
                        onOpenLighting = {
                            previousTab = selectedTab
                            selectedTab = TAB_PROFILE
                            profileScrollTarget = "lighting"
                        },
                        onOpenInternet = {
                            previousTab = selectedTab
                            selectedTab = TAB_PROFILE
                            profileScrollTarget = "internet"
                        },
                        onOpenCardAnalytics = {
                            previousTab = selectedTab
                            selectedTab = TAB_PROFILE
                            openCardAnalytics = true
                        },
                        onOpenAppHub = {
                            previousTab = selectedTab
                            selectedTab = TAB_APPS
                        },
                        recentApps = recentApps,
                        onRecordApp = recordApp,
                        favoriteIds = favoriteIds,
                        onFavoriteIdsChange = onFavoriteIdsChange,
                        agendaEventsByDate = agendaEventsByDate,
                        onOpenAgenda = { homePage = HOME_AGENDA },
                        onAddAgenda = { agendaOpenAdd = true; homePage = HOME_AGENDA },
                        onNeedsLogin = onReauth
                    )
                }
                selectedTab == TAB_HOME -> {
                    when (homePage) {
                        HOME_SCHEDULE -> ScheduleScreen(
                            viewModel = scheduleViewModel,
                            assessmentRepository = app.assessmentRepository,
                            onBack = { homePage = HOME_DASHBOARD },
                            onNeedsLogin = onReauth
                        )
                        HOME_NOTICE_LIST -> JwcNoticeListScreen(
                            viewModel = jwcNoticeListViewModel,
                            onBack = { homePage = HOME_DASHBOARD }
                        )
                        HOME_GRADE -> GradeScreen(
                            viewModel = gradeViewModel,
                            onBack = { homePage = HOME_DASHBOARD },
                            onNeedsLogin = onReauth
                        )
                        HOME_EXAM -> ExamScreen(
                            viewModel = examViewModel,
                            onBack = { homePage = HOME_DASHBOARD },
                            onNeedsLogin = onReauth,
                            onOpenPrediction = { homePage = HOME_EXAM_PREDICTION }
                        )
                        HOME_EXAM_PREDICTION -> {
                            // 进入排考预测页时才创建 VM,触发首次拉取
                            val examPredictionViewModel = remember {
                                ExamPredictionViewModel(app.examDataRepository, sessionManager)
                            }
                            ExamPredictionScreen(
                                viewModel = examPredictionViewModel,
                                onBack = { homePage = HOME_EXAM }
                            )
                        }
                        HOME_BILLS -> {
                            val cardState by cardViewModel.uiState.collectAsStateWithLifecycle()
                            com.yourname.ahu_plus.ui.screen.profile.BillDetailScreen(
                                bills = cardState.bills,
                                isLoading = cardState.billsLoading,
                                error = cardState.billsError,
                                onBack = { homePage = HOME_DASHBOARD },
                                onRefresh = cardViewModel::onRefresh,
                                onOpenAnalytics = {
                                    previousTab = selectedTab
                                    selectedTab = TAB_PROFILE
                                    openCardAnalytics = true
                                }
                            )
                        }
                        HOME_TRAINING_PLAN -> TrainingPlanScreen(
                            viewModel = trainingPlanViewModel,
                            onBack = { homePage = HOME_DASHBOARD },
                            onNeedsLogin = onReauth
                        )
                        HOME_EMPTY_CLASSROOM -> EmptyClassroomScreen(
                            viewModel = emptyClassroomViewModel,
                            onBack = { homePage = HOME_DASHBOARD },
                            onNeedsLogin = onReauth
                        )
                        HOME_WEATHER -> WeatherScreen(
                            viewModel = weatherViewModel,
                            onBack = { homePage = HOME_DASHBOARD }
                        )
                        HOME_AGENDA -> com.yourname.ahu_plus.ui.screen.agenda.AgendaScreen(
                            viewModel = agendaViewModel,
                            onBack = { homePage = HOME_DASHBOARD },
                            openAddOnEnter = agendaOpenAdd,
                            onAddConsumed = { agendaOpenAdd = false },
                        )
                        else -> DashboardScreen(
                            viewModel = scheduleViewModel,
                            noticeViewModel = jwcNoticeViewModel,
                            onOpenSchedule = { homePage = HOME_SCHEDULE },
                            onOpenCard = { homePage = HOME_BILLS },
                            onOpenNoticeList = { homePage = HOME_NOTICE_LIST },
                            onOpenGrade = { homePage = HOME_GRADE },
                            onOpenExam = { homePage = HOME_EXAM },
                            onOpenTrainingPlan = { homePage = HOME_TRAINING_PLAN },
                            onOpenEmptyClassroom = { homePage = HOME_EMPTY_CLASSROOM },
                            onOpenWeather = { homePage = HOME_WEATHER },
                            onOpenBathroom = {
                                previousTab = selectedTab
                                selectedTab = TAB_PROFILE
                                profileScrollTarget = "bathroom"
                            },
                            onOpenAc = {
                                previousTab = selectedTab
                                selectedTab = TAB_PROFILE
                                profileScrollTarget = "ac"
                            },
                            onOpenLighting = {
                                previousTab = selectedTab
                                selectedTab = TAB_PROFILE
                                profileScrollTarget = "lighting"
                            },
                            onOpenInternet = {
                                previousTab = selectedTab
                                selectedTab = TAB_PROFILE
                                profileScrollTarget = "internet"
                            },
                            onOpenCardAnalytics = {
                                previousTab = selectedTab
                                selectedTab = TAB_PROFILE
                                openCardAnalytics = true
                            },
                            onOpenAppHub = {
                                previousTab = selectedTab
                                selectedTab = TAB_APPS
                            },
                            recentApps = recentApps,
                            onRecordApp = recordApp,
                            favoriteIds = favoriteIds,
                            onFavoriteIdsChange = onFavoriteIdsChange,
                            agendaEventsByDate = agendaEventsByDate,
                            onOpenAgenda = { homePage = HOME_AGENDA },
                            onAddAgenda = { agendaOpenAdd = true; homePage = HOME_AGENDA },
                            onNeedsLogin = onReauth
                        )
                    }
                }
                selectedTab == TAB_MARKET -> MarketScreen(viewModel = marketViewModel)
                selectedTab == TAB_CHAOXING -> ChaoxingTabScreen(
                    viewModel = chaoxingViewModel,
                    onSwitchToAppsTab = { selectedTab = TAB_APPS },
                )
                selectedTab == TAB_WELEARN -> {
                    // WeLearn 内部三段式:课程列表 → 课程详情(单元+章节) → 刷课控制
                    // 2026-06-28: 插入 CourseDetailScreen 显示章节,后续可拓展为针对性刷
                    // 2026-07-06 P0: rememberSaveable(stateSaver=WeLearnNavSaver) 跨 Tab 切换保留 WeLearn 内部路径。
                    var welearnScreen by rememberSaveable(stateSaver = WeLearnNavSaver) {
                        mutableStateOf<WeLearnNav>(WeLearnNav.Main)
                    }
                    val ctx = LocalContext.current
                    // session 过期 → 强制回主页,LoginSheet 由 WeLearnMainScreen 自己弹
                    val welearnCoursesNeedsLogin by weLearnViewModel.coursesState.collectAsStateWithLifecycle()
                    LaunchedEffect(welearnCoursesNeedsLogin.needsLogin) {
                        if (welearnCoursesNeedsLogin.needsLogin) welearnScreen = WeLearnNav.Main
                    }
                    when (val ws = welearnScreen) {
                        WeLearnNav.Main -> WeLearnMainScreen(
                            viewModel = weLearnViewModel,
                            onCourseClick = { welearnScreen = WeLearnNav.Detail(it) },
                        )
                        is WeLearnNav.Detail -> WeLearnCourseDetailScreen(
                            course = ws.course,
                            viewModel = weLearnViewModel,
                            onBack = { welearnScreen = WeLearnNav.Main },
                            // 2026-06-28:顶栏 PlayArrow + 选择性刷 — 启动 Service + 跳到 StudyScreen
                            // unitFilter=null 刷全部,IntArray 刷选中单元
                            onStartStudy = { unitFilter ->
                                weLearnViewModel.startStudying(
                                    ctx, ws.course.cid, "100", false, unitFilter,
                                )
                                welearnScreen = WeLearnNav.Study(ws.course, unitFilter)
                            },
                            // 2026-06-28:刷全部章节 — 仅跳转,Service 由用户在 Study 屏手动启动
                            onOpenStudy = {
                                welearnScreen = WeLearnNav.Study(ws.course, null)
                            },
                        )
                        is WeLearnNav.Study -> WeLearnStudyScreen(
                            course = ws.course,
                            viewModel = weLearnViewModel,
                            unitFilter = ws.unitFilter,
                            onBack = { welearnScreen = WeLearnNav.Detail(ws.course) },
                        )
                    }
                }
                selectedTab == TAB_APPS -> AppHubScreen(
                    scheduleViewModel = scheduleViewModel,
                    gradeViewModel = gradeViewModel,
                    examViewModel = examViewModel,
                    trainingPlanViewModel = trainingPlanViewModel,
                    emptyClassroomViewModel = emptyClassroomViewModel,
                    cardViewModel = cardViewModel,
                    jwcNoticeListViewModel = jwcNoticeListViewModel,
                    studentInfoViewModel = studentInfoViewModel,
                    financeViewModel = financeViewModel,
                    attendanceViewModel = attendanceViewModel,
                    weatherViewModel = weatherViewModel,
                    agendaViewModel = agendaViewModel,
                    onNeedsLogin = onReauth
                )
                selectedTab == TAB_PROFILE -> ProfileScreen(
                    cardViewModel = cardViewModel,
                    marketViewModel = marketViewModel,
                    studentInfoViewModel = studentInfoViewModel,
                    financeViewModel = financeViewModel,
                    scheduleUiState = scheduleUiState,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    scrollTarget = profileScrollTarget,
                    onScrollTargetConsumed = { profileScrollTarget = null },
                    profileSubPage = profileSubPage,
                    onProfileSubPageConsumed = { profileSubPage = null },
                    openCardAnalytics = openCardAnalytics,
                    onCardAnalyticsConsumed = { openCardAnalytics = false },
                    guideIntroSeen = guideIntroSeen,
                    onGuideIntroSeen = {
                        guideIntroSeen = true
                        scope.launch { sessionManager.setGuideIntroSeen() }
                    },
                    onLogout = onLogout
                )
                else -> DashboardScreen(
                    viewModel = scheduleViewModel,
                    noticeViewModel = jwcNoticeViewModel,
                    onOpenSchedule = { homePage = HOME_SCHEDULE },
                    onOpenCard = { homePage = HOME_BILLS },
                    onOpenNoticeList = { homePage = HOME_NOTICE_LIST },
                    onOpenGrade = { homePage = HOME_GRADE },
                    onOpenExam = { homePage = HOME_EXAM },
                    onOpenBathroom = {
                        previousTab = selectedTab
                        selectedTab = TAB_PROFILE
                        profileScrollTarget = "bathroom"
                    },
                    onOpenAc = {
                        previousTab = selectedTab
                        selectedTab = TAB_PROFILE
                        profileScrollTarget = "ac"
                    },
                    onOpenLighting = {
                        previousTab = selectedTab
                        selectedTab = TAB_PROFILE
                        profileScrollTarget = "lighting"
                    },
                    onOpenInternet = {
                        previousTab = selectedTab
                        selectedTab = TAB_PROFILE
                        profileScrollTarget = "internet"
                    },
                    onOpenCardAnalytics = {
                        previousTab = selectedTab
                        selectedTab = TAB_PROFILE
                        openCardAnalytics = true
                    },
                    onOpenAppHub = {
                        previousTab = selectedTab
                        selectedTab = TAB_APPS
                    },
                    recentApps = recentApps,
                    onRecordApp = recordApp,
                    favoriteIds = favoriteIds,
                    onFavoriteIdsChange = onFavoriteIdsChange,
                    onNeedsLogin = onReauth
                )
            }       // when close (含 else 分支)
        }       // Box close
    }       // Scaffold trailing lambda close
}       // MainScreen close
