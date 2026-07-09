package com.ahu_plus.ui.screen.autologin

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

/**
 * 自动登录 / 重试页。
 *
 * - 加载中:校徽缓慢旋转 + 呼吸缩放 + 底部"正在登录..."文字
 * - 失败:校徽停止动画,显示错误信息 + "点击任意处重试"提示
 * - 成功:onSuccess 回调,跳转到主页
 * - 无凭据:onNoCredentials 回调,跳转到登录页
 */
@Composable
fun AutoLoginScreen(
    viewModel: AutoLoginViewModel,
    onSuccess: () -> Unit,
    onNoCredentials: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 状态切换:成功 → 跳转;无凭据 → 跳转
    LaunchedEffect(uiState) {
        when (uiState) {
            is AutoLoginState.Success -> onSuccess()
            is AutoLoginState.NoCredentials -> onNoCredentials()
            else -> {}
        }
    }

    // 校徽简单动画:柔和呼吸(0.95 ↔ 1.05)
    // 加载中持续呼吸,失败时缩放回 1.0 但保持轻微 alpha 呼吸表示"可交互"
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // SVG 加载器
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    // 点击任意处重试(整页可点击)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (uiState is AutoLoginState.Failed) {
                    viewModel.retry()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // 校徽(简单呼吸动画,不旋转)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/ahu_plus_icon.png")
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "安大+",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(160.dp)
                    .scale(breathScale)
                    .alpha(breathAlpha)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // 状态文字
            when (val s = uiState) {
                is AutoLoginState.Loading -> {
                    Text(
                        text = "正在登录...",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is AutoLoginState.Failed -> {
                    Text(
                        text = "登录失败",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = s.message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "点击任意处重试",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    TextButton(onClick = { viewModel.logout() }) {
                        Text(
                            text = "切换账号",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                else -> {
                    // Success / NoCredentials 会立即跳走,这里不会停留
                }
            }
        }
    }
}