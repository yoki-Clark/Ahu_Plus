package com.yourname.ahu_plus.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.local.AppThemeMode
import com.yourname.ahu_plus.data.local.CourseNoteRepository
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.repository.CardRepository
import com.yourname.ahu_plus.data.repository.CasAuthRepository
import com.yourname.ahu_plus.data.repository.CourseRepository
import com.yourname.ahu_plus.data.repository.JwcNoticeRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.MarketRepository
import com.yourname.ahu_plus.data.repository.StudentInfoRepository
import com.yourname.ahu_plus.data.repository.YcardRepository
import com.yourname.ahu_plus.ui.screen.dashboard.DashboardScreen
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeListScreen
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeListViewModel
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeViewModel
import com.yourname.ahu_plus.ui.screen.home.HomeViewModel
import com.yourname.ahu_plus.ui.screen.market.MarketScreen
import com.yourname.ahu_plus.ui.screen.market.MarketViewModel
import com.yourname.ahu_plus.ui.screen.profile.ProfileScreen
import com.yourname.ahu_plus.ui.screen.profile.StudentInfoViewModel
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleScreen
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleViewModel

private const val TAB_HOME = 0
private const val TAB_MARKET = 1
private const val TAB_PROFILE = 2

private const val HOME_DASHBOARD = 0
private const val HOME_SCHEDULE = 1
private const val HOME_NOTICE_LIST = 2

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
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    /** 仅清除会话并跳转登录(保留凭据/集市token等本地数据) */
    onReauth: () -> Unit,
    /** 完全退出登录(清除所有本地数据) */
    onLogout: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var homePage by rememberSaveable { mutableIntStateOf(HOME_DASHBOARD) }

    // 系统返回键:优先回退子页面 → 回退到首页 Tab → 交由系统处理(退出)
    BackHandler(enabled = homePage != HOME_DASHBOARD || selectedTab != TAB_HOME) {
        when {
            homePage != HOME_DASHBOARD -> homePage = HOME_DASHBOARD
            selectedTab != TAB_HOME -> selectedTab = TAB_HOME
        }
    }

    val cardViewModel = remember {
        HomeViewModel(cardRepository, casAuthRepository, ycardRepository)
    }
    val scheduleViewModel = remember {
        ScheduleViewModel(jwAuthRepository, courseRepository, courseNoteRepository)
    }
    val marketViewModel = remember {
        MarketViewModel(marketRepository)
    }
    val jwcNoticeViewModel = remember {
        JwcNoticeViewModel(jwcNoticeRepository)
    }
    val jwcNoticeListViewModel = remember {
        JwcNoticeListViewModel(jwcNoticeRepository)
    }
    val studentInfoViewModel = remember {
        StudentInfoViewModel(studentInfoRepository, sessionManager)
    }
    val scheduleUiState by scheduleViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
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
                    label = { Text("首页") }
                )
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
                    label = { Text("集市") }
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
                    label = { Text("我的") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                TAB_HOME -> {
                    when (homePage) {
                        HOME_SCHEDULE -> ScheduleScreen(
                            viewModel = scheduleViewModel,
                            onBack = { homePage = HOME_DASHBOARD },
                            onNeedsLogin = onReauth
                        )
                        HOME_NOTICE_LIST -> JwcNoticeListScreen(
                            viewModel = jwcNoticeListViewModel,
                            onBack = { homePage = HOME_DASHBOARD }
                        )
                        else -> DashboardScreen(
                            viewModel = scheduleViewModel,
                            noticeViewModel = jwcNoticeViewModel,
                            onOpenSchedule = { homePage = HOME_SCHEDULE },
                            onOpenCard = { selectedTab = TAB_PROFILE },
                            onOpenNoticeList = { homePage = HOME_NOTICE_LIST },
                            onNeedsLogin = onReauth
                        )
                    }
                }
                TAB_MARKET -> MarketScreen(viewModel = marketViewModel)
                TAB_PROFILE -> ProfileScreen(
                    cardViewModel = cardViewModel,
                    marketViewModel = marketViewModel,
                    studentInfoViewModel = studentInfoViewModel,
                    scheduleUiState = scheduleUiState,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onLogout = onLogout
                )
            }
        }
    }
}
