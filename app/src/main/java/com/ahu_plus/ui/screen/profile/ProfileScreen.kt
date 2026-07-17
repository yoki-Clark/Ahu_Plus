package com.ahu_plus.ui.screen.profile

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.ahu_plus.ui.components.AhuPullToRefreshBox
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu_plus.data.repository.CacheCleanupRepository
import com.ahu_plus.data.local.AppThemeMode
import com.ahu_plus.data.model.BillRecord
import com.ahu_plus.data.model.CheckResult
import com.ahu_plus.data.model.FinanceSummary
import com.ahu_plus.data.model.StudentInfo
import com.ahu_plus.data.model.StudentInfoCodeLookup
import com.ahu_plus.data.model.StudentInfoField
import com.ahu_plus.data.repository.AdwmhQrCode
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.ui.components.AhuInfoRow
import com.ahu_plus.ui.components.AhuSectionHeader
import com.ahu_plus.ui.components.LoginRequiredCard
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.components.AhuStatusCard
import com.ahu_plus.data.local.ElectricityRoomConfig
import com.ahu_plus.data.model.ElectricityDailyRecord
import com.ahu_plus.data.model.ElectricityUiData
import com.ahu_plus.data.local.BottomNavService
import com.ahu_plus.data.developer.DeveloperRuntime
import com.ahu_plus.ui.screen.home.BathroomBalanceCard
import com.ahu_plus.data.model.InternetBalanceData
import com.ahu_plus.data.model.InternetBillRecord
import com.ahu_plus.ui.screen.home.ElectricityBalanceCard
import com.ahu_plus.ui.screen.home.ElectricityBillRange
import com.ahu_plus.ui.screen.home.ElectricityState
import com.ahu_plus.ui.screen.home.HomeViewModel
import com.ahu_plus.ui.screen.home.ElectricityTarget
import com.ahu_plus.ui.screen.home.InternetBalanceCard
import com.ahu_plus.ui.screen.home.QrCodeFullScreenDialog
import com.ahu_plus.ui.screen.market.MarketIdentityEditor
import com.ahu_plus.ui.screen.market.MarketViewModel
import com.ahu_plus.ui.screen.schedule.ScheduleUiState
import com.ahu_plus.notification.CardBalanceAlertMode
import com.ahu_plus.notification.recentCanteenDailyAverage
import com.ahu_plus.ui.theme.AhuGreen
import com.ahu_plus.util.BrowserOpener
import com.ahu_plus.util.QrCodeBitmap
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    cardViewModel: HomeViewModel,
    marketViewModel: MarketViewModel,
    studentInfoViewModel: StudentInfoViewModel,
    financeViewModel: FinanceViewModel,
    scheduleUiState: ScheduleUiState,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    scrollTarget: String? = null,
    onScrollTargetConsumed: () -> Unit = {},
    profileSubPage: String? = null,
    onProfileSubPageConsumed: () -> Unit = {},
    openCardAnalytics: Boolean = false,
    onCardAnalyticsConsumed: () -> Unit = {},
    /** 使用帮助首开说明弹窗是否已看过（持久化，退登不清）。 */
    guideIntroSeen: Boolean = true,
    /** 首次展示帮助弹窗后落盘标记。 */
    onGuideIntroSeen: () -> Unit = {},
    bottomNavServices: List<String> = emptyList(),
    onBottomNavServicesChanged: (List<String>) -> Unit = {},
    onOpenScheduleSettings: () -> Unit = {},
    onOpenMarketSettings: () -> Unit = {},
    onOpenChaoxingSettings: () -> Unit = {},
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    // 2026-07-06 修复: 提升到 ProfileScreen 顶层(不嵌套在 if 链内部)。
    // 原因: SaveableStateHolder 内的 inner Composable 切换(如 ProfileHomeScreen → AppSettingsScreen)
    // 不会保留 inner registry 的 saved state。提升后 listState 注册到 ProfileScreen 自己的
    // registry,inner Composable 切换不影响。
    val profileListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var showBills by rememberSaveable { mutableStateOf(false) }
    var showMyInfoHub by rememberSaveable { mutableStateOf(false) }
    var showStudentBasicInfo by rememberSaveable { mutableStateOf(false) }
    var showHousingInfo by rememberSaveable { mutableStateOf(false) }
    var showAcademicWarning by rememberSaveable { mutableStateOf(false) }
    var showFinance by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showUtilities by rememberSaveable { mutableStateOf(false) }
    var showCardAnalytics by rememberSaveable { mutableStateOf(false) }
    var showCacheCleanup by rememberSaveable { mutableStateOf(false) }
    var showXzxx by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var showFaq by rememberSaveable { mutableStateOf(false) }
    var showAnnouncements by rememberSaveable { mutableStateOf(false) }
    var showOpenSourceLicenses by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showFullQrCode by rememberSaveable { mutableStateOf(false) }
    val cardUiState by cardViewModel.uiState.collectAsStateWithLifecycle()
    val marketUiState by marketViewModel.uiState.collectAsStateWithLifecycle()

    var utilityTarget by remember { mutableStateOf<String?>(null) }

    // 内测计划:ProfileScreen 顶层持有,持久化到 SessionManager
    val appContext = LocalContext.current
    val app = appContext.applicationContext as AhuPlusApplication
    val sessionManager = app.sessionManager
    var betaEnabled by remember { mutableStateOf(sessionManager.isBetaEnabled()) }
    var developerEnabled by remember { mutableStateOf(sessionManager.isDeveloperEnabled()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(developerEnabled) {
        DeveloperRuntime.setDeveloperEnabled(developerEnabled)
    }

    fun openUtility(target: String) {
        utilityTarget = target
        showUtilities = true
    }

    val studentInfoUiState by studentInfoViewModel.uiState.collectAsStateWithLifecycle()
    val financeUiState by financeViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(studentInfoUiState.info) {
        cardViewModel.applyStudentInfoPrefill(studentInfoUiState.info)
    }

    LaunchedEffect(scrollTarget) {
        val target = scrollTarget ?: return@LaunchedEffect
        if (target == "bathroom" || target == "ac" || target == "lighting" || target == "internet") {
            openUtility(target)
            onScrollTargetConsumed()
        }
    }

    LaunchedEffect(profileSubPage) {
        when (profileSubPage) {
            "myInfoHub" -> showMyInfoHub = true
            "finance" -> showFinance = true
            "settings" -> showSettings = true
            "cardAnalytics" -> showCardAnalytics = true
            "cacheCleanup" -> showCacheCleanup = true
        }
        if (profileSubPage != null) {
            onProfileSubPageConsumed()
        }
    }

    LaunchedEffect(openCardAnalytics) {
        if (openCardAnalytics) {
            showCardAnalytics = true
            onCardAnalyticsConsumed()
        }
    }

    if (showCardAnalytics) {
        BackHandler(enabled = true) { showCardAnalytics = false }
        CardAnalyticsScreen(
            bills = cardUiState.bills,
            isLoading = cardUiState.billsLoading,
            error = cardUiState.billsError,
            onBack = { showCardAnalytics = false },
            onRefresh = cardViewModel::onRefresh
        )
    } else if (showBills) {
        BackHandler(enabled = true) { showBills = false }
        BillDetailScreen(
            bills = cardUiState.bills,
            isLoading = cardUiState.billsLoading,
            error = cardUiState.billsError,
            onBack = { showBills = false },
            onRefresh = cardViewModel::onRefresh,
            isLoggedIn = isLoggedIn,
            onLogin = onLogin,
            onOpenAnalytics = { showCardAnalytics = true }
        )
    } else if (showUtilities) {
        BackHandler(enabled = true) { showUtilities = false; utilityTarget = null }
        WaterElectricityUtilityDetailScreen(
            bathroomData = cardUiState.bathroomData,
            bathroomLoading = cardUiState.bathroomLoading,
            bathroomError = cardUiState.bathroomError,
            bathroomPhone = cardUiState.bathroomPhone,
            acState = cardUiState.ac,
            acBills = cardUiState.acBills,
            acBillRange = cardUiState.acBillRange,
            acBillsLoading = cardUiState.acBillsLoading,
            acBillsError = cardUiState.acBillsError,
            lightingState = cardUiState.lighting,
            lightingBills = cardUiState.lightingBills,
            lightingBillRange = cardUiState.lightingBillRange,
            lightingBillsLoading = cardUiState.lightingBillsLoading,
            lightingBillsError = cardUiState.lightingBillsError,
            internetData = cardUiState.internetData,
            internetLoading = cardUiState.internetLoading,
            internetError = cardUiState.internetError,
            internetBills = cardUiState.internetBills,
            internetBillsLoading = cardUiState.internetBillsLoading,
            internetBillsError = cardUiState.internetBillsError,
            onBack = { showUtilities = false },
            onSaveBathroomPhone = cardViewModel::saveBathroomPhone,
            onSaveElectricityConfig = { config, target -> cardViewModel.saveElectricityConfig(config, target) },
            onRefreshBathroom = cardViewModel::loadBathroomBalance,
            onRefreshAcBalance = { cardViewModel.loadElectricityBalance(ElectricityTarget.AC) },
            onRefreshLightingBalance = { cardViewModel.loadElectricityBalance(ElectricityTarget.LIGHTING) },
            onRefreshInternetBalance = cardViewModel::loadInternetBalance,
            onRefreshAcBills = { cardViewModel.loadElectricityBills(ElectricityTarget.AC) },
            onRefreshLightingBills = { cardViewModel.loadElectricityBills(ElectricityTarget.LIGHTING) },
            onRefreshInternetBills = cardViewModel::loadInternetBills,
            onAcBillRangeSelected = cardViewModel::setAcBillRange,
            onLightingBillRangeSelected = cardViewModel::setLightingBillRange,
            cardViewModel = cardViewModel,
            initialUtility = utilityTarget
        )
    } else if (showStudentBasicInfo) {
        BackHandler(enabled = true) { showStudentBasicInfo = false; showMyInfoHub = true }
        CategoryDetailScreen(
            title = "学生基本信息",
            fields = studentInfoUiState.info?.basicFields ?: emptyList(),
            isLoading = studentInfoUiState.isLoading,
            error = studentInfoUiState.error,
            lastUpdatedAt = studentInfoUiState.lastUpdatedAt,
            onBack = { showStudentBasicInfo = false; showMyInfoHub = true },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
    } else if (showHousingInfo) {
        BackHandler(enabled = true) { showHousingInfo = false; showMyInfoHub = true }
        CategoryDetailScreen(
            title = "住宿数据",
            fields = studentInfoUiState.info?.housingFields ?: emptyList(),
            isLoading = studentInfoUiState.isLoading,
            error = studentInfoUiState.error,
            lastUpdatedAt = studentInfoUiState.lastUpdatedAt,
            onBack = { showHousingInfo = false; showMyInfoHub = true },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
    } else if (showAcademicWarning) {
        BackHandler(enabled = true) { showAcademicWarning = false; showMyInfoHub = true }
        CategoryDetailScreen(
            title = "学业预警信息",
            fields = studentInfoUiState.info?.academicWarningFields ?: emptyList(),
            isLoading = studentInfoUiState.isLoading,
            error = studentInfoUiState.error,
            lastUpdatedAt = studentInfoUiState.lastUpdatedAt,
            onBack = { showAcademicWarning = false; showMyInfoHub = true },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
    } else if (showFinance) {
        BackHandler(enabled = true) { showFinance = false }
        FinanceDetailScreen(
            uiState = financeUiState,
            onBack = { showFinance = false },
            onRefresh = financeViewModel::refreshFinance
        )
    } else if (showMyInfoHub) {
        BackHandler(enabled = true) { showMyInfoHub = false }
        MyInfoHubScreen(
            studentInfoUiState = studentInfoUiState,
            financeUiState = financeUiState,
            onBack = { showMyInfoHub = false },
            onRefreshAll = {
                studentInfoViewModel.refreshStudentInfo()
                financeViewModel.refreshFinance()
            },
            onOpenBasicInfo = { showStudentBasicInfo = true },
            onOpenHousing = { showHousingInfo = true },
            onOpenAcademicWarning = { showAcademicWarning = true },
            onOpenFinance = { showFinance = true },
        )
    } else if (showXzxx) {
        BackHandler(enabled = true) { showXzxx = false }
        XzxxScreen(
            onBack = { showXzxx = false },
            repository = app.xzxxRepository,
        )
    } else if (showGuide) {
        BackHandler(enabled = true) { showGuide = false }
        GuideScreen(
            introSeen = guideIntroSeen,
            onIntroSeen = onGuideIntroSeen,
            onBack = { showGuide = false }
        )
    } else if (showFaq) {
        BackHandler(enabled = true) { showFaq = false }
        FaqScreen(onBack = { showFaq = false })
    } else if (showAnnouncements) {
        BackHandler(enabled = true) { showAnnouncements = false }
        AnnouncementHistoryScreen(onBack = { showAnnouncements = false })
    } else if (showOpenSourceLicenses) {
        BackHandler(enabled = true) { showOpenSourceLicenses = false }
        OpenSourceLicensesScreen(onBack = { showOpenSourceLicenses = false })
    } else if (showSettings) {
        BackHandler(enabled = true) { showSettings = false }
        AppSettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            qrBrightnessBoost = cardViewModel.getQrBrightnessBoost(),
            onQrBrightnessBoostChanged = cardViewModel::setQrBrightnessBoost,
            adwmhConcurrentRetry = cardViewModel.getAdwmhConcurrentRetry(),
            onAdwmhConcurrentRetryChanged = cardViewModel::setAdwmhConcurrentRetry,
            cardBalanceAlertEnabled = cardViewModel.getCardBalanceAlertEnabled(),
            cardBalanceAlertThreshold = cardViewModel.getCardBalanceAlertThreshold(),
            cardBalanceAlertMode = CardBalanceAlertMode.fromStored(cardViewModel.getCardBalanceAlertMode()),
            cardBalanceAlertLookbackDays = cardViewModel.getCardBalanceAlertLookbackDays(),
            recentCanteenDailyAverages = listOf(7, 14, 30).associateWith { days ->
                recentCanteenDailyAverage(cardUiState.bills, days)
            },
            onCardBalanceAlertEnabledChanged = cardViewModel::setCardBalanceAlertEnabled,
            onCardBalanceAlertThresholdChanged = cardViewModel::setCardBalanceAlertThreshold,
            onCardBalanceAlertModeChanged = { cardViewModel.setCardBalanceAlertMode(it.name) },
            onCardBalanceAlertLookbackDaysChanged = cardViewModel::setCardBalanceAlertLookbackDays,
            bottomNavServices = bottomNavServices,
            marketEnabled = marketUiState.thirdPartyServicesEnabled && marketUiState.marketChildEnabled,
            chaoxingEnabled = marketUiState.thirdPartyServicesEnabled && marketUiState.chaoxingChildEnabled,
            welearnEnabled = marketUiState.thirdPartyServicesEnabled && marketUiState.welearnChildEnabled,
            onBottomNavServicesChanged = onBottomNavServicesChanged,
            onOpenScheduleSettings = {
                showSettings = false
                onOpenScheduleSettings()
            },
            onOpenMarketSettings = {
                showSettings = false
                onOpenMarketSettings()
            },
            onOpenChaoxingSettings = {
                showSettings = false
                onOpenChaoxingSettings()
            },
            onOpenCacheCleanup = {
                showCacheCleanup = true
                showSettings = false
            },
            onBack = { showSettings = false }
        )
    } else if (showCacheCleanup) {
        BackHandler(enabled = true) { showCacheCleanup = false; showSettings = true }
        val cacheRepo = remember(appContext) {
            CacheCleanupRepository(
                appDataStore = app.appDataStore,
                appContext = appContext
            )
        }
        val cacheVm: CacheCleanupViewModel = viewModel(factory = CacheCleanupViewModel.Factory(cacheRepo))
        val cacheUi by cacheVm.uiState.collectAsStateWithLifecycle()
        CacheCleanupScreen(
            sizeInfo = cacheUi.sizeInfo,
            downloadSize = cacheUi.downloadSize,
            downloadCount = cacheUi.downloadCount,
            isCalculating = cacheUi.isCalculating || cacheUi.isClearing,
            onToggleGroup = { /* 由 Screen 内部维护选中态,此处无需上抛 */ },
            onClear = { selected -> cacheVm.clear(selected) },
            onBack = { showCacheCleanup = false }
        )
    } else if (showAbout) {
        BackHandler(enabled = true) { showAbout = false }
        AboutScreen(
            onBack = { showAbout = false },
            guideIntroSeen = guideIntroSeen,
            onGuideIntroSeen = onGuideIntroSeen,
            betaEnabled = betaEnabled,
            onBetaEnabledChange = { newValue ->
                scope.launch {
                    app.updateManager.changeChannel(newValue)
                    betaEnabled = newValue
                }
            },
            developerEnabled = developerEnabled,
            onDeveloperEnabledChange = { newValue ->
                developerEnabled = newValue
                scope.launch { sessionManager.setDeveloperEnabled(newValue) }
            },
        )
    } else {
        val studentInfo = studentInfoUiState.info
        ProfileHomeScreen(
            listState = profileListState,
            studentName = studentInfo?.displayName() ?: scheduleUiState.studentName,
            department = studentInfo?.department() ?: scheduleUiState.department,
            className = studentInfo?.classOrMajor() ?: scheduleUiState.className,
            hasStudentInfo = studentInfo != null,
            financeItemCount = financeUiState.summary?.let { s ->
                s.scholarship.size + s.grant.size + s.hardshipGrant.size +
                    s.workStudy.size + s.loan.size + s.arrearsStatus.size
            } ?: 0,
            balance = cardUiState.balance,
            balanceLoading = cardUiState.isLoading,
            balanceError = cardUiState.error,
            timestamp = cardUiState.timestamp,
            qrCode = cardUiState.qrCode,
            qrBalance = cardUiState.qrBalance,
            qrLoading = cardUiState.qrLoading,
            qrError = cardUiState.qrError,
            qrCountdownSeconds = cardUiState.qrCountdownSeconds,
            onQrClick = {
                if (isLoggedIn) {
                    cardViewModel.loadCampusQrCode()
                    showFullQrCode = true
                } else {
                    onLogin()
                }
            },
            identityCount = marketUiState.identities.size,
            hasMarketIdentity = marketUiState.hasSavedIdentity,
            // 「第三方服务」section: parent (5s 弹窗) + 集市/学习通 子开关
            thirdPartyEnabled = marketUiState.thirdPartyServicesEnabled,
            marketChildEnabled = marketUiState.marketChildEnabled,
            chaoxingChildEnabled = marketUiState.chaoxingChildEnabled,
            welearnChildEnabled = marketUiState.welearnChildEnabled,
            bottomNavServices = bottomNavServices,
            onThirdPartyEnabledChanged = marketViewModel::setMarketEnabled,
            onMarketChildEnabledChanged = marketViewModel::setMarketChildEnabled,
            onChaoxingChildEnabledChanged = marketViewModel::setChaoxingChildEnabled,
            onWelearnChildEnabledChanged = marketViewModel::setWelearnChildEnabled,
            bathroomData = cardUiState.bathroomData,
            bathroomLoading = cardUiState.bathroomLoading,
            bathroomError = cardUiState.bathroomError,
            bathroomPhone = cardUiState.bathroomPhone,
            onSaveBathroomPhone = cardViewModel::saveBathroomPhone,
            onRetryBathroom = cardViewModel::loadBathroomBalance,
            internetData = cardUiState.internetData,
            internetLoading = cardUiState.internetLoading,
            internetError = cardUiState.internetError,
            onRetryInternet = cardViewModel::loadInternetBalance,
            onRefresh = { cardViewModel.onRefresh() },
            onOpenBills = { if (isLoggedIn) showBills = true else onLogin() },
            onOpenUtilityOverview = {
                if (isLoggedIn) {
                    showUtilities = true
                    utilityTarget = null
                } else {
                    onLogin()
                }
            },
            onOpenBathroom = { if (isLoggedIn) openUtility("bathroom") else onLogin() },
            onOpenAc = { if (isLoggedIn) openUtility("ac") else onLogin() },
            onOpenLighting = { if (isLoggedIn) openUtility("lighting") else onLogin() },
            onOpenInternet = { if (isLoggedIn) openUtility("internet") else onLogin() },
            onOpenMyInfoHub = { showMyInfoHub = true },
            themeMode = themeMode,
            onOpenSettings = { showSettings = true },
            onOpenXzxx = { showXzxx = true },
            onOpenAbout = { showAbout = true },
            isLoggedIn = isLoggedIn,
            onLogin = onLogin,
            onLogout = onLogout
        )
    }

    // 全屏支付码弹窗 — 放在 ProfileScreen 顶层,确保能访问 cardUiState/cardViewModel/showFullQrCode
    if (showFullQrCode) {
        QrCodeFullScreenDialog(
            qrCode = cardUiState.qrCode,
            balance = cardUiState.qrBalance,
            isLoading = cardUiState.qrLoading,
            countdownSeconds = cardUiState.qrCountdownSeconds,
            totalCountdownSeconds = 45,
            qrError = cardUiState.qrError,
            isStale = cardUiState.qrStale,
            ageSeconds = cardUiState.qrAgeSeconds,
            brightnessBoost = cardViewModel.getQrBrightnessBoost(),
            onDismiss = { showFullQrCode = false },
            onRefresh = { cardViewModel.loadCampusQrCode() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHomeScreen(
    listState: LazyListState,
    studentName: String?,
    department: String?,
    className: String?,
    hasStudentInfo: Boolean,
    financeItemCount: Int,
    balance: Double,
    balanceLoading: Boolean,
    balanceError: String?,
    timestamp: Long,
    qrCode: AdwmhQrCode?,
    qrBalance: Double?,
    qrLoading: Boolean,
    qrError: String?,
    qrCountdownSeconds: Int,
    onQrClick: () -> Unit,
    identityCount: Int,
    hasMarketIdentity: Boolean,
    // 第三方服务 (parent 总开关 + 集市/学习通/WeLearn 子开关),v1.3.6 引入
    thirdPartyEnabled: Boolean,
    marketChildEnabled: Boolean,
    chaoxingChildEnabled: Boolean,
    welearnChildEnabled: Boolean,
    bottomNavServices: List<String>,
    onThirdPartyEnabledChanged: (Boolean) -> Unit,
    onMarketChildEnabledChanged: (Boolean) -> Unit,
    onChaoxingChildEnabledChanged: (Boolean) -> Unit,
    onWelearnChildEnabledChanged: (Boolean) -> Unit,
    // 浴室余额
    bathroomData: com.ahu_plus.data.model.BathroomBalanceData?,
    bathroomLoading: Boolean,
    bathroomError: String?,
    bathroomPhone: String,
    onSaveBathroomPhone: (String) -> Unit,
    onRetryBathroom: () -> Unit,
    // 网费
    internetData: InternetBalanceData?,
    internetLoading: Boolean,
    internetError: String?,
    onRetryInternet: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBills: () -> Unit,
    /** 水电费总览（4 张卡片列表）— 2026-06-23 修复：原来点击会直接落到浴室子页 */
    onOpenUtilityOverview: () -> Unit,
    onOpenBathroom: () -> Unit,
    onOpenAc: () -> Unit,
    onOpenLighting: () -> Unit,
    onOpenInternet: () -> Unit,
    onOpenMyInfoHub: () -> Unit,
    themeMode: AppThemeMode,
    onOpenSettings: () -> Unit,
    onOpenXzxx: () -> Unit,
    onOpenAbout: () -> Unit,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val displayName = if (isLoggedIn) {
        studentName?.takeIf { it.isNotBlank() } ?: "未命名同学"
    } else {
        "未登录"
    }
    val subtitle = if (isLoggedIn) {
        listOfNotNull(
            department?.takeIf { it.isNotBlank() },
            className?.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { "学生信息加载中" }
    } else {
        "登录后查看校园账户与个人数据"
    }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    // 第三方服务 parent 启用前的 5s 风险声明弹窗 (子开关不需要二次确认)
    var showThirdPartyDialog by rememberSaveable { mutableStateOf(false) }
    var unpinnedServiceName by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeveloperContact by rememberSaveable { mutableStateOf(false) }
    var showShareSheet by rememberSaveable { mutableStateOf(false) }
    var showQrCard by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    if (showThirdPartyDialog) {
        ThirdPartyEnableDialog(
            onConfirm = {
                onThirdPartyEnabledChanged(true)
                showThirdPartyDialog = false
            },
            onDismiss = { showThirdPartyDialog = false }
        )
    }

    unpinnedServiceName?.let { serviceName ->
        AlertDialog(
            onDismissRequest = { unpinnedServiceName = null },
            title = { Text("$serviceName 已启用") },
            text = {
                Text("底部栏最多固定两个第三方服务，因此该服务会显示在“应用”页。可在“设置 > 底部栏服务”中调整固定项。")
            },
            confirmButton = {
                TextButton(onClick = { unpinnedServiceName = null }) { Text("知道了") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        unpinnedServiceName = null
                        onOpenSettings()
                    }
                ) { Text("去设置") }
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("确认退出登录？") },
            text = { Text("将清除本地凭据与账户数据。退出后仍可使用无需认证的功能。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确认退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeveloperContact) {
        DeveloperContactDialog(onDismiss = { showDeveloperContact = false })
    }

    if (showShareSheet) {
        ShareSheet(
            onDismiss = { showShareSheet = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                actions = {
                    if (isLoggedIn) {
                        IconButton(onClick = { showLogoutConfirm = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "退出登录",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = onLogin) {
                            Icon(
                                Icons.AutoMirrored.Filled.Login,
                                contentDescription = "登录",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(balanceLoading) {
            if (!balanceLoading) isRefreshing = false
        }
        AhuPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                onRefresh()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ProfileHeader(
                    displayName = displayName,
                    subtitle = subtitle,
                    onClick = if (isLoggedIn) onOpenMyInfoHub else onLogin,
                )
            }

            if (!isLoggedIn) {
                item {
                    LoginRequiredCard(onLogin = onLogin)
                }
            }

            item {
                AhuSectionHeader(
                    title = "校园卡",
                    subtitle = "余额、账单与支付码"
                )
            }

            item {
                if (showQrCard) {
                    ProfileQrCard(
                        qrCode = qrCode,
                        qrBalance = qrBalance,
                        qrLoading = qrLoading,
                        qrError = qrError,
                        qrCountdownSeconds = qrCountdownSeconds,
                        onBack = { showQrCard = false },
                        onQrClick = onQrClick,
                        onRefresh = onRefresh
                    )
                } else {
                    BalanceCard(
                        balance = balance,
                        qrBalance = qrBalance,
                        isLoading = balanceLoading,
                        error = balanceError,
                        timestamp = timestamp,
                        qrAuthUrl = "",
                        qrCode = qrCode,
                        onQrClick = { showQrCard = true },
                        onClick = onOpenBills
                    )
                }
            }

            item {
                AhuSectionHeader(
                    title = "校园服务",
                    subtitle = "生活保障服务"
                )
            }

            item {
                ProfileSection {
                    SettingsRow(
                        title = "水电费查询",
                        description = "浴室、空调、照明、网费余额",
                        iconColor = Color(0xFF2F80ED),
                        icon = { Icon(Icons.Filled.WaterDrop, contentDescription = null) },
                        // 2026-06-23 修复：原 onClick=onOpenBathroom 会直接落到浴室子页，
                        // 现改为打开总览页（4 张卡片列表），用户可从总览进入具体子项
                        onClick = onOpenUtilityOverview
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "校长信箱",
                        description = "向校长反映问题、提交建议与诉求",
                        iconColor = Color(0xFF6C63FF),
                        icon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        onClick = onOpenXzxx
                    )
                }
            }

            // ── 第三方服务 section (v1.3.6) ─────────────────
            // 收纳式展开卡片:点击头部展开,内部包含 parent 总开关 + 子开关
            item {
                AhuSectionHeader(
                    title = "第三方服务",
                    subtitle = "非安大官方平台,启用前需确认风险声明"
                )
            }

            item {
                var expandedThirdParty by rememberSaveable { mutableStateOf(false) }
                ProfileSection {
                    Column {
                        // 头部:可点击展开/收起
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedThirdParty = !expandedThirdParty }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(AhuShapes.IconBox)
                                    .background(Color(0xFF9B59B6).copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Group,
                                    contentDescription = null,
                                    tint = Color(0xFF9B59B6)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    text = "第三方服务",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "非安大官方平台,启用前需确认风险声明",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (expandedThirdParty) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (expandedThirdParty) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 展开内容
                        AnimatedVisibility(
                            visible = expandedThirdParty,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                // parent: 是否开启
                                SettingsSwitchRow(
                                    title = "是否开启",
                                    description = if (thirdPartyEnabled) {
                                        "已开启：前两个启用项自动固定到底部栏，其余显示在应用页"
                                    } else {
                                        "默认关闭，启用需阅读并确认风险声明"
                                    },
                                    checked = thirdPartyEnabled,
                                    onCheckedChange = { wantEnable ->
                                        if (wantEnable) {
                                            showThirdPartyDialog = true
                                        } else {
                                            onThirdPartyEnabledChanged(false)
                                        }
                                    }
                                )
                                // 子开关: 仅在 parent 开启后可见
                                if (thirdPartyEnabled) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    SettingsSwitchRow(
                                    title = "校园集市",
                                    description = if (marketChildEnabled) {
                                            if (BottomNavService.MARKET in bottomNavServices) {
                                                "已固定在底部栏"
                                            } else {
                                                "已启用，可从应用页进入"
                                            }
                                        } else {
                                            "已关闭，本地 token/设置保留"
                                        },
                                    checked = marketChildEnabled,
                                    onCheckedChange = { enabled ->
                                        onMarketChildEnabledChanged(enabled)
                                        if (enabled && BottomNavService.MARKET !in bottomNavServices && bottomNavServices.size >= 2) {
                                            unpinnedServiceName = "校园集市"
                                        }
                                    }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    SettingsSwitchRow(
                                    title = "超星学习通",
                                    description = if (chaoxingChildEnabled) {
                                            if (BottomNavService.CHAOXING in bottomNavServices) {
                                                "已固定在底部栏"
                                            } else {
                                                "已启用，可从应用页进入"
                                            }
                                        } else {
                                            "已关闭，本地登录态保留"
                                        },
                                    checked = chaoxingChildEnabled,
                                    onCheckedChange = { enabled ->
                                        onChaoxingChildEnabledChanged(enabled)
                                        if (enabled && BottomNavService.CHAOXING !in bottomNavServices && bottomNavServices.size >= 2) {
                                            unpinnedServiceName = "超星学习通"
                                        }
                                    }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    SettingsSwitchRow(
                                    title = "WeLearn 随行课堂",
                                    description = if (welearnChildEnabled) {
                                            if (BottomNavService.WELEARN in bottomNavServices) {
                                                "已固定在底部栏"
                                            } else {
                                                "已启用，可从应用页进入"
                                            }
                                        } else {
                                            "已关闭，本地登录态保留"
                                        },
                                    checked = welearnChildEnabled,
                                    onCheckedChange = { enabled ->
                                        onWelearnChildEnabledChanged(enabled)
                                        if (enabled && BottomNavService.WELEARN !in bottomNavServices && bottomNavServices.size >= 2) {
                                            unpinnedServiceName = "WeLearn 随行课堂"
                                        }
                                    }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                AhuSectionHeader(
                    title = "其他",
                    subtitle = "设置、关于与推荐"
                )
            }

            item {
                ProfileSection {
                    SettingsRow(
                        title = "设置",
                        description = "外观：${themeMode.titleText()}、功能设置",
                        iconColor = Color(0xFF6C63FF),
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = onOpenSettings
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "关于",
                        description = "软件信息、公告、帮助与协议",
                        iconColor = Color(0xFF607D8B),
                        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        onClick = onOpenAbout
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "推荐",
                        description = "分享下载链接或安装包给好友",
                        iconColor = Color(0xFF2F80ED),
                        icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { showShareSheet = true }
                    )
                }
            }

            item {
                Text(
                    text = "联系开发者",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeveloperContact = true }
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF2F80ED)
                )
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
        } // AhuPullToRefreshBox
    }
}

private fun buildMyInfoDescription(
    hasStudentInfo: Boolean,
    financeItemCount: Int
): String {
    val parts = mutableListOf<String>()
    if (hasStudentInfo) parts.add("基本信息")
    if (financeItemCount > 0) parts.add("财务")
    return if (parts.isEmpty()) "学生基本信息、住宿、财务等" else parts.joinToString("、") + "等"
}

@Composable
private fun DeveloperContactDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val devEmail = "2867299793@qq.com"

    fun sendEmail() {
        val ok = com.ahu_plus.util.BrowserOpener.openEmail(
            context = context,
            email = devEmail,
            subject = "[Ahu_Plus 反馈] "
        )
        if (!ok) {
            Toast.makeText(context, "未检测到邮件客户端,请手动发送至 $devEmail", Toast.LENGTH_LONG).show()
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("联系开发者") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ContactMethodRow(
                    title = "发送邮件给开发者",
                    value = devEmail,
                    onClick = { sendEmail() }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun ContactMethodRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileHeader(
    displayName: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp)
            )
        }
        Column(
            modifier = Modifier.padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Double,
    qrBalance: Double? = null,
    isLoading: Boolean,
    error: String?,
    timestamp: Long,
    qrAuthUrl: String,
    qrCode: AdwmhQrCode?,
    onQrClick: () -> Unit,
    onClick: () -> Unit
) {
    val displayBalance = qrBalance ?: balance
    val formatter = DecimalFormat("\u00A5#,##0.00")

    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // \u70B9\u51FB\u4F59\u989D\u533A\u57DF \u2192 \u8DF3\u8D26\u5355\u9875
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(AhuShapes.IconBox)
                        .background(AhuGreen.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = AhuGreen
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "\u6821\u56ED\u5361",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val hasBalance = displayBalance > 0.0
                    val subText = when {
                        hasBalance -> null
                        isLoading -> null
                        error != null -> null
                        else -> updatedText(timestamp)
                    }
                    if (subText != null) {
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                when {
                    isLoading && displayBalance == 0.0 -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    displayBalance > 0.0 -> {
                        Text(
                            text = formatter.format(displayBalance),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // QR \u56FE\u6807\u72EC\u7ACB\u70B9\u51FB\u533A
            IconButton(
                onClick = onQrClick,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    Icons.Filled.QrCode2,
                    contentDescription = "\u667A\u6167\u5B89\u5927\u652F\u4ED8\u7801",
                    tint = if (qrCode != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}



/**
 * 我的页面内嵌的 QR 支付码卡片（与余额卡互为 tab 切换）。
 */
@Composable
private fun ProfileQrCard(
    qrCode: AdwmhQrCode?,
    qrBalance: Double?,
    qrLoading: Boolean,
    qrError: String?,
    qrCountdownSeconds: Int,
    onBack: () -> Unit,
    onQrClick: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部栏：标题 + 返回按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "智慧安大支付码",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onBack) {
                    Text("返回余额")
                }
            }

            when {
                qrCode != null -> {
                    val image = remember(qrCode.payload) {
                        QrCodeBitmap.create(qrCode.payload, 720)
                    }
                    Image(
                        bitmap = image,
                        contentDescription = "支付码 — 点击放大",
                        modifier = Modifier
                            .size(200.dp)
                            .clickable { onQrClick() }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 倒计时
                        Text(
                            text = "${qrCountdownSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (qrBalance != null) {
                            Text(
                                text = DecimalFormat("¥#,##0.00").format(qrBalance),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = qrCode.statusMsg.ifBlank { "已刷新" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                qrLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    Text("加载中...", style = MaterialTheme.typography.bodySmall)
                }
                qrError != null -> {
                    Text(qrError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRefresh) { Text("重试") }
                }
                else -> {
                    Text("点击刷新加载支付码", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRefresh) { Text("加载") }
                }
            }

            // 刷新 + 放大按钮
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onRefresh) { Text("刷新") }
                if (qrCode != null) {
                    TextButton(onClick = onQrClick) { Text("放大") }
                }
            }
        }
    }
}

@Composable
fun ProfileSection(content: @Composable () -> Unit) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    description: String,
    iconColor: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(
                text = description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(AhuShapes.IconBox)
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides iconColor
                ) {
                    icon()
                }
            }
        },
        trailingContent = {
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun updatedText(timestamp: Long): String {
    if (timestamp <= 0) return "点击查看账单明细"
    val text = try {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
    return if (text.isBlank()) "点击查看账单明细" else "更新于 $text · 点击查看账单"
}

private fun StudentInfo.displayName(): String? {
    return firstValueOf("姓名", "学生姓名", "本人姓名", "USER_NAME")
}

private fun StudentInfo.department(): String? {
    return firstValueOf("学院", "院系", "院系名称", "所在院系", "培养单位", "UNIT_NAME")
}

private fun StudentInfo.classOrMajor(): String? {
    val major = firstValueOf("专业", "专业名称", "所在专业")
    val className = firstValueOf("班级", "行政班", "自然班")
    return listOfNotNull(major, className)
        .distinct()
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }
}

// ─── 财务汇总 ──────────────────────────────────────────────
