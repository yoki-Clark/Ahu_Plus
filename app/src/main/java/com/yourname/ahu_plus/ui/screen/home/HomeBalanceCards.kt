package com.yourname.ahu_plus.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.FeeItemOption
import com.yourname.ahu_plus.data.model.InternetBalanceData
import com.yourname.ahu_plus.ui.theme.AhuShapes
import java.text.DecimalFormat

/**
 * 兼容旧入口的校园卡页面。
 *
 * 新版主流程已迁移到「我的」页,这里保留为独立页面以便后续复用或调试。
 */

@Composable
fun BathroomBalanceCard(
    data: com.yourname.ahu_plus.data.model.BathroomBalanceData?,
    isLoading: Boolean,
    error: String?,
    phone: String,
    onSavePhone: (String) -> Unit,
    onRetry: () -> Unit
) {
    var showPhoneDialog by remember { mutableStateOf(false) }

    if (showPhoneDialog) {
        PhoneInputDialog(
            currentPhone = phone,
            onDismiss = { showPhoneDialog = false },
            onConfirm = { newPhone ->
                onSavePhone(newPhone)
                showPhoneDialog = false
            }
        )
    }

    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                phone.isBlank() -> {
                    // 未设置手机号
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "浴室余额",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击设置手机号以查询",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showPhoneDialog = true }) {
                            Text("设置手机号")
                        }
                    }
                }
                isLoading && data == null -> {
                    CircularProgressIndicator()
                }
                error != null && data == null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Row {
                            TextButton(onClick = { showPhoneDialog = true }) {
                                Text("修改手机号")
                            }
                            TextButton(onClick = onRetry) {
                                Text("重试")
                            }
                        }
                    }
                }
                data != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "浴室余额",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DecimalFormat("¥#,##0.00").format(data.cashYuan),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (data.bonusYuan > 0) {
                            Text(
                                text = "赠送 ${DecimalFormat("¥#,##0.00").format(data.bonusYuan)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "手机 ${data.telPhone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showPhoneDialog = true },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("修改", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (isLoading) {
                            Spacer(modifier = Modifier.height(4.dp))
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneInputDialog(
    currentPhone: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember { mutableStateOf(currentPhone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置浴室手机号") },
        text = {
            Column {
                Text(
                    text = "请输入在浴室水控系统中绑定的手机号（11位）",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 11) input = it },
                    label = { Text("手机号") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input.trim()) },
                enabled = input.trim().length == 11
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ─── 电费卡片 (空调/照明/新区通用) ──────────────────────────────

@Composable
fun ElectricityBalanceCard(
    label: String,
    state: ElectricityState,
    target: ElectricityTarget,
    onLoadBuildings: () -> Unit,
    onSelectBuilding: (FeeItemOption) -> Unit,
    onSelectFloor: (FeeItemOption) -> Unit,
    onSelectRoom: (FeeItemOption) -> Unit,
    onRetry: () -> Unit
) {
    var showConfigDialog by remember { mutableStateOf(false) }

    if (showConfigDialog) {
        RoomConfigCascadeDialog(
            title = "设置${label}房间",
            state = state,
            onLoadBuildings = onLoadBuildings,
            onSelectBuilding = onSelectBuilding,
            onSelectFloor = onSelectFloor,
            onSelectRoom = onSelectRoom,
            onDismiss = { showConfigDialog = false }
        )
    }

    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                !state.config.isComplete -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击设置房间信息以查询",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showConfigDialog = true }) {
                            Text("设置房间")
                        }
                    }
                }
                state.loading && state.data == null -> {
                    CircularProgressIndicator()
                }
                state.error != null && state.data == null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error)
                        Row {
                            TextButton(onClick = { showConfigDialog = true }) {
                                Text("修改房间")
                            }
                            TextButton(onClick = onRetry) {
                                Text("重试")
                            }
                        }
                    }
                }
                state.data != null -> {
                    val kwh = state.data.remainingKwh
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.data.buildingName.ifBlank { label },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (kwh != null) {
                            Text(
                                text = "${DecimalFormat("#,##0.00").format(kwh)} 度",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        } else {
                            Text(
                                text = state.data.infoText.ifBlank { "未知" },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${state.data.roomName} · ${state.data.floorName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showConfigDialog = true },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("修改", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (state.loading) {
                            Spacer(modifier = Modifier.height(4.dp))
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomConfigCascadeDialog(
    title: String,
    state: ElectricityState,
    onLoadBuildings: () -> Unit,
    onSelectBuilding: (FeeItemOption) -> Unit,
    onSelectFloor: (FeeItemOption) -> Unit,
    onSelectRoom: (FeeItemOption) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (state.cascade.buildings.isEmpty() && !state.cascade.loadingBuildings) {
            onLoadBuildings()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                state.cascade.dormHint?.let { hint ->
                    Text(
                        "宿舍: ${hint.buildingName} ${hint.roomNumber} (自动匹配中)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    "依次选择楼栋 → 楼层 → 房间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                CascadeDropdown(
                    label = "楼栋",
                    options = state.cascade.buildings,
                    selected = state.cascade.selectedBuilding,
                    isLoading = state.cascade.loadingBuildings,
                    error = state.cascade.buildingsError,
                    enabled = !state.cascade.loadingBuildings,
                    onSelect = onSelectBuilding
                )
                Spacer(modifier = Modifier.height(8.dp))
                CascadeDropdown(
                    label = "楼层",
                    options = state.cascade.floors,
                    selected = state.cascade.selectedFloor,
                    isLoading = state.cascade.loadingFloors,
                    error = state.cascade.floorsError,
                    enabled = state.cascade.selectedBuilding != null && !state.cascade.loadingFloors,
                    onSelect = onSelectFloor
                )
                Spacer(modifier = Modifier.height(8.dp))
                CascadeDropdown(
                    label = "房间",
                    options = state.cascade.rooms,
                    selected = state.cascade.selectedRoom,
                    isLoading = state.cascade.loadingRooms,
                    error = state.cascade.roomsError,
                    enabled = state.cascade.selectedFloor != null && !state.cascade.loadingRooms,
                    onSelect = onSelectRoom
                )

            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    state.cascade.selectedRoom?.let { onSelectRoom(it) }
                    onDismiss()
                },
                enabled = state.cascade.selectedBuilding != null &&
                    state.cascade.selectedFloor != null &&
                    state.cascade.selectedRoom != null
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 单个级联下拉框。空状态显示提示文案,加载中显示 spinner,
 * 出错显示红色错误 + 提示用户检查网络。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CascadeDropdown(
    label: String,
    options: List<FeeItemOption>,
    selected: FeeItemOption?,
    isLoading: Boolean,
    error: String?,
    enabled: Boolean,
    onSelect: (FeeItemOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled && options.isNotEmpty(),
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected?.name ?: when {
                isLoading -> "加载中..."
                error != null -> "加载失败"
                options.isEmpty() -> "请选择"
                else -> "请选择"
            },
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            isError = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled
                )
        )

        DropdownMenu(
            expanded = expanded && options.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

// ─── 网费卡片 ──────────────────────────────────────────────

@Composable
fun InternetBalanceCard(
    data: InternetBalanceData?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading && data == null -> {
                    CircularProgressIndicator()
                }
                error != null && data == null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                }
                data != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "网费余额",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${DecimalFormat("#,##0.00").format(data.balanceYuan)} 元",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val details = buildString {
                            if (data.usedFlowMb > 0) append("流量 ${formatFlow(data.usedFlowMb)}")
                            if (data.usedTimeMinutes > 0L) {
                                if (isNotEmpty()) append(" · ")
                                append("时长 ${formatMinutes(data.usedTimeMinutes)}")
                            }
                        }
                        if (details.isNotEmpty()) {
                            Text(
                                text = "本期: $details",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = "账号 ${data.account}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        )
                        if (isLoading) {
                            Spacer(modifier = Modifier.height(4.dp))
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatFlow(mb: Double): String {
    return if (mb >= 1024) "${DecimalFormat("#,##0.0").format(mb / 1024)} GB" else "${DecimalFormat("#,##0").format(mb)} MB"
}

private fun formatMinutes(minutes: Long): String {
    return if (minutes >= 60) "${minutes / 60}h${minutes % 60}m" else "${minutes}m"
}
