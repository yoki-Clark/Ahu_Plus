package com.yourname.ahu_plus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.yourname.ahu_plus.ui.components.AnnouncementDialog
import com.yourname.ahu_plus.ui.components.UpdateDialog
import com.yourname.ahu_plus.ui.navigation.AppNavigation
import com.yourname.ahu_plus.ui.theme.AhuPlusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * deep-link extra key:由通知 / widget 等入口传入,
         * AppNavigation 据此决定初始跳转。
         */
        const val EXTRA_DEEP_LINK = "deep_link"

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
    private var deepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink = intent?.getStringExtra(EXTRA_DEEP_LINK)
        val app = application as AhuPlusApplication
        setContent {
            val themeMode by app.sessionManager.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = app.sessionManager.getThemeMode()
            )
            val systemDarkTheme = isSystemInDarkTheme()
            val updateState by app.updateManager.uiState.collectAsStateWithLifecycle()
            val announcement by app.announcementManager.uiState.collectAsStateWithLifecycle()

            // 启动时检查一次更新,后续状态全由 UpdateManager 通过 StateFlow 推送。
            LaunchedEffect(Unit) {
                app.updateManager.checkForUpdateWithIgnore()
            }
            // 启动时检查开发者公告(零登录,best-effort)。
            LaunchedEffect(Unit) {
                app.announcementManager.checkAnnouncements()
            }

            AhuPlusTheme(
                darkTheme = themeMode.shouldUseDarkTheme(systemDarkTheme)
            ) {
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
                    initCoordinator = app.initCoordinator,
                    initMessageFlow = app.initMessageFlow,
                    deepLink = deepLink,
                    onDeepLinkConsumed = { deepLink = null },
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
                if (updateState is com.yourname.ahu_plus.data.update.UpdateUiState.Idle) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // App 已在前台/后台栈顶被通知再次拉起(FLAG_ACTIVITY_CLEAR_TOP):
        // 更新当前 intent 并刷新 deepLink,触发 MainScreen 重新跳转。
        setIntent(intent)
        intent.getStringExtra(EXTRA_DEEP_LINK)?.let { deepLink = it }
    }
}
