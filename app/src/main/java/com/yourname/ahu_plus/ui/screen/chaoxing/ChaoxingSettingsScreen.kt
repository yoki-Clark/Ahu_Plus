package com.yourname.ahu_plus.ui.screen.chaoxing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.AiPlatform
import com.yourname.ahu_plus.ui.components.AhuShapes

/**
 * 学习通设置页(2026-06-20 重做)。
 *
 * [isEmbedded] = true 时只渲染纯内容（供 ChaoxingTabScreen 嵌入 pager）。
 * [isEmbedded] = false 时包一层 Scaffold + TopAppBar（供 ChaoxingMainScreen 独立使用）。
 *
 * 设计改进:
 *   - 登录表单全宽独立 Card，不再挤在一行
 *   - 分组更清晰：账户 → 学习设置 → 答题设置 → 关于
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChaoxingSettingsScreen(
    loginState: CxLoginState,
    settingsState: CxSettingsState,
    viewModel: ChaoxingViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onLogin: (String, String) -> Unit = { _, _ -> },
    isEmbedded: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        SettingsContent(
            loginState = loginState,
            settingsState = settingsState,
            viewModel = viewModel,
            onLogout = onLogout,
            onLogin = onLogin,
        )
    }

    if (isEmbedded) {
        content()
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("设置") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                content()
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  设置内容（纯 Column，可嵌入任何容器）
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsContent(
    loginState: CxLoginState,
    settingsState: CxSettingsState,
    viewModel: ChaoxingViewModel,
    onLogout: () -> Unit,
    onLogin: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // ── 账户管理 ──────────────────────────────────────────
        SectionTitle("账户管理")

        if (loginState.isLoggedIn) {
            LoggedInCard(onLogout = onLogout)
        } else {
            LoginForm(
                isLoading = loginState.isLoading,
                error = loginState.error,
                onLogin = onLogin,
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── 学习设置 ──────────────────────────────────────────
        SectionTitle("学习设置")

        Card(shape = AhuShapes.Card, elevation = CardDefaults.cardElevation(1.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingItem(title = "视频倍速", subtitle = "%.1fx".format(settingsState.speed))
                Slider(
                    value = settingsState.speed,
                    onValueChange = { viewModel.updateSpeed("%.1f".format(it).toFloat()) },
                    valueRange = 1.0f..2.0f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                SettingItem(title = "并发章节数", subtitle = "${settingsState.concurrency} 个")
                Slider(
                    value = settingsState.concurrency.toFloat(),
                    onValueChange = { viewModel.updateConcurrency(it.toInt()) },
                    valueRange = 1f..4f,
                    steps = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                SettingItem(title = "未开放章节", subtitle = null)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("retry" to "重试", "ask" to "询问", "continue" to "跳过").forEach { (v, label) ->
                        FilterChip(
                            selected = settingsState.notOpenAction == v,
                            onClick = { viewModel.updateNotOpenAction(v) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自动签到", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(
                            "老师发起签到时自动完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settingsState.autoSign,
                        onCheckedChange = { viewModel.updateAutoSign(it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── 消息设置 ──────────────────────────────────────────
        SectionTitle("消息设置")

        Card(shape = AhuShapes.Card, elevation = CardDefaults.cardElevation(1.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                SwitchSetting(
                    title = "收件箱聚合",
                    subtitle = "将课程活动通知合并到收件箱，统一查看",
                    checked = settingsState.messagesMergeInbox,
                    onCheckedChange = { viewModel.updateMessagesMergeInbox(it) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── 答题设置 ──────────────────────────────────────────
        SectionTitle("答题设置")

        Card(shape = AhuShapes.Card, elevation = CardDefaults.cardElevation(1.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingItem(title = "答题策略", subtitle = null)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto" to "自动提交", "save" to "仅保存", "skip" to "不答题").forEach { (v, label) ->
                        FilterChip(
                            selected = settingsState.submitMode == v,
                            onClick = { viewModel.updateSubmitMode(v) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                SettingItem(title = "题库来源", subtitle = null)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DISABLED" to "关闭", "CACHE" to "本地缓存", "YANXI" to "言溪", "AI" to "AI").forEach { (v, label) ->
                        FilterChip(
                            selected = settingsState.tikuType == v,
                            onClick = { viewModel.updateTikuType(v) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }

                AnimatedVisibility(visible = settingsState.tikuType == "YANXI") {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = settingsState.tikuToken,
                            onValueChange = { viewModel.updateCxTokensYanxi(it) },
                            label = { Text("言溪 Token") },
                            placeholder = { Text("多个 Token 逗号分隔") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = AhuShapes.Card,
                        )
                    }
                }

                AnimatedVisibility(visible = settingsState.tikuType == "AI") {
                    Column {
                        Spacer(Modifier.height(12.dp))

                        // ── AI 平台选择 ────────────────────────────
                        AiPlatformSelector(
                            selectedPlatform = AiPlatform.fromBaseUrl(settingsState.aiBaseUrl),
                            onPlatformSelected = { platform ->
                                viewModel.updateAiBaseUrl(platform.defaultBaseUrl)
                                if (platform.defaultModels.isNotEmpty()) {
                                    viewModel.updateAiModel(platform.defaultModels.first())
                                }
                            },
                        )

                        Spacer(Modifier.height(8.dp))

                        // ── API Key ────────────────────────────────
                        OutlinedTextField(
                            value = settingsState.aiApiKey,
                            onValueChange = { viewModel.updateAiApiKey(it) },
                            label = { Text("API Key") },
                            placeholder = { Text("sk-...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = AhuShapes.Card,
                        )
                        Spacer(Modifier.height(8.dp))

                        // ── Base URL ────────────────────────────────
                        OutlinedTextField(
                            value = settingsState.aiBaseUrl,
                            onValueChange = { viewModel.updateAiBaseUrl(it) },
                            label = { Text("Base URL") },
                            placeholder = { Text("https://api.deepseek.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = AhuShapes.Card,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        Spacer(Modifier.height(8.dp))

                        // ── 模型选择 ────────────────────────────────
                        val currentPlatform = AiPlatform.fromBaseUrl(settingsState.aiBaseUrl)
                        val models = currentPlatform.defaultModels.ifEmpty {
                            // 自定义平台：显示当前 model 作为唯一选项
                            settingsState.aiModel.takeIf { it.isNotBlank() }?.let { listOf(it) }
                                ?: emptyList()
                        }
                        AiModelSelector(
                            models = models,
                            selectedModel = settingsState.aiModel,
                            onModelSelected = { viewModel.updateAiModel(it) },
                            onCustomModelEntered = { viewModel.updateAiModel(it) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ══════════════════════════════════════════════════════════════
//  已登录卡片
// ══════════════════════════════════════════════════════════════

@Composable
private fun LoggedInCard(onLogout: () -> Unit) {
    Card(shape = AhuShapes.Card, elevation = CardDefaults.cardElevation(1.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "已登录",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "超星账号已绑定",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onLogout,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("退出登录")
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  登录表单（全宽独立 Card）
// ══════════════════════════════════════════════════════════════

@Composable
private fun LoginForm(
    isLoading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Card(shape = AhuShapes.Card, elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "登录超星账号",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.trim() },
                label = { Text("手机号 / 学号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = AhuShapes.Card,
                enabled = !isLoading,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = AhuShapes.Card,
                enabled = !isLoading,
            )

            if (error != null && error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (username.isNotBlank() && password.isNotBlank()) {
                        onLogin(username, password)
                    }
                },
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = AhuShapes.Card,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp).width(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isLoading) "登录中..." else "登录")
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  公共组件
// ══════════════════════════════════════════════════════════════

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SettingItem(title: String, subtitle: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        if (subtitle != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ══════════════════════════════════════════════════════════════
//  AI 平台选择器
// ══════════════════════════════════════════════════════════════

/**
 * AI 平台下拉选择器。
 *
 * 选中平台后通过 [onPlatformSelected] 回调,外部负责更新 baseUrl 和模型。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiPlatformSelector(
    selectedPlatform: AiPlatform,
    onPlatformSelected: (AiPlatform) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedPlatform.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("AI 平台") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = AhuShapes.Card,
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AiPlatform.entries.forEach { platform ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(platform.displayName, style = MaterialTheme.typography.bodyMedium)
                            if (platform.defaultBaseUrl.isNotBlank()) {
                                Text(
                                    platform.defaultBaseUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        onPlatformSelected(platform)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * AI 模型选择器。
 *
 * 固定模型列表(下拉选择)+底部的自定义输入框(可手填任意模型名)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiModelSelector(
    models: List<String>,
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    onCustomModelEntered: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(models.none { it == selectedModel } && selectedModel.isNotBlank()) }

    Column {
        // 下拉选择已有的模型
        ExposedDropdownMenuBox(
            expanded = expanded && !showCustom,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = if (showCustom) "$selectedModel (自定义)" else selectedModel,
                onValueChange = {},
                readOnly = true,
                label = { Text("模型") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = AhuShapes.Card,
                singleLine = true,
            )
            if (models.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                showCustom = false
                                onModelSelected(model)
                                expanded = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                "输入自定义模型...",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = {
                            showCustom = true
                            expanded = false
                        },
                    )
                }
            }
        }

        // 自定义模型输入（用户想输入不在列表中的模型时）
        if (showCustom || models.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = if (showCustom) selectedModel else "",
                onValueChange = { onCustomModelEntered(it) },
                label = { Text(if (models.isEmpty()) "自定义模型" else "自定义模型 (覆盖下拉)") },
                placeholder = {
                    Text(
                        if (models.isEmpty()) "输入模型名称" else selectedModel.ifBlank { "输入其他模型名称" },
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = AhuShapes.Card,
            )
        }
    }
}
