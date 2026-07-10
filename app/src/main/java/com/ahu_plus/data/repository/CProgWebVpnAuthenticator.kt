package com.ahu_plus.data.repository

import com.ahu_plus.util.DES
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException

/** Establishes an authenticated WebVPN session through the proxied AHU CAS flow. */
internal class CProgWebVpnAuthenticator(
    private val client: OkHttpClient,
    private val portalUrl: HttpUrl,
    private val webVpnLoginUrl: HttpUrl,
    private val credentials: () -> Pair<String, String>?,
    private val userAgent: String = DEFAULT_UA,
) {
    fun authenticate() {
        val entryPage = resolvePage(
            Request.Builder().url(portalUrl).header("User-Agent", userAgent).build(),
            context = "WebVPN 入口",
        )
        if (!isCasForm(entryPage.html)) {
            activateWebVpnLogin()
            return
        }

        val casLoginUrl = entryPage.url
        val loginHtml = entryPage.html
        val document = Jsoup.parse(loginHtml)
        val lt = document.selectFirst("input[name=lt]")?.attr("value")?.takeIf(String::isNotBlank)
            ?: throw IOException("WebVPN CAS 页面缺少 lt")
        val execution = document.selectFirst("input[name=execution]")?.attr("value")?.takeIf(String::isNotBlank)
            ?: throw IOException("WebVPN CAS 页面缺少 execution")
        val (username, password) = credentials()
            ?: throw IOException("非校园网访问需要先登录安大统一身份认证")
        val encrypted = DES.strEnc(username + password + lt, "1", "2", "3")
        val ul = username.length.toString()
        val pl = password.length.toString()

        val deviceUrl = casLoginUrl.newBuilder()
            .removePathSegment(casLoginUrl.pathSize - 1)
            .addPathSegment("device")
            .query(null)
            .addQueryParameter(DEVICE_PROXY_MARKER, null)
            .build()
        val deviceBody = FormBody.Builder()
            .add("ul", ul)
            .add("pl", pl)
            .add("rsa", encrypted)
            .add("method", "login")
            .build()
        execute(
            Request.Builder().url(deviceUrl)
                .header("User-Agent", userAgent)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", casLoginUrl.toString())
                .post(deviceBody)
                .build()
        ).use { response ->
            if (!response.isSuccessful) throw IOException("WebVPN CAS 预验证 HTTP ${response.code}")
            val info = runCatching {
                JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject.get("info")?.asString
            }.getOrNull()
            when (info) {
                "ok" -> Unit
                "nf" -> throw IOException("统一身份认证用户名或密码错误")
                else -> throw IOException("WebVPN CAS 预验证失败")
            }
        }

        val loginBody = FormBody.Builder()
            .add("rsa", encrypted)
            .add("ul", ul)
            .add("pl", pl)
            .add("lt", lt)
            .add("execution", execution)
            .add("_eventId", "submit")
            .build()
        val redirectUrl = execute(
            Request.Builder().url(casLoginUrl)
                .header("User-Agent", userAgent)
                .header("Referer", casLoginUrl.toString())
                .post(loginBody)
                .build()
        ).use(::requireRedirectUrl)
        followRedirects(redirectUrl, referer = casLoginUrl)
        activateWebVpnLogin()
    }

    private fun activateWebVpnLogin() {
        val page = resolvePage(
            Request.Builder().url(webVpnLoginUrl)
                .header("User-Agent", userAgent)
                .header("Referer", portalUrl.toString())
                .build(),
            context = "WebVPN 激活",
        )
        if (isCasForm(page.html)) throw IOException("WebVPN 激活仍停留在登录页")
    }

    private fun followRedirects(initialUrl: HttpUrl, referer: HttpUrl) {
        val page = resolvePage(
            Request.Builder().url(initialUrl)
                .header("User-Agent", userAgent)
                .header("Referer", referer.toString())
                .build(),
            context = "WebVPN 登录跳转",
        )
        if (isCasForm(page.html)) throw IOException("WebVPN 统一身份认证失败")
    }

    private fun resolvePage(initialRequest: Request, context: String): ResolvedPage {
        var request = initialRequest
        val chainReferer = initialRequest.header("Referer")
        repeat(MAX_REDIRECTS + 1) {
            execute(request).use { response ->
                if (response.isRedirect) {
                    val redirectUrl = requireRedirectUrl(response)
                    request = Request.Builder().url(redirectUrl)
                        .header("User-Agent", userAgent)
                        .apply {
                            if (chainReferer != null) header("Referer", chainReferer)
                        }
                        .build()
                } else {
                    if (!response.isSuccessful) throw IOException("$context HTTP ${response.code}")
                    return ResolvedPage(
                        url = response.request.url,
                        html = response.body?.string().orEmpty(),
                    )
                }
            }
        }
        throw IOException("$context 重定向次数过多")
    }

    private fun execute(request: Request): Response = client.newCall(request).execute()

    private fun requireRedirectUrl(response: Response): HttpUrl {
        if (!response.isRedirect) throw IOException("WebVPN 登录未返回重定向: HTTP ${response.code}")
        val location = response.header("Location") ?: throw IOException("WebVPN 登录缺少 Location")
        return response.request.url.resolve(location) ?: throw IOException("WebVPN 重定向地址无效")
    }

    private fun isCasForm(html: String): Boolean =
        html.contains("name=\"lt\"") || html.contains("name='lt'")

    private data class ResolvedPage(
        val url: HttpUrl,
        val html: String,
    )

    companion object {
        private const val DEVICE_PROXY_MARKER = "vpn-12-o2-one.ahu.edu.cn"
        private const val MAX_REDIRECTS = 12
        private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
    }
}
