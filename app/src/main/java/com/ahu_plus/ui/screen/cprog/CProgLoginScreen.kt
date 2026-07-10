package com.ahu_plus.ui.screen.cprog

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.ui.components.AhuTopAppBar

/**
 * 大学计算机平台登录页:用户名(C+学号) + 密码(学号) + 验证码图。
 * 全程 App 内完成,验证码点击刷新。校园网直连,其他网络自动通过 WebVPN。
 * 密码始终打码;提示只给格式,不带任何真实账号。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CProgLoginScreen(
    viewModel: CProgViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.login.collectAsStateWithLifecycle()
    var captchaInput by rememberSaveable { mutableStateOf("") }

    // 首次进入自动拉验证码
    LaunchedEffect(Unit) {
        if (state.captcha == null && !state.captchaLoading) viewModel.refreshCaptcha()
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("大学计算机平台", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "校园网直连，其他网络自动通过 WebVPN。\n" +
                        "用户名 = 字母 C + 学号\n密码 = 你的学号",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("用户名") },
                placeholder = { Text("字母 C + 学号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("密码") },
                placeholder = { Text("你的学号") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // 验证码行:输入框 + 图片(点击刷新)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = captchaInput,
                    onValueChange = { captchaInput = it },
                    label = { Text("验证码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .width(120.dp)
                        .clickable { viewModel.refreshCaptcha() },
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = state.captcha
                    when {
                        state.captchaLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        bmp != null -> Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "验证码,点击刷新",
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> Text("点击加载", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { viewModel.login(captchaInput) },
                enabled = !state.loggingIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.loggingIn) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.loggingIn) "登录中…" else "登录")
            }
        }
    }
}
