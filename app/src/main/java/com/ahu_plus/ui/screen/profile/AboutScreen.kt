package com.ahu_plus.ui.screen.profile

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.R
import com.ahu_plus.data.model.CheckResult
import kotlinx.coroutines.launch

/**
 * 关于页面 —— 收纳软件信息、通知公告、使用帮助、常见问题、检查更新、开源协议、内测计划。
 * 内部自包含导航：子页面（常见问题/使用帮助/通知公告/开源协议）返回后回到本页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    guideIntroSeen: Boolean,
    onGuideIntroSeen: () -> Unit,
    betaEnabled: Boolean,
    onBetaEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as AhuPlusApplication

    // 内测计划确认弹窗
    var showBetaPlanDialog by remember { mutableStateOf(false) }

    // 内部子页面导航
    var subPage by remember { mutableStateOf<AboutSubPage>(AboutSubPage.None) }

    if (showBetaPlanDialog) {
        BetaPlanEnableDialog(
            onDecline = {
                showBetaPlanDialog = false
            },
            onConfirm = { onBetaEnabledChange(true) },
            onClose = {
                showBetaPlanDialog = false
            }
        )
    }

    when (subPage) {
        AboutSubPage.Announcements -> {
            BackHandler { subPage = AboutSubPage.None }
            AnnouncementHistoryScreen(onBack = { subPage = AboutSubPage.None })
        }
        AboutSubPage.Guide -> {
            BackHandler { subPage = AboutSubPage.None }
            GuideScreen(
                introSeen = guideIntroSeen,
                onIntroSeen = onGuideIntroSeen,
                onBack = { subPage = AboutSubPage.None }
            )
        }
        AboutSubPage.Faq -> {
            BackHandler { subPage = AboutSubPage.None }
            FaqScreen(onBack = { subPage = AboutSubPage.None })
        }
        AboutSubPage.OpenSourceLicenses -> {
            BackHandler { subPage = AboutSubPage.None }
            OpenSourceLicensesScreen(onBack = { subPage = AboutSubPage.None })
        }
        AboutSubPage.None -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("关于") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // ── 软件基本信息 ──
                    AboutAppHeader()

                    // ── 功能入口 ──
                    ProfileSection {
                        SettingsRow(
                            title = "通知公告",
                            description = "查看开发者发布的历史公告",
                            iconColor = Color(0xFFE67E22),
                            icon = { Icon(Icons.Filled.Campaign, contentDescription = null) },
                            onClick = { subPage = AboutSubPage.Announcements }
                        )
                        HorizontalDivider()
                        SettingsRow(
                            title = "使用帮助",
                            description = "功能说明、操作指引与常见问题",
                            iconColor = Color(0xFF2F80ED),
                            icon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null) },
                            onClick = { subPage = AboutSubPage.Guide }
                        )
                        HorizontalDivider()
                        SettingsRow(
                            title = "常见问题",
                            description = "数据安全、平台接入、性能等 ${faqCategories.sumOf { it.items.size }} 题分类整理",
                            iconColor = Color(0xFF00B894),
                            icon = { Icon(Icons.Filled.QuestionAnswer, contentDescription = null) },
                            onClick = { subPage = AboutSubPage.Faq }
                        )
                        HorizontalDivider()
                        SettingsRow(
                            title = "检查更新",
                            description = "当前版本 ${com.ahu_plus.BuildConfig.VERSION_NAME}",
                            iconColor = Color(0xFF6C63FF),
                            icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = {
                                scope.launch {
                                    Toast.makeText(context, "正在检查更新…", Toast.LENGTH_SHORT).show()
                                    when (app.updateManager.checkManually()) {
                                        CheckResult.UPDATE_AVAILABLE,
                                        CheckResult.FORCE_UPDATE -> Unit
                                        CheckResult.LATEST -> {
                                            Toast.makeText(
                                                context,
                                                "已是最新版本",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                        CheckResult.ERROR -> {
                                            Toast.makeText(
                                                context,
                                                "检查更新失败,请检查网络连接",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                        SettingsRow(
                            title = "开源协议",
                            description = "本应用使用的所有开源项目与 License",
                            iconColor = Color(0xFF607D8B),
                            icon = { Icon(Icons.Filled.Code, contentDescription = null) },
                            onClick = { subPage = AboutSubPage.OpenSourceLicenses }
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "内测计划",
                            description = if (betaEnabled) "已加入,正在接收内测版本" else "加入内测计划,体验未发布功能",
                            checked = betaEnabled,
                            onCheckedChange = { wantOn ->
                                if (wantOn) {
                                    showBetaPlanDialog = true
                                } else {
                                    onBetaEnabledChange(false)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

private sealed class AboutSubPage {
    object None : AboutSubPage()
    object Announcements : AboutSubPage()
    object Guide : AboutSubPage()
    object Faq : AboutSubPage()
    object OpenSourceLicenses : AboutSubPage()
}

@Composable
private fun AboutAppHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "安大加",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "版本 ${com.ahu_plus.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
