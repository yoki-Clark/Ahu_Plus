package com.ahu_plus.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ahu_plus.data.local.AppThemeMode
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
import com.ahu_plus.data.repository.InitCoordinator
import com.ahu_plus.data.repository.JwcNoticeRepository
import com.ahu_plus.data.repository.JwAuthRepository
import com.ahu_plus.data.repository.MarketRepository
import com.ahu_plus.data.repository.StudentInfoRepository
import com.ahu_plus.data.repository.YcardRepository
import com.ahu_plus.ui.screen.autologin.AutoLoginState
import com.ahu_plus.ui.screen.autologin.AutoLoginViewModel
import com.ahu_plus.ui.screen.login.LoginScreen
import com.ahu_plus.ui.screen.login.LoginViewModel
import com.ahu_plus.ui.screen.main.MainScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
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
    /** 首次登录初始化协调器 (2026-06-22 新增) */
    initCoordinator: InitCoordinator? = null,
    /** 首次登录初始化消息流 (LoginViewModel emit → MainScreen 订阅 → 底部 Snackbar) */
    initMessageFlow: kotlinx.coroutines.flow.MutableSharedFlow<String>? = null,
    /** 通知/widget deep-link 目标(MainActivity.DEEP_LINK_*),登录态就绪后由 MainScreen 消费 */
    deepLink: String? = null,
    /** MainScreen 完成 deep-link 跳转后回调,清空 deepLink 避免重复触发 */
    onDeepLinkConsumed: () -> Unit = {},
    onSessionInitialized: () -> Unit = {},
    onAccountDataCleared: suspend () -> Unit = {},
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    var startRoute by remember { mutableStateOf<String?>(null) }
    var hasCredentials by remember { mutableStateOf(false) }
    var authRefreshVersion by remember { mutableIntStateOf(0) }

    // 只等待本地 DataStore 恢复。网络认证不再阻塞首屏，主界面显示后再静默进行。
    LaunchedEffect(Unit) {
        sessionManager.init()
        onSessionInitialized()
        hasCredentials = sessionManager.hasCredentials()
        startRoute = "main"
    }

    if (startRoute == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "加载中...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    val clearAllCookies: () -> Unit = {
        casAuthRepository.clearCookies()
        jwAuthRepository.clearCookies()
        ycardRepository.clearCookies()
        adwmhCardRepository.clearCookies()
        attendanceRepository.clearCookies()
    }

    /**
     * 2026-06-23 静默重新登录(后台自动续期):
     *
     * 各业务 ViewModel 触发 `onReauth` 时,先调用此函数尝试通过 CASTGC/凭据
     * 自动恢复会话:
     *  1. CAS 续期 → `casAuthRepository.ensureValidSession()` → 拿到新 CASTGC
     *  2. 清 JW 旧 session/cookie(否则 `authenticate()` 会直接复用旧值,绕过 SSO)
     *  3. 教务续期 → `jwAuthRepository.authenticate()` → 用新 CASTGC 换新 SESSION
     *
     * 返回 true 表示后台续期成功；返回 false 时由用户当前的显式操作决定是否进入登录页。
     */
    suspend fun attemptSilentReauth(): Boolean {
        // Step 1: CAS 续期
        val casResult = try {
            casAuthRepository.ensureValidSession()
        } catch (e: Exception) {
            Log.w("AppNavigation", "attemptSilentReauth: CAS 异常 ${e.message}")
            null
        }
        if (casResult == null || casResult.isFailure) {
            Log.w("AppNavigation", "attemptSilentReauth: CAS 续期失败 ${casResult?.exceptionOrNull()?.message}")
            return false
        }
        return try {
            val jwResult = jwAuthRepository.forceReauthenticate()
            if (!jwResult.isSuccess) {
                Log.w("AppNavigation", "attemptSilentReauth: JW 续期失败 ${jwResult.exceptionOrNull()?.message}")
            }
            jwResult.isSuccess
        } catch (e: Exception) {
            Log.w("AppNavigation", "attemptSilentReauth: JW 异常 ${e.message}")
            false
        }
    }

    NavHost(
        navController = navController,
        startDestination = startRoute ?: return
    ) {
        // ── 手动登录页 ─────────────────────────────────
        composable("login") {
            val viewModel = viewModel {
                LoginViewModel(
                    casAuthRepository = casAuthRepository,
                    ycardRepository = ycardRepository,
                    adwmhCardRepository = adwmhCardRepository,
                    sessionManager = sessionManager,
                    initCoordinator = initCoordinator,
                    initMessageEmitter = initMessageFlow,
                )
            }
            LoginScreen(
                viewModel = viewModel,
                savedUsername = sessionManager.getUsername(),
                savedPassword = sessionManager.getPassword(),
                onNavigateToHome = {
                    hasCredentials = true
                    authRefreshVersion++
                    if (!navController.popBackStack()) {
                        navController.navigate("main") {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        // ── 主页(一卡通 + 课表)────────────────────────
        composable("main") {
            val navigateToLogin: () -> Unit = {
                navController.navigate("login") {
                    launchSingleTop = true
                }
            }

            // 有保存凭据时在后台恢复 CAS/ycard/adwmh 会话。失败只记录状态，不遮挡主界面。
            val silentLoginViewModel = viewModel {
                AutoLoginViewModel(
                    sessionManager,
                    casAuthRepository,
                    ycardRepository,
                    adwmhCardRepository,
                )
            }
            val silentLoginState by silentLoginViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(silentLoginState) {
                when (val state = silentLoginState) {
                    AutoLoginState.Success -> {
                        hasCredentials = true
                        authRefreshVersion++
                        Log.i("AppNavigation", "启动静默登录成功，后台刷新账户数据")
                    }
                    is AutoLoginState.Failed -> {
                        Log.w("AppNavigation", "启动静默登录失败，保留主界面: ${state.message}")
                    }
                    else -> Unit
                }
            }

            MainScreen(
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
                initMessageFlow = initMessageFlow,
                examRepository = examRepository,
                financeRepository = financeRepository,
                attendanceRepository = attendanceRepository,
                adwmhCardRepository = adwmhCardRepository,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                deepLink = deepLink,
                onDeepLinkConsumed = onDeepLinkConsumed,
                hasCredentials = hasCredentials,
                authRefreshVersion = authRefreshVersion,
                onLogin = navigateToLogin,
                onReauth = reauth@{
                    if (!sessionManager.hasCredentials()) {
                        navigateToLogin()
                        return@reauth
                    }
                    coroutineScope.launch {
                        val reauthed = attemptSilentReauth()
                        if (reauthed) {
                            Log.i("AppNavigation", "onReauth: 后台静默续期成功,通知 UI 刷新")
                            authRefreshVersion++
                            initMessageFlow?.emit("会话已恢复，正在刷新数据")
                        } else {
                            Log.w("AppNavigation", "onReauth: 后台续期失败，等待用户重新登录")
                            clearAllCookies()
                            sessionManager.clearSession()
                            sessionManager.clearJwSession()
                            navigateToLogin()
                        }
                    }
                },
                onLogout = {
                    silentLoginViewModel.cancel()
                    coroutineScope.launch {
                        clearAllCookies()
                        sessionManager.clearAuthData()
                        onAccountDataCleared()
                        hasCredentials = false
                        authRefreshVersion = 0
                        // 留在可匿名使用的主界面，并用新的 NavBackStackEntry 清掉旧 ViewModel 数据。
                        navController.navigate("main") {
                            popUpTo("main") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}
