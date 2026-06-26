package com.yourname.ahu_plus.ui.screen.profile

import android.content.ClipData
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.yourname.ahu_plus.data.local.AppThemeMode
import com.yourname.ahu_plus.data.model.BillRecord
import com.yourname.ahu_plus.data.model.CheckResult
import com.yourname.ahu_plus.data.model.FinanceSummary
import com.yourname.ahu_plus.data.model.StudentInfo
import com.yourname.ahu_plus.data.model.StudentInfoCodeLookup
import com.yourname.ahu_plus.data.model.StudentInfoField
import com.yourname.ahu_plus.data.repository.AdwmhQrCode
import com.yourname.ahu_plus.ui.components.AhuInfoRow
import com.yourname.ahu_plus.ui.components.AhuSectionHeader
import com.yourname.ahu_plus.ui.theme.AhuShapes
import com.yourname.ahu_plus.ui.components.AhuStatusCard
import com.yourname.ahu_plus.data.local.ElectricityRoomConfig
import com.yourname.ahu_plus.data.model.ElectricityDailyRecord
import com.yourname.ahu_plus.data.model.ElectricityUiData
import com.yourname.ahu_plus.ui.screen.home.BathroomBalanceCard
import com.yourname.ahu_plus.data.model.InternetBalanceData
import com.yourname.ahu_plus.data.model.InternetBillRecord
import com.yourname.ahu_plus.ui.screen.home.ElectricityBalanceCard
import com.yourname.ahu_plus.ui.screen.home.ElectricityBillRange
import com.yourname.ahu_plus.ui.screen.home.ElectricityState
import com.yourname.ahu_plus.ui.screen.home.HomeViewModel
import com.yourname.ahu_plus.ui.screen.home.ElectricityTarget
import com.yourname.ahu_plus.ui.screen.home.InternetBalanceCard
import com.yourname.ahu_plus.ui.screen.home.QrCodeFullScreenDialog
import com.yourname.ahu_plus.ui.screen.market.MarketIdentityEditor
import com.yourname.ahu_plus.ui.screen.market.MarketSettingsScreen
import com.yourname.ahu_plus.ui.screen.market.MarketViewModel
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleUiState
import com.yourname.ahu_plus.ui.theme.AhuGreen
import com.yourname.ahu_plus.util.BrowserOpener
import com.yourname.ahu_plus.util.QrCodeBitmap
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSettingsScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    showCompletedTasks: Boolean = false,
    showCompletedExams: Boolean = false,
    onShowCompletedTasksChanged: (Boolean) -> Unit = {},
    onShowCompletedExamsChanged: (Boolean) -> Unit = {},
    qrBrightnessBoost: Boolean = true,
    onQrBrightnessBoostChanged: (Boolean) -> Unit = {},
    adwmhConcurrentRetry: Boolean = false,
    onAdwmhConcurrentRetryChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as com.yourname.ahu_plus.AhuPlusApplication

    // 本地状态确保开关即时响应
    var localQrBrightness by remember { mutableStateOf(qrBrightnessBoost) }
    var localAdwmhRetry by remember { mutableStateOf(adwmhConcurrentRetry) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ProfileSection {
                    Column {
                        Text(
                            text = "外观",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AppThemeMode.entries.forEachIndexed { index, option ->
                            if (index > 0) HorizontalDivider()
                            ThemeModeRow(
                                themeMode = option,
                                selected = themeMode == option,
                                onClick = { onThemeModeChange(option) }
                            )
                        }
                    }
                }
            }
            item {
                ProfileSection {
                    Column {
                        Text(
                            text = "功能",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        SettingsSwitchRow(
                            title = "显示已完成考试",
                            description = if (showCompletedExams) {
                                "近期任务中展示已结束的考试"
                            } else {
                                "近期任务中隐藏已考完的考试"
                            },
                            checked = showCompletedExams,
                            onCheckedChange = onShowCompletedExamsChanged,
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "显示已完成任务",
                            description = if (showCompletedTasks) {
                                "近期任务中展示已划线的待办与作业"
                            } else {
                                "近期任务中隐藏已完成的待办与作业"
                            },
                            checked = showCompletedTasks,
                            onCheckedChange = onShowCompletedTasksChanged,
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "支付码调高亮度",
                            description = if (localQrBrightness) {
                                "打开支付码时自动将屏幕亮度调至最高"
                            } else {
                                "打开支付码时不改变屏幕亮度"
                            },
                            checked = localQrBrightness,
                            onCheckedChange = {
                                localQrBrightness = it
                                onQrBrightnessBoostChanged(it)
                            },
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "智慧安大并发重试",
                            description = if (localAdwmhRetry) {
                                "登录超时时同时发起多个请求，任意一个成功即返回"
                            } else {
                                "登录失败时按顺序逐一重试"
                            },
                            checked = localAdwmhRetry,
                            onCheckedChange = {
                                localAdwmhRetry = it
                                onAdwmhConcurrentRetryChanged(it)
                            },
                        )
                        HorizontalDivider()
                        SettingsRow(
                            title = "检查更新",
                            description = "当前版本 ${com.yourname.ahu_plus.BuildConfig.VERSION_NAME}",
                            iconColor = Color(0xFF6C63FF),
                            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = {
                                scope.launch {
                                    Toast.makeText(context, "正在检查更新…", Toast.LENGTH_SHORT).show()
                                    when (app.updateManager.checkManually()) {
                                        CheckResult.UPDATE_AVAILABLE,
                                        CheckResult.FORCE_UPDATE -> Unit
                                        CheckResult.LATEST -> {
                                            Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                                        }
                                        CheckResult.ERROR -> {
                                            Toast.makeText(context, "检查更新失败,请检查网络连接", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
private fun ThemeModeRow(
    themeMode: AppThemeMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = themeMode.titleText(),
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(text = themeMode.descriptionText())
        },
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

internal fun AppThemeMode.titleText(): String = when (this) {
    AppThemeMode.DAY -> "白天"
    AppThemeMode.DARK -> "深色"
    AppThemeMode.SYSTEM -> "跟随系统"
}

private fun AppThemeMode.descriptionText(): String = when (this) {
    AppThemeMode.DAY -> "始终使用浅色界面"
    AppThemeMode.DARK -> "始终使用深色界面"
    AppThemeMode.SYSTEM -> "根据系统深色模式自动切换"
}

// ─── 我的信息二级入口 (Hub) ──────────────────────────────

