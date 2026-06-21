package com.yourname.ahu_plus.ui.screen.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.AiCommentModel
import com.yourname.ahu_plus.data.model.AiCommentTemplate
import com.yourname.ahu_plus.data.model.MarketIdentity
import com.yourname.ahu_plus.data.remote.market.MarketApi
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import com.yourname.ahu_plus.ui.components.AhuShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketSettingsScreen(
    uiState: MarketUiState,
    onBack: () -> Unit,
    onIdentityChanged: (String) -> Unit,
    onAddIdentity: () -> Unit,
    onClearIdentities: () -> Unit,
    onRemoveIdentity: (String) -> Unit,
    onToggleIdentitySelection: (String, Boolean) -> Unit,
    onBlockPinnedChanged: (Boolean) -> Unit,
    onKeywordInputChanged: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onToggleFilterNode: (Long) -> Unit,
    onListLayoutModeChange: (String) -> Unit = {},
    onScrollToTopChanged: (Boolean) -> Unit = {},
    // ── AI 评论助手 ──────────────────────────────────
    aiCommentEnabled: Boolean = false,
    aiCommentModel: AiCommentModel = AiCommentModel.FLASH,
    aiOverallPrompt: String = "",
    aiTemplates: List<AiCommentTemplate> = emptyList(),
    aiSelectedTemplateId: String = "",
    aiApiKeyConfigured: Boolean = false,
    onAiCommentEnabledChanged: (Boolean) -> Unit = {},
    onAiCommentModelChanged: (AiCommentModel) -> Unit = {},
    onAiOverallPromptChanged: (String) -> Unit = {},
    onAiTemplateSelected: (String) -> Unit = {},
    onSaveAiTemplate: (String?, String, String) -> Unit = { _, _, _ -> },
    onDeleteAiTemplate: (String) -> Unit = {},
    onResetAiPrompts: () -> Unit = {},
    onSaveAiApiKey: (String) -> Unit = {},
    onClearAiApiKey: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("集市设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }
        var apiKeyInput by rememberSaveable { mutableStateOf("") }
        var editingPromptTarget by rememberSaveable { mutableStateOf<String?>(null) }
        var templateNameInput by rememberSaveable { mutableStateOf("") }
        var promptInput by rememberSaveable { mutableStateOf("") }

        // ── API Key 弹窗 ──────────────────────────────
        if (showApiKeyDialog) {
            AlertDialog(
                onDismissRequest = { showApiKeyDialog = false; apiKeyInput = "" },
                title = { Text("DeepSeek API Key") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("密钥仅加密保存在本机，用于直接请求 DeepSeek，不会发送给集市服务。")
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("API Key") },
                            placeholder = { Text("sk-...") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = apiKeyInput.isNotBlank(),
                        onClick = {
                            onSaveAiApiKey(apiKeyInput)
                            apiKeyInput = ""
                            showApiKeyDialog = false
                        }
                    ) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showApiKeyDialog = false; apiKeyInput = "" }) { Text("取消") }
                }
            )
        }

        // ── 提示词编辑弹窗 ──────────────────────────────
        editingPromptTarget?.let { target ->
            val isOverall = target == "OVERALL"
            val isNewTemplate = target == "NEW_TEMPLATE"
            val template = aiTemplates.firstOrNull { it.id == target }
            AlertDialog(
                onDismissRequest = {
                    editingPromptTarget = null
                    templateNameInput = ""
                    promptInput = ""
                },
                title = {
                    val titleText = when {
                        isOverall -> "编辑整体角色设定"
                        isNewTemplate -> "新建评论模板"
                        else -> "“${template?.name.orEmpty()}”模板"

                    }
                    Text(titleText)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (isOverall) "用于规定整体回复身份、自然程度和写作习惯。安全规则与上下文边界不会被覆盖。"
                            else "只填写这个风格的语气、措辞和表达偏好。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isOverall) {
                            OutlinedTextField(
                                value = templateNameInput,
                                onValueChange = { templateNameInput = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("模板名称") }
                            )
                        }
                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            minLines = 7,
                            maxLines = 14,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("提示词") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = promptInput.isNotBlank() && (isOverall || templateNameInput.isNotBlank()),
                        onClick = {
                            if (isOverall) onAiOverallPromptChanged(promptInput)
                            else onSaveAiTemplate(if (isNewTemplate) null else target, templateNameInput, promptInput)
                            editingPromptTarget = null
                            templateNameInput = ""
                            promptInput = ""
                        }
                    ) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        editingPromptTarget = null
                        templateNameInput = ""
                        promptInput = ""
                    }) { Text("取消") }
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── 1. 身份管理 ──────────────────────────
            item { SettingsSectionTitle("身份管理") }
            item {
                CompactIdentityCard(
                    uiState = uiState,
                    onIdentityChanged = onIdentityChanged,
                    onAddIdentity = onAddIdentity,
                    onRemoveIdentity = {
                        // 移除第一个身份(紧凑卡只显示一个)
                        uiState.identities.firstOrNull()?.let { onRemoveIdentity(it.id) }
                    }
                )
            }

            if (uiState.identities.isNotEmpty()) {
                item {
                    Card(
                        shape = AhuShapes.Card,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "已保存的校区身份（${uiState.identities.size} 个）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.identities.forEachIndexed { index, identity ->
                                if (index > 0) HorizontalDivider()
                                SavedIdentityRow(
                                    identity = identity,
                                    isSelected = identity.id in uiState.selectedIdentityIds,
                                    onToggle = { checked ->
                                        onToggleIdentitySelection(identity.id, checked)
                                    },
                                    onRemove = { onRemoveIdentity(identity.id) }
                                )
                            }
                        }
                    }
                }
            }

            // ── 2. 屏蔽置顶 ──────────────────────────
            item { SettingsSectionTitle("屏蔽置顶") }
            item {
                SettingsToggleCard(
                    title = "屏蔽置顶帖",
                    description = "开启后隐藏置顶/广告帖子",
                    checked = uiState.blockPinned,
                    onCheckedChange = onBlockPinnedChanged
                )
            }

            // ── 2.5 列表显示模式 ────────────────────────────
            item { SettingsSectionTitle("列表显示模式") }
            item {
                ListLayoutModeCard(
                    currentMode = uiState.listLayoutMode,
                    onSelect = onListLayoutModeChange
                )
            }

            // ── 2.6 回到顶部按钮 ────────────────────────────
            item { SettingsSectionTitle("回到顶部") }
            item {
                SettingsToggleCard(
                    title = "显示回到顶部按钮",
                    description = "下滑后左下角出现按钮，点击回到顶部并刷新",
                    checked = uiState.scrollToTopEnabled,
                    onCheckedChange = onScrollToTopChanged
                )
            }

            // ── 3. 屏蔽词 ────────────────────────────
            item { SettingsSectionTitle("屏蔽词") }
            item {
                Card(
                    shape = AhuShapes.Card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "帖子标题或正文包含以下关键词时将被隐藏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = uiState.keywordInput,
                                onValueChange = onKeywordInputChanged,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("输入屏蔽词") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = onAddKeyword) {
                                Text("添加")
                            }
                        }
                        if (uiState.blockKeywords.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.blockKeywords.forEach { kw ->
                                    KeywordChip(
                                        keyword = kw,
                                        onRemove = { onRemoveKeyword(kw) }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "暂无屏蔽词",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── 4. 板块筛选 ──────────────────────────
            item { SettingsSectionTitle("板块筛选") }
            item {
                Card(
                    shape = AhuShapes.Card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "仅显示所选板块的帖子（未选中则显示全部）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MarketApi.DEFAULT_NODES.forEach { node ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleFilterNode(node.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = node.id in uiState.filterNodeIds,
                                    onCheckedChange = { onToggleFilterNode(node.id) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = node.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // ── 5. AI 评论助手 ──────────────────────────
            item { SettingsSectionTitle("AI 评论助手") }
            item {
                SettingsToggleCard(
                    title = "启用 AI 一键写评论",
                    description = "生成内容只填入草稿，不会自动发送",
                    checked = aiCommentEnabled,
                    onCheckedChange = onAiCommentEnabledChanged
                )
            }
            if (aiCommentEnabled) {
                item {
                    Card(
                        shape = AhuShapes.Card,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            ListItem(
                                headlineContent = { Text("DeepSeek API Key", fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    Text(if (aiApiKeyConfigured) "已加密保存在本机" else "未配置，点击填写")
                                },
                                trailingContent = {
                                    Row {
                                        if (aiApiKeyConfigured) {
                                            TextButton(onClick = onClearAiApiKey) { Text("清除") }
                                        }
                                        TextButton(onClick = { showApiKeyDialog = true }) {
                                            Text(if (aiApiKeyConfigured) "更换" else "填写")
                                        }
                                    }
                                }
                            )
                            HorizontalDivider()
                            Text(
                                "模型",
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                            AiCommentModel.entries.forEach { model ->
                                ListItem(
                                    headlineContent = { Text(model.displayName) },
                                    supportingContent = {
                                        Text(if (model == AiCommentModel.FLASH) "响应更快，适合日常评论" else "质量优先，生成可能稍慢")
                                    },
                                    leadingContent = {
                                        RadioButton(
                                            selected = aiCommentModel == model,
                                            onClick = { onAiCommentModelChanged(model) }
                                        )
                                    },
                                    modifier = Modifier.clickable { onAiCommentModelChanged(model) }
                                )
                            }
                            HorizontalDivider()
                            ListItem(
                                headlineContent = { Text("整体角色设定", fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    Text(
                                        aiOverallPrompt,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingContent = {
                                    TextButton(onClick = {
                                        promptInput = aiOverallPrompt
                                        templateNameInput = ""
                                        editingPromptTarget = "OVERALL"
                                    }) { Text("编辑") }
                                }
                            )
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "默认模板与模板提示词",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    templateNameInput = ""
                                    promptInput = ""
                                    editingPromptTarget = "NEW_TEMPLATE"
                                }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("新建")
                                }
                            }
                            aiTemplates.forEach { template ->
                                ListItem(
                                    headlineContent = {
                                        Text(template.name + if (template.builtIn) " · 系统" else "")
                                    },
                                    supportingContent = {
                                        Text(
                                            template.prompt,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingContent = {
                                        RadioButton(
                                            selected = aiSelectedTemplateId == template.id,
                                            onClick = { onAiTemplateSelected(template.id) }
                                        )
                                    },
                                    trailingContent = {
                                        Row {
                                            if (!template.builtIn) {
                                                IconButton(onClick = { onDeleteAiTemplate(template.id) }) {
                                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "删除模板")
                                                }
                                            }
                                            TextButton(onClick = {
                                                templateNameInput = template.name
                                                promptInput = template.prompt
                                                editingPromptTarget = template.id
                                            }) { Text("编辑") }
                                        }
                                    },
                                    modifier = Modifier.clickable { onAiTemplateSelected(template.id) }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onResetAiPrompts) {
                                    Text("恢复默认提示词")
                                }
                            }
                            Text(
                                "整体设定与模板提示词会共同生效；回复目标、评论上下文和安全边界由应用自动补充。",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "隐私提示：生成时，当前帖子、已加载评论和回复目标会发送至 DeepSeek。",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SettingsToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SavedIdentityRow(
    identity: MarketIdentity,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = onToggle)
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = identity.school ?: "未识别校区",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = maskToken(identity.token),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun KeywordChip(keyword: String, onRemove: () -> Unit) {
    Surface(
        shape = AhuShapes.LargeCard,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "删除",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun ListLayoutModeCard(
    currentMode: String,
    onSelect: (String) -> Unit
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "列表显示模式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "单列文字优先 / 小红书双列瀑布流(大图+标题)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentMode == "list",
                    onClick = { onSelect("list") },
                    label = { Text("单列列表") },
                    leadingIcon = {
                        if (currentMode == "list") {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    }
                )
                FilterChip(
                    selected = currentMode == "stagger",
                    onClick = { onSelect("stagger") },
                    label = { Text("双列瀑布") },
                    leadingIcon = {
                        if (currentMode == "stagger") {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    }
                )
            }
        }
    }
}
