package com.ahu_plus.ui.screen.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ahu_plus.AhuPlusApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * 推荐给朋友 (2026-06-22)。
 */
@Composable
fun ShareSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fallbackUrl = "https://gitee.com/yao-enqi/ahu-plus/releases"

    var isDownloading by remember { mutableStateOf(false) }

    fun shareLink() {
        val app = context.applicationContext as AhuPlusApplication
        val url = app.updateManager.lastFetchedUpdateInfo?.downloadUrl
            ?: fallbackUrl
        val text = "安大 Plus - 安徽大学校园助手\n下载链接：$url"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(android.content.Intent.createChooser(intent, "分享下载链接"))
        }
        onDismiss()
    }

    fun shareApk() {
        if (isDownloading) return
        isDownloading = true
        scope.launch(Dispatchers.IO) {
            try {
                // 1. 获取下载 URL
                val app = context.applicationContext as AhuPlusApplication
                var info = app.updateManager.lastFetchedUpdateInfo
                if (info == null) {
                    app.updateManager.checkManually()
                    info = app.updateManager.lastFetchedUpdateInfo
                }
                val url = info?.downloadUrl
                if (url.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        isDownloading = false
                        Toast.makeText(context, "未获取到下载地址,请检查网络", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 2. 下载 APK — 复用 UpdateManager 同款 OkHttp 客户端
                val fileName = info.apkFileName.ifBlank { "ahu_plus.apk" }
                val dir = File(context.cacheDir, "apk_share").apply { mkdirs() }
                val apkFile = File(dir, fileName)

                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        isDownloading = false
                        Toast.makeText(context, "下载失败(${response.code})", Toast.LENGTH_SHORT).show()
                    }
                    response.close()
                    return@launch
                }

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(apkFile).use { out -> input.copyTo(out) }
                }
                response.close()
                android.util.Log.d("ShareSheet", "APK 下载成功: ${apkFile.length()} bytes")

                // 3. 分享
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", apkFile
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    runCatching {
                        context.startActivity(android.content.Intent.createChooser(intent, "分享安装包"))
                    }.onFailure { e ->
                        Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("推荐给朋友", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isDownloading) {
                    Text("正在下载安装包,请稍候...", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    TextButton(onClick = { shareLink() }, modifier = Modifier.fillMaxWidth()) {
                        Text("分享下载链接", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider()
                    TextButton(onClick = { shareApk() }, modifier = Modifier.fillMaxWidth(), enabled = !isDownloading) {
                        Text("分享安装包", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
