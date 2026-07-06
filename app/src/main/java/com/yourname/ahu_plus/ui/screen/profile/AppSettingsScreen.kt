package com.yourname.ahu_plus.ui.screen.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.local.AppThemeMode
import com.yourname.ahu_plus.data.model.CheckResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSettingsScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
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

