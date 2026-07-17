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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.R
import com.ahu_plus.data.developer.DeveloperTapUnlocker
import com.ahu_plus.data.developer.DeveloperTimePasswordValidator
import com.ahu_plus.data.model.CheckResult
import com.ahu_plus.ui.screen.developer.DeveloperCenterScreen
import kotlinx.coroutines.delay
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
    developerEnabled: Boolean,
    onDeveloperEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as AhuPlusApplication
    val scrollState = rememberScrollState()
    val developerUnlocker = remember { DeveloperTapUnlocker() }
    val passwordValidator = remember { DeveloperTimePasswordValidator() }

    var developerOptionRevealed by rememberSaveable { mutableStateOf(developerEnabled) }
    var showDeveloperPasswordDialog by remember { mutableStateOf(false) }

    // 内测计划确认弹窗
    var showBetaPlanDialog by remember { mutableStateOf(false) }

    // 内部子页面导航
    var subPage by remember { mutableStateOf<AboutSubPage>(AboutSubPage.None) }

    LaunchedEffect(developerEnabled) {
        if (developerEnabled) developerOptionRevealed = true
    }

    LaunchedEffect(developerOptionRevealed, developerEnabled) {
        if (developerOptionRevealed) {
            // Wait for the newly revealed bottom section to participate in measurement.
            delay(80L)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

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

    if (showDeveloperPasswordDialog) {
        DeveloperPasswordDialog(
            validator = passwordValidator,
            onDismiss = { showDeveloperPasswordDialog = false },
            onVerified = {
                showDeveloperPasswordDialog = false
                developerOptionRevealed = true
                onDeveloperEnabledChange(true)
            },
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
        AboutSubPage.DeveloperCenter -> {
            BackHandler { subPage = AboutSubPage.None }
            DeveloperCenterScreen(onBack = { subPage = AboutSubPage.None })
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
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // ── 软件基本信息 ──
                    AboutAppHeader(
                        onVersionClick = {
                            if (!developerEnabled) {
                                val result = developerUnlocker.onTap()
                                when {
                                    result.justUnlocked -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        developerOptionRevealed = true
                                        Toast.makeText(
                                            context,
                                            "开发者选项已显示",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    result.remainingTapCount in 1..3 -> {
                                        Toast.makeText(
                                            context,
                                            "再点击 ${result.remainingTapCount} 次即可显示开发者选项",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        },
                    )

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

                    if (developerOptionRevealed || developerEnabled) {
                        // 开发者入口必须是关于页最后一个功能分组。
                        ProfileSection {
                            SettingsSwitchRow(
                                title = "开发者选项",
                                description = if (developerEnabled) "已开启开发者测试功能" else "开启后进入开发者测试功能",
                                checked = developerEnabled,
                                onCheckedChange = { wantOn ->
                                    if (wantOn) {
                                        showDeveloperPasswordDialog = true
                                    } else {
                                        onDeveloperEnabledChange(false)
                                        developerOptionRevealed = false
                                        developerUnlocker.reset()
                                    }
                                },
                            )
                            if (developerEnabled) {
                                HorizontalDivider()
                                SettingsRow(
                                    title = "开发者中心",
                                    description = "诊断、测试、模拟、日志与数据工具",
                                    iconColor = Color(0xFF455A64),
                                    icon = { Icon(Icons.Filled.DeveloperMode, contentDescription = null) },
                                    onClick = { subPage = AboutSubPage.DeveloperCenter },
                                )
                            }
                        }
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
    object DeveloperCenter : AboutSubPage()
}

@Composable
private fun AboutAppHeader(onVersionClick: () -> Unit) {
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onVersionClick),
        )
    }
}

@Composable
private fun DeveloperPasswordDialog(
    validator: DeveloperTimePasswordValidator,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun verify() {
        if (validator.isValid(password)) {
            onVerified()
        } else {
            isError = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("启用开发者选项") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("请输入密码")
                OutlinedTextField(
                    value = password,
                    onValueChange = { value ->
                        if (value.length <= DeveloperTimePasswordValidator.PASSWORD_LENGTH &&
                            value.all { it in '0'..'9' }
                        ) {
                            password = value
                            isError = false
                        }
                    },
                    label = { Text("密码") },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) ({ Text("密码错误") }) else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::verify) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
