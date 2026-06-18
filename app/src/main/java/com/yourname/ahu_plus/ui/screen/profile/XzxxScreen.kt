package com.yourname.ahu_plus.ui.screen.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yourname.ahu_plus.data.model.XzxxLetter
import com.yourname.ahu_plus.data.model.XzxxLetterDetail
import com.yourname.ahu_plus.data.model.XzxxSubmitResult
import com.yourname.ahu_plus.data.repository.XzxxRepository
import com.yourname.ahu_plus.ui.components.AhuTopAppBar
import com.yourname.ahu_plus.ui.components.AhuShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XzxxScreen(
    onBack: () -> Unit,
    repository: XzxxRepository = XzxxRepository()
) {
    val viewModel: XzxxListViewModel = viewModel(
        factory = viewModelFactory { initializer { XzxxListViewModel(repository) } }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCompose by rememberSaveable { mutableStateOf(false) }

    // ── 写信页 ──
    if (showCompose) {
        XzxxComposeScreen(
            repository = repository,
            onBack = { showCompose = false }
        )
        return
    }

    // ── 列表 + 离屏抓取 ──
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.apply {
                stopLoading(); (parent as? ViewGroup)?.removeView(this); destroy()
            }
        }
    }

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
                    IconButton(onClick = viewModel::loadFirstPage) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCompose = true },
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("写信") }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            XzxxLetterList(
                uiState = uiState,
                onToggleDetail = viewModel::toggleDetail,
                onLoadMore = viewModel::loadNextPage,
                onRetry = viewModel::loadFirstPage
            )
            // 列表分页 WebView
            uiState.letterFetchUrl?.let { url ->
                XzxxListHtmlLoader(url, uiState.letterFetchKey,
                    viewModel::onLetterHtmlLoaded, viewModel::onLetterHtmlError
                ) { webViewRef.value = it }
            }
            // 详情 WebView
            uiState.detailFetchUrl?.let { url ->
                XzxxDetailHtmlLoader(url, uiState.detailFetchKey,
                    viewModel::onDetailHtmlLoaded, viewModel::onDetailHtmlError)
            }
        }
    }
}

// ─── 列表 ────────────────────────────────────────────────

@Composable
private fun XzxxLetterList(
    uiState: XzxxListUiState,
    onToggleDetail: (XzxxLetter) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val t = listState.layoutInfo.totalItemsCount
            val lv = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            t > 0 && lv >= t - 3
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.hasMore, uiState.isLoadingMore, uiState.isLoading) {
        if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore && !uiState.isLoading)
            onLoadMore()
    }

    LazyColumn(
        state = listState, modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        when {
            uiState.isLoading && uiState.letters.isEmpty() -> item {
                Row(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("正在加载校长信箱...", modifier = Modifier.padding(start = 10.dp))
                }
            }
            uiState.error != null && uiState.letters.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(uiState.error, color = MaterialTheme.colorScheme.error)
                    FilledTonalButton(onClick = onRetry) { Text("重新加载") }
                }
            }
            else -> {
                items(items = uiState.letters, key = { it.contentId }) { letter ->
                    XzxxLetterCard(letter, onToggle = { onToggleDetail(letter) })
                }
                if (uiState.isLoadingMore || (uiState.hasMore && uiState.letters.isNotEmpty() && !uiState.isLoading))
                    item { LoadingFooter("加载下一页...") }
                else if (!uiState.hasMore && uiState.letters.isNotEmpty())
                    item { EndFooter() }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

// ─── 卡片 ────────────────────────────────────────────────

@Composable
private fun XzxxLetterCard(letter: XzxxLetter, onToggle: () -> Unit) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 头部(始终可见)
            Column(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(letter.title, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (letter.viewCount.isNotBlank()) {
                        Text("${letter.viewCount} 次查看", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DotSep()
                    }
                    Text("写 ${letter.writeDate}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DotSep()
                    if (letter.replyDate.isNotBlank())
                        Text("回 ${letter.replyDate}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else BadgeChip("待回复", Color(0xFFFEF7E0), Color(0xFFB06000))
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (letter.isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 展开区域
            AnimatedVisibility(
                visible = letter.isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider()
                    when {
                        letter.detail != null -> XzxxDetailBody(letter.detail!!)
                        letter.detailError != null -> {
                            Text(letter.detailError!!, modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        else -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("正在加载详情...", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        // 标题框
        if (detail.title.isNotBlank()) DetailBox(
            label = "主　题",
            content = detail.title,
            labelBg = Color(0xFFE8F0FE), labelFg = Color(0xFF1967D2)
        )
        // 内容框
        if (detail.content.isNotBlank()) DetailBox(
            label = "写信内容",
            content = detail.content,
            labelBg = Color(0xFFF1F3F4), labelFg = Color(0xFF5F6368)
        )
        // 回复框
        if (detail.replyContent.isNotBlank()) DetailBox(
            label = detail.replyLabel.ifBlank { "回复" },
            content = detail.replyContent,
            labelBg = Color(0xFFE6F4EA), labelFg = Color(0xFF137333)
        ) else if (detail.replyLabel.isNotBlank()) {
            // 标记了未回复状态
            Surface(color = Color(0xFFFEF7E0), shape = AhuShapes.Card) {
                Text("⏳ ${detail.replyLabel}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB06000))
            }
        }
    }
}

@Composable
private fun DetailBox(label: String, content: String, labelBg: Color, labelFg: Color) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Surface(color = labelBg, shape = RoundedCornerShape(6.dp)) {
                Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = labelFg, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight)
        }
    }
}

// ─── 写信页（原生 Compose 表单 + WebView 验证码/提交）──────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XzxxComposeScreen(repository: XzxxRepository, onBack: () -> Unit) {
    var uname by rememberSaveable { mutableStateOf("") }
    var telnum by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var checkcode by rememberSaveable { mutableStateOf("") }

    // captcha state
    var captchaBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var captchaLoading by remember { mutableStateOf(true) }
    var captchaError by remember { mutableStateOf<String?>(null) }
    var captchaKey by remember { mutableIntStateOf(0) }

    // submit state
    var submitting by remember { mutableStateOf(false) }
    var submitResult by remember { mutableStateOf<XzxxSubmitResult?>(null) }
    var formWv by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose { formWv?.apply { stopLoading(); (parent as? ViewGroup)?.removeView(this); destroy() } }
    }

    // success dialog
    submitResult?.let { result ->
        AlertDialog(
            onDismissRequest = { submitResult = null; if (result.success) onBack() },
            title = { Text(if (result.success) "提交成功" else "提交失败") },
            text = { Text(result.message) },
            confirmButton = {
                TextButton(onClick = {
                    submitResult = null
                    if (result.success) onBack()
                }) { Text("确定") }
            }
        )
    }

    BackHandler(enabled = true) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("写信") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { ip ->
        Box(Modifier.fillMaxSize().padding(ip)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 联系人
                OutlinedTextField(
                    value = uname, onValueChange = { if (it.length <= 8) uname = it },
                    label = { Text("联系人") },
                    placeholder = { Text("请输入姓名") },
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )

                // 验证码
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = checkcode, onValueChange = { checkcode = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("验证码") },
                        placeholder = { Text("4位") },
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = AhuShapes.Card,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.height(56.dp).width(120.dp)
                    ) {
                        when {
                            captchaLoading -> Box(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                            captchaBitmap != null -> Image(
                                bitmap = captchaBitmap!!,
                                contentDescription = "验证码",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.padding(4.dp).clickable { captchaKey++ }
                            )
                            else -> Box(
                                modifier = Modifier.padding(horizontal = 12.dp).clickable { captchaKey++ },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(captchaError ?: "点击获取",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    IconButton(onClick = { captchaKey++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新验证码")
                    }
                }

                // 联系方式
                OutlinedTextField(
                    value = telnum, onValueChange = { if (it.length <= 30) telnum = it },
                    label = { Text("联系方式") },
                    placeholder = { Text("电话/邮箱（选填）") },
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )

                // 发信主题
                OutlinedTextField(
                    value = title, onValueChange = { if (it.length <= 50) title = it },
                    label = { Text("发信主题") },
                    placeholder = { Text("请简要概括您的来信内容") },
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )

                // 发信内容
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = { Text("发信内容") },
                    placeholder = { Text("请理性、客观地陈述您的意见或建议...") },
                    minLines = 6, maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                // 提交按钮
                Button(
                    onClick = {
                        val wv = formWv ?: return@Button
                        submitting = true
                        val js = buildString {
                            append("(function(){")
                            append("var f=document.forms['form1']||document.forms[0];")
                            append("if(!f)return 'no form';")
                            append("f.uname.value=${jsStr(uname)};")
                            append("f.checkcode.value=${jsStr(checkcode)};")
                            append("f.telnum.value=${jsStr(telnum)};")
                            append("f.title.value=${jsStr(title)};")
                            append("f.content.value=${jsStr(content)};")
                            append("f.submit();")
                            append("return 'ok';")
                            append("})()")
                        }
                        wv.evaluateJavascript(js, null)
                    },
                    enabled = !submitting && uname.isNotBlank() && checkcode.length == 4
                            && title.isNotBlank() && content.isNotBlank() && formWv != null,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (submitting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (submitting) "提交中..." else "提交")
                }

                Spacer(Modifier.height(72.dp))
            }

            // ── Hidden WebView: loads compose page → extracts captcha → submits form ──
            // captchaKey changes trigger re-extraction of captcha image from the loaded page
            AndroidView(
                modifier = Modifier.size(1.dp).alpha(0f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.apply {
                            javaScriptEnabled = true; domStorageEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            useWideViewPort = true; loadWithOverviewMode = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 7) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(v: WebView, u: String?) {
                                val url = u.orEmpty()
                                when {
                                    // Compose page loaded — extract captcha <img> from the page
                                    url.contains("add.asp") -> {
                                        v.evaluateJavascript(CAPTCHA_EXTRACT_JS) { encoded ->
                                            val b64 = decodeJs(encoded)
                                            if (b64.isNotBlank() && b64 != "null" && !b64.startsWith("data:,")) {
                                                try {
                                                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                    captchaBitmap = bmp?.asImageBitmap()
                                                    captchaError = if (bmp == null) "解码失败" else null
                                                } catch (_: Exception) { captchaError = "解码失败" }
                                            } else {
                                                captchaError = "验证码获取失败"
                                            }
                                            captchaLoading = false
                                        }
                                    }
                                    // Form submitted → redirect to list.asp means success
                                    url.contains("list.asp") -> {
                                        submitResult = XzxxSubmitResult(true, "提交成功")
                                        submitting = false
                                    }
                                    // Form submitted → stayed on save.asp means error
                                    url.contains("save.asp") -> {
                                        v.evaluateJavascript(
                                            "(function(){return document.documentElement.outerHTML||'';})()"
                                        ) { encoded ->
                                            val html = decodeJs(encoded)
                                            val result = XzxxRepository.parseSubmitResult(html, 200)
                                            submitResult = result
                                            submitting = false
                                            if (!result.success) {
                                                checkcode = ""
                                                // Reload compose page to get fresh captcha
                                                captchaLoading = true
                                                v.loadUrl(repository.writeUrl())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        formWv = this
                        loadUrl(repository.writeUrl())
                    }
                },
                update = { wv ->
                    // Re-extract captcha when captchaKey changes (refresh button clicked)
                    // by reloading the compose page entirely
                    val tag = "ck_$captchaKey"
                    if (wv.tag != tag) {
                        wv.tag = tag
                        if (captchaKey > 0) {
                            captchaLoading = true
                            wv.loadUrl(repository.writeUrl())
                        }
                    }
                }
            )
        }
    }
}

/** Escape a Kotlin string for embedding in a JS single-quoted literal. */
private fun jsStr(s: String): String {
    val escaped = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
    return "'$escaped'"
}

/** JS to extract captcha image as base64 PNG from the checkcode <img> on the page. */
private const val CAPTCHA_EXTRACT_JS = """
(function(){
    try {
        var img = document.querySelector('img[src*="checkcode"]');
        if (!img || !img.complete || !img.naturalWidth) return '';
        var c = document.createElement('canvas');
        c.width = img.naturalWidth; c.height = img.naturalHeight;
        var ctx = c.getContext('2d');
        ctx.drawImage(img, 0, 0);
        var d = c.toDataURL('image/png');
        var i = d.indexOf(',');
        return i >= 0 ? d.substring(i+1) : '';
    } catch(e) { return 'err:'+e.message; }
})()"""


// ─── 离屏 WebView:列表 ──────────────────────────────────

@Composable
private fun XzxxListHtmlLoader(
    url: String, reloadKey: Int,
    onHtml: (String, String) -> Unit, onError: (String, String) -> Unit,
    onReady: (WebView) -> Unit
) {
    AndroidView(
        modifier = Modifier.size(1.dp).alpha(0f),
        factory = { ctx -> makeOffscreenWebView(ctx, url, onHtml, onError).also(onReady) },
        update = { wv -> val k = "$url#$reloadKey"; if (wv.tag != k) { wv.tag = k; wv.loadUrl(url) } }
    )
}

// ─── 离屏 WebView:详情 ──────────────────────────────────

@Composable
private fun XzxxDetailHtmlLoader(
    url: String, reloadKey: Int,
    onHtml: (String, String) -> Unit, onError: (String, String) -> Unit
) {
    AndroidView(
        modifier = Modifier.size(1.dp).alpha(0f),
        factory = { ctx -> makeOffscreenWebView(ctx, url, onHtml, onError) },
        update = { wv -> val k = "$url#$reloadKey"; if (wv.tag != k) { wv.tag = k; wv.loadUrl(url) } }
    )
}

private fun makeOffscreenWebView(
    ctx: android.content.Context, url: String,
    onHtml: (String, String) -> Unit, onError: (String, String) -> Unit
): WebView = WebView(ctx).apply {
    settings.apply {
        javaScriptEnabled = true; domStorageEnabled = true
        cacheMode = WebSettings.LOAD_DEFAULT
        loadsImagesAutomatically = false; blockNetworkImage = true
        userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    }
    webViewClient = object : WebViewClient() {
        private var t0 = 0L
        override fun onPageStarted(v: WebView, u: String?, f: Bitmap?) { t0 = System.currentTimeMillis() }
        override fun onPageFinished(view: WebView, url: String?) {
            view.postDelayed({
                view.evaluateJavascript(
                    """(function(){var t=document.querySelector('table tbody')||document.querySelector('table');return t?t.outerHTML:document.documentElement.outerHTML;})()"""
                ) { encoded ->
                    val h = decodeJs(encoded)
                    if (h.isBlank()) { onError(reqUrl(view), "页面内容为空"); return@evaluateJavascript }
                    val isCh = h.contains("\$_ss=") && !h.contains("<tr")
                    if (isCh && System.currentTimeMillis() - t0 < 12_000L) {
                        view.postDelayed({
                            view.evaluateJavascript(
                                """(function(){var t=document.querySelector('table tbody')||document.querySelector('table');return t?t.outerHTML:document.documentElement.outerHTML;})()"""
                            ) { e2 ->
                                val h2 = decodeJs(e2)
                                if (h2.isBlank() || h2.contains("\$_ss=")) onError(reqUrl(view), "校验未通过")
                                else onHtml(reqUrl(view), h2)
                            }
                        }, 600)
                        return@evaluateJavascript
                    }
                    if (isCh) onError(reqUrl(view), "校验未通过")
                    else onHtml(reqUrl(view), h)
                }
            }, 350)
        }
        override fun onReceivedHttpError(v: WebView, req: WebResourceRequest, err: WebResourceResponse) {
            if (req.isForMainFrame && err.statusCode != 412)
                onError(reqUrl(v), "HTTP ${err.statusCode}")
        }
    }
}

// ─── 共享小部件 ──────────────────────────────────────────

@Composable
private fun DotSep() {
    Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BadgeChip(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(10.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun LoadingFooter(msg: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EndFooter() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text("已经到底啦", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun reqUrl(wv: WebView) = (wv.tag as? String)?.substringBeforeLast("#") ?: wv.url.orEmpty()

private fun decodeJs(v: String?): String {
    if (v.isNullOrBlank() || v == "null") return ""
    return runCatching { org.json.JSONArray("[$v]").getString(0) }.getOrDefault(v)
}
