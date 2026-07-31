package com.ahu_plus.ui.screen.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.Alignment
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.data.home.AppHubCardStyle
import com.ahu_plus.data.home.AppHubColumns
import com.ahu_plus.data.home.AppHubDensity
import com.ahu_plus.data.home.AppHubLayoutConfig
import com.ahu_plus.data.home.AppRegistry
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.ui.navigation.AppsRoute
import com.ahu_plus.ui.navigation.AppsTarget
import kotlinx.coroutines.launch
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.AhuBlue
import com.ahu_plus.ui.theme.AhuGradient
import com.ahu_plus.ui.theme.AhuOrange
import com.ahu_plus.ui.theme.AhuViolet
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.ahu_plus.ui.components.AhuSectionTitle
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.components.CenteredMessage
import com.ahu_plus.ui.screen.dashboard.JwcNoticeListScreen
import com.ahu_plus.ui.screen.dashboard.JwcNoticeListViewModel
import com.ahu_plus.ui.screen.dashboard.JwcNoticeViewModel
import com.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomScreen
import com.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomViewModel
import com.ahu_plus.ui.screen.lessonsearch.LessonSearchScreen
import com.ahu_plus.ui.screen.lessonsearch.LessonSearchViewModel
import com.ahu_plus.ui.screen.cengke.CengKeScreen
import com.ahu_plus.ui.screen.cengke.CengKeViewModel
import com.ahu_plus.ui.screen.roomcoursetable.RoomCourseTableScreen
import com.ahu_plus.ui.screen.roomcoursetable.RoomCourseTableViewModel
import com.ahu_plus.ui.screen.evaluation.EvaluationDetailScreen
import com.ahu_plus.ui.screen.evaluation.EvaluationListScreen
import com.ahu_plus.ui.screen.evaluation.EvaluationViewModel
import com.ahu_plus.ui.screen.weather.WeatherScreen
import com.ahu_plus.ui.screen.weather.WeatherViewModel
import com.ahu_plus.ui.screen.market.MarketViewModel
import com.ahu_plus.ui.screen.chaoxing.ChaoxingViewModel
import com.ahu_plus.ui.screen.messages.UnifiedMessageCenterScreen
import com.ahu_plus.ui.screen.exam.ExamScreen
import com.ahu_plus.ui.screen.exam.ExamViewModel
import com.ahu_plus.ui.screen.grade.GradeScreen
import com.ahu_plus.ui.screen.grade.GradeViewModel
import com.ahu_plus.ui.screen.home.HomeViewModel
import com.ahu_plus.ui.screen.home.ElectricityTarget
import com.ahu_plus.ui.screen.attendance.AttendanceScreen
import com.ahu_plus.ui.screen.profile.AttendanceViewModel
import com.ahu_plus.ui.screen.profile.BathroomUtilityDetailScreen
import com.ahu_plus.ui.screen.profile.BillDetailScreen
import com.ahu_plus.ui.screen.profile.CardAnalyticsScreen
import com.ahu_plus.ui.screen.profile.CategoryDetailScreen
import com.ahu_plus.ui.screen.profile.ElectricityUtilityDetailScreen
import com.ahu_plus.ui.screen.profile.FinanceDetailScreen
import com.ahu_plus.ui.screen.profile.FinanceViewModel
import com.ahu_plus.ui.screen.profile.InternetUtilityDetailScreen
import com.ahu_plus.ui.screen.profile.MyInfoHubScreen
import com.ahu_plus.ui.screen.profile.StudentInfoViewModel
import com.ahu_plus.ui.screen.schedule.ScheduleScreen
import com.ahu_plus.ui.screen.schedule.ScheduleViewModel
import com.ahu_plus.ui.screen.trainingplan.TrainingPlanScreen
import com.ahu_plus.ui.screen.trainingplan.TrainingPlanViewModel
import com.ahu_plus.ui.screen.agenda.AgendaScreen
import com.ahu_plus.ui.screen.agenda.AgendaViewModel

// ── internal page keys ─────────────────────────────────────────────
private const val PAGE_AGENDA = "agenda"
private const val PAGE_SCHEDULE = "schedule"
private const val PAGE_GRADE = "grade"
private const val PAGE_EXAM = "exam"
private const val PAGE_NOTICES = "notices"
private const val PAGE_MESSAGE_CENTER = "messageCenter"
private const val PAGE_BILLS = "bills"
private const val PAGE_ANALYTICS = "analytics"
private const val PAGE_BATHROOM = "bathroom"
private const val PAGE_AC = "ac"
private const val PAGE_LIGHTING = "lighting"
private const val PAGE_INTERNET = "internet"
private const val PAGE_MY_INFO_HUB = "myInfoHub"
private const val PAGE_STUDENT_BASIC_INFO = "studentBasicInfo"
private const val PAGE_HOUSING_INFO = "housingInfo"
private const val PAGE_ACADEMIC_WARNING = "academicWarning"
private const val PAGE_FINANCE = "finance"
private const val PAGE_TRAINING_PLAN = "trainingPlan"
private const val PAGE_ATTENDANCE = "attendance"
private const val PAGE_EMPTY_CLASSROOM = "emptyClassroom"
private const val PAGE_LESSON_SEARCH = "lessonSearch"
private const val PAGE_ROOM_COURSE_TABLE = "roomCourseTable"
private const val PAGE_CENGKE = "cengke"
private const val PAGE_WEATHER = "weather"
private const val PAGE_CPROG = "cprog"
private const val PAGE_EVALUATION = "evaluation"
private const val PAGE_EVALUATION_DETAIL = "evaluationDetail"

internal fun appHubPageForAppKey(appKey: String): String? = when (appKey) {
    AppRegistry.KEY_AGENDA -> PAGE_AGENDA
    AppRegistry.KEY_SCHEDULE -> PAGE_SCHEDULE
    AppRegistry.KEY_GRADE -> PAGE_GRADE
    AppRegistry.KEY_EXAM -> PAGE_EXAM
    AppRegistry.KEY_TRAINING_PLAN -> PAGE_TRAINING_PLAN
    AppRegistry.KEY_EMPTY_CLASSROOM -> PAGE_EMPTY_CLASSROOM
    AppRegistry.KEY_LESSON_SEARCH -> PAGE_LESSON_SEARCH
    AppRegistry.KEY_ROOM_COURSE_TABLE -> PAGE_ROOM_COURSE_TABLE
    AppRegistry.KEY_CENGKE -> PAGE_CENGKE
    AppRegistry.KEY_CPROG -> PAGE_CPROG
    AppRegistry.KEY_EVALUATION -> PAGE_EVALUATION
    AppRegistry.KEY_NOTICE_LIST -> PAGE_NOTICES
    AppRegistry.KEY_MESSAGE_CENTER -> PAGE_MESSAGE_CENTER
    AppRegistry.KEY_CARD -> PAGE_BILLS
    AppRegistry.KEY_CARD_ANALYTICS -> PAGE_ANALYTICS
    AppRegistry.KEY_BATHROOM -> PAGE_BATHROOM
    AppRegistry.KEY_AC -> PAGE_AC
    AppRegistry.KEY_LIGHTING -> PAGE_LIGHTING
    AppRegistry.KEY_INTERNET -> PAGE_INTERNET
    AppRegistry.KEY_WEATHER -> PAGE_WEATHER
    AppRegistry.KEY_STUDENT_INFO -> PAGE_MY_INFO_HUB
    AppRegistry.KEY_FINANCE -> PAGE_FINANCE
    AppRegistry.KEY_ATTENDANCE -> PAGE_ATTENDANCE
    else -> null
}

@Composable
fun AppHubScreen(
    scheduleViewModel: ScheduleViewModel,
    gradeViewModel: GradeViewModel,
    examViewModel: ExamViewModel,
    trainingPlanViewModel: TrainingPlanViewModel,
    emptyClassroomViewModel: EmptyClassroomViewModel,
    lessonSearchViewModel: LessonSearchViewModel,
    roomCourseTableViewModel: RoomCourseTableViewModel,
    cengKeViewModel: CengKeViewModel,
    cardViewModel: HomeViewModel,
    jwcNoticeListViewModel: JwcNoticeListViewModel,
    jwcNoticeViewModel: JwcNoticeViewModel,
    chaoxingViewModel: ChaoxingViewModel,
    studentInfoViewModel: StudentInfoViewModel,
    financeViewModel: FinanceViewModel,
    attendanceViewModel: AttendanceViewModel,
    marketViewModel: MarketViewModel,
    weatherViewModel: WeatherViewModel,
    agendaViewModel: AgendaViewModel,
    evaluationViewModel: EvaluationViewModel,
    appsTarget: AppsTarget? = null,
    onNavigateBack: () -> Unit = {},
    onRecordApp: (String) -> Unit = {},
    hasCredentials: Boolean = false,
    authRefreshVersion: Int = 0,
    marketEnabled: Boolean = false,
    chaoxingEnabled: Boolean = false,
    welearnEnabled: Boolean = false,
    marketInAppHub: Boolean = marketEnabled,
    chaoxingInAppHub: Boolean = chaoxingEnabled,
    welearnInAppHub: Boolean = welearnEnabled,
    layout: AppHubLayoutConfig = AppHubLayoutConfig(),
    onOpenMarket: () -> Unit = {},
    onOpenChaoxing: () -> Unit = {},
    onOpenWelearn: () -> Unit = {},
    onOpenMarketFromMessages: () -> Unit = {},
    onOpenChaoxingFromMessages: () -> Unit = {},
    onOpenRegisteredApp: (String) -> Unit = {},
    onNeedsLogin: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AhuPlusApplication
    val sessionManager: SessionManager = app.sessionManager
    val scope = rememberCoroutineScope()
    var messagePreviewCount by remember { mutableStateOf(sessionManager.getMessagePreviewCount()) }

    val cardUiState by cardViewModel.uiState.collectAsStateWithLifecycle()
    val scheduleUiState by scheduleViewModel.uiState.collectAsStateWithLifecycle()
    val gradeUiState by gradeViewModel.uiState.collectAsStateWithLifecycle()
    val studentInfoUiState by studentInfoViewModel.uiState.collectAsStateWithLifecycle()
    val financeUiState by financeViewModel.uiState.collectAsStateWithLifecycle()
    val attendanceUiState by attendanceViewModel.uiState.collectAsStateWithLifecycle()
    val marketUiState by marketViewModel.uiState.collectAsStateWithLifecycle()

    var currentPage by rememberSaveable { mutableStateOf<String?>(null) }
    var analyticsFromBills by rememberSaveable { mutableStateOf(false) }
    val hubGridState = rememberLazyGridState()

    LaunchedEffect(appsTarget) {
        val target = appsTarget ?: return@LaunchedEffect
        if (target.route != AppsRoute.APP) return@LaunchedEffect
        val appKey = target.appKey ?: return@LaunchedEffect
        currentPage = appHubPageForAppKey(appKey)
        analyticsFromBills = false
        if (currentPage != null) onRecordApp(appKey)
        onNavigateBack()
    }

    // 评教详情子页的当前任务(由列表点击进入,不序列化以避免 stdSumTaskId 序列化要求)
    var selectedEvaluationTask by remember {
        mutableStateOf<com.ahu_plus.data.model.evaluation.TeacherEvaluationTask?>(null)
    }

    // 系统返回键：子页面 → hub
    // 注意: 我的信息二级入口(基本信息/住宿/预警) → MyInfoHub；财务/考勤 → 直接回应用页
    BackHandler(enabled = currentPage != null) {
        currentPage = when (currentPage) {
            PAGE_STUDENT_BASIC_INFO, PAGE_HOUSING_INFO, PAGE_ACADEMIC_WARNING -> PAGE_MY_INFO_HUB
            PAGE_EVALUATION_DETAIL -> PAGE_EVALUATION
            PAGE_ANALYTICS -> if (analyticsFromBills) PAGE_BILLS else null
            else -> null
        }
        if (currentPage != PAGE_ANALYTICS) analyticsFromBills = false
    }

    LaunchedEffect(currentPage, authRefreshVersion) {
        when (currentPage) {
            PAGE_GRADE -> gradeViewModel.activate()
            PAGE_ANALYTICS -> gradeViewModel.activate()
            PAGE_EXAM -> examViewModel.activate()
            PAGE_TRAINING_PLAN -> trainingPlanViewModel.activate()
            PAGE_ROOM_COURSE_TABLE -> roomCourseTableViewModel.activate()
            PAGE_CENGKE -> cengKeViewModel.activate()
            PAGE_NOTICES -> jwcNoticeListViewModel.activate()
            PAGE_MESSAGE_CENTER -> {
                jwcNoticeViewModel.loadNotices()
                if (chaoxingEnabled) chaoxingViewModel.loadMessages()
                if (marketEnabled) marketViewModel.refreshNotices()
            }
            PAGE_EVALUATION -> {
                if (authRefreshVersion > 0) evaluationViewModel.refreshList()
                else evaluationViewModel.activate()
            }
            PAGE_EVALUATION_DETAIL -> {
                if (authRefreshVersion > 0) {
                    selectedEvaluationTask?.let(evaluationViewModel::openTask)
                }
            }
            PAGE_MY_INFO_HUB, PAGE_STUDENT_BASIC_INFO, PAGE_HOUSING_INFO,
            PAGE_ACADEMIC_WARNING -> studentInfoViewModel.activate()
            PAGE_FINANCE -> financeViewModel.activate()
            PAGE_ATTENDANCE -> attendanceViewModel.activate()
            PAGE_WEATHER -> weatherViewModel.activate()
            PAGE_BATHROOM -> cardViewModel.loadBathroomBalance()
            PAGE_AC -> cardViewModel.loadElectricityBalance(ElectricityTarget.AC)
            PAGE_LIGHTING -> cardViewModel.loadElectricityBalance(ElectricityTarget.LIGHTING)
            PAGE_INTERNET -> cardViewModel.loadInternetBalance()
        }
    }

    when (currentPage) {
        PAGE_AGENDA -> AgendaScreen(
            viewModel = agendaViewModel,
            onBack = { currentPage = null },
            startExpanded = true, // 应用列表进入默认月视图
        )
        PAGE_SCHEDULE -> ScheduleScreen(
            viewModel = scheduleViewModel,
            assessmentRepository = app.assessmentRepository,
            onBack = { currentPage = null },
            onNeedsLogin = onNeedsLogin
        )
        PAGE_GRADE -> GradeScreen(
            viewModel = gradeViewModel,
            onBack = { currentPage = null },
            onNeedsLogin = onNeedsLogin
        )
        PAGE_EXAM -> ExamScreen(
            viewModel = examViewModel,
            onBack = { currentPage = null },
            onNeedsLogin = onNeedsLogin,
        )
        PAGE_TRAINING_PLAN -> TrainingPlanScreen(
            viewModel = trainingPlanViewModel,
            onBack = { currentPage = null },
            onNeedsLogin = onNeedsLogin
        )
        PAGE_EMPTY_CLASSROOM -> EmptyClassroomScreen(
            viewModel = emptyClassroomViewModel,
            onBack = { currentPage = null },
            onNeedsLogin = onNeedsLogin
        )
        PAGE_LESSON_SEARCH -> LessonSearchScreen(
            viewModel = lessonSearchViewModel,
            onBack = { currentPage = null },
            onNeedsLogin = onNeedsLogin
        )
        PAGE_ROOM_COURSE_TABLE -> RoomCourseTableScreen(
            viewModel = roomCourseTableViewModel,
            onBack = { currentPage = null },
        )
        PAGE_CENGKE -> CengKeScreen(
            viewModel = cengKeViewModel,
            onBack = { currentPage = null },
        )
        PAGE_WEATHER -> WeatherScreen(
            viewModel = weatherViewModel,
            onBack = { currentPage = null }
        )
        PAGE_CPROG -> {
            // 进入页面时再创建 VM，避免未使用时触发登录态判定和首次拉取。
            val cProgViewModel = remember {
                com.ahu_plus.ui.screen.cprog.CProgViewModel(app)
            }
            com.ahu_plus.ui.screen.cprog.CProgScreen(
                viewModel = cProgViewModel,
                onBack = { currentPage = null }
            )
        }
        PAGE_EVALUATION -> EvaluationListScreen(
            viewModel = evaluationViewModel,
            onBack = { currentPage = null },
            onNeedsLogin = onNeedsLogin,
            onOpenTask = {
                selectedEvaluationTask = it
                currentPage = PAGE_EVALUATION_DETAIL
            },
        )
        PAGE_EVALUATION_DETAIL -> {
            val task = selectedEvaluationTask
            if (task == null) {
                // 无 task 上下文(理论上 BackHandler 已经回 PAGE_EVALUATION),直接返回列表
                LaunchedEffect(Unit) { currentPage = PAGE_EVALUATION }
                CenteredMessage("正在返回列表…")
            } else {
                EvaluationDetailScreen(
                    task = task,
                    viewModel = evaluationViewModel,
                    onNeedsLogin = onNeedsLogin,
                    onBack = {
                        evaluationViewModel.resetDetail()
                        selectedEvaluationTask = null
                        currentPage = PAGE_EVALUATION
                    },
                )
            }
        }
        PAGE_NOTICES -> JwcNoticeListScreen(
            viewModel = jwcNoticeListViewModel,
            onBack = { currentPage = null }
        )
        PAGE_MESSAGE_CENTER -> {
            val academicState by jwcNoticeViewModel.uiState.collectAsStateWithLifecycle()
            val cxState by chaoxingViewModel.messagesState.collectAsStateWithLifecycle()
            val cxLoginState by chaoxingViewModel.loginState.collectAsStateWithLifecycle()
            UnifiedMessageCenterScreen(
                academicNotices = academicState.notices,
                marketNotices = marketUiState.notices,
                marketAvailable = marketEnabled && marketUiState.hasSavedIdentity,
                cxMessages = cxState.messages,
                cxAvailable = chaoxingEnabled && cxLoginState.isLoggedIn,
                isRefreshing = academicState.isLoading || marketUiState.noticesLoading || cxState.isLoading,
                previewCount = messagePreviewCount,
                onPreviewCountChange = { count ->
                    messagePreviewCount = count
                    scope.launch { sessionManager.setMessagePreviewCount(count) }
                },
                onRefresh = {
                    jwcNoticeViewModel.loadNotices()
                    if (chaoxingEnabled) chaoxingViewModel.loadMessages()
                    if (marketEnabled) marketViewModel.refreshNotices()
                },
                onOpenAcademic = { currentPage = PAGE_NOTICES },
                onOpenMarket = {
                    marketViewModel.openNotices()
                    onOpenMarketFromMessages()
                },
                onOpenChaoxing = onOpenChaoxingFromMessages,
                onBack = { currentPage = null },
            )
        }
        PAGE_BILLS -> BillDetailScreen(
            bills = cardUiState.bills,
            isLoading = cardUiState.billsLoading,
            error = cardUiState.billsError,
            onBack = { currentPage = null },
            onRefresh = cardViewModel::onRefresh,
            isLoggedIn = hasCredentials,
            onLogin = onNeedsLogin,
            onOpenAnalytics = {
                analyticsFromBills = true
                currentPage = PAGE_ANALYTICS
            }
        )
        PAGE_ANALYTICS -> CardAnalyticsScreen(
            bills = cardUiState.bills,
            academicSemesters = (
                listOfNotNull(scheduleUiState.semester) + gradeUiState.academicSemesters
            ),
            isLoading = cardUiState.billsLoading,
            error = cardUiState.billsError,
            onBack = {
                currentPage = if (analyticsFromBills) PAGE_BILLS else null
                analyticsFromBills = false
            },
            onRefresh = cardViewModel::onRefresh
        )
        PAGE_BATHROOM -> BathroomUtilityDetailScreen(
            data = cardUiState.bathroomData,
            isLoading = cardUiState.bathroomLoading,
            error = cardUiState.bathroomError,
            phone = cardUiState.bathroomPhone,
            onBack = { currentPage = null },
            onSavePhone = cardViewModel::saveBathroomPhone,
            onRefresh = cardViewModel::loadBathroomBalance
        )
        PAGE_AC -> ElectricityUtilityDetailScreen(
            title = "空调余额",
            state = cardUiState.ac,
            target = ElectricityTarget.AC,
            cardViewModel = cardViewModel,
            bills = cardUiState.acBills,
            billRange = cardUiState.acBillRange,
            billsLoading = cardUiState.acBillsLoading,
            billsError = cardUiState.acBillsError,
            onBack = { currentPage = null },
            onSaveConfig = { config, _ -> cardViewModel.saveElectricityConfig(config, ElectricityTarget.AC) },
            onRefreshBalance = { cardViewModel.loadElectricityBalance(ElectricityTarget.AC) },
            onRefreshBills = { cardViewModel.loadElectricityBills(ElectricityTarget.AC) },
            onBillRangeSelected = cardViewModel::setAcBillRange
        )
        PAGE_LIGHTING -> ElectricityUtilityDetailScreen(
            title = "照明余额",
            state = cardUiState.lighting,
            target = ElectricityTarget.LIGHTING,
            cardViewModel = cardViewModel,
            bills = cardUiState.lightingBills,
            billRange = cardUiState.lightingBillRange,
            billsLoading = cardUiState.lightingBillsLoading,
            billsError = cardUiState.lightingBillsError,
            onBack = { currentPage = null },
            onSaveConfig = { config, _ -> cardViewModel.saveElectricityConfig(config, ElectricityTarget.LIGHTING) },
            onRefreshBalance = { cardViewModel.loadElectricityBalance(ElectricityTarget.LIGHTING) },
            onRefreshBills = { cardViewModel.loadElectricityBills(ElectricityTarget.LIGHTING) },
            onBillRangeSelected = cardViewModel::setLightingBillRange
        )
        PAGE_INTERNET -> InternetUtilityDetailScreen(
            data = cardUiState.internetData,
            isLoading = cardUiState.internetLoading,
            error = cardUiState.internetError,
            bills = cardUiState.internetBills,
            billsLoading = cardUiState.internetBillsLoading,
            billsError = cardUiState.internetBillsError,
            onBack = { currentPage = null },
            onRefreshBalance = cardViewModel::loadInternetBalance,
            onRefreshBills = cardViewModel::loadInternetBills,
            cardViewModel = cardViewModel,
        )
        PAGE_MY_INFO_HUB -> MyInfoHubScreen(
            studentInfoUiState = studentInfoUiState,
            financeUiState = financeUiState,
            onBack = { currentPage = null },
            onRefreshAll = {
                studentInfoViewModel.refreshStudentInfo()
                financeViewModel.refreshFinance()
            },
            onOpenBasicInfo = { currentPage = PAGE_STUDENT_BASIC_INFO },
            onOpenHousing = { currentPage = PAGE_HOUSING_INFO },
            onOpenAcademicWarning = { currentPage = PAGE_ACADEMIC_WARNING },
            onOpenFinance = { currentPage = PAGE_FINANCE },
        )
        PAGE_STUDENT_BASIC_INFO -> CategoryDetailScreen(
            title = "学生基本信息",
            fields = studentInfoUiState.info?.basicFields ?: emptyList(),
            isLoading = studentInfoUiState.isLoading,
            error = studentInfoUiState.error,
            lastUpdatedAt = studentInfoUiState.lastUpdatedAt,
            onBack = { currentPage = PAGE_MY_INFO_HUB },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
        PAGE_HOUSING_INFO -> CategoryDetailScreen(
            title = "住宿数据",
            fields = studentInfoUiState.info?.housingFields ?: emptyList(),
            isLoading = studentInfoUiState.isLoading,
            error = studentInfoUiState.error,
            lastUpdatedAt = studentInfoUiState.lastUpdatedAt,
            onBack = { currentPage = PAGE_MY_INFO_HUB },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
        PAGE_ACADEMIC_WARNING -> CategoryDetailScreen(
            title = "学业预警信息",
            fields = studentInfoUiState.info?.academicWarningFields ?: emptyList(),
            isLoading = studentInfoUiState.isLoading,
            error = studentInfoUiState.error,
            lastUpdatedAt = studentInfoUiState.lastUpdatedAt,
            onBack = { currentPage = PAGE_MY_INFO_HUB },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
        PAGE_FINANCE -> FinanceDetailScreen(
            uiState = financeUiState,
            onBack = { currentPage = null },
            onRefresh = financeViewModel::refreshFinance
        )
        PAGE_ATTENDANCE -> AttendanceScreen(
            uiState = attendanceUiState,
            onBack = { currentPage = null },
            onRefresh = attendanceViewModel::refreshAttendance
        )
        else -> {
            // 排序依赖的最近使用 / 使用次数,进入应用页时刷新一次快照
            val recentKeys = remember(currentPage) { sessionManager.getRecentApps() }
            val usageCounts = remember(currentPage) { sessionManager.getAppUsageCounts() }
            AppHubPage(
                gridState = hubGridState,
                layout = layout,
                recentKeys = recentKeys,
                usageCounts = usageCounts,
                marketEnabled = marketInAppHub,
                chaoxingEnabled = chaoxingInAppHub,
                welearnEnabled = welearnInAppHub,
                onOpenMarket = onOpenMarket,
                onOpenChaoxing = onOpenChaoxing,
                onOpenWelearn = onOpenWelearn,
                onNavigate = { appKey ->
                    val page = appHubPageForAppKey(appKey)
                    if (page != null) {
                        analyticsFromBills = false
                        currentPage = page
                        onRecordApp(appKey)
                    } else {
                        // 不在 AppHub 内部的应用(如 KEY_EMAIL),委托给上层处理
                        onOpenRegisteredApp(appKey)
                    }
                }
            )
        }
    }
}

// ── Hub page ──────────────────────────────────────────────────────

@Composable
private fun AppHubPage(
    gridState: LazyGridState,
    layout: AppHubLayoutConfig,
    recentKeys: List<String>,
    usageCounts: Map<String, Int>,
    marketEnabled: Boolean,
    chaoxingEnabled: Boolean,
    welearnEnabled: Boolean,
    onOpenMarket: () -> Unit,
    onOpenChaoxing: () -> Unit,
    onOpenWelearn: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val searchEnabled = layout.showSearchBar
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // 搜索被设置关掉时,收起并清空,避免残留过滤态
    if (!searchEnabled && (searchVisible || query.isNotEmpty())) {
        searchVisible = false
        query = ""
    }
    val normalizedQuery = if (searchEnabled) query.trim() else ""

    // 分区:隐藏 / 排序 / 分组由 AppRegistry.arrange 统一处理(与设置页预览同源)
    val sections = remember(layout, recentKeys, usageCounts, normalizedQuery) {
        AppRegistry.arrange(layout, recentKeys, usageCounts)
            .mapNotNull { section ->
                if (normalizedQuery.isBlank()) section
                else {
                    val filtered = section.apps.filter {
                        it.title.contains(normalizedQuery, ignoreCase = true) ||
                            it.group.contains(normalizedQuery, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) null else section.copy(apps = filtered)
                }
            }
    }
    val totalApps = sections.sumOf { it.apps.size }
    val serviceMatches: (String) -> Boolean = { title ->
        normalizedQuery.isBlank() ||
            title.contains(normalizedQuery, ignoreCase = true) ||
            "第三方服务".contains(normalizedQuery, ignoreCase = true)
    }
    val hasMatchingService = layout.showThirdPartyServices && (
        (marketEnabled && serviceMatches("集市")) ||
            (chaoxingEnabled && serviceMatches("学习通")) ||
            (welearnEnabled && serviceMatches("WeLearn"))
    )

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = {
                    if (searchEnabled && searchVisible) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索应用或分类") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Text("应用")
                    }
                },
                actions = {
                    if (searchEnabled) {
                        IconButton(
                            onClick = {
                                if (searchVisible) query = ""
                                searchVisible = !searchVisible
                            }
                        ) {
                            Icon(
                                if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (searchVisible) "关闭搜索" else "搜索应用",
                            )
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        // COMPACT 卡片强制单列(normalize 已保证,预览态再兜底一次)
        val effectiveColumns =
            if (layout.cardStyle == AppHubCardStyle.COMPACT) AppHubColumns.ONE else layout.columns
        val gridCells = when (effectiveColumns) {
            AppHubColumns.ONE -> GridCells.Fixed(1)
            AppHubColumns.TWO -> GridCells.Fixed(2)
            AppHubColumns.THREE -> GridCells.Fixed(3)
            AppHubColumns.ADAPTIVE -> GridCells.Adaptive(minSize = 150.dp)
        }
        val gap = if (layout.density == AppHubDensity.COMPACT) 8.dp else 12.dp
        val showHeaders = layout.showSectionHeaders

        LazyVerticalGrid(
            columns = gridCells,
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (hasMatchingService) {
                if (showHeaders) {
                    item(
                        key = "header:third-party",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        AppHubSectionTitle("第三方服务")
                    }
                }
                if (marketEnabled && serviceMatches("集市")) {
                    item(key = "service:market") {
                        AppHubTile(
                            title = "集市",
                            icon = Icons.Filled.Storefront,
                            iconColor = AhuOrange,
                            cardStyle = layout.cardStyle,
                            density = layout.density,
                            showIcon = layout.showIcons,
                            onClick = onOpenMarket,
                        )
                    }
                }
                if (chaoxingEnabled && serviceMatches("学习通")) {
                    item(key = "service:chaoxing") {
                        AppHubTile(
                            title = "学习通",
                            icon = Icons.Filled.School,
                            iconColor = AhuViolet,
                            cardStyle = layout.cardStyle,
                            density = layout.density,
                            showIcon = layout.showIcons,
                            onClick = onOpenChaoxing,
                        )
                    }
                }
                if (welearnEnabled && serviceMatches("WeLearn")) {
                    item(key = "service:welearn") {
                        AppHubTile(
                            title = "WeLearn",
                            icon = Icons.AutoMirrored.Filled.LibraryBooks,
                            iconColor = AhuBlue,
                            cardStyle = layout.cardStyle,
                            density = layout.density,
                            showIcon = layout.showIcons,
                            onClick = onOpenWelearn,
                        )
                    }
                }
            }

            sections.forEach { section ->
                val title = section.title
                if (showHeaders && title != null) {
                    item(
                        key = "header:$title",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        AppHubSectionTitle(title)
                    }
                }
                items(items = section.apps, key = { it.key }) { spec ->
                    AppHubTile(
                        title = spec.title,
                        icon = spec.icon,
                        iconColor = spec.tint,
                        iconBackground = spec.gradient,
                        cardStyle = layout.cardStyle,
                        density = layout.density,
                        showIcon = layout.showIcons,
                        onClick = { onNavigate(spec.key) },
                    )
                }
            }

            if (totalApps == 0 && !hasMatchingService) {
                item(
                    key = "empty-state",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    CenteredMessage(
                        text = if (normalizedQuery.isNotBlank()) "没有找到相关应用"
                        else "已隐藏全部应用,可在\"应用页设置\"中恢复",
                        modifier = Modifier.height(160.dp),
                    )
                }
            }
        }
    }
}

// ── Grid components ───────────────────────────────────────────────

@Composable
private fun AppHubSectionTitle(title: String) {
    AhuSectionTitle(
        text = title,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * 应用磁贴调度:按卡片样式分发到横向 / 竖向 / 紧凑三种渲染。设置页预览复用同一实现。
 *
 * @param showIcon 为 false 时不绘制图标,磁贴只留文字标题(纯文字列表风)。
 */
@Composable
internal fun AppHubTile(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    cardStyle: AppHubCardStyle,
    density: AppHubDensity,
    showIcon: Boolean = true,
    iconBackground: Brush? = null,
    onClick: () -> Unit,
) {
    when (cardStyle) {
        AppHubCardStyle.HORIZONTAL ->
            HorizontalTile(title, icon, iconColor, iconBackground, density, showIcon, onClick)
        AppHubCardStyle.VERTICAL ->
            VerticalTile(title, icon, iconColor, iconBackground, density, showIcon, onClick)
        AppHubCardStyle.COMPACT ->
            CompactTile(title, icon, iconColor, iconBackground, showIcon, onClick)
    }
}

private val tileBorder: @Composable () -> BorderStroke = {
    BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

/** 横向:图标在左、标题在右。默认样式。 */
@Composable
private fun HorizontalTile(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    iconBackground: Brush?,
    density: AppHubDensity,
    showIcon: Boolean,
    onClick: () -> Unit,
) {
    val compact = density == AppHubDensity.COMPACT
    // 无图标时压低高度换取紧凑(应用名多为单行);有图标沿用原高度
    val height = when {
        !showIcon -> if (compact) 40.dp else 48.dp
        compact -> 68.dp
        else -> 84.dp
    }
    val iconBox = if (compact) 36.dp else 42.dp
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = tileBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcon) {
                AppHubIcon(
                    icon = icon,
                    iconColor = iconColor,
                    background = iconBackground,
                    boxSize = iconBox,
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                // 无图标时文字居中,避免大片留白偏左
                textAlign = if (showIcon) null else androidx.compose.ui.text.style.TextAlign.Center,
                // 无图标时压成单行,匹配降低后的行高
                maxLines = if (showIcon) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 竖向:图标在上、标题在下居中。适合 3 列 / 自适应密铺。 */
@Composable
private fun VerticalTile(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    iconBackground: Brush?,
    density: AppHubDensity,
    showIcon: Boolean,
    onClick: () -> Unit,
) {
    val compact = density == AppHubDensity.COMPACT
    // 无图标时压低高度换取紧凑(文字单行居中)
    val height = when {
        !showIcon -> if (compact) 40.dp else 48.dp
        compact -> 88.dp
        else -> 104.dp
    }
    val iconBox = if (compact) 36.dp else 42.dp
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = tileBorder(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showIcon) {
                AppHubIcon(
                    icon = icon,
                    iconColor = iconColor,
                    background = iconBackground,
                    boxSize = iconBox,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** 紧凑:单行列表项,小图标 + 标题。强制单列。 */
@Composable
private fun CompactTile(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    iconBackground: Brush?,
    showIcon: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            // 无图标时行高从 52 压到 40,单行文字列表更密
            .height(if (showIcon) 52.dp else 40.dp),
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = tileBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcon) {
                AppHubIcon(
                    icon = icon,
                    iconColor = iconColor,
                    background = iconBackground,
                    boxSize = 34.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppHubIcon(
    icon: ImageVector,
    iconColor: Color,
    background: Brush? = null,
    boxSize: androidx.compose.ui.unit.Dp = 42.dp,
) {
    // background 缺省时按 iconColor 生成渐变;brush 记忆化避免网格高频重组时反复分配。
    val resolvedBackground = background ?: remember(iconColor) { AhuGradient.forTint(iconColor) }

    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(AhuShapes.IconBox)
            .background(resolvedBackground),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(boxSize * 0.52f),
        )
    }
}
