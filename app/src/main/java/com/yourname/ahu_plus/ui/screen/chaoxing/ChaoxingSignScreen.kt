package com.yourname.ahu_plus.ui.screen.chaoxing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.CxActivity
import com.yourname.ahu_plus.data.model.CxSignType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 签到中心(2026-06-20 重做)。
 *
 * 显示进行中的签到活动列表，每条带类型图标 + 一键签到按钮。
 * 支持 4 种签到类型:
 *   - 普通签到 (NORMAL)
 *   - 手势签到 (GESTURE) - 需在设置页配置手势码
 *   - 位置签到 (LOCATION) - 需在设置页配置经纬度
 *   - preSign 前置 (PRE_SIGN)
 */
@Composable
fun ChaoxingSignScreen(viewModel: ChaoxingViewModel) {
    val signState by viewModel.signState.collectAsStateWithLifecycle()

    when {
        signState.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        signState.activities.isEmpty() -> {
            EmptySignState()
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 标题行
                item {
                    Text(
                        text = "进行中的签到 (${signState.activities.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(signState.activities, key = { it.id }) { activity ->
                    SignActivityCard(
                        activity = activity,
                        signing = signState.signingIds.contains(activity.id),
                        onSign = { viewModel.signActivity(activity) },
                        configuredLat = signState.configuredLat,
                        configuredLon = signState.configuredLon,
                        configuredGesture = signState.configuredGesture,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignActivityCard(
    activity: CxActivity,
    signing: Boolean,
    onSign: () -> Unit,
    configuredLat: Double,
    configuredLon: Double,
    configuredGesture: String,
) {
    val (icon, color, typeLabel) = when (activity.signType) {
        CxSignType.NORMAL -> Triple(Icons.Outlined.AssignmentTurnedIn, Color(0xFF4CAF50), "普通签到")
        CxSignType.GESTURE -> Triple(Icons.Filled.TouchApp, Color(0xFFFF9800), "手势签到")
        CxSignType.LOCATION -> Triple(Icons.Filled.LocationOn, Color(0xFF2196F3), "位置签到")
        CxSignType.PRE_SIGN -> Triple(Icons.Outlined.AssignmentTurnedIn, Color(0xFF9C27B0), "预签到")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = com.yourname.ahu_plus.ui.components.AhuShapes.Card,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = color.copy(alpha = 0.12f),
                ) {
                    Icon(
                        icon as ImageVector,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activity.name.ifBlank { "签到活动" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$typeLabel · 开始于 ${formatTime(activity.startTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 位置 / 手势提示
            if (activity.signType == CxSignType.LOCATION && (configuredLat < 0 || configuredLon < 0)) {
                Spacer(modifier = Modifier.height(8.dp))
                HintBox("⚠ 请先在设置页配置签到经纬度")
            }
            if (activity.signType == CxSignType.GESTURE && configuredGesture.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                HintBox("⚠ 请先在设置页配置手势签到码")
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (signing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Button(onClick = onSign, enabled = !signing) {
                    Text("一键签到")
                }
            }
        }
    }
}

@Composable
private fun HintBox(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = com.yourname.ahu_plus.ui.components.AhuShapes.IconBox,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun EmptySignState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.AssignmentTurnedIn,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "暂无进行中的签到",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "下拉刷新或登录后等待教师发起签到",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(ts: Long): String {
    if (ts <= 0) return "未知"
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(ts))
}
