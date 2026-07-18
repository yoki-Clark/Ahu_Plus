package com.ahu_plus.ui.screen.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.repository.AdwmhQrCode
import com.ahu_plus.ui.components.AhuHeroCard
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.components.CountdownArc
import com.ahu_plus.ui.theme.AhuGradient
import com.ahu_plus.util.QrCodeBitmap
import java.text.DecimalFormat

/**
 * 兼容旧入口的校园卡页面。
 *
 * 新版主流程已迁移到「我的」页,这里保留为独立页面以便后续复用或调试。
 */

@Composable
fun CampusQrCodeCard(
    qrCode: AdwmhQrCode?,
    balance: Double?,
    isLoading: Boolean,
    error: String?,
    countdownSeconds: Int,
    totalCountdownSeconds: Int,
    isStale: Boolean = false,
    ageSeconds: Int = 0,
    onAutoLogin: () -> Unit,
    onRefresh: () -> Unit,
    onQrClick: () -> Unit
) {

    // 脉冲动画（仅在有 QR 码时生效）
    val pulseTransition = rememberInfiniteTransition(label = "qrPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    AhuHeroCard(
        gradient = AhuGradient.Teal.brush,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 顶部标签行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.QrCode2,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "智慧安大支付码",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                // 倒计时环（仅在有 QR 码时显示）
                if (qrCode != null) {
                    CountdownArc(
                        secondsRemaining = countdownSeconds,
                        totalSeconds = totalCountdownSeconds,
                        size = 36.dp,
                        strokeWidth = 2.5.dp,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        progressColor = Color.White,
                        textColor = Color.White
                    )
                }
            }

            // 内容区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    // 加载中（无缓存）
                    isLoading && qrCode == null -> {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "加载支付码...",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // 已加载 QR 码
                    qrCode != null && !isStale -> {
                        val image = remember(qrCode.payload) {
                            QrCodeBitmap.create(qrCode.payload, 720)
                        }
                        Image(
                            bitmap = image,
                            contentDescription = "校园支付码",
                            modifier = Modifier
                                .size(220.dp)
                                .clickable { onQrClick() }
                                .graphicsLayer(
                                    scaleX = pulseScale,
                                    scaleY = pulseScale
                                )
                        )
                        // "点击放大" 文字提示
                        Text(
                            text = "点击放大",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )

                        // 余额
                        if (balance != null) {
                            Text(
                                text = "余额 ${DecimalFormat("¥#,##0.00").format(balance)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        // 服务器时间 / 失效提醒 + 刷新中指示
                        if (isStale) {
                            // 码可能已失效 → 醒目琥珀色警告
                            Row(
                                modifier = Modifier
                                    .clip(AhuShapes.Pill)
                                    .background(Color(0xFFFFA000).copy(alpha = 0.22f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFFFFE082),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "${formatQrAge(ageSeconds)}的码，可能已失效，请刷新",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFFE082)
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = qrCode.statusMsg.ifBlank { "已刷新" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    // API 错误
                    error != null -> {
                        Text(
                            text = error,
                            color = Color(0xFFFFCDD2),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = onRefresh) {
                            Text("重试", color = Color.White)
                        }
                    }

                    // 无 session — 自动登录
                    else -> {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "正在登录智慧安大...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        } else {
                            // 此分支进入前提是 error == null(error != null 已被上方 when 分支处理),
                            // 故不再重复判断 error
                            Text(
                                text = "点击按钮登录智慧安大",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            TextButton(onClick = onAutoLogin) {
                                Text("登录智慧安大", color = Color.White)
                            }
                        }
                    }
                }
            }

            // 底部操作栏（仅在有 QR 码时显示刷新）
            if (qrCode != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onRefresh) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("刷新", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

/** 将"距今秒数"格式化为人类可读文案:"刚刚 / X 秒前 / X 分钟前"。 */
internal fun formatQrAge(ageSeconds: Int): String = when {
    ageSeconds < 5 -> "刚刚"
    ageSeconds < 60 -> "$ageSeconds 秒前"
    else -> "${ageSeconds / 60} 分钟前"
}
