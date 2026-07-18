package com.ahu_plus.ui.screen.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahu_plus.data.remote.market.MarketApi
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.MarketColors

// ═══════════════════════════════════════════════════════════
//  头像 / Loading / Status / Footer
// ═══════════════════════════════════════════════════════════


@Composable
internal fun IdentityCard(
    uiState: MarketUiState,
    onIdentityChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showIdentity by rememberSaveable { mutableStateOf(false) }
    var showScanner by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(AhuShapes.Card)
                        .background(MarketColors.IdentityAccentBg.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = MarketColors.IdentityAccent
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "集市身份字段",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.school?.let { "当前学校：$it" } ?: "粘贴 Bearer 字段后加载内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = uiState.identityInput,
                onValueChange = onIdentityChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API 身份字段") },
                placeholder = { Text("Bearer eyJ...") },
                minLines = 2,
                maxLines = 4,
                leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showIdentity = !showIdentity }) {
                        Icon(
                            imageVector = if (showIdentity) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                            contentDescription = if (showIdentity) "隐藏" else "显示"
                        )
                    }
                },
                visualTransformation = if (showIdentity) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            OutlinedButton(
                onClick = { showScanner = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("扫描电脑二维码")
            }

            uiState.identityError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            uiState.saveMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MarketColors.Success
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存")
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = uiState.hasSavedIdentity
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("清除")
                }
            }
        }
    }

    if (showScanner) {
        MarketQrScannerDialog(
            onDismiss = { showScanner = false },
            onDecoded = { value ->
                val identity = MarketApi.parseImportUri(value).getOrNull()?.normalizedToken ?: value
                onIdentityChanged(identity)
                showScanner = false
            },
        )
    }
}

/**
 * 公开 API —— `ProfileScreen` 通过它嵌入"集市身份字段"卡到我的页。
 * 实际渲染委托给共享的 [IdentityCard]。
 */
@Composable
fun MarketIdentityEditor(
    uiState: MarketUiState,
    onIdentityChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    IdentityCard(
        uiState = uiState,
        onIdentityChanged = onIdentityChanged,
        onSave = onSave,
        onClear = onClear,
        modifier = modifier
    )
}

/**
 * 紧凑模式身份卡 —— 简化版"添加 API"入口。
 *
 * 不再显示已保存身份的 token 片段(原有展示给人诡异感),
 * 统一作为「添加 API 身份字段」按钮,点击弹出 [IdentityInputDialog]。
 *
 * @param onAddIdentity 点 "保存" 后的回调 —— 沿用现有 [MarketViewModel.saveIdentity] 逻辑
 */
@Composable
fun CompactIdentityCard(
    uiState: MarketUiState,
    onIdentityChanged: (String) -> Unit,
    onAddIdentity: () -> Unit,
    onRemoveIdentity: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showScanner by rememberSaveable { mutableStateOf(false) }
    val identityCount = uiState.identities.size

    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(AhuShapes.Card)
                    .background(MarketColors.IdentityAccentBg.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "添加 API 身份",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (identityCount > 0) "已保存 $identityCount 个校区身份, 点击继续添加"
                    else "点击添加 Bearer JWT 身份字段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        uiState.identityError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
        uiState.saveMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MarketColors.Success,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
    }

    if (showDialog) {
        IdentityInputDialog(
            initialValue = uiState.identityInput,
            onValueChange = onIdentityChanged,
            onDismiss = { showDialog = false },
            onSave = {
                onAddIdentity()
                showDialog = false
            },
            onScan = {
                showDialog = false
                showScanner = true
            },
        )
    }
    if (showScanner) {
        MarketQrScannerDialog(
            onDismiss = {
                showScanner = false
                showDialog = true
            },
            onDecoded = { value ->
                val identity = MarketApi.parseImportUri(value).getOrNull()?.normalizedToken ?: value
                onIdentityChanged(identity)
                showScanner = false
                showDialog = true
            },
        )
    }
}

@Composable
private fun IdentityInputDialog(
    initialValue: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onScan: () -> Unit,
) {
    var showToken by rememberSaveable { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties()
    ) {
        Card(
            shape = AhuShapes.Card,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "添加 API 身份字段",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "在电脑上运行“集市Token获取工具”生成二维码，\n扫码导入，或从剪贴板粘贴 Bearer JWT",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = initialValue,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Bearer eyJ...") },
                    minLines = 2,
                    maxLines = 4,
                    leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                contentDescription = if (showToken) "隐藏" else "显示"
                            )
                        }
                    },
                    visualTransformation = if (showToken) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                OutlinedButton(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("扫描电脑二维码")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = onSave,
                        enabled = initialValue.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
