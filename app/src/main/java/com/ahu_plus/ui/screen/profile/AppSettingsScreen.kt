package com.ahu_plus.ui.screen.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.local.AppThemeMode
import com.ahu_plus.data.local.BottomNavService
import androidx.core.content.ContextCompat
import com.ahu_plus.notification.CardBalanceAlertMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSettingsScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    qrBrightnessBoost: Boolean = true,
    onQrBrightnessBoostChanged: (Boolean) -> Unit = {},
    adwmhConcurrentRetry: Boolean = false,
    onAdwmhConcurrentRetryChanged: (Boolean) -> Unit = {},
    cardBalanceAlertEnabled: Boolean = false,
    cardBalanceAlertThreshold: Double = 20.0,
    cardBalanceAlertMode: CardBalanceAlertMode = CardBalanceAlertMode.FIXED,
    cardBalanceAlertLookbackDays: Int = 30,
    recentCanteenDailyAverages: Map<Int, Double> = emptyMap(),
    onCardBalanceAlertEnabledChanged: (Boolean) -> Unit = {},
    onCardBalanceAlertThresholdChanged: (Double) -> Unit = {},
    onCardBalanceAlertModeChanged: (CardBalanceAlertMode) -> Unit = {},
    onCardBalanceAlertLookbackDaysChanged: (Int) -> Unit = {},
    bottomNavServices: List<String> = emptyList(),
    marketEnabled: Boolean = false,
    chaoxingEnabled: Boolean = false,
    welearnEnabled: Boolean = false,
    onBottomNavServicesChanged: (List<String>) -> Unit = {},
    onOpenScheduleSettings: () -> Unit = {},
    onOpenMarketSettings: () -> Unit = {},
    onOpenChaoxingSettings: () -> Unit = {},
    onOpenCacheCleanup: () -> Unit = {},
    onBack: () -> Unit
) {
    // 本地状态确保开关即时响应
    var localQrBrightness by remember { mutableStateOf(qrBrightnessBoost) }
    var localAdwmhRetry by remember { mutableStateOf(adwmhConcurrentRetry) }
    var localCardAlert by remember { mutableStateOf(cardBalanceAlertEnabled) }
    var localCardThreshold by remember { mutableStateOf(formatThresholdInput(cardBalanceAlertThreshold)) }
    var localCardMode by remember { mutableStateOf(cardBalanceAlertMode) }
    var localLookbackDays by remember { mutableStateOf(cardBalanceAlertLookbackDays) }
    val recentCanteenDailyAverage = recentCanteenDailyAverages[localLookbackDays] ?: 0.0
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        localCardAlert = granted
        if (granted) onCardBalanceAlertEnabledChanged(true)
    }
    val setCardAlertEnabled: (Boolean) -> Unit = { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            localCardAlert = enabled
            onCardBalanceAlertEnabledChanged(enabled)
        }
    }

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
                            text = "底部导航",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "首页、应用、我的固定显示；可再固定最多 2 个服务（已选 ${bottomNavServices.size}/2）",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val availableServices = listOfNotNull(
                            if (marketEnabled) BottomNavService.MARKET to "集市" else null,
                            if (chaoxingEnabled) BottomNavService.CHAOXING to "学习通" else null,
                            if (welearnEnabled) BottomNavService.WELEARN to "WeLearn" else null,
                        )
                        availableServices.forEachIndexed { index, (id, title) ->
                            if (index > 0) HorizontalDivider()
                            val selected = id in bottomNavServices
                            val canSelect = selected || bottomNavServices.size < 2
                            ListItem(
                                headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    Text(if (selected) "显示在底栏" else "仍可从应用页进入")
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = selected,
                                        enabled = canSelect,
                                        onCheckedChange = null,
                                    )
                                },
                                modifier = Modifier.toggleable(
                                    value = selected,
                                    enabled = canSelect,
                                    role = Role.Checkbox,
                                    onValueChange = { checked ->
                                        onBottomNavServicesChanged(
                                            if (checked) bottomNavServices + id
                                            else bottomNavServices - id
                                        )
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            item {
                ProfileSection {
                    Column {
                        Text(
                            text = "页面设置",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        SettingsRouteRow(
                            title = "课表设置",
                            description = "列宽、行高、字体和显示内容",
                            icon = Icons.Filled.CalendarMonth,
                            onClick = onOpenScheduleSettings,
                        )
                        if (marketEnabled) {
                            HorizontalDivider()
                            SettingsRouteRow(
                                title = "集市设置",
                                description = "身份、列表布局和内容过滤",
                                icon = Icons.Filled.Storefront,
                                onClick = onOpenMarketSettings,
                            )
                        }
                        if (chaoxingEnabled) {
                            HorizontalDivider()
                            SettingsRouteRow(
                                title = "学习通设置",
                                description = "登录、学习策略和通知方式",
                                icon = Icons.Filled.School,
                                onClick = onOpenChaoxingSettings,
                            )
                        }
                    }
                }
            }
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
                        SettingsSwitchRow(
                            title = "校园卡低余额提醒",
                            description = if (localCardAlert) {
                                "余额刷新后低于阈值时发送通知"
                            } else {
                                "关闭后不发送校园卡余额通知"
                            },
                            checked = localCardAlert,
                            onCheckedChange = setCardAlertEnabled,
                        )
                        if (localCardAlert) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "预警方式",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                AlertModeRow(
                                    title = "固定金额",
                                    description = "余额低于手动设定金额时提醒",
                                    selected = localCardMode == CardBalanceAlertMode.FIXED,
                                    onClick = {
                                        localCardMode = CardBalanceAlertMode.FIXED
                                        onCardBalanceAlertModeChanged(localCardMode)
                                    },
                                )
                                AlertModeRow(
                                    title = "近期食堂日均",
                                    description = if (recentCanteenDailyAverage > 0.0) {
                                        "当前估算 %.2f 元，按有食堂消费的日期计算".format(recentCanteenDailyAverage)
                                    } else {
                                        "暂无可用食堂账单，将使用固定金额"
                                    },
                                    selected = localCardMode == CardBalanceAlertMode.CANTEEN_DAILY_AVERAGE,
                                    onClick = {
                                        localCardMode = CardBalanceAlertMode.CANTEEN_DAILY_AVERAGE
                                        onCardBalanceAlertModeChanged(localCardMode)
                                    },
                                )
                                if (localCardMode == CardBalanceAlertMode.CANTEEN_DAILY_AVERAGE) {
                                    Text("统计范围", style = MaterialTheme.typography.labelMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(7, 14, 30).forEach { days ->
                                            FilterChip(
                                                selected = localLookbackDays == days,
                                                onClick = {
                                                    localLookbackDays = days
                                                    onCardBalanceAlertLookbackDaysChanged(days)
                                                },
                                                label = { Text("$days 天") },
                                            )
                                        }
                                    }
                                }
                                if (localCardMode == CardBalanceAlertMode.FIXED) {
                                    OutlinedTextField(
                                        value = localCardThreshold,
                                        onValueChange = { value ->
                                            val normalized = value.filterIndexed { index, char ->
                                                char.isDigit() || (char == '.' && index > 0 && '.' !in value.take(index))
                                            }.take(7)
                                            localCardThreshold = normalized
                                            normalized.toDoubleOrNull()?.takeIf { it in 1.0..500.0 }?.let {
                                                onCardBalanceAlertThresholdChanged(it)
                                            }
                                        },
                                        label = { Text("固定预警金额") },
                                        suffix = { Text("元") },
                                        supportingText = { Text("可输入 1 至 500 元") },
                                        isError = localCardThreshold.toDoubleOrNull()?.let { it !in 1.0..500.0 } ?: true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                        ListItem(
                            headlineContent = {
                                Text("清理本地缓存", fontWeight = FontWeight.Medium)
                            },
                            supportingContent = {
                                Text(
                                    "释放已缓存的业务数据；登录态、个人设置与超星凭据均保留",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.CleaningServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable { onOpenCacheCleanup() }
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AlertModeRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(description) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

private fun formatThresholdInput(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)

@Composable
private fun SettingsRouteRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
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
            Switch(checked = checked, onCheckedChange = null)
        },
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
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
                onClick = null,
            )
        },
        modifier = Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick,
        ),
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
