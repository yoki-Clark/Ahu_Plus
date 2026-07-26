package com.ahu_plus.ui.screen.profile

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Scaffold


import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.ahu_plus.ui.components.AhuPullToRefreshBox
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu_plus.data.repository.CacheCleanupRepository
import com.ahu_plus.data.home.AppHubLayoutConfig
import com.ahu_plus.ui.screen.apps.AppHubSettingsScreen
import com.ahu_plus.data.local.AppThemeMode
import com.ahu_plus.data.model.BillRecord

import com.ahu_plus.data.model.FinanceSummary
import com.ahu_plus.data.model.StudentInfo


import com.ahu_plus.data.model.jw.SemesterInfo
import com.ahu_plus.data.repository.AdwmhQrCode
import com.ahu_plus.ui.navigation.ProfileRoute
import com.ahu_plus.ui.navigation.ProfileTarget
import com.ahu_plus.AhuPlusApplication

import com.ahu_plus.ui.components.AhuSectionHeader
import com.ahu_plus.ui.components.LoginRequiredCard
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.AhuStatusColors

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
import com.ahu_plus.ui.screen.home.AdwmhCaptchaDialog
import com.ahu_plus.ui.screen.home.ElectricityTarget
import com.ahu_plus.ui.screen.home.InternetBalanceCard
import com.ahu_plus.ui.screen.home.QrCodeFullScreenDialog

import com.ahu_plus.ui.screen.market.MarketViewModel
import com.ahu_plus.ui.screen.schedule.ScheduleUiState
import com.ahu_plus.notification.CardBalanceAlertMode
import com.ahu_plus.notification.recentCanteenDailyAverage
import com.ahu_plus.ui.theme.AhuGreen
import com.ahu_plus.util.BrowserOpener
import com.ahu_plus.ui.components.rememberQrCodeImage
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    cardViewModel: HomeViewModel,
    marketViewModel: MarketViewModel,
    studentInfoViewModel: StudentInfoViewModel,
    financeViewModel: FinanceViewModel,
    scheduleUiState: ScheduleUiState,
    academicSemesters: List<SemesterInfo> = emptyList(),
    onLoadAcademicSemesters: () -> Unit = {},
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    scrollTarget: String? = null,
    onScrollTargetConsumed: () -> Unit = {},
    profileTarget: ProfileTarget? = null,
    onNavigateBack: () -> Unit = {},
    /** 浣跨敤甯姪棣栧紑璇存槑寮圭獥鏄惁宸茬湅杩囷紙鎸佷箙鍖栵紝閫€鐧讳笉娓咃級銆?*/
    guideIntroSeen: Boolean = true,
    /** 棣栨灞曠ず甯姪寮圭獥鍚庤惤鐩樻爣璁般€?*/
    onGuideIntroSeen: () -> Unit = {},
    bottomNavServices: List<String> = emptyList(),
    onBottomNavServicesChanged: (List<String>) -> Unit = {},
    appHubLayout: AppHubLayoutConfig = AppHubLayoutConfig(),
    onAppHubLayoutChanged: (AppHubLayoutConfig) -> Unit = {},
    appHubRecentKeys: List<String> = emptyList(),
    appHubUsageCounts: Map<String, Int> = emptyMap(),
    onOpenScheduleSettings: () -> Unit = {},
    onOpenMarketSettings: () -> Unit = {},
    onOpenChaoxingSettings: () -> Unit = {},
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    // 2026-07-06 淇: 鎻愬崌鍒?ProfileScreen 椤跺眰(涓嶅祵濂楀湪 if 閾惧唴閮?銆?
    // 鍘熷洜: SaveableStateHolder 鍐呯殑 inner Composable 鍒囨崲(濡?ProfileHomeScreen 鈫?AppSettingsScreen)
    // 涓嶄細淇濈暀 inner registry 鐨?saved state銆傛彁鍗囧悗 listState 娉ㄥ唽鍒?ProfileScreen 鑷繁鐨?
    // registry,inner Composable 鍒囨崲涓嶅奖鍝嶃€?
    val profileListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var showBills by rememberSaveable { mutableStateOf(false) }
    var showMyInfoHub by rememberSaveable { mutableStateOf(false) }
    var showStudentBasicInfo by rememberSaveable { mutableStateOf(false) }
    var showHousingInfo by rememberSaveable { mutableStateOf(false) }
    var showAcademicWarning by rememberSaveable { mutableStateOf(false) }
    var showFinance by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAppHubSettings by rememberSaveable { mutableStateOf(false) }
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

    // 鍐呮祴璁″垝:ProfileScreen 椤跺眰鎸佹湁,鎸佷箙鍖栧埌 SessionManager
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

    // 浠庡鑸洰鏍囨淳鐢熷瓙椤甸潰璺敱,娑堣垂鍚庡脊鏍堝洖鍒?Profile 鏍?
    LaunchedEffect(profileTarget?.route) {
        val route = profileTarget?.route ?: return@LaunchedEffect
        when (route) {
            ProfileRoute.MY_INFO -> showMyInfoHub = true
            ProfileRoute.FINANCE -> showFinance = true
            ProfileRoute.SETTINGS -> showSettings = true
            ProfileRoute.CARD_ANALYTICS -> showCardAnalytics = true
            ProfileRoute.CACHE_CLEANUP -> showCacheCleanup = true
            else -> return@LaunchedEffect
        }
        onNavigateBack()
    }

    LaunchedEffect(showCardAnalytics) {
        if (showCardAnalytics) onLoadAcademicSemesters()
    }

    if (showCardAnalytics) {
        BackHandler(enabled = true) { showCardAnalytics = false }
        CardAnalyticsScreen(
            bills = cardUiState.bills,
            academicSemesters = academicSemesters,
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
            title = "瀛︾敓鍩烘湰淇℃伅",
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
            title = "浣忓鏁版嵁",
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
            title = "瀛︿笟棰勮淇℃伅",
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
            onOpenAppHubSettings = {
                showSettings = false
                showAppHubSettings = true
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
    } else if (showAppHubSettings) {
        BackHandler(enabled = true) { showAppHubSettings = false; showSettings = true }
        AppHubSettingsScreen(
            config = appHubLayout,
            recentKeys = appHubRecentKeys,
            usageCounts = appHubUsageCounts,
            onConfigChange = onAppHubLayoutChanged,
            onBack = { showAppHubSettings = false; showSettings = true },
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
            onToggleGroup = { /* 鐢?Screen 鍐呴儴缁存姢閫変腑鎬?姝ゅ鏃犻渶涓婃姏 */ },
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
            onQrRefresh = cardViewModel::loadCampusQrCode,
            onQrEnsure = cardViewModel::ensureCampusQrCode,
            onQrClick = {
                if (isLoggedIn) {
                    showFullQrCode = true
                } else {
                    onLogin()
                }
            },
            identityCount = marketUiState.identities.size,
            hasMarketIdentity = marketUiState.hasSavedIdentity,
            // 銆岀涓夋柟鏈嶅姟銆峴ection: parent (5s 寮圭獥) + 闆嗗競/瀛︿範閫?瀛愬紑鍏?
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

    // 鍏ㄥ睆鏀粯鐮佸脊绐?鈥?鏀惧湪 ProfileScreen 椤跺眰,纭繚鑳借闂?cardUiState/cardViewModel/showFullQrCode
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
    if (cardUiState.adwmhLoginDialogVisible) {
        AdwmhCaptchaDialog(
            captchaBytes = cardUiState.adwmhCaptchaBytes,
            captchaLoading = cardUiState.adwmhCaptchaLoading,
            captchaError = cardUiState.adwmhCaptchaError,
            loginLoading = cardUiState.adwmhLoginLoading,
            loginError = cardUiState.adwmhLoginError,
            onRefresh = cardViewModel::refreshAdwmhCaptcha,
            onSubmit = cardViewModel::submitAdwmhCaptcha,
            onDismiss = cardViewModel::dismissAdwmhLogin,
        )
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
