package com.ahu_plus.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.developer.DeveloperRuntime
import com.ahu_plus.data.developer.DeveloperRuntimeState
import com.ahu_plus.data.local.AppThemeMode
import com.ahu_plus.data.home.AppHubLayoutConfig
import com.ahu_plus.data.local.BottomNavService
import com.ahu_plus.data.local.reconcileBottomNavServices
import com.ahu_plus.data.local.CourseNoteRepository
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.repository.AdwmhCardRepository
import com.ahu_plus.data.repository.KqAttendanceRepository
import com.ahu_plus.data.repository.CardRepository
import com.ahu_plus.data.repository.CasAuthRepository
import com.ahu_plus.data.repository.CourseRepository
import com.ahu_plus.data.repository.ExamRepository
import com.ahu_plus.data.repository.FinanceRepository
import com.ahu_plus.data.repository.GradeRepository
import com.ahu_plus.data.repository.JwcNoticeRepository
import com.ahu_plus.data.repository.JwAuthRepository
import com.ahu_plus.data.repository.MarketRepository
import com.ahu_plus.data.repository.StudentInfoRepository
import com.ahu_plus.data.repository.YcardRepository
import com.ahu_plus.data.remote.market.MarketApi
import com.ahu_plus.data.remote.market.MarketIdentityExpiryState
import com.ahu_plus.data.remote.market.MarketImportRequest
import com.ahu_plus.data.remote.market.MarketImportSource
import com.ahu_plus.ui.screen.apps.AppHubScreen
import com.ahu_plus.ui.screen.chaoxing.ChaoxingTabScreen
import com.ahu_plus.ui.screen.chaoxing.ChaoxingSubTab
import com.ahu_plus.ui.screen.welearn.WeLearnCourseDetailScreen
import com.ahu_plus.ui.screen.welearn.WeLearnMainScreen
import com.ahu_plus.ui.screen.welearn.WeLearnStudyScreen
import com.ahu_plus.ui.screen.welearn.WeLearnViewModel
import com.ahu_plus.ui.screen.chaoxing.ChaoxingViewModel
import com.ahu_plus.ui.screen.dashboard.DashboardScreen
import com.ahu_plus.ui.screen.dashboard.JwcNoticeListScreen
import com.ahu_plus.ui.screen.dashboard.JwcNoticeListViewModel
import com.ahu_plus.ui.screen.dashboard.JwcNoticeViewModel
import com.ahu_plus.ui.screen.exam.ExamScreen
import com.ahu_plus.ui.screen.exam.ExamViewModel
import com.ahu_plus.ui.screen.grade.GradeScreen
import com.ahu_plus.ui.screen.grade.GradeViewModel
import com.ahu_plus.ui.screen.home.HomeViewModel
import com.ahu_plus.ui.screen.market.MarketScreen
import com.ahu_plus.ui.screen.market.MarketViewModel
import com.ahu_plus.ui.screen.profile.AttendanceViewModel
import com.ahu_plus.ui.screen.profile.FinanceViewModel
import com.ahu_plus.ui.screen.profile.ProfileScreen
import com.ahu_plus.ui.screen.profile.StudentInfoViewModel
import com.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomScreen
import com.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomViewModel
import com.ahu_plus.ui.screen.schedule.ScheduleScreen
import com.ahu_plus.ui.screen.schedule.ScheduleViewModel
import com.ahu_plus.ui.screen.trainingplan.TrainingPlanScreen
import com.ahu_plus.ui.screen.trainingplan.TrainingPlanViewModel
import com.ahu_plus.ui.screen.weather.WeatherScreen
import com.ahu_plus.ui.screen.weather.WeatherViewModel
import com.ahu_plus.ui.navigation.ChaoxingTarget
import com.ahu_plus.ui.navigation.AppsRoute
import com.ahu_plus.ui.navigation.AppsTarget
import com.ahu_plus.ui.navigation.HomeRoute
import com.ahu_plus.ui.navigation.HomeTarget
import com.ahu_plus.ui.navigation.MainNavigationViewModel
import com.ahu_plus.ui.navigation.MarketTarget
import com.ahu_plus.ui.navigation.NavigationRequest
import com.ahu_plus.ui.navigation.NavigationSource
import com.ahu_plus.ui.navigation.ProfileRoute
import com.ahu_plus.ui.navigation.ProfileTarget
import com.ahu_plus.ui.navigation.TopLevelDestination
import com.ahu_plus.ui.navigation.WeLearnTarget

private const val TAB_HOME = 0
private const val TAB_MARKET = 1
private const val TAB_CHAOXING = 2
private const val TAB_WELEARN = 3
private const val TAB_APPS = 4
private const val TAB_PROFILE = 5

private data class TopLevelNavItem(
    val tab: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private const val HOME_DASHBOARD = 0
private const val HOME_SCHEDULE = 1
private const val HOME_NOTICE_LIST = 2
private const val HOME_GRADE = 3
private const val HOME_EXAM = 4
private const val HOME_BILLS = 5
private const val HOME_TRAINING_PLAN = 6
private const val HOME_EMPTY_CLASSROOM = 7
private const val HOME_WEATHER = 9
private const val HOME_AGENDA = 10

private fun TopLevelDestination.toLegacyTab(): Int = when (this) {
    TopLevelDestination.HOME -> TAB_HOME
    TopLevelDestination.MARKET -> TAB_MARKET
    TopLevelDestination.CHAOXING -> TAB_CHAOXING
    TopLevelDestination.WELEARN -> TAB_WELEARN
    TopLevelDestination.APPS -> TAB_APPS
    TopLevelDestination.PROFILE -> TAB_PROFILE
}

private fun Int.toTopLevelDestination(): TopLevelDestination = when (this) {
    TAB_MARKET -> TopLevelDestination.MARKET
    TAB_CHAOXING -> TopLevelDestination.CHAOXING
    TAB_WELEARN -> TopLevelDestination.WELEARN
    TAB_APPS -> TopLevelDestination.APPS
    TAB_PROFILE -> TopLevelDestination.PROFILE
    else -> TopLevelDestination.HOME
}

private fun HomeRoute.toLegacyPage(): Int = when (this) {
    HomeRoute.DASHBOARD -> HOME_DASHBOARD
    HomeRoute.SCHEDULE -> HOME_SCHEDULE
    HomeRoute.NOTICES -> HOME_NOTICE_LIST
    HomeRoute.GRADE -> HOME_GRADE
    HomeRoute.EXAM -> HOME_EXAM
    HomeRoute.BILLS -> HOME_BILLS
    HomeRoute.TRAINING_PLAN -> HOME_TRAINING_PLAN
    HomeRoute.EMPTY_CLASSROOM -> HOME_EMPTY_CLASSROOM
    HomeRoute.WEATHER -> HOME_WEATHER
    HomeRoute.AGENDA -> HOME_AGENDA
}

private fun Int.toHomeRoute(): HomeRoute = when (this) {
    HOME_SCHEDULE -> HomeRoute.SCHEDULE
    HOME_NOTICE_LIST -> HomeRoute.NOTICES
    HOME_GRADE -> HomeRoute.GRADE
    HOME_EXAM -> HomeRoute.EXAM
    HOME_BILLS -> HomeRoute.BILLS
    HOME_TRAINING_PLAN -> HomeRoute.TRAINING_PLAN
    HOME_EMPTY_CLASSROOM -> HomeRoute.EMPTY_CLASSROOM
    HOME_WEATHER -> HomeRoute.WEATHER
    HOME_AGENDA -> HomeRoute.AGENDA
    else -> HomeRoute.DASHBOARD
}

/** WeLearn 内部三段式导航 (2026-06-28 新增 CourseDetailScreen) */
private sealed class WeLearnNav {
    object Main : WeLearnNav()
    data class Detail(val course: com.ahu_plus.data.model.WeLearnCourse) : WeLearnNav()
    data class Study(val course: com.ahu_plus.data.model.WeLearnCourse, val unitFilter: IntArray? = null) : WeLearnNav()
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
                com.ahu_plus.data.model.WeLearnCourse(cid = cid, name = "", per = 0)
            )
            2 -> WeLearnNav.Study(
                com.ahu_plus.data.model.WeLearnCourse(cid = cid, name = "", per = 0),
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
    /** 是否保存了统一身份认证凭据，用于匿名态与账户态 UI 切换。 */
    hasCredentials: Boolean,
    /** 每次后台或手动认证成功后递增，驱动当前可见数据静默刷新。 */
    authRefreshVersion: Int,
    /** 用户显式选择登录。 */
    onLogin: () -> Unit,
    /** 当前功能明确需要认证时，先尝试静默续期，再按需打开登录页。 */
    onReauth: () -> Unit,
    /** 完全退出登录(清除所有本地数据) */
    onLogout: () -> Unit,
    /** 首次登录初始化消息流 (LoginViewModel emit → MainScreen 订阅 → 底部 Snackbar 1 秒) */
    initMessageFlow: kotlinx.coroutines.flow.MutableSharedFlow<String>? = null,
    navigationRequest: NavigationRequest? = null,
    navigationRequestId: Long = 0L,
    onNavigationRequestConsumed: () -> Unit = {},
    marketImportRequest: MarketImportRequest? = null,
    onMarketImportConsumed: () -> Unit = {},
) {
    val mainNavigationViewModel: MainNavigationViewModel = viewModel()
    val mainNavigationState by mainNavigationViewModel.state.collectAsStateWithLifecycle()
    val selectedTab = mainNavigationState.activeTopLevel.toLegacyTab()
    val homePage = (mainNavigationState.stacks[TopLevelDestination.HOME]
        ?.lastOrNull() as? HomeTarget)?.route?.toLegacyPage() ?: HOME_DASHBOARD
    val currentAppsTarget = mainNavigationState.currentTarget as? AppsTarget
    val currentProfileTarget = mainNavigationState.currentTarget as? ProfileTarget
    // scrollTarget 是一次性滚动提示,与导航目标分离:进入 UTILITY 页后由 ProfileScreen 消费并清空
    var profileScrollTarget by rememberSaveable { mutableStateOf<String?>(null) }
    fun openHome(page: Int) {
        mainNavigationViewModel.navigate(
            NavigationRequest(HomeTarget(page.toHomeRoute()), NavigationSource.INTERNAL)
        )
    }
    fun openProfile(route: ProfileRoute, utility: String? = null) {
        profileScrollTarget = utility
        mainNavigationViewModel.navigate(
            NavigationRequest(ProfileTarget(route, utility), NavigationSource.INTERNAL)
        )
    }
    fun openApps(appKey: String? = null) {
        mainNavigationViewModel.navigate(
            NavigationRequest(
                if (appKey == null) AppsTarget() else AppsTarget(AppsRoute.APP, appKey),
                if (appKey == null) NavigationSource.INTERNAL else NavigationSource.RECENT_APP,
            )
        )
    }
    // 首页"日程"卡片右上 + → 进日程页并自动弹添加 sheet(一次性)
    var agendaOpenAdd by rememberSaveable { mutableStateOf(false) }
    var pendingMarketImport by remember { mutableStateOf<MarketImportRequest?>(null) }

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
    // B-002: 双击返回键退出
    var backPressedTime by remember { mutableLongStateOf(0L) }

    // 首页"最近使用"追踪 (mutableStateOf 保证 recordRecentApp 后 UI 立即刷新)
    var recentApps by remember { mutableStateOf(sessionManager.getRecentApps()) }
    // 首页"我的收藏"应用列表 (mutableStateOf 保证 onFavoriteIdsChange 后 UI 立即刷新)
    var favoriteIds by remember { mutableStateOf(sessionManager.getFavoriteAppIds()) }
    var bottomNavServices by remember { mutableStateOf(sessionManager.getBottomNavServices()) }
    // 应用页排版配置 + 使用次数 (mutableStateOf 保证设置变更/使用后跨 Tab 立即刷新)
    var appHubLayout by remember { mutableStateOf(sessionManager.getAppHubLayout()) }
    var appUsageCounts by remember { mutableStateOf(sessionManager.getAppUsageCounts()) }
    var previousEnabledServices by remember {
        mutableStateOf(buildSet {
            if (sessionManager.getThirdPartyServicesEnabled() && sessionManager.getMarketChildEnabled()) add(BottomNavService.MARKET)
            if (sessionManager.getThirdPartyServicesEnabled() && sessionManager.getChaoxingChildEnabled()) add(BottomNavService.CHAOXING)
            if (sessionManager.getThirdPartyServicesEnabled() && sessionManager.getWelearnChildEnabled()) add(BottomNavService.WELEARN)
        })
    }
    var returnToAggregateSettings by rememberSaveable { mutableStateOf(false) }
    var requestedChaoxingSubTab by remember { mutableStateOf<ChaoxingSubTab?>(null) }
    // 使用帮助首开弹窗：本会话内只弹一次，标记后即时生效（避免同会话二次进入重弹）
    var guideIntroSeen by remember { mutableStateOf(sessionManager.getGuideIntroSeen()) }
    val scope = rememberCoroutineScope()
    val recordApp: (String) -> Unit = remember {
        { appKey: String ->
            scope.launch {
                sessionManager.recordRecentApp(appKey)
                recentApps = sessionManager.getRecentApps()
                appUsageCounts = sessionManager.getAppUsageCounts()
            }
        }
    }
    val onAppHubLayoutChange: (AppHubLayoutConfig) -> Unit = remember {
        { config: AppHubLayoutConfig ->
            appHubLayout = config
            scope.launch { sessionManager.saveAppHubLayout(config) }
        }
    }
    val onFavoriteIdsChange: (List<String>) -> Unit = remember {
        { ids: List<String> ->
            favoriteIds = ids
            scope.launch { sessionManager.saveFavoriteAppIds(ids) }
        }
    }
    val onBottomNavServicesChange: (List<String>) -> Unit = remember {
        { services: List<String> ->
            bottomNavServices = services.distinct().take(2)
            scope.launch {
                sessionManager.setBottomNavServices(bottomNavServices)
                bottomNavServices = sessionManager.getBottomNavServices()
            }
        }
    }

    val context = LocalContext.current

    // 统一外部入口。事件 id 确保连续两次相同目标仍会被消费。
    LaunchedEffect(navigationRequestId, navigationRequest) {
        val request = navigationRequest ?: return@LaunchedEffect
        val allowed = when (request.target) {
            is ChaoxingTarget -> {
                sessionManager.getThirdPartyServicesEnabled() &&
                    sessionManager.getChaoxingChildEnabled()
            }
            is WeLearnTarget -> {
                sessionManager.getThirdPartyServicesEnabled() &&
                    sessionManager.getWelearnChildEnabled()
            }
            else -> true
        }
        if (allowed) {
            mainNavigationViewModel.navigate(request)
        } else {
            mainNavigationViewModel.selectTopLevel(TopLevelDestination.HOME)
            initSnackbarHostState.showSnackbar("该第三方服务当前未启用")
        }
        onNavigationRequestConsumed()
    }

    // 系统返回键: 当前业务栈 → 跨 Tab 来源 → 双击退出
    BackHandler {
        if (!mainNavigationViewModel.back()) {
            val now = DebugClock.nowMillis()
            if (now - backPressedTime < 1500) {
                (context as? Activity)?.finish()
            } else {
                backPressedTime = now
                Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
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
    val cardViewModel: com.ahu_plus.ui.screen.home.HomeViewModel = viewModel(factory = factory)
    val scheduleViewModel: com.ahu_plus.ui.screen.schedule.ScheduleViewModel =
        viewModel(factory = factory)
    val marketViewModel: MarketViewModel = viewModel(factory = factory)
    val marketUiState by marketViewModel.uiState.collectAsStateWithLifecycle()
    // 第三方服务聚合 (集市 + 学习通 + WeLearn):每个 Tab 可见 = parent 总开关 && 对应子开关
    // parent 总开关需 5s 弹窗确认;子开关可独立切换;关闭 parent 后即使 selectedTab 残留也降级到首页
    val thirdPartyEnabled = marketUiState.thirdPartyServicesEnabled
    val marketVisible = thirdPartyEnabled && marketUiState.marketChildEnabled
    val chaoxingVisible = thirdPartyEnabled && marketUiState.chaoxingChildEnabled
    val welearnVisible = thirdPartyEnabled && marketUiState.welearnChildEnabled
    val marketPinned = marketVisible && BottomNavService.MARKET in bottomNavServices
    val chaoxingPinned = chaoxingVisible && BottomNavService.CHAOXING in bottomNavServices
    val welearnPinned = welearnVisible && BottomNavService.WELEARN in bottomNavServices

    LaunchedEffect(marketImportRequest) {
        val request = marketImportRequest ?: return@LaunchedEffect
        if (
            MarketApi.expiryState(request.identity.metadata.expiresAtEpochSeconds) ==
            MarketIdentityExpiryState.EXPIRED
        ) {
            initSnackbarHostState.showSnackbar("该集市身份已过期，请重新获取")
        } else {
            pendingMarketImport = request
        }
        onMarketImportConsumed()
    }

    var lastIdentityWarning by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(marketUiState.identities) {
        val warning = MarketApi.expiryWarning(marketUiState.identities.map { it.token })
        if (warning == null) {
            lastIdentityWarning = null
        } else if (warning != lastIdentityWarning) {
            lastIdentityWarning = warning
            initSnackbarHostState.showSnackbar(warning)
        }
    }

    pendingMarketImport?.let { request ->
        val identity = request.identity
        AlertDialog(
            onDismissRequest = { pendingMarketImport = null },
            title = { Text("导入集市身份") },
            text = {
                Text(
                    "学校：${identity.metadata.school}\n" +
                        "${MarketApi.expiryLabel(identity.metadata)}\n\n" +
                        if (request.source == MarketImportSource.LEGACY_EXTERNAL_LINK) {
                            "来源：其他应用提供的旧版导入链接。该链接包含长期身份凭据，请仅在刚刚主动发起导入时继续。\n\n"
                        } else {
                            "来源：Ahu_Plus 应用内扫码。\n\n"
                        } +
                        "确认后将保存到本机；同一学校的旧身份会被替换。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingMarketImport = null
                        marketViewModel.importIdentity(identity.normalizedToken) { result ->
                            scope.launch {
                                initSnackbarHostState.showSnackbar(
                                    result.getOrElse { it.message ?: "集市身份导入失败" }
                                )
                            }
                        }
                        if (marketVisible) {
                            mainNavigationViewModel.navigate(NavigationRequest(MarketTarget()))
                            marketViewModel.openSettings()
                        }
                    }
                ) { Text("确认导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMarketImport = null }) { Text("取消") }
            },
        )
    }

    LaunchedEffect(marketVisible, chaoxingVisible, welearnVisible) {
        val enabled = buildSet {
            if (marketVisible) add(BottomNavService.MARKET)
            if (chaoxingVisible) add(BottomNavService.CHAOXING)
            if (welearnVisible) add(BottomNavService.WELEARN)
        }
        val reconciled = reconcileBottomNavServices(
            selected = bottomNavServices,
            previouslyEnabled = previousEnabledServices,
            currentlyEnabled = enabled,
        )
        previousEnabledServices = enabled
        if (reconciled != bottomNavServices) {
            bottomNavServices = reconciled
            sessionManager.setBottomNavServices(reconciled)
        }
    }

    LaunchedEffect(selectedTab, marketVisible, chaoxingVisible, welearnVisible) {
        val hiddenThirdPartyTab =
            (selectedTab == TAB_MARKET && !marketVisible) ||
                (selectedTab == TAB_CHAOXING && !chaoxingVisible) ||
                (selectedTab == TAB_WELEARN && !welearnVisible)
        if (hiddenThirdPartyTab) {
            mainNavigationViewModel.disable(selectedTab.toTopLevelDestination())
            scope.launch { initSnackbarHostState.showSnackbar("该第三方服务当前未启用") }
        }
    }
    val jwcNoticeViewModel: com.ahu_plus.ui.screen.dashboard.JwcNoticeViewModel =
        viewModel(factory = factory)
    val jwcNoticeListViewModel: com.ahu_plus.ui.screen.dashboard.JwcNoticeListViewModel =
        viewModel(factory = factory)
    val studentInfoViewModel: StudentInfoViewModel = viewModel(factory = factory)
    val gradeViewModel: GradeViewModel = viewModel(factory = factory)
    val examViewModel: ExamViewModel = viewModel(factory = factory)
    val financeViewModel: FinanceViewModel = viewModel(factory = factory)
    val attendanceViewModel: AttendanceViewModel = viewModel(factory = factory)
    val trainingPlanViewModel: TrainingPlanViewModel = viewModel(factory = factory)
    val emptyClassroomViewModel: com.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomViewModel =
        viewModel(factory = factory)
    val roomCourseTableViewModel: com.ahu_plus.ui.screen.roomcoursetable.RoomCourseTableViewModel =
        viewModel(factory = factory)
    val chaoxingViewModel: ChaoxingViewModel = viewModel(factory = factory)
    val weLearnViewModel: WeLearnViewModel = viewModel(factory = factory)
    val weatherViewModel: WeatherViewModel = viewModel(factory = factory)
    val evaluationViewModel: com.ahu_plus.ui.screen.evaluation.EvaluationViewModel =
        viewModel(factory = factory)
    val agendaViewModel: com.ahu_plus.ui.screen.agenda.AgendaViewModel = viewModel(factory = factory)
    val agendaEventsByDate by agendaViewModel.eventsByDate.collectAsStateWithLifecycle()

    // 首屏已经展示后，认证成功再刷新账户数据；不会用登录加载页阻塞导航。
    LaunchedEffect(authRefreshVersion) {
        if (authRefreshVersion <= 0) return@LaunchedEffect
        scheduleViewModel.onRefresh()
        studentInfoViewModel.refreshStudentInfo()
        if (selectedTab == TAB_PROFILE) cardViewModel.onRefresh()
        if (selectedTab == TAB_HOME) {
            when (homePage) {
                HOME_GRADE -> gradeViewModel.activate()
                HOME_EXAM -> examViewModel.activate()
                HOME_TRAINING_PLAN -> trainingPlanViewModel.activate()
                HOME_EMPTY_CLASSROOM -> emptyClassroomViewModel.onRefresh()
                HOME_BILLS -> cardViewModel.onRefresh()
            }
        }
    }

    LaunchedEffect(selectedTab, homePage) {
        val onHome = selectedTab == TAB_HOME
        cardViewModel.setVisible(selectedTab == TAB_PROFILE || (onHome && homePage == HOME_BILLS))
        if (onHome && homePage == HOME_GRADE) gradeViewModel.activate()
        if (onHome && homePage == HOME_EXAM) examViewModel.activate()
        if (onHome && homePage == HOME_TRAINING_PLAN) trainingPlanViewModel.activate()
        if (onHome && homePage == HOME_NOTICE_LIST) jwcNoticeListViewModel.activate()
        if (onHome && (homePage == HOME_DASHBOARD || homePage == HOME_WEATHER)) {
            weatherViewModel.activate()
        }
        if (selectedTab == TAB_MARKET) marketViewModel.activate()
        if (selectedTab == TAB_WELEARN) weLearnViewModel.activate()
        if (selectedTab == TAB_PROFILE) studentInfoViewModel.activate()
    }
    val scheduleUiState by scheduleViewModel.uiState.collectAsStateWithLifecycle()
    val gradeUiState by gradeViewModel.uiState.collectAsStateWithLifecycle()
    val developerRuntime by DeveloperRuntime.state.collectAsStateWithLifecycle()
    val showTopLevelNavigation = selectedTab != TAB_MARKET || (
        marketUiState.selectedTopic == null &&
            !marketUiState.showCompose &&
            !marketUiState.showSettings &&
            !marketUiState.showHotTopics &&
            !marketUiState.showNotices
        )
    val useNavigationRail = LocalConfiguration.current.screenWidthDp >= 600
    val navigationDestinations = buildList {
        add(TopLevelNavItem(TAB_HOME, "首页", Icons.Filled.Home, Icons.Outlined.Home))
        if (marketPinned) {
            add(TopLevelNavItem(TAB_MARKET, "集市", Icons.Filled.Storefront, Icons.Outlined.Storefront))
        }
        if (chaoxingPinned) {
            add(TopLevelNavItem(TAB_CHAOXING, "学习通", Icons.Filled.School, Icons.Outlined.School))
        }
        if (welearnPinned) {
            add(
                TopLevelNavItem(
                    TAB_WELEARN,
                    "WeLearn",
                    Icons.AutoMirrored.Filled.LibraryBooks,
                    Icons.AutoMirrored.Outlined.LibraryBooks,
                )
            )
        }
        add(TopLevelNavItem(TAB_APPS, "应用", Icons.Filled.Apps, Icons.Outlined.Apps))
        add(TopLevelNavItem(TAB_PROFILE, "我的", Icons.Filled.Person, Icons.Outlined.Person))
    }
    val onSelectTopLevelDestination: (Int) -> Unit = { tab ->
        returnToAggregateSettings = false
        mainNavigationViewModel.selectTopLevel(tab.toTopLevelDestination())
    }

    Scaffold(
        // 顶层 Scaffold 不消耗系统栏 inset:顶部由各内层屏 (DashboardScreen/ScheduleScreen/
        // GradeScreen/AppHubScreen/ChaoxingTabScreen/...) 自己的 Scaffold + AhuTopAppBar
        // 自适应处理,底部由 NavigationBar 自带 navigationBarsPadding 自动处理。
        // 这样不会出现"双重状态栏空白"。
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(initSnackbarHostState) },
        bottomBar = {
            Column {
                if (developerRuntime.hasActiveOverrides) {
                    DeveloperFaultBanner(
                        state = developerRuntime,
                        applyNavigationBarInset = !showTopLevelNavigation || useNavigationRail,
                    )
                }
                if (showTopLevelNavigation && !useNavigationRail) {
                    TopLevelNavigationBar(
                        destinations = navigationDestinations,
                        selectedTab = selectedTab,
                        onSelect = onSelectTopLevelDestination,
                    )
                }
            }
        }
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (showTopLevelNavigation && useNavigationRail) {
                TopLevelNavigationRail(
                    destinations = navigationDestinations,
                    selectedTab = selectedTab,
                    onSelect = onSelectTopLevelDestination,
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            when {
                (!marketVisible && selectedTab == TAB_MARKET) ||
                    (!chaoxingVisible && selectedTab == TAB_CHAOXING) ||
                    (!welearnVisible && selectedTab == TAB_WELEARN) -> {
                    // 第三方服务对应 Tab 被禁用 (parent 关或对应子开关关) — 降级到首页
                    DashboardScreen(
                        viewModel = scheduleViewModel,
                        noticeViewModel = jwcNoticeViewModel,
                        onOpenSchedule = { openHome(HOME_SCHEDULE) },
                        onOpenCard = { openHome(HOME_BILLS) },
                        onOpenNoticeList = { openHome(HOME_NOTICE_LIST) },
                        onOpenGrade = { openHome(HOME_GRADE) },
                        onOpenExam = { openHome(HOME_EXAM) },
                        onOpenBathroom = { openProfile(ProfileRoute.UTILITY, "bathroom") },
                        onOpenAc = { openProfile(ProfileRoute.UTILITY, "ac") },
                        onOpenLighting = { openProfile(ProfileRoute.UTILITY, "lighting") },
                        onOpenInternet = { openProfile(ProfileRoute.UTILITY, "internet") },
                        onOpenCardAnalytics = { openProfile(ProfileRoute.CARD_ANALYTICS) },
                        onOpenAppHub = { openApps() },
                        onOpenRegisteredApp = ::openApps,
                        recentApps = recentApps,
                        onRecordApp = recordApp,
                        favoriteIds = favoriteIds,
                        onFavoriteIdsChange = onFavoriteIdsChange,
                        agendaEventsByDate = agendaEventsByDate,
                        onOpenAgenda = { openHome(HOME_AGENDA) },
                        onAddAgenda = { agendaOpenAdd = true; openHome(HOME_AGENDA) },
                        onNeedsLogin = onReauth
                    )
                }
                selectedTab == TAB_HOME -> {
                    when (homePage) {
                        HOME_SCHEDULE -> ScheduleScreen(
                            viewModel = scheduleViewModel,
                            assessmentRepository = app.assessmentRepository,
                            onBack = { mainNavigationViewModel.back() },
                            onNeedsLogin = onReauth,
                            onSettingsDismissed = if (returnToAggregateSettings) {
                                {
                                    returnToAggregateSettings = false
                                    openProfile(ProfileRoute.SETTINGS)
                                }
                            } else null,
                        )
                        HOME_NOTICE_LIST -> JwcNoticeListScreen(
                            viewModel = jwcNoticeListViewModel,
                            onBack = { mainNavigationViewModel.back() }
                        )
                        HOME_GRADE -> GradeScreen(
                            viewModel = gradeViewModel,
                            onBack = { mainNavigationViewModel.back() },
                            onNeedsLogin = onReauth
                        )
                        HOME_EXAM -> ExamScreen(
                            viewModel = examViewModel,
                            onBack = { mainNavigationViewModel.back() },
                            onNeedsLogin = onReauth,
                        )
                        HOME_BILLS -> {
                            val cardState by cardViewModel.uiState.collectAsStateWithLifecycle()
                            com.ahu_plus.ui.screen.profile.BillDetailScreen(
                                bills = cardState.bills,
                                isLoading = cardState.billsLoading,
                                error = cardState.billsError,
                                onBack = { mainNavigationViewModel.back() },
                                onRefresh = cardViewModel::onRefresh,
                                isLoggedIn = hasCredentials,
                                onLogin = onReauth,
                                onOpenAnalytics = { openProfile(ProfileRoute.CARD_ANALYTICS) }
                            )
                        }
                        HOME_TRAINING_PLAN -> TrainingPlanScreen(
                            viewModel = trainingPlanViewModel,
                            onBack = { mainNavigationViewModel.back() },
                            onNeedsLogin = onReauth
                        )
                        HOME_EMPTY_CLASSROOM -> EmptyClassroomScreen(
                            viewModel = emptyClassroomViewModel,
                            onBack = { mainNavigationViewModel.back() },
                            onNeedsLogin = onReauth
                        )
                        HOME_WEATHER -> WeatherScreen(
                            viewModel = weatherViewModel,
                            onBack = { mainNavigationViewModel.back() }
                        )
                        HOME_AGENDA -> com.ahu_plus.ui.screen.agenda.AgendaScreen(
                            viewModel = agendaViewModel,
                            onBack = { mainNavigationViewModel.back() },
                            openAddOnEnter = agendaOpenAdd,
                            onAddConsumed = { agendaOpenAdd = false },
                        )
                        else -> DashboardScreen(
                            viewModel = scheduleViewModel,
                            noticeViewModel = jwcNoticeViewModel,
                            onOpenSchedule = { openHome(HOME_SCHEDULE) },
                            onOpenCard = { openHome(HOME_BILLS) },
                            onOpenNoticeList = { openHome(HOME_NOTICE_LIST) },
                            onOpenGrade = { openHome(HOME_GRADE) },
                            onOpenExam = { openHome(HOME_EXAM) },
                            onOpenTrainingPlan = { openHome(HOME_TRAINING_PLAN) },
                            onOpenEmptyClassroom = { openHome(HOME_EMPTY_CLASSROOM) },
                            onOpenWeather = { openHome(HOME_WEATHER) },
                            onOpenBathroom = { openProfile(ProfileRoute.UTILITY, "bathroom") },
                            onOpenAc = { openProfile(ProfileRoute.UTILITY, "ac") },
                            onOpenLighting = { openProfile(ProfileRoute.UTILITY, "lighting") },
                            onOpenInternet = { openProfile(ProfileRoute.UTILITY, "internet") },
                            onOpenCardAnalytics = { openProfile(ProfileRoute.CARD_ANALYTICS) },
                            onOpenAppHub = { openApps() },
                            onOpenRegisteredApp = ::openApps,
                            recentApps = recentApps,
                            onRecordApp = recordApp,
                            favoriteIds = favoriteIds,
                            onFavoriteIdsChange = onFavoriteIdsChange,
                            agendaEventsByDate = agendaEventsByDate,
                            onOpenAgenda = { openHome(HOME_AGENDA) },
                            onAddAgenda = { agendaOpenAdd = true; openHome(HOME_AGENDA) },
                            onNeedsLogin = onReauth
                        )
                    }
                }
                selectedTab == TAB_MARKET -> MarketScreen(
                    viewModel = marketViewModel,
                    onSettingsBack = if (returnToAggregateSettings) {
                        {
                            marketViewModel.closeSettings()
                            returnToAggregateSettings = false
                            openProfile(ProfileRoute.SETTINGS)
                        }
                    } else null,
                )
                selectedTab == TAB_CHAOXING -> ChaoxingTabScreen(
                    viewModel = chaoxingViewModel,
                    onSwitchToAppsTab = { openApps() },
                    requestedSubTab = requestedChaoxingSubTab,
                    onRequestedSubTabConsumed = { requestedChaoxingSubTab = null },
                    onSettingsBack = if (returnToAggregateSettings) {
                        {
                            returnToAggregateSettings = false
                            openProfile(ProfileRoute.SETTINGS)
                        }
                    } else null,
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
                    roomCourseTableViewModel = roomCourseTableViewModel,
                    cardViewModel = cardViewModel,
                    jwcNoticeListViewModel = jwcNoticeListViewModel,
                    jwcNoticeViewModel = jwcNoticeViewModel,
                    chaoxingViewModel = chaoxingViewModel,
                    studentInfoViewModel = studentInfoViewModel,
                    financeViewModel = financeViewModel,
                    attendanceViewModel = attendanceViewModel,
                    marketViewModel = marketViewModel,
                    weatherViewModel = weatherViewModel,
                    agendaViewModel = agendaViewModel,
                    evaluationViewModel = evaluationViewModel,
                    appsTarget = currentAppsTarget,
                    onNavigateBack = { mainNavigationViewModel.back() },
                    onRecordApp = recordApp,
                    hasCredentials = hasCredentials,
                    authRefreshVersion = authRefreshVersion,
                    marketEnabled = marketVisible,
                    chaoxingEnabled = chaoxingVisible,
                    welearnEnabled = welearnVisible,
                    marketInAppHub = marketVisible && !marketPinned,
                    chaoxingInAppHub = chaoxingVisible && !chaoxingPinned,
                    welearnInAppHub = welearnVisible && !welearnPinned,
                    layout = appHubLayout,
                    onOpenMarket = {
                        mainNavigationViewModel.navigate(NavigationRequest(MarketTarget()))
                    },
                    onOpenChaoxing = {
                        mainNavigationViewModel.navigate(NavigationRequest(ChaoxingTarget()))
                    },
                    onOpenWelearn = {
                        mainNavigationViewModel.navigate(NavigationRequest(WeLearnTarget()))
                    },
                    onOpenMarketFromMessages = {
                        mainNavigationViewModel.navigate(NavigationRequest(MarketTarget()))
                    },
                    onOpenChaoxingFromMessages = {
                        requestedChaoxingSubTab = ChaoxingSubTab.MESSAGES
                        mainNavigationViewModel.navigate(NavigationRequest(ChaoxingTarget()))
                    },
                    onNeedsLogin = onReauth
                )
                selectedTab == TAB_PROFILE -> ProfileScreen(
                    cardViewModel = cardViewModel,
                    marketViewModel = marketViewModel,
                    studentInfoViewModel = studentInfoViewModel,
                    financeViewModel = financeViewModel,
                    scheduleUiState = scheduleUiState,
                    academicSemesters = (
                        listOfNotNull(scheduleUiState.semester) + gradeUiState.academicSemesters
                    ),
                    onLoadAcademicSemesters = gradeViewModel::activate,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    scrollTarget = profileScrollTarget,
                    onScrollTargetConsumed = { profileScrollTarget = null },
                    profileTarget = currentProfileTarget,
                    onNavigateBack = { mainNavigationViewModel.back() },
                    guideIntroSeen = guideIntroSeen,
                    onGuideIntroSeen = {
                        guideIntroSeen = true
                        scope.launch { sessionManager.setGuideIntroSeen() }
                    },
                    bottomNavServices = bottomNavServices,
                    onBottomNavServicesChanged = onBottomNavServicesChange,
                    appHubLayout = appHubLayout,
                    onAppHubLayoutChanged = onAppHubLayoutChange,
                    appHubRecentKeys = recentApps,
                    appHubUsageCounts = appUsageCounts,
                    onOpenScheduleSettings = {
                        returnToAggregateSettings = true
                        scheduleViewModel.onToggleSettings()
                        openHome(HOME_SCHEDULE)
                    },
                    onOpenMarketSettings = {
                        returnToAggregateSettings = true
                        marketViewModel.openSettings()
                        mainNavigationViewModel.navigate(NavigationRequest(MarketTarget()))
                    },
                    onOpenChaoxingSettings = {
                        returnToAggregateSettings = true
                        requestedChaoxingSubTab = ChaoxingSubTab.SETTINGS
                        mainNavigationViewModel.navigate(NavigationRequest(ChaoxingTarget()))
                    },
                    isLoggedIn = hasCredentials,
                    onLogin = onLogin,
                    onLogout = onLogout
                )
                else -> DashboardScreen(
                    viewModel = scheduleViewModel,
                    noticeViewModel = jwcNoticeViewModel,
                    onOpenSchedule = { openHome(HOME_SCHEDULE) },
                    onOpenCard = { openHome(HOME_BILLS) },
                    onOpenNoticeList = { openHome(HOME_NOTICE_LIST) },
                    onOpenGrade = { openHome(HOME_GRADE) },
                    onOpenExam = { openHome(HOME_EXAM) },
                    onOpenBathroom = { openProfile(ProfileRoute.UTILITY, "bathroom") },
                    onOpenAc = { openProfile(ProfileRoute.UTILITY, "ac") },
                    onOpenLighting = { openProfile(ProfileRoute.UTILITY, "lighting") },
                    onOpenInternet = { openProfile(ProfileRoute.UTILITY, "internet") },
                    onOpenCardAnalytics = { openProfile(ProfileRoute.CARD_ANALYTICS) },
                    onOpenAppHub = { openApps() },
                    onOpenRegisteredApp = ::openApps,
                    recentApps = recentApps,
                    onRecordApp = recordApp,
                    favoriteIds = favoriteIds,
                    onFavoriteIdsChange = onFavoriteIdsChange,
                    onNeedsLogin = onReauth
                )
            }       // when close (含 else 分支)
            }       // Box close
        }       // Row close
    }       // Scaffold trailing lambda close
}       // MainScreen close

@Composable
private fun DeveloperFaultBanner(
    state: DeveloperRuntimeState,
    applyNavigationBarInset: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyNavigationBarInset) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "开发者故障覆盖已启用",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${state.networkFault.title} · ${state.targetHost.ifBlank { "全部主机" }}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = DeveloperRuntime::resetOverrides) {
                Icon(Icons.Filled.Restore, contentDescription = "恢复正常网络")
            }
        }
    }
}

@Composable
private fun TopLevelNavigationBar(
    destinations: List<TopLevelNavItem>,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationBar(
        tonalElevation = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        destinations.forEach { destination ->
            val selected = selectedTab == destination.tab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination.tab) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        destination.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun TopLevelNavigationRail(
    destinations: List<TopLevelNavItem>,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            destinations.forEach { destination ->
                val selected = selectedTab == destination.tab
                NavigationRailItem(
                    selected = selected,
                    onClick = { onSelect(destination.tab) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(
                            destination.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
