package com.ahu_plus.ui.screen.mail

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ahu_plus.data.model.mail.MailAddress
import com.ahu_plus.ui.theme.AhuSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 邮件详情页。
 *
 * 用 [WebView] 渲染 HTML 邮件体(Sirius 邮件含自定义 CSS,纯 Compose Text 无法正确渲染)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailDetailScreen(
    messageId: String,
    viewModel: MailViewModel,
    onBack: () -> Unit,
) {
    val detailState by viewModel.detailState.collectAsState()

    LaunchedEffect(messageId) {
        viewModel.openMessage(messageId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detailState.detail?.subject ?: "邮件详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearDetail()
                        onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                detailState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(AhuSpacing.md))
                            Text("正在加载邮件...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                detailState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            detailState.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                detailState.detail != null -> {
                    val detail = detailState.detail!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(AhuSpacing.md),
                    ) {
                        // 主题
                        Text(
                            text = detail.subject,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(AhuSpacing.md))
                        // 发件人/收件人/时间
                        AddressRow("发件人", detail.from)
                        if (detail.to.isNotEmpty()) {
                            AddressRow("收件人", detail.to.first())
                        }
                        detail.cc?.firstOrNull()?.let { AddressRow("抄送", it) }
                        Text(
                            text = "时间: ${formatMailDate(detail.date)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(AhuSpacing.md))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(AhuSpacing.md))
                        // HTML 邮件体
                        HtmlMailBody(html = detail.htmlBody)
                        // 附件(首版只展示列表,不实现下载)
                        if (!detail.attachments.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(AhuSpacing.md))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(AhuSpacing.md))
                            Text(
                                "附件(${detail.attachments.size})",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            detail.attachments.forEach { att ->
                                Text(
                                    text = "• ${att.name} (${formatSize(att.size)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = AhuSpacing.sm),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressRow(label: String, address: MailAddress) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = 48.dp, height = 16.dp).padding(end = AhuSpacing.sm),
        )
        Text(
            text = "${address.name ?: ""} <${address.address}>",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HtmlMailBody(html: String) {
    if (html.isBlank()) {
        Text("(邮件内容为空)", style = MaterialTheme.typography.bodyMedium)
        return
    }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.apply {
                    javaScriptEnabled = false  // 安全:禁用邮件内 JS
                    blockNetworkImage = false
                    loadsImagesAutomatically = true
                    domStorageEnabled = true
                }
            }
        },
        update = { webView ->
            // 包裹一层基本样式,保证在移动端可读
            val wrappedHtml = """
                <html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0"/>
                <style>body{font-size:14px;line-height:1.6;word-wrap:break-word;padding:0;margin:0;}img{max-width:100%;height:auto;}</style>
                </head><body>$html</body></html>
            """.trimIndent()
            webView.loadDataWithBaseURL(null, wrappedHtml, "text/html", "UTF-8", null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(800.dp),  // 固定高度避免 WebView 嵌套滚动冲突(首版简化)
    )
}

private fun formatMailDate(timestamp: Long): String {
    if (timestamp <= 0) return "未知"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(timestamp))
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    if (bytes < 1024 * 1024) return "${bytes / 1024}KB"
    return "${bytes / (1024 * 1024)}MB"
}
