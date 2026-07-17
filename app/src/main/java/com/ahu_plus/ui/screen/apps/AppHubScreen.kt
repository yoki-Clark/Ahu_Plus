package com.ahu_plus.ui.screen.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.data.home.AppRegistry
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.ui.components.AhuIconBox
import com.ahu_plus.ui.theme.AhuShapes
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.ahu_plus.ui.components.AhuSectionTitle
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.components.CenteredMessage
import com.ahu_plus.ui.screen.dashboard.JwcNoticeListScreen
import com.ahu_plus.ui.screen.dashboard.JwcNoticeListViewModel
import com.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomScreen
import com.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomViewModel
import com.ahu_plus.ui.screen.evaluation.EvaluationDetailScreen
import com.ahu_plus.ui.screen.evaluation.EvaluationListScreen
import com.ahu_plus.ui.screen.evaluation.EvaluationViewModel
import com.ahu_plus.ui.screen.weather.WeatherScreen
import com.ahu_plus.ui.screen.weather.WeatherViewModel
import com.ahu_plus.ui.screen.exam.ExamScreen
import com.ahu_plus.ui.screen.exam.ExamViewModel
import com.ahu_plus.ui.screen.exam.ExamPredictionScreen
import com.ahu_plus.ui.screen.exam.ExamPredictionViewModel
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
private const val PAGE_EXAM_PREDICTION = "examPrediction"
private const val PAGE_NOTICES = "notices"
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
    AppRegistry.KEY_CPROG -> PAGE_CPROG
    AppRegistry.KEY_EVALUATION -> PAGE_EVALUATION
    AppRegistry.KEY_NOTICE_LIST -> PAGE_NOTICES
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
    cardViewModel: HomeViewModel,
    jwcNoticeListViewModel: JwcNoticeListViewModel,
    studentInfoViewModel: StudentInfoViewModel,
    financeViewModel: FinanceViewModel,
    attendanceViewModel: AttendanceViewModel,
    weatherViewModel: WeatherViewModel,
    agendaViewModel: AgendaViewModel,
    evaluationViewModel: EvaluationViewModel,
    requestedAppKey: String? = null,
    onRequestedAppConsumed: () -> Unit = {},
    onRecordApp: (String) -> Unit = {},
    hasCredentials: Boolean = false,
    authRefreshVersion: Int = 0,
    onNeedsLogin: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AhuPlusApplication
    val sessionManager: SessionManager = app.sessionManager

    val cardUiState by cardViewModel.uiState.collectAsStateWithLifecycle()
    val studentInfoUiState by studentInfoViewModel.uiState.collectAsStateWithLifecycle()
    val financeUiState by financeViewModel.uiState.collectAsStateWithLifecycle()
    val attendanceUiState by attendanceViewModel.uiState.collectAsStateWithLifecycle()

    var currentPage by rememberSaveable { mutableStateOf<String?>(null) }
    var analyticsFromBills by rememberSaveable { mutableStateOf(false) }
    val hubListState = rememberLazyListState()

    LaunchedEffect(requestedAppKey) {
        val appKey = requestedAppKey ?: return@LaunchedEffect
        currentPage = appHubPageForAppKey(appKey)
        analyticsFromBills = false
        if (currentPage != null) onRecordApp(appKey)
        onRequestedAppConsumed()
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
            PAGE_EXAM_PREDICTION -> PAGE_EXAM
            PAGE_EVALUATION_DETAIL -> PAGE_EVALUATION
            PAGE_ANALYTICS -> if (analyticsFromBills) PAGE_BILLS else null
            else -> null
        }
        if (currentPage != PAGE_ANALYTICS) analyticsFromBills = false
    }

    LaunchedEffect(currentPage, authRefreshVersion) {
        when (currentPage) {
            PAGE_GRADE -> gradeViewModel.activate()
            PAGE_EXAM -> examViewModel.activate()
            PAGE_TRAINING_PLAN -> trainingPlanViewModel.activate()
            PAGE_NOTICES -> jwcNoticeListViewModel.activate()
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
            onOpenPrediction = { currentPage = PAGE_EXAM_PREDICTION }
        )
        PAGE_EXAM_PREDICTION -> {
            // 进入排考预测页时才创建 VM,触发首次拉取
            val examPredictionViewModel = remember {
                ExamPredictionViewModel(app.examDataRepository, sessionManager)
            }
            ExamPredictionScreen(
                viewModel = examPredictionViewModel,
                onBack = { currentPage = PAGE_EXAM }
            )
        }
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
        PAGE_WEATHER -> WeatherScreen(
            viewModel = weatherViewModel,
            onBack = { currentPage = null }
        )
        PAGE_CPROG -> {
            // 进入时才建 VM(触发登录态判定/首次拉列表),对齐排考预测懒创建
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
        else -> AppHubPage(
            listState = hubListState,
            onNavigate = { appKey ->
                appHubPageForAppKey(appKey)?.let { page ->
                    analyticsFromBills = false
                    currentPage = page
                    onRecordApp(appKey)
                }
            }
        )
    }
}

// ── Hub page ──────────────────────────────────────────────────────

@Composable
private fun AppHubPage(
    listState: androidx.compose.foundation.lazy.LazyListState,
    onNavigate: (String) -> Unit
) {
    Scaffold(
        topBar = {
            AhuTopAppBar(title = { Text("应用") })
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            AppRegistry.grouped().forEach { (group, specs) ->
                item(key = "header:$group") {
                    Spacer(modifier = Modifier.height(8.dp))
                    AhuSectionTitle(text = group)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(items = specs, key = { it.key }) { spec ->
                    AppHubItem(
                        title = spec.title,
                        icon = spec.icon,
                        iconColor = spec.tint,
                        gradient = spec.gradient,
                        onClick = { onNavigate(spec.key) },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ── Shared item component ─────────────────────────────────────────

@Composable
private fun AppHubItem(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    gradient: Brush? = null,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            leadingContent = {
                if (gradient != null) {
                    // 一级入口：渐变图标盒
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(AhuShapes.IconBox)
                            .background(gradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    AhuIconBox(
                        imageVector = icon,
                        tint = iconColor,
                        size = 38.dp,
                    )
                }
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 62.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}
