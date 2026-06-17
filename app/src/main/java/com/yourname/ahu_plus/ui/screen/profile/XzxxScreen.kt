package com.yourname.ahu_plus.ui.screen.profile

import android.graphics.Bitmap
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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.yourname.ahu_plus.data.repository.XzxxRepository
import com.yourname.ahu_plus.ui.components.AhuTopAppBar

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

    // ── 写信页 WebView ──
    if (showCompose) {
        BackHandler(enabled = true) { showCompose = false }
        XzxxComposeScreen(
            url = repository.writeUrl(),
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
        shape = RoundedCornerShape(12.dp),
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
            Surface(color = Color(0xFFFEF7E0), shape = RoundedCornerShape(8.dp)) {
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

// ─── 写信页 WebView ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XzxxComposeScreen(url: String, onBack: () -> Unit) {
    val wvRef = remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        onDispose { wvRef.value?.apply { stopLoading(); (parent as? ViewGroup)?.removeView(this); destroy() } }
    }
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
            AndroidView(
                modifier = Modifier.fillMaxSize(),
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
                            override fun onPageStarted(v: WebView, u: String?, f: Bitmap?) { loading = true }
                            override fun onPageFinished(v: WebView, u: String?) {
                                loading = false
                                injectComposeCleanup(v)
                            }
                        }
                        wvRef.value = this
                        loadUrl(url)
                    }
                }
            )
            if (loading) {
                androidx.compose.material3.LinearProgressIndicator(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

private fun injectComposeCleanup(view: WebView) {
    view.evaluateJavascript(
        """
        (function(){
            try {
                if (document.getElementById('ahu-xzxx-rebuild')) return;
                var h = document.documentElement.outerHTML||'';
                if (h.indexOf("${'$'}_ss=")>=0) return;

                /* 1. 从原页面提取数据 */
                var action = 'save.asp';
                var f = document.querySelector('form[name="form1"]');
                if (f) action = f.getAttribute('action') || 'save.asp';
                var depVal = '校办';
                var di = document.querySelector('input[name="depname"]');
                if (di) depVal = di.value||'校办';

                /* 2. 用我们自己的干净 HTML 替换整个 body */
                document.body.innerHTML =
                    '<div id="ahu-compose-form" style="padding:14px;background:#f5f6f8;min-height:100vh;">'+
                    '<form name="form1" method="post" action="'+action+'" style="display:flex;flex-direction:column;gap:14px;">'+
                    '<input name="depname" value="'+depVal+'" type="hidden">'+
                    '<div style="display:flex;gap:12px;align-items:flex-start;">'+
                    '<div style="flex:1;">'+
                    '<label style="display:block;font-size:14px;font-weight:600;color:#202124;margin-bottom:6px;">联 系 人 <span style="color:#d93025;">*</span></label>'+
                    '<input name="uname" type="text" maxlength="8" placeholder="请输入姓名" style="width:100%;height:44px;padding:0 12px;font-size:15px;border:1px solid #dadce0;border-radius:8px;background:#fff;color:#202124;outline:none;box-sizing:border-box;">'+
                    '</div>'+
                    '<div style="flex-shrink:0;">'+
                    '<label style="display:block;font-size:14px;font-weight:600;color:#202124;margin-bottom:6px;">验 证 码 <span style="color:#d93025;">*</span></label>'+
                    '<div style="display:flex;gap:8px;align-items:center;">'+
                    '<input name="checkcode" type="text" maxlength="4" placeholder="4位" style="width:80px;height:44px;padding:0 8px;font-size:15px;text-align:center;border:1px solid #dadce0;border-radius:8px;background:#fff;outline:none;box-sizing:border-box;">'+
                    '<img src="checkcode.asp" onclick="this.src=\\'checkcode.asp?\\'+Date.now()" style="height:44px;border:1px solid #dadce0;border-radius:6px;cursor:pointer;">'+
                    '</div></div></div>'+
                    '<div>'+
                    '<label style="display:block;font-size:14px;font-weight:600;color:#202124;margin-bottom:6px;">联系方式</label>'+
                    '<input name="telnum" type="text" maxlength="30" placeholder="电话/邮箱（选填）" style="width:100%;height:44px;padding:0 12px;font-size:15px;border:1px solid #dadce0;border-radius:8px;background:#fff;color:#202124;outline:none;box-sizing:border-box;">'+
                    '</div>'+
                    '<div>'+
                    '<label style="display:block;font-size:14px;font-weight:600;color:#202124;margin-bottom:6px;">发信主题 <span style="color:#d93025;">*</span></label>'+
                    '<input name="title" type="text" maxlength="50" placeholder="请简要概括您的来信内容" style="width:100%;height:44px;padding:0 12px;font-size:15px;border:1px solid #dadce0;border-radius:8px;background:#fff;color:#202124;outline:none;box-sizing:border-box;">'+
                    '</div>'+
                    '<div>'+
                    '<label style="display:block;font-size:14px;font-weight:600;color:#202124;margin-bottom:6px;">发信内容 <span style="color:#d93025;">*</span></label>'+
                    '<textarea name="content" rows="8" placeholder="请理性、客观地陈述您的意见或建议..." style="width:100%;min-height:180px;padding:11px 12px;font-size:15px;border:1px solid #dadce0;border-radius:8px;background:#fff;color:#202124;resize:vertical;outline:none;box-sizing:border-box;font-family:inherit;"></textarea>'+
                    '</div>'+
                    '<div style="text-align:center;padding:4px 0;">'+
                    '<button type="submit" onclick="return confirm(\\'提示:一旦提交不可修改?\\\\n是否确定要提交?\\')" style="width:100%;max-width:320px;height:46px;background:#1a73e8;color:#fff;border:none;border-radius:8px;font-size:15px;font-weight:500;cursor:pointer;">提 交</button>'+
                    '</div>'+
                    '<div style="text-align:center;">'+
                    '<a href="list.asp" style="color:#1a73e8;text-decoration:none;font-size:14px;">查看公开的回复</a>'+
                    '</div>'+
                    '</form></div>';

                /* 3. 焦点样式 */
                var st = document.createElement('style');
                st.id = 'ahu-xzxx-rebuild';
                st.textContent = 'input:focus,textarea:focus{border-color:#1a73e8!important;box-shadow:0 0 0 2px rgba(26,115,232,0.15)!important;}'+
                    'button:active{background:#1557b0!important;}';
                (document.head||document.documentElement).appendChild(st);
            } catch(e) {}
        })();
        """.trimIndent(), null
    )
}


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
