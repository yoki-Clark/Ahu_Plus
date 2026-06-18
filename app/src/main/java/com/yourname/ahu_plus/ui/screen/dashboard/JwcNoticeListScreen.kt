package com.yourname.ahu_plus.ui.screen.dashboard

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.JwcNotice
import com.yourname.ahu_plus.data.model.JwcNoticeAttachment
import com.yourname.ahu_plus.data.model.JwcNoticeDetail
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import com.yourname.ahu_plus.ui.components.AhuShapes
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwcNoticeListScreen(
    viewModel: JwcNoticeListViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val selectedNotice = uiState.selectedNotice

    BackHandler(enabled = selectedNotice != null) {
        viewModel.closeNotice()
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text(if (selectedNotice == null) "通知公告" else "通知详情") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedNotice == null) {
                                onBack()
                            } else {
                                viewModel.closeNotice()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (selectedNotice == null) {
                                viewModel.loadFirstPage()
                            } else {
                                viewModel.retryDetail()
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedNotice == null) {
                NoticeListBody(
                    uiState = uiState,
                    onOpen = viewModel::openNotice,
                    onLoadMore = viewModel::loadNextPage,
                    onRetry = viewModel::loadFirstPage
                )
            } else {
                NoticeDetailBody(
                    notice = selectedNotice,
                    detailState = uiState.details[selectedNotice.url],
                    onRetry = viewModel::retryDetail,
                    onDownload = { attachment -> downloadJwcAttachment(context, attachment) }
                )
            }

            val fetchUrl = uiState.noticeFetchUrl
            if (fetchUrl != null) {
                JwcListHtmlLoader(
                    url = fetchUrl,
                    reloadKey = uiState.noticeFetchKey,
                    onHtml = viewModel::onNoticeHtmlLoaded,
                    onError = viewModel::onNoticeHtmlError
                )
            }

            val detailFetchUrl = uiState.detailFetchUrl
            if (detailFetchUrl != null) {
                JwcListHtmlLoader(
                    url = detailFetchUrl,
                    reloadKey = uiState.detailFetchKey,
                    onHtml = viewModel::onDetailHtmlLoaded,
                    onError = viewModel::onDetailHtmlError
                )
            }
        }
    }
}

@Composable
private fun NoticeListBody(
    uiState: JwcNoticeListUiState,
    onOpen: (JwcNotice) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.hasMore, uiState.isLoadingMore, uiState.isLoading) {
        if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore && !uiState.isLoading) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        when {
            uiState.isLoading && uiState.notices.isEmpty() -> {
                item {
                    LoadingRow(text = "正在加载通知公告...")
                }
            }

            uiState.error != null && uiState.notices.isEmpty() -> {
                item {
                    ErrorBlock(message = uiState.error, onRetry = onRetry)
                }
            }

            else -> {
                items(items = uiState.notices, key = { it.url }) { notice ->
                    NoticeRow(notice = notice, onClick = { onOpen(notice) })
                }
                if (uiState.isLoadingMore || (uiState.hasMore && uiState.notices.isNotEmpty() && !uiState.isLoading)) {
                    item {
                        LoadingRow(text = "加载下一页...", compact = true)
                    }
                } else if (!uiState.hasMore && uiState.notices.isNotEmpty()) {
                    item {
                        Text(
                            text = "已经到底了",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(
    notice: JwcNotice,
    onClick: () -> Unit
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .padding(end = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (notice.date.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notice.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeDetailBody(
    notice: JwcNotice,
    detailState: NoticeDetailState?,
    onRetry: () -> Unit,
    onDownload: (JwcNoticeAttachment) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            NoticeDetailHeader(notice = notice)
        }

        when (detailState) {
            null, NoticeDetailState.Loading -> {
                item {
                    LoadingRow(text = "正在加载通知详情...")
                }
            }

            is NoticeDetailState.Error -> {
                item {
                    ErrorBlock(message = detailState.message, onRetry = onRetry)
                }
            }

            is NoticeDetailState.Success -> {
                item {
                    NoticeDetailContent(detail = detailState.detail)
                }
                if (detailState.detail.attachments.isNotEmpty()) {
                    item {
                        SectionTitle(text = "附件")
                    }
                    items(items = detailState.detail.attachments, key = { it.url }) { attachment ->
                        AttachmentRow(
                            attachment = attachment,
                            onDownload = { onDownload(attachment) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeDetailHeader(notice: JwcNotice) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = notice.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (notice.date.isNotBlank()) {
                Text(
                    text = notice.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NoticeDetailContent(detail: JwcNoticeDetail) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Text(
                text = detail.content.ifBlank { "未读取到正文内容" },
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun AttachmentRow(
    attachment: JwcNoticeAttachment,
    onDownload: () -> Unit
) {
    Surface(
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDownload)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = attachment.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "下载"
            )
        }
    }
}

@Composable
private fun LoadingRow(
    text: String,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 0.dp else 80.dp, bottom = if (compact) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (compact) 18.dp else 22.dp),
            strokeWidth = 2.dp
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorBlock(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        FilledTonalButton(onClick = onRetry) {
            Text("重新加载")
        }
    }
}

@Composable
private fun JwcListHtmlLoader(
    url: String,
    reloadKey: Int,
    onHtml: (String, String) -> Unit,
    onError: (String, String) -> Unit
) {
    AndroidView(
        modifier = Modifier
            .size(1.dp)
            .alpha(0f),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                settings.userAgentString = JWC_WEBVIEW_USER_AGENT
                webViewClient = object : WebViewClient() {
                    private var pageStartTime = 0L

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        pageStartTime = System.currentTimeMillis()
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        view.postDelayed(
                            { extractListHtml(view, currentRequestUrl(view), onHtml, onError, pageStartTime) },
                            350
                        )
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse
                    ) {
                        if (request.isForMainFrame && errorResponse.statusCode != 412) {
                            onError(
                                currentRequestUrl(view),
                                "教务处网站返回 HTTP ${errorResponse.statusCode}"
                            )
                        }
                    }
                }
            }
        },
        update = { webView ->
            val requestKey = "$url#$reloadKey"
            if (webView.tag != requestKey) {
                webView.tag = requestKey
                webView.loadUrl(url)
            }
        }
    )
}

private fun downloadJwcAttachment(
    context: Context,
    attachment: JwcNoticeAttachment
) {
    val uri = Uri.parse(attachment.url)
    if (uri.scheme !in setOf("http", "https")) {
        Toast.makeText(context, "附件链接无效", Toast.LENGTH_SHORT).show()
        return
    }

    val fileName = resolveDownloadFileName(attachment, uri)
    val request = DownloadManager.Request(uri)
        .setTitle(fileName)
        .setDescription("安徽大学教务处附件")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        .addRequestHeader("User-Agent", JWC_WEBVIEW_USER_AGENT)

    CookieManager.getInstance().getCookie(attachment.url)
        ?.takeIf { it.isNotBlank() }
        ?.let { request.addRequestHeader("Cookie", it) }

    runCatching {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }.onSuccess {
        Toast.makeText(context, "已开始下载：$fileName", Toast.LENGTH_SHORT).show()
    }.onFailure { error ->
        Toast.makeText(context, error.message ?: "下载启动失败", Toast.LENGTH_SHORT).show()
    }
}

private fun resolveDownloadFileName(
    attachment: JwcNoticeAttachment,
    uri: Uri
): String {
    val urlName = Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast('/')
    val rawName = attachment.name.trim().ifBlank { urlName }.ifBlank { "jwc_attachment" }
    val sanitized = rawName
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(120)
        .ifBlank { "jwc_attachment" }
    val urlExtension = urlName.substringAfterLast('.', missingDelimiterValue = "")
    return if (sanitized.contains('.') || urlExtension.isBlank() || urlExtension.length > 8) {
        sanitized
    } else {
        "$sanitized.$urlExtension"
    }
}

private fun currentRequestUrl(webView: WebView): String {
    return (webView.tag as? String)?.substringBeforeLast("#") ?: webView.url.orEmpty()
}

private fun extractListHtml(
    webView: WebView,
    expectedUrl: String,
    onHtml: (String, String) -> Unit,
    onError: (String, String) -> Unit,
    pageStartTime: Long
) {
    webView.evaluateJavascript(
        """
            (function() {
              return document.documentElement.outerHTML;
            })();
        """.trimIndent()
    ) { encoded ->
        val html = decodeJsString(encoded)
        if (html.isBlank()) {
            onError(expectedUrl, "教务处页面内容为空")
            return@evaluateJavascript
        }
        val isChallenge = html.contains("\$_ss=") && !html.contains("news_list") &&
            !html.contains("wp_news_w14") && !html.contains("wp_articlecontent")
        if (isChallenge && System.currentTimeMillis() - pageStartTime < 12_000L) {
            webView.postDelayed(
                { extractListHtml(webView, expectedUrl, onHtml, onError, pageStartTime) },
                600
            )
            return@evaluateJavascript
        }
        if (isChallenge) {
            onError(expectedUrl, "教务处网站校验未通过，请稍后重试")
        } else {
            onHtml(expectedUrl, html)
        }
    }
}

private fun decodeJsString(value: String?): String {
    if (value.isNullOrBlank() || value == "null") return ""
    return runCatching { JSONArray("[$value]").getString(0) }.getOrDefault(value)
}

private const val JWC_WEBVIEW_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
