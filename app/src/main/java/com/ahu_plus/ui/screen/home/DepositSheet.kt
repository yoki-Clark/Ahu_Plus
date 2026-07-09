package com.ahu_plus.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.model.BathroomBalanceData
import com.ahu_plus.data.model.ElectricityBalanceData
import com.ahu_plus.ui.theme.AhuShapes
import kotlinx.coroutines.launch

/**
 * 充值 sheet。HomeScreen / UtilityDetailScreens 共用。
 *
 * 状态在调用方 (HomeUiState.depositSheet) 管理,本组件只负责 UI 渲染和回调。
 *
 * 2026-06-29: Step 1/2 已实测可达确认支付阶段,Step 3 提交由 [onConfirm] 触发。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositSheet(
    state: DepositSheetState,
    sheetState: SheetState,
    onAmountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubTargetChange: (DepositSubTarget) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible) return
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()

            // 金额
            Spacer(Modifier.height(16.dp))
            Text(
                if (state.minAmount >= 1.0) "充值金额(元,1 元起)" else "充值金额(元)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.amount,
                onValueChange = onAmountChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("请输入金额,如 5") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.inProgress,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("5", "10", "20", "50").forEach { v ->
                    FilterChip(
                        selected = state.amount == v,
                        onClick = { onAmountChange(v) },
                        label = { Text("¥$v") },
                        enabled = !state.inProgress,
                    )
                }
            }

            // 新区 feeitemid=488 时:空调/照明 单选 (UI flavor,API 仍传 488)
            if (state.showAcOrLighting) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "充哪个?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = state.subTarget == DepositSubTarget.Ac,
                        onClick = { onSubTargetChange(DepositSubTarget.Ac) },
                        label = { Text("空调") },
                        leadingIcon = {
                            Icon(Icons.Filled.Bolt, null, modifier = Modifier.size(18.dp))
                        },
                        enabled = !state.inProgress,
                    )
                    FilterChip(
                        selected = state.subTarget == DepositSubTarget.Lighting,
                        onClick = { onSubTargetChange(DepositSubTarget.Lighting) },
                        label = { Text("照明") },
                        leadingIcon = {
                            Icon(Icons.Filled.Lightbulb, null, modifier = Modifier.size(18.dp))
                        },
                        enabled = !state.inProgress,
                    )
                }
            }

            // 密码
            Spacer(Modifier.height(20.dp))
            Text(
                "查询密码 (6 位)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "默认从学生信息自动填,错误时可手改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                placeholder = {
                    Text(if (state.passwordPrefilled) "已自动填入" else "请输入 6 位查询密码")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.inProgress,
                isError = state.passwordError != null,
                supportingText = state.passwordError?.let { { Text(it) } },
            )

            // 错误条
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(20.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !state.inProgress,
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }
                Button(
                    onClick = {
                        scope.launch {
                            onConfirm()
                        }
                    },
                    enabled = !state.inProgress && state.canConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    if (state.inProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("处理中…")
                    } else {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("立即充值")
                    }
                }
            }
        }
    }
}

/** 充值 sheet UI 状态。HomeUiState 持有 + 通过 onXxx 回调修改 */
data class DepositSheetState(
    val visible: Boolean = false,
    val target: DepositTarget = DepositTarget.None,
    val title: String = "充值",
    val subtitle: String = "",
    val amount: String = "5",
    val password: String = "",
    val passwordPrefilled: Boolean = false,
    val passwordError: String? = null,
    val showAcOrLighting: Boolean = false,
    val subTarget: DepositSubTarget = DepositSubTarget.Ac,
    val inProgress: Boolean = false,
    val error: String? = null,
) {
    /** 照明(428)最低 1 元,空调/浴室 0.01 元起 */
    val minAmount: Double
        get() = if ((target as? DepositTarget.Electricity)?.feeitemid == "428") 1.0 else 0.01

    val canConfirm: Boolean
        get() {
            val amt = amount.toDoubleOrNull() ?: 0.0
            return amt >= minAmount && amt <= 9999 && password.length == 6 && password.all { it.isDigit() }
        }
}

/** 充值类型 */
sealed class DepositTarget {
    object None : DepositTarget()

    /** 电费充值,需要 [room] (UI 响应) + [building/floor/room code&name] + [feeitemid] */
    data class Electricity(
        val feeitemid: String,
        val room: com.ahu_plus.data.model.ElectricityUiData,
        val buildingName: String,
        val areaName: String,
        val buildingValue: String,
        val floorValue: String,
        val roomValue: String,
        val floorName: String,
    ) : DepositTarget()

    /** 浴室充值 */
    data class Bathroom(
        val bathroom: BathroomBalanceData,
        val feeitemid: String = "430",
    ) : DepositTarget()

    /** 网费充值 */
    data class Internet(
        val internet: com.ahu_plus.data.model.InternetBalanceData,
        val feeitemid: String = "431",
    ) : DepositTarget()
}

/** feeitemid=488 (新区) 时让用户选:空调 或 照明 (UI 标记,实际仍传 488) */
enum class DepositSubTarget { Ac, Lighting }
