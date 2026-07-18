package com.ahu_plus.ui.screen.dashboard

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray

@Composable
internal fun JwcWafBootstrap(
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
                    userAgentString = JWC_WEBVIEW_USER_AGENT
                }
                webViewClient = JwcWafWebViewClient(url, onCookie, onError)
                webView = this
                loadUrl(url)
            }
        },
    )
}

private class JwcWafWebViewClient(
    private val challengeUrl: String,
    private val onCookie: (String) -> Unit,
    private val onError: (String) -> Unit,
) : WebViewClient() {
    private val startedAt = System.currentTimeMillis()
    private var delivered = false

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = Unit

    override fun onPageFinished(view: WebView, url: String?) {
        pollUntilReady(view)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) fail("教务处安全校验加载失败")
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        response: WebResourceResponse,
    ) {
        if (request.isForMainFrame && response.statusCode != 412) {
            fail("教务处安全校验失败：HTTP ${response.statusCode}")
        }
    }

    private fun pollUntilReady(view: WebView) {
        if (delivered) return
        view.postDelayed({
            if (delivered) return@postDelayed
            view.evaluateJavascript(WAF_READY_JS) { encoded ->
                if (decodeJsString(encoded) == "ready") {
                    val cookieManager = CookieManager.getInstance()
                    val cookies = cookieManager.getCookie(challengeUrl).orEmpty()
                    if (cookies.contains("EdaP18tkVMlRP=")) {
                        delivered = true
                        cookieManager.flush()
                        onCookie(cookies)
                        return@evaluateJavascript
                    }
                }
                if (System.currentTimeMillis() - startedAt >= WAF_TIMEOUT_MILLIS) {
                    fail("教务处安全校验超时，请重试")
                } else {
                    pollUntilReady(view)
                }
            }
        }, WAF_POLL_INTERVAL_MILLIS)
    }

    private fun fail(message: String) {
        if (delivered) return
        delivered = true
        onError(message)
    }
}

private fun decodeJsString(value: String?): String {
    if (value.isNullOrBlank() || value == "null") return ""
    return runCatching { JSONArray("[$value]").getString(0) }.getOrDefault(value)
}

private const val WAF_READY_JS =
    "(function(){return document.querySelector('#wp_news_w14, .news_list') ? 'ready' : 'wait';})();"
private const val WAF_TIMEOUT_MILLIS = 15_000L
private const val WAF_POLL_INTERVAL_MILLIS = 500L
internal const val JWC_WEBVIEW_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
