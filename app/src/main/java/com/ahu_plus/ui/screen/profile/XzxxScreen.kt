package com.ahu_plus.ui.screen.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ahu_plus.data.model.XzxxLetter
import com.ahu_plus.data.model.XzxxLetterDetail
import com.ahu_plus.data.model.XzxxSubmitRequest
import com.ahu_plus.data.repository.XzxxRepository
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.theme.AhuShapes

@Composable
fun XzxxScreen(
    onBack: () -> Unit,
    repository: XzxxRepository,
) {
    val viewModel: XzxxViewModel = viewModel(
        factory = viewModelFactory { initializer { XzxxViewModel(repository) } },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Box(Modifier.fillMaxSize()) {
        if (uiState.isComposeVisible) {
            XzxxComposeScreen(
                uiState = uiState,
                onBack = viewModel::closeCompose,
                onRefreshCaptcha = viewModel::refreshCaptcha,
                onSubmit = viewModel::submitLetter,
                onDismissResult = viewModel::dismissSubmitResult,
            )
        } else {
            XzxxListScreen(
                uiState = uiState,
                listState = listState,
                onBack = onBack,
                onRefresh = viewModel::loadFirstPage,
                onOpenCompose = viewModel::openCompose,
                onToggleDetail = viewModel::toggleDetail,
                onRetryDetail = viewModel::retryDetail,
                onLoadMore = viewModel::loadNextPage,
            )
        }

        if (uiState.needsWaf) {
            key(uiState.wafReloadKey) {
                XzxxWafBootstrap(
                    url = viewModel.challengeUrl(),
                    onCookie = viewModel::onWafCookieCaptured,
                    onError = viewModel::onWafBootstrapError,
                )
            }
            WafStatusOverlay(
                isValidating = uiState.wafValidating,
                error = uiState.wafError,
                onRetry = viewModel::retryWafBootstrap,
            )
        }
    }
}

@Composable
private fun XzxxListScreen(
    uiState: XzxxUiState,
    listState: LazyListState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenCompose: () -> Unit,
    onToggleDetail: (XzxxLetter) -> Unit,
    onRetryDetail: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("校长信箱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenCompose,
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("写信") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        XzxxLetterList(
            modifier = Modifier.padding(innerPadding),
            listState = listState,
            uiState = uiState,
            onToggleDetail = onToggleDetail,
            onRetryDetail = onRetryDetail,
            onLoadMore = onLoadMore,
            onRetry = onRefresh,
        )
    }
}

@Composable
private fun XzxxLetterList(
    modifier: Modifier,
    listState: LazyListState,
    uiState: XzxxUiState,
    onToggleDetail: (XzxxLetter) -> Unit,
    onRetryDetail: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
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
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        when {
            uiState.isLoading && uiState.letters.isEmpty() -> item {
                LoadingFooter("正在加载校长信箱...")
            }
            uiState.error != null && uiState.letters.isEmpty() -> item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(uiState.error, color = MaterialTheme.colorScheme.error)
                    FilledTonalButton(onClick = onRetry) { Text("重新加载") }
                }
            }
            else -> {
                items(items = uiState.letters, key = { it.contentId }) { letter ->
                    val contentId = letter.contentId
                    XzxxLetterCard(
                        letter = letter,
                        expanded = contentId in uiState.expandedIds,
                        detail = uiState.details[contentId],
                        isDetailLoading = contentId in uiState.detailLoadingIds,
                        detailError = uiState.detailErrors[contentId],
                        onToggle = { onToggleDetail(letter) },
                        onRetryDetail = { onRetryDetail(contentId) },
                    )
                }
                when {
                    uiState.isLoadingMore -> item { LoadingFooter("加载下一页...") }
                    uiState.loadMoreError != null -> item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                uiState.loadMoreError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = onLoadMore) { Text("重试") }
                        }
                    }
                    !uiState.hasMore && uiState.letters.isNotEmpty() -> item { EndFooter() }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun XzxxLetterCard(
    letter: XzxxLetter,
    expanded: Boolean,
    detail: XzxxLetterDetail?,
    isDetailLoading: Boolean,
    detailError: String?,
    onToggle: () -> Unit,
    onRetryDetail: () -> Unit,
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Column(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    letter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (letter.viewCount.isNotBlank()) {
                        Text(
                            "${letter.viewCount} 次查看",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DotSep()
                    }
                    Text(
                        "写 ${letter.writeDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DotSep()
                    if (letter.replyDate.isNotBlank()) {
                        Text(
                            "回 ${letter.replyDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        BadgeChip("待回复", Color(0xFFFEF7E0), Color(0xFFB06000))
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    HorizontalDivider()
                    when {
                        detail != null -> XzxxDetailBody(detail)
                        isDetailLoading -> LoadingFooter("正在加载详情...")
                        detailError != null -> Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                detailError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = onRetryDetail) { Text("重试") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XzxxDetailBody(detail: XzxxLetterDetail) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (detail.title.isNotBlank()) DetailBox(
            label = "主　题",
            content = detail.title,
            labelBg = Color(0xFFE8F0FE),
            labelFg = Color(0xFF1967D2),
        )
        if (detail.content.isNotBlank()) DetailBox(
            label = "写信内容",
            content = detail.content,
            labelBg = Color(0xFFF1F3F4),
            labelFg = Color(0xFF5F6368),
        )
        if (detail.replyContent.isNotBlank()) DetailBox(
            label = detail.replyLabel.ifBlank { "回复" },
            content = detail.replyContent,
            labelBg = Color(0xFFE6F4EA),
            labelFg = Color(0xFF137333),
        ) else if (detail.replyLabel.isNotBlank()) {
            Surface(color = Color(0xFFFEF7E0), shape = AhuShapes.Card) {
                Text(
                    detail.replyLabel,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB06000),
                )
            }
        }
    }
}

@Composable
private fun DetailBox(label: String, content: String, labelBg: Color, labelFg: Color) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Surface(color = labelBg, shape = RoundedCornerShape(6.dp)) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = labelFg,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun XzxxComposeScreen(
    uiState: XzxxUiState,
    onBack: () -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSubmit: (XzxxSubmitRequest) -> Unit,
    onDismissResult: () -> Unit,
) {
    var uname by rememberSaveable { mutableStateOf("") }
    var telnum by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var checkcode by rememberSaveable { mutableStateOf("") }

    val captchaBitmap = remember(uiState.captchaBytes) {
        uiState.captchaBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    LaunchedEffect(uiState.captchaRevision) {
        checkcode = ""
    }
    BackHandler(enabled = true) { onBack() }

    uiState.submitResult?.let { result ->
        AlertDialog(
            onDismissRequest = onDismissResult,
            title = { Text(if (result.success) "提交成功" else "提交失败") },
            text = { Text(result.message) },
            confirmButton = { TextButton(onClick = onDismissResult) { Text("确定") } },
        )
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("写信") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.isSubmitting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uname,
                onValueChange = { if (it.length <= 8) uname = it },
                label = { Text("联系人") },
                placeholder = { Text("请输入姓名") },
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = checkcode,
                    onValueChange = { checkcode = it.filter(Char::isDigit).take(4) },
                    label = { Text("验证码") },
                    placeholder = { Text("4位") },
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = AhuShapes.Card,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.height(56.dp).width(120.dp),
                ) {
                    when {
                        uiState.captchaLoading -> Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        captchaBitmap != null -> Image(
                            bitmap = captchaBitmap,
                            contentDescription = "验证码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.padding(4.dp).clickable(onClick = onRefreshCaptcha),
                        )
                        else -> Box(
                            modifier = Modifier.clickable(onClick = onRefreshCaptcha).padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                uiState.captchaError ?: "点击获取",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(onClick = onRefreshCaptcha, enabled = !uiState.captchaLoading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新验证码")
                }
            }

            OutlinedTextField(
                value = telnum,
                onValueChange = { if (it.length <= 30) telnum = it },
                label = { Text("联系方式") },
                placeholder = { Text("电话/邮箱（选填）") },
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 50) title = it },
                label = { Text("发信主题") },
                placeholder = { Text("请简要概括您的来信内容") },
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("发信内容") },
                placeholder = { Text("请理性、客观地陈述您的意见或建议...") },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    onSubmit(XzxxSubmitRequest(uname, checkcode, telnum, title, content))
                },
                enabled = !uiState.isSubmitting && captchaBitmap != null && uname.isNotBlank() &&
                    checkcode.length == 4 && title.isNotBlank() && content.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (uiState.isSubmitting) "提交中..." else "提交")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun XzxxWafBootstrap(
    url: String,
    onCookie: (String) -> Unit,
    onError: (String) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
            webView = null
        }
    }

    AndroidView(
        modifier = Modifier.size(1.dp).alpha(0f),
        factory = { context ->
            CookieManager.getInstance().setAcceptCookie(true)
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(1, 1)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    loadsImagesAutomatically = false
                    blockNetworkImage = true
                    userAgentString = WAF_USER_AGENT
                }
                webViewClient = WafWebViewClient(onCookie, onError)
                webView = this
                loadUrl(url)
            }
        },
    )
}

private class WafWebViewClient(
    private val onCookie: (String) -> Unit,
    private val onError: (String) -> Unit,
) : WebViewClient() {
    private val startedAt = System.currentTimeMillis()
    private var delivered = false

    override fun onPageFinished(view: WebView, url: String?) {
        pollUntilReady(view, url.orEmpty())
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame && !delivered) onError("安全校验加载失败")
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
        if (request.isForMainFrame && response.statusCode != 412 && !delivered) {
            onError("安全校验失败：HTTP ${response.statusCode}")
        }
    }

    private fun pollUntilReady(view: WebView, url: String) {
        if (delivered) return
        view.postDelayed({
            if (delivered) return@postDelayed
            view.evaluateJavascript(WAF_READY_JS) { encoded ->
                if (decodeJs(encoded) == "ready") {
                    val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
                    if (cookies.contains("EdaP18tkVMlRP=")) {
                        delivered = true
                        onCookie(cookies)
                        return@evaluateJavascript
                    }
                }
                if (System.currentTimeMillis() - startedAt >= WAF_TIMEOUT_MILLIS) {
                    onError("安全校验超时，请重试")
                } else {
                    pollUntilReady(view, url)
                }
            }
        }, 500)
    }
}

@Composable
private fun WafStatusOverlay(
    isValidating: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    BackHandler(enabled = true) {}
    Box(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = AhuShapes.Card,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (error == null) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(if (isValidating) "正在验证安全凭证..." else "正在完成安全校验...")
                } else {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    FilledTonalButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
    }
}

@Composable
private fun DotSep() {
    Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BadgeChip(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(10.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun LoadingFooter(message: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EndFooter() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            "已经到底啦",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun decodeJs(value: String?): String {
    if (value.isNullOrBlank() || value == "null") return ""
    return runCatching { org.json.JSONArray("[$value]").getString(0) }.getOrDefault(value)
}

private const val WAF_READY_JS = """
(function(){
    var html=document.documentElement?document.documentElement.outerHTML:'';
    var ready=document.title==='校长信箱' && !!document.querySelector('table') && html.indexOf('${'$'}_ss=')<0;
    return ready?'ready':'waiting';
})()
"""
private const val WAF_TIMEOUT_MILLIS = 12_000L
private const val WAF_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 7) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
