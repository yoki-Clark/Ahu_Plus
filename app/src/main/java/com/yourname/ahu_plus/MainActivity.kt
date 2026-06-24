package com.yourname.ahu_plus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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

        /** 深链到成绩页 */
        const val DEEP_LINK_GRADE = "grade"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AhuPlusApplication
        setContent {
            val themeMode by app.sessionManager.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = app.sessionManager.getThemeMode()
            )
            val systemDarkTheme = isSystemInDarkTheme()
            val updateState by app.updateManager.uiState.collectAsStateWithLifecycle()

            // 启动时检查一次更新,后续状态全由 UpdateManager 通过 StateFlow 推送。
            LaunchedEffect(Unit) {
                app.updateManager.checkForUpdateWithIgnore()
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
            }
        }
    }
}
