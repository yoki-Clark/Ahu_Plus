package com.ahu_plus.ui.screen.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun AdwmhCaptchaDialog(
    captchaBytes: ByteArray?,
    captchaLoading: Boolean,
    captchaError: String?,
    loginLoading: Boolean,
    loginError: String?,
    onRefresh: () -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var captcha by remember(captchaBytes) { mutableStateOf("") }
    val bitmap = remember(captchaBytes) {
        captchaBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    AlertDialog(
        onDismissRequest = { if (!loginLoading) onDismiss() },
        title = { Text("登录智慧安大") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "验证码图片仅从智慧安大获取并在本机显示，请手动输入。图片不会发送到任何识别服务。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                when {
                    captchaLoading -> CircularProgressIndicator()
                    bitmap != null -> Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "智慧安大验证码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                    )
                    else -> Text(captchaError ?: "验证码暂不可用", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = captcha,
                    onValueChange = { value -> captcha = value.filter(Char::isLetterOrDigit).take(8) },
                    label = { Text("验证码") },
                    singleLine = true,
                    enabled = !loginLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    trailingIcon = {
                        IconButton(onClick = onRefresh, enabled = !captchaLoading && !loginLoading) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新验证码")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                loginError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(captcha) },
                enabled = captcha.isNotBlank() && bitmap != null && !loginLoading,
            ) {
                if (loginLoading) CircularProgressIndicator() else Text("登录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loginLoading) { Text("取消") }
        },
    )
}
