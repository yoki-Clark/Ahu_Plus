package com.ahu_plus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ahu_plus.data.legal.LegalGateState
import com.ahu_plus.data.local.AppFontScale
import com.ahu_plus.ui.components.AnnouncementDialog
import com.ahu_plus.ui.components.UpdateDialog
import com.ahu_plus.ui.navigation.AppNavigation
import com.ahu_plus.ui.navigation.NavigationIntentCodec
import com.ahu_plus.ui.navigation.NavigationRequest
import com.ahu_plus.ui.screen.legal.LegalConsentScreen
import com.ahu_plus.ui.theme.AhuPlusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * deep-link extra key:由通知 / widget 等入口传入,
         * AppNavigation 据此决定初始跳转。
         */
        @Deprecated("Use NavigationIntentCodec")
        const val EXTRA_DEEP_LINK = NavigationIntentCodec.LEGACY_EXTRA_DEEP_LINK

        /** 深链到课表页(课程提醒通知点击时使用) */
        const val DEEP_LINK_SCHEDULE = "schedule"

        /** 深链到日程页(日程提醒通知点击时使用) */
        const val DEEP_LINK_AGENDA = "agenda"

        /** 深链到成绩页 */
        const val DEEP_LINK_GRADE = "grade"

        /** 深链到学习通 Tab */
        const val DEEP_LINK_CHAOXING = "chaoxing"

        /** 深链到 WeLearn Tab */
        const val DEEP_LINK_WELEARN = "welearn"

    }

    /**
     * 当前待消费的 deep-link 目标。冷启动时取自启动 intent;App 已在前台时由
     * [onNewIntent] 更新。MainScreen 消费后回调置空,避免重复跳转。
     */
    private var navigationRequest by mutableStateOf<NavigationRequest?>(null)
    private var navigationRequestId by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NavigationIntentCodec.decode(intent)?.let(::publishNavigationRequest)
        setIntent(intent)
        val app = application as AhuPlusApplication
        setContent {
            val themeMode by app.sessionManager.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = app.sessionManager.getThemeMode()
            )
            val accentColor by app.sessionManager.accentColorFlow.collectAsStateWithLifecycle(
                initialValue = app.sessionManager.getAccentColor()
            )
            val fontScale by app.sessionManager.fontScaleFlow.collectAsStateWithLifecycle(
                initialValue = app.sessionManager.getFontScale()
            )
            val systemDarkTheme = isSystemInDarkTheme()
            val updateState by app.updateManager.uiState.collectAsStateWithLifecycle()
            val announcement by app.announcementManager.uiState.collectAsStateWithLifecycle()
            val legalGateState by app.legalConsentRepository.gateState.collectAsStateWithLifecycle(
                initialValue = LegalGateState.Loading
            )

            AhuPlusTheme(
                darkTheme = themeMode.shouldUseDarkTheme(systemDarkTheme),
                accentColor = accentColor,
            ) {
                // 全局字号缩放：复用系统 density，仅覆盖 fontScale（批次一项40）
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = baseDensity.density,
                        fontScale = fontScale.factor,
                    ),
                ) {
                    when (legalGateState) {
                    LegalGateState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    LegalGateState.RequiresConsent -> {
                        LegalConsentScreen(
                            onAccept = {
                                lifecycleScope.launch {
                                    app.legalConsentRepository.acceptCurrent(BuildConfig.VERSION_NAME)
                                }
                            },
                            onDecline = { finishAffinity() },
                        )
                    }

                    is LegalGateState.Accepted -> {
                        // All startup network and background work stays behind the consent gate.
                        LaunchedEffect(Unit) {
                            app.startPostConsentServices()
                            app.updateManager.checkForUpdateWithIgnore()
                            app.announcementManager.checkAnnouncements()
                        }

                        AppNavigation(
                            sessionManager = app.sessionManager,
                            cardRepository = app.cardRepository,
                            casAuthRepository = app.casAuthRepository,
                            jwAuthRepository = app.jwAuthRepository,
                            courseRepository = app.courseRepository,
                            ycardRepository = app.ycardRepository,
                            marketRepository = app.marketRepository,
                            jwcNoticeRepository = app.jwcNoticeRepository,
                            studentInfoRepository = app.studentInfoRepository,
                            courseNoteRepository = app.courseNoteRepository,
                            gradeRepository = app.gradeRepository,
                            examRepository = app.examRepository,
                            financeRepository = app.financeRepository,
                            attendanceRepository = app.attendanceRepository,
                            adwmhCardRepository = app.adwmhCardRepository,
                            themeMode = themeMode,
                            onThemeModeChange = { newThemeMode ->
                                lifecycleScope.launch {
                                    app.sessionManager.saveThemeMode(newThemeMode)
                                }
                            },
                            accentColor = accentColor,
                            onAccentColorChange = { newAccent ->
                                lifecycleScope.launch {
                                    app.sessionManager.saveAccentColor(newAccent)
                                }
                            },
                            fontScale = fontScale,
                            onFontScaleChange = { newScale ->
                                lifecycleScope.launch {
                                    app.sessionManager.saveFontScale(newScale)
                                }
                            },
                            initCoordinator = app.initCoordinator,
                            navigationRequest = navigationRequest,
                            navigationRequestId = navigationRequestId,
                            onNavigationRequestConsumed = { navigationRequest = null },
                            onSessionInitialized = app::restorePersistedRepositoryState,
                            onAccountDataCleared = app::clearAccountScopedRepositoryState,
                        )

                        UpdateDialog(
                            state = updateState,
                            onUpdate = { info, force ->
                                app.updateManager.downloadApk(info, forceUpdate = force)
                            },
                            onCancelDownload = { app.updateManager.cancelDownload() },
                            onRetryInstall = { app.updateManager.retryInstall() },
                            onIgnore = {
                                lifecycleScope.launch { app.updateManager.ignoreCurrent() }
                            },
                            onDismiss = { app.updateManager.dismiss() }
                        )

                        // 开发者公告弹窗。仅当无更新弹窗时显示(更新优先,避免叠加)。
                        if (updateState is com.ahu_plus.data.update.UpdateUiState.Idle) {
                            AnnouncementDialog(
                                announcement = announcement,
                                onDismiss = { dontShowAgain ->
                                    lifecycleScope.launch {
                                        app.announcementManager.dismiss(dontShowAgain)
                                    }
                                },
                                onAction = { url -> app.announcementManager.openAction(url) }
                            )
                        }
                    }
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // App 已在前台/后台栈顶被通知再次拉起(FLAG_ACTIVITY_CLEAR_TOP):
        // 更新当前 intent 并刷新 deepLink,触发 MainScreen 重新跳转。
        setIntent(intent)
        NavigationIntentCodec.decode(intent)?.let(::publishNavigationRequest)
    }

    private fun publishNavigationRequest(request: NavigationRequest) {
        navigationRequestId++
        navigationRequest = request
    }
}
