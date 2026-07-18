package com.ahu_plus.data.repository

import com.ahu_plus.data.local.JwcWafCookie
import com.ahu_plus.data.local.JwcWafCookieStorage
import com.ahu_plus.data.model.JwcNotice
import com.ahu_plus.data.model.JwcNoticeAttachment
import com.ahu_plus.data.model.JwcNoticeDetail
import com.ahu_plus.data.network.SecureHttpClientFactory
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class JwcWafChallengeRequiredException : IOException("教务处网站需要完成安全校验")

data class JwcNoticePage(
    val notices: List<JwcNotice>,
    val page: Int,
    val hasMore: Boolean,
)

class JwcNoticeRepository(
    private val cookieStorage: JwcWafCookieStorage = EmptyJwcWafCookieStorage,
    private val baseUrl: String = "https://jwc.ahu.edu.cn",
    private val channelId: String = "10314",
    clientFactory: (CookieJar) -> OkHttpClient = { cookieJar ->
        SecureHttpClientFactory.create(
            cookieJar = cookieJar,
            followRedirects = true,
            disableGzip = false,
            trustAll = true,
        )
    },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val rootUrl = baseUrl.trimEnd('/').toHttpUrl()
    private val cookieStore = ConcurrentHashMap<String, Cookie>()
    private val restoreMutex = Mutex()
    @Volatile private var restored = false

    internal val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = nowMillis()
            cookieStore.entries.removeIf { it.value.expiresAt < now }
            return cookieStore.values.filter { it.matches(url) }
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { cookie -> cookieStore[cookieSlot(cookie)] = cookie }
        }
    }

    private val client = clientFactory(cookieJar)

    fun listUrl(page: Int = 1): String = absoluteUrl(pagePath(page))

    fun challengeUrl(): String = listUrl(1)

    private fun pagePath(page: Int): String {
        val safePage = page.coerceAtLeast(1)
        return if (safePage == 1) "/$channelId/list.htm" else "/$channelId/list$safePage.htm"
    }

    suspend fun getNotices(limit: Int = 6): Result<List<JwcNotice>> = runCatching {
        getNoticePage(1).getOrThrow().notices.take(limit)
    }

    suspend fun getNoticesByPage(page: Int, limit: Int = Int.MAX_VALUE): Result<List<JwcNotice>> = runCatching {
        val notices = getNoticePage(page).getOrThrow().notices
        if (limit == Int.MAX_VALUE) notices else notices.take(limit)
    }

    suspend fun getNoticePage(page: Int): Result<JwcNoticePage> = runCatching {
        withContext(Dispatchers.IO) {
            ensurePersistentCookieLoaded()
            val safePage = page.coerceAtLeast(1)
            val html = executeHtml(
                url = listUrl(safePage),
                contentMarkers = listOf("news_list", "wp_news_w14"),
            )
            val notices = parseNoticeList(html, baseUrl)
            if (notices.isEmpty()) throw IOException("未读取到通知公告")
            JwcNoticePage(
                notices = notices,
                page = safePage,
                hasMore = parseNextPagePath(html, safePage) != null,
            )
        }
    }

    suspend fun getNoticeDetail(notice: JwcNotice): Result<JwcNoticeDetail> = runCatching {
        withContext(Dispatchers.IO) {
            ensurePersistentCookieLoaded()
            val html = executeHtml(
                url = notice.url,
                contentMarkers = listOf("wp_articlecontent", "arti_title"),
            )
            parseNoticeDetail(html, notice)
        }
    }

    suspend fun acceptWafCookies(cookieHeader: String): Boolean = withContext(Dispatchers.IO) {
        val value = extractCookieValue(cookieHeader, WAF_COOKIE_NAME) ?: return@withContext false
        val expiresAt = nowMillis() + WAF_COOKIE_TTL_MILLIS
        cookieStore[cookieSlot(WAF_COOKIE_NAME, "/")] = Cookie.Builder()
            .name(WAF_COOKIE_NAME)
            .value(value)
            .domain(rootUrl.host)
            .path("/")
            .apply { if (rootUrl.isHttps) secure() }
            .expiresAt(expiresAt)
            .build()
        restored = true
        try {
            if (validateWafAccess()) {
                cookieStorage.save(JwcWafCookie(value, expiresAt))
                true
            } else {
                invalidateWafCookie()
                false
            }
        } catch (error: Throwable) {
            invalidateWafCookie()
            throw error
        }
    }

    suspend fun validateWafAccess(): Boolean = withContext(Dispatchers.IO) {
        ensurePersistentCookieLoaded()
        val request = requestBuilder(listUrl(1)).get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 412) {
                invalidateWafCookie()
                return@withContext false
            }
            if (!response.isSuccessful) return@withContext false
            val body = response.body?.string().orEmpty()
            if (isChallengePage(body, listOf("news_list", "wp_news_w14"))) {
                invalidateWafCookie()
                false
            } else {
                true
            }
        }
    }

    suspend fun getCookieHeader(url: String): String = withContext(Dispatchers.IO) {
        ensurePersistentCookieLoaded()
        val httpUrl = url.toHttpUrl()
        cookieJar.loadForRequest(httpUrl).joinToString("; ") { "${it.name}=${it.value}" }
    }

    private suspend fun ensurePersistentCookieLoaded() {
        if (restored) return
        restoreMutex.withLock {
            if (restored) return
            val persisted = cookieStorage.read()
            if (persisted != null && persisted.expiresAtMillis > nowMillis()) {
                val cookie = Cookie.Builder()
                    .name(WAF_COOKIE_NAME)
                    .value(persisted.value)
                    .domain(rootUrl.host)
                    .path("/")
                    .apply { if (rootUrl.isHttps) secure() }
                    .expiresAt(persisted.expiresAtMillis)
                    .build()
                cookieStore[cookieSlot(cookie)] = cookie
            } else if (persisted != null) {
                cookieStorage.clear()
            }
            restored = true
        }
    }

    private suspend fun executeHtml(url: String, contentMarkers: List<String>): String {
        val request = requestBuilder(url).get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 412) {
                invalidateWafCookie()
                throw JwcWafChallengeRequiredException()
            }
            val body = response.body?.string().orEmpty()
            if (isChallengePage(body, contentMarkers)) {
                invalidateWafCookie()
                throw JwcWafChallengeRequiredException()
            }
            if (!response.isSuccessful) throw IOException("教务处网站返回 HTTP ${response.code}")
            return body
        }
    }

    private fun requestBuilder(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36",
        )
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "zh-CN,zh;q=0.9")

    private fun isChallengePage(body: String, contentMarkers: List<String>): Boolean =
        body.contains("\$_ss=") && contentMarkers.none { body.contains(it, ignoreCase = true) }

    private suspend fun invalidateWafCookie() {
        cookieStore.entries.removeIf { it.value.name == WAF_COOKIE_NAME }
        cookieStorage.clear()
        restored = false
    }

    private fun absoluteUrl(pathOrUrl: String): String = absolutize(pathOrUrl, baseUrl)

    private fun cookieSlot(cookie: Cookie): String = "${cookie.name}|${cookie.domain}|${cookie.path}"
    private fun cookieSlot(name: String, path: String): String = "$name|${rootUrl.host}|$path"

    companion object {
        private const val WAF_COOKIE_NAME = "EdaP18tkVMlRP"
        private const val WAF_COOKIE_TTL_MILLIS = 6L * 24 * 60 * 60 * 1000
        private val dateRegex = Regex("""\d{4}-\d{2}-\d{2}""")
        private val attachmentExtensionRegex = Regex(
            """\.(pdf|docx?|xlsx?|pptx?|zip|rar|7z|txt|wps|et|jpg|jpeg|png)(?:[?#].*)?$""",
            RegexOption.IGNORE_CASE,
        )
        private val attachmentHintRegex = Regex("""(附件|下载|download|_upload|/upload/)""", RegexOption.IGNORE_CASE)
        private val listPageRegex = Regex("""list(\d+)\.htm""", RegexOption.IGNORE_CASE)

        internal fun extractCookieValue(header: String, name: String): String? = header
            .split(';')
            .map { it.trim() }
            .firstOrNull { it.substringBefore('=', "") == name }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotBlank() }

        fun parseNoticeList(html: String, baseUrl: String = "https://jwc.ahu.edu.cn"): List<JwcNotice> {
            val document = Jsoup.parse(html, normalizedBaseUrl(baseUrl))
            return document.select("li.news").mapNotNull { item ->
                val anchor = item.selectFirst("a[href]") ?: return@mapNotNull null
                val title = anchor.attr("title").ifBlank(anchor::text).trim()
                val href = anchor.attr("href").trim()
                if (title.isBlank() || href.isBlank()) return@mapNotNull null
                val dateText = item.selectFirst(".post_time")?.text().orEmpty()
                JwcNotice(
                    title = title,
                    date = dateRegex.find(dateText)?.value ?: dateText.trim(),
                    url = anchor.absUrl("href").ifBlank { absolutize(href, baseUrl) },
                )
            }
        }

        fun parseNextPagePath(html: String, currentPage: Int? = null): String? {
            val document = Jsoup.parse(html)
            document.select("a[href]").firstOrNull { anchor ->
                anchor.text().replace(" ", "").let { label ->
                    label.contains("下一页") || label.equals("next", ignoreCase = true)
                } && !anchor.attr("href").startsWith("javascript", ignoreCase = true)
            }?.attr("href")?.takeIf { it.isNotBlank() }?.let { return it }

            val pages = document.select("a[href]").mapNotNull { anchor ->
                listPageRegex.find(anchor.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
            }
            return if (currentPage == null) {
                pages.maxOrNull()?.takeIf { it >= 2 }?.let { "list$it.htm" }
            } else {
                pages.filter { it > currentPage }.minOrNull()?.let { "list$it.htm" }
            }
        }

        fun parseNoticeDetail(html: String, fallback: JwcNotice): JwcNoticeDetail {
            val document = Jsoup.parse(html, fallback.url)
            val article = document.selectFirst(".wp_articlecontent")
            val parsedTitle = document.selectFirst("h1.arti_title")?.text()?.trim()
            val dateText = document.selectFirst(".arti_update, .arti_metas, .arti_info")?.text().orEmpty()
            val content = article?.let(::elementText)?.takeIf { it.isNotBlank() }
                ?: elementText(document.body()).take(1000)

            return JwcNoticeDetail(
                title = parsedTitle?.takeIf { it.isNotBlank() } ?: fallback.title,
                date = dateRegex.find(dateText)?.value
                    ?: dateRegex.find(document.body().text())?.value
                    ?: fallback.date.takeIf { it.isNotBlank() },
                content = content,
                url = fallback.url,
                attachments = parseAttachments(article ?: document.body()),
            )
        }

        private fun parseAttachments(element: Element): List<JwcNoticeAttachment> = element
            .select("a[href]")
            .mapNotNull { anchor ->
                val href = anchor.attr("href").trim()
                if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:", true)) {
                    return@mapNotNull null
                }
                val label = anchor.attr("title").ifBlank(anchor::text).trim()
                val absoluteUrl = anchor.absUrl("href").ifBlank { href }
                val hrefWithoutQuery = href.substringBefore('?').substringBefore('#')
                val looksLikeAttachment = attachmentExtensionRegex.containsMatchIn(hrefWithoutQuery) ||
                    attachmentHintRegex.containsMatchIn(href) ||
                    attachmentHintRegex.containsMatchIn(label)
                if (!looksLikeAttachment) return@mapNotNull null
                JwcNoticeAttachment(
                    name = label.ifBlank { fileNameFromUrl(absoluteUrl) },
                    url = absoluteUrl,
                )
            }
            .distinctBy { it.url }

        private fun elementText(element: Element): String {
            val clone = element.clone()
            clone.select("br").forEach { it.after("\n") }
            clone.select("p, div, tr, li").forEach {
                it.before("\n")
                it.after("\n")
            }
            return clone.wholeText()
                .replace(Regex("""[ \t\x0B\f\r]+"""), " ")
                .lines()
                .map(String::trim)
                .filter(String::isNotBlank)
                .joinToString("\n")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()
        }

        private fun fileNameFromUrl(url: String): String {
            val rawName = runCatching { URI(url).path.substringAfterLast('/') }
                .getOrDefault(url.substringBefore('?').substringAfterLast('/'))
            return runCatching { URLDecoder.decode(rawName, Charsets.UTF_8.name()) }
                .getOrDefault(rawName)
                .ifBlank { "attachment" }
        }

        private fun normalizedBaseUrl(baseUrl: String): String =
            if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"

        private fun absolutize(pathOrUrl: String, baseUrl: String): String {
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl
            return runCatching {
                val base = if (baseUrl.endsWith(".htm", true) || baseUrl.endsWith(".html", true)) {
                    URI(baseUrl)
                } else {
                    URI(baseUrl.trimEnd('/') + "/")
                }
                base.resolve(pathOrUrl).toString()
            }.getOrElse {
                baseUrl.trimEnd('/') + "/" + pathOrUrl.trimStart('/')
            }
        }
    }
}

private object EmptyJwcWafCookieStorage : JwcWafCookieStorage {
    override suspend fun read(): JwcWafCookie? = null
    override suspend fun save(cookie: JwcWafCookie) = Unit
    override suspend fun clear() = Unit
}
