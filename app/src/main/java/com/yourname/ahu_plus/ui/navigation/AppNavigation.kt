package com.yourname.ahu_plus.ui.navigation

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import com.yourname.ahu_plus.data.repository.InitCoordinator
import com.yourname.ahu_plus.data.repository.JwcNoticeRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.MarketRepository
import com.yourname.ahu_plus.data.repository.StudentInfoRepository
import com.yourname.ahu_plus.data.repository.YcardRepository
import com.yourname.ahu_plus.ui.screen.autologin.AutoLoginScreen
import com.yourname.ahu_plus.ui.screen.autologin.AutoLoginViewModel
import com.yourname.ahu_plus.ui.screen.login.LoginScreen
import com.yourname.ahu_plus.ui.screen.login.LoginViewModel
import com.yourname.ahu_plus.ui.screen.main.MainScreen
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
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    var startRoute by remember { mutableStateOf<String?>(null) }

    // 启动决策:
    //  - 正在初始化 → 短暂显示 loading
    //  - 有凭据 → 进入 autologin(自动登录 + 失败时显示校徽重试页)
    //  - 无凭据 → 进入 login
    LaunchedEffect(Unit) {
        sessionManager.init()
        startRoute = if (sessionManager.hasCredentials()) "autologin" else "login"
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
     * 返回 true 表示后台续期成功,调用方应让用户手动刷新当前页(emit "会话已恢复"
     * 提示),不要跳转登录;返回 false 表示凭据丢失或网络问题,只能走 `navigateToLogin`。
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
        // Step 2: 强制丢弃 JW 旧会话(否则 authenticate() 会用旧 session 直接返回 success)
        try {
            jwAuthRepository.clearCookies()
            sessionManager.clearJwSession()
        } catch (e: Exception) {
            Log.w("AppNavigation", "attemptSilentReauth: 清 JW cookie 异常 ${e.message}")
        }
        // Step 3: JW 续期 — authenticate() 在没 session 时会走 trySimplifiedSso 用新 CASTGC 换 SESSION
        return try {
            val jwResult = jwAuthRepository.authenticate()
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
        // ── 自动登录 + 重试页 ──────────────────────────
        composable("autologin") {
            val viewModel = viewModel {
                AutoLoginViewModel(sessionManager, casAuthRepository, ycardRepository, adwmhCardRepository)
            }
            AutoLoginScreen(
                viewModel = viewModel,
                onSuccess = {
                    navController.navigate("main") {
                        popUpTo("autologin") { inclusive = true }
                    }
                },
                onNoCredentials = {
                    navController.navigate("login") {
                        popUpTo("autologin") { inclusive = true }
                    }
                }
            )
        }

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
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // ── 主页(一卡通 + 课表)────────────────────────
        composable("main") {
            val navigateToLogin: () -> Unit = {
                navController.navigate("login") {
                    popUpTo("main") { inclusive = true }
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
                onReauth = {
                    coroutineScope.launch {
                        // 2026-06-23 修复：先尝试后台静默续期,只有真正失败才清数据+跳登录
                        val reauthed = attemptSilentReauth()
                        if (reauthed) {
                            Log.i("AppNavigation", "onReauth: 后台静默续期成功,通知 UI 刷新")
                            initMessageFlow?.emit("会话已恢复，请下拉刷新")
                        } else {
                            Log.w("AppNavigation", "onReauth: 后台续期失败,跳登录页")
                            clearAllCookies()
                            sessionManager.clearSession()
                            sessionManager.clearJwSession()
                            navigateToLogin()
                        }
                    }
                },
                onLogout = {
                    coroutineScope.launch {
                        clearAllCookies()
                        sessionManager.clearAuthData()
                        navigateToLogin()
                    }
                }
            )
        }
    }
}
