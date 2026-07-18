package com.ahu_plus.data.repository

import com.ahu_plus.data.local.XzxxWafCookie
import com.ahu_plus.data.local.XzxxWafCookieStorage
import com.ahu_plus.data.model.XzxxCaptcha
import com.ahu_plus.data.model.XzxxLetter
import com.ahu_plus.data.model.XzxxLetterDetail
import com.ahu_plus.data.model.XzxxPage
import com.ahu_plus.data.model.XzxxSubmitRequest
import com.ahu_plus.data.model.XzxxSubmitResult
import com.ahu_plus.data.network.SecureHttpClientFactory
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class XzxxWafChallengeRequiredException : IOException("校长信箱需要完成安全校验")

class XzxxRepository(
    private val cookieStorage: XzxxWafCookieStorage,
    private val baseUrl: String = "https://www6.ahu.edu.cn",
    clientFactory: (CookieJar) -> OkHttpClient = { cookieJar ->
        SecureHttpClientFactory.create(
            cookieJar = cookieJar,
            followRedirects = false,
            trustAll = false,
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

    fun listUrl(page: Int = 1): String {
        val safePage = page.coerceAtLeast(1)
        return rootUrl.newBuilder()
            .addPathSegments("xzxx/list.asp")
            .apply { if (safePage > 1) addQueryParameter("page", safePage.toString()) }
            .build()
            .toString()
    }

    fun writeUrl(): String = endpoint("xzxx/add.asp").toString()

    fun detailUrl(contentId: String): String = endpoint("xzxx/show.asp").newBuilder()
        .addQueryParameter("contentid", contentId)
        .build()
        .toString()

    fun challengeUrl(): String = listUrl(1)

    suspend fun getLetters(page: Int): XzxxPage = withContext(Dispatchers.IO) {
        ensurePersistentCookieLoaded()
        val html = executeHtml(Request.Builder().url(listUrl(page)).get().build())
        val letters = parseLetterList(html, baseUrl)
        XzxxPage(
            letters = letters,
            hasMore = parseNextPageUrl(html, page) != null,
        )
    }

    suspend fun getLetterDetail(contentId: String): XzxxLetterDetail = withContext(Dispatchers.IO) {
        ensurePersistentCookieLoaded()
        val html = executeHtml(Request.Builder().url(detailUrl(contentId)).get().build())
        parseLetterDetail(html) ?: throw IOException("暂未获取到信件内容")
    }

    suspend fun prepareCompose(): XzxxCaptcha = withContext(Dispatchers.IO) {
        ensurePersistentCookieLoaded()
        executeHtml(Request.Builder().url(writeUrl()).get().build())
        fetchCaptcha()
    }

    suspend fun refreshCaptcha(): XzxxCaptcha = withContext(Dispatchers.IO) {
        ensurePersistentCookieLoaded()
        if (!hasAspSession()) {
            executeHtml(Request.Builder().url(writeUrl()).get().build())
        }
        fetchCaptcha()
    }

    suspend fun submitLetter(request: XzxxSubmitRequest): XzxxSubmitResult = withContext(Dispatchers.IO) {
        ensurePersistentCookieLoaded()
        if (!hasAspSession()) throw IOException("验证码会话已过期，请刷新验证码")

        val form = FormBody.Builder(GBK)
            .add("uname", request.uname)
            .add("checkcode", request.checkcode)
            .add("telnum", request.telnum)
            .add("depname", "校办")
            .add("title", request.title)
            .add("content", request.content)
            .build()
        val httpRequest = Request.Builder()
            .url(endpoint("xzxx/save.asp"))
            .header("Origin", rootUrl.toString().trimEnd('/'))
            .header("Referer", writeUrl())
            .post(form)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (response.code == 412) {
                invalidateWafCookie()
                throw XzxxWafChallengeRequiredException()
            }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            val html = bytes.toString(GBK)
            if (html.contains("\$_ss=") && !html.contains("<form", ignoreCase = true)) {
                invalidateWafCookie()
                throw XzxxWafChallengeRequiredException()
            }
            if (response.code in 300..399) return@withContext XzxxSubmitResult(true, "提交成功")
            if (!response.isSuccessful) throw IOException("校长信箱请求失败：HTTP ${response.code}")
            parseSubmitResult(html, response.code)
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
        cookieStorage.save(XzxxWafCookie(value, expiresAt))
        restored = true
        validateWafAccess()
    }

    suspend fun validateWafAccess(): Boolean = withContext(Dispatchers.IO) {
        ensurePersistentCookieLoaded()
        val request = Request.Builder().url(listUrl(1)).get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 412) {
                invalidateWafCookie()
                return@withContext false
            }
            if (!response.isSuccessful) return@withContext false
            val body = (response.body?.bytes() ?: ByteArray(0)).toString(GBK)
            if (body.contains("\$_ss=") && !body.contains("<tr", ignoreCase = true)) {
                invalidateWafCookie()
                false
            } else {
                true
            }
        }
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

    private suspend fun executeHtml(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (response.code == 412) {
                invalidateWafCookie()
                throw XzxxWafChallengeRequiredException()
            }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            val html = bytes.toString(GBK)
            if (html.contains("\$_ss=") && !html.contains("<tr", ignoreCase = true)) {
                invalidateWafCookie()
                throw XzxxWafChallengeRequiredException()
            }
            if (!response.isSuccessful) throw IOException("校长信箱请求失败：HTTP ${response.code}")
            return html
        }
    }

    private suspend fun fetchCaptcha(): XzxxCaptcha {
        val url = endpoint("xzxx/checkcode.asp").newBuilder()
            .addQueryParameter("_", nowMillis().toString())
            .build()
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (response.code == 412) {
                invalidateWafCookie()
                throw XzxxWafChallengeRequiredException()
            }
            if (!response.isSuccessful) throw IOException("验证码获取失败：HTTP ${response.code}")
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (bytes.isEmpty()) throw IOException("验证码内容为空")
            return XzxxCaptcha(bytes)
        }
    }

    private fun hasAspSession(): Boolean = cookieStore.values.any {
        it.name.startsWith(ASP_SESSION_PREFIX, ignoreCase = true) && it.expiresAt >= nowMillis()
    }

    private suspend fun invalidateWafCookie() {
        cookieStore.entries.removeIf { it.value.name == WAF_COOKIE_NAME }
        cookieStorage.clear()
        restored = false
    }

    private fun endpoint(path: String): HttpUrl = rootUrl.newBuilder().addPathSegments(path).build()

    private fun cookieSlot(cookie: Cookie): String = "${cookie.name}|${cookie.domain}|${cookie.path}"
    private fun cookieSlot(name: String, path: String): String = "$name|${rootUrl.host}|$path"

    companion object {
        private val GBK: Charset = Charset.forName("GBK")
        private const val WAF_COOKIE_NAME = "EdaP18tkVMlRP"
        private const val ASP_SESSION_PREFIX = "ASPSESSIONID"
        private const val WAF_COOKIE_TTL_MILLIS = 6L * 24 * 60 * 60 * 1000

        internal fun extractCookieValue(header: String, name: String): String? = header
            .split(';')
            .map { it.trim() }
            .firstOrNull { it.substringBefore('=', "") == name }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotBlank() }

        fun parseSubmitResult(html: String, httpCode: Int): XzxxSubmitResult {
            val lower = html.lowercase()
            return when {
                httpCode in 300..399 -> XzxxSubmitResult(true, "提交成功")
                html.contains("提交成功") || html.contains("感谢您的来信") ->
                    XzxxSubmitResult(true, "提交成功")
                html.contains("验证码") && (html.contains("错误") || html.contains("不正确") || html.contains("wrong")) ->
                    XzxxSubmitResult(false, "验证码错误，请重新输入")
                html.contains("联系人") && html.contains("不能为空") ->
                    XzxxSubmitResult(false, "请填写联系人姓名")
                html.contains("主题") && html.contains("不能为空") ->
                    XzxxSubmitResult(false, "请填写发信主题")
                html.contains("内容") && html.contains("不能为空") ->
                    XzxxSubmitResult(false, "请填写发信内容")
                lower.contains("<html") && !lower.contains("\$_ss=") ->
                    XzxxSubmitResult(false, "提交失败，请检查填写内容")
                else -> XzxxSubmitResult(false, "提交失败，请稍后重试")
            }
        }

        private val rowRegex = Regex("""<tr\b[^>]*>(.*?)</tr>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val cellRegex = Regex("""<td\b[^>]*>(.*?)</td>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val anchorRegex = Regex("""<a\b([^>]*)>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val hrefRegex = Regex("""\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
        private val contentIdRegex = Regex("""contentid=(\d+)""", RegexOption.IGNORE_CASE)
        private val dateTextRegex = Regex("""\d{4}\s*[-/.年]\s*\d{1,2}\s*[-/.月]\s*\d{1,2}""")
        private val tagRegex = Regex("""<[^>]+>""")
        private val styleScriptRegex = Regex("""<(script|style)\b[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val wsRegex = Regex("""\s+""")
        private val blankLinesRegex = Regex("""\n{3,}""")
        private val paginationAspRegex = Regex("""href=["']([^"']*?list\.asp\?page=(\d+))["']""", RegexOption.IGNORE_CASE)
        private val paginationHtmRegex = Regex("""href=["']([^"']*?list(\d+)\.htm)["']""", RegexOption.IGNORE_CASE)
        private val nextPageLabelRegex = Regex("""<a\b[^>]*?href=["']([^"']+?)["'][^>]*>([^<]*(?:下\s?一\s?页|next)[^<]*)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        fun parseLetterList(html: String, baseUrl: String = "https://www6.ahu.edu.cn"): List<XzxxLetter> {
            return rowRegex.findAll(html).mapNotNull { match ->
                val row = match.groupValues[1]
                val cells = cellRegex.findAll(row).map { it.groupValues[1] }.toList()
                val titleCellIndex = cells.indexOfFirst { cell ->
                    contentIdRegex.containsMatchIn(extractAnchorHref(cell))
                }
                if (titleCellIndex < 0) return@mapNotNull null
                val href = extractAnchorHref(cells[titleCellIndex])
                val contentId = contentIdRegex.find(href)?.groupValues?.get(1).orEmpty()
                val title = extractAnchorText(cells[titleCellIndex])
                if (title.isBlank() || contentId.isBlank()) return@mapNotNull null
                val metadata = cells.mapIndexedNotNull { index, cell ->
                    if (index == titleCellIndex) null else plainText(cell).trim()
                }
                val dates = metadata.filter(dateTextRegex::containsMatchIn)
                val viewCount = metadata
                    .filterNot(dateTextRegex::containsMatchIn)
                    .firstOrNull { value -> value.any(Char::isDigit) }
                    .orEmpty()
                XzxxLetter(
                    contentId = contentId,
                    viewCount = viewCount,
                    title = title,
                    writeDate = dates.getOrNull(0).orEmpty(),
                    replyDate = dates.getOrNull(1).orEmpty(),
                    url = absolutize(href, baseUrl),
                )
            }.toList()
        }

        fun parseNextPageUrl(html: String, current: Int): String? {
            nextPageLabelRegex.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                ?.let { if (!it.contains("javascript", true)) return absolutize(it, "/xzxx/") }
            paginationAspRegex.findAll(html).mapNotNull { it.groupValues[2].toIntOrNull() }
                .filter { it > current }.minOrNull()?.let { return absolutize("list.asp?page=$it", "/xzxx/") }
            paginationHtmRegex.findAll(html).mapNotNull { it.groupValues[2].toIntOrNull() }
                .filter { it > current }.minOrNull()?.let { return absolutize("list$it.htm", "/xzxx/") }
            return null
        }

        fun parseLetterDetail(html: String): XzxxLetterDetail? {
            var title = ""
            var content = ""
            var replyLabel = ""
            var replyContent = ""
            for (match in rowRegex.findAll(html)) {
                val row = match.groupValues[1]
                if (row.contains("colspan", true) || row.contains("#41b6f7", true) ||
                    row.contains("校长信箱致辞") || row.contains(".jpg") || row.contains(".png") || row.contains(".gif")) continue
                val cells = cellRegex.findAll(row).map { it.groupValues[1] }.toList()
                if (cells.size < 2) continue
                val rawLabel = plainText(cells[0]).trim()
                val rawValue = plainText(cells[1]).trim()
                if (rawValue.isBlank()) continue
                val label = rawLabel.replace(Regex("""[\s　]+"""), "")
                when {
                    label.contains("主题") || label.contains("标题") -> title = rawValue
                    label.contains("内容") -> content = rawValue
                    label.contains("回复") || label.contains("已回") || label.contains("未回") ||
                        label.contains("答复") || label.contains("已答") -> {
                        replyLabel = rawLabel
                        replyContent = rawValue
                    }
                }
            }
            return content.takeIf { it.isNotBlank() }?.let {
                XzxxLetterDetail(title, content, replyLabel, replyContent)
            }
        }

        private fun extractAnchorText(cellHtml: String): String =
            plainText(anchorRegex.find(cellHtml)?.groupValues?.get(2) ?: cellHtml)

        private fun extractAnchorHref(cellHtml: String): String {
            val attributes = anchorRegex.find(cellHtml)?.groupValues?.get(1) ?: return ""
            val hrefMatch = hrefRegex.find(attributes) ?: return ""
            return (1..3).asSequence()
                .map { hrefMatch.groupValues[it] }
                .firstOrNull { it.isNotBlank() }
                ?.let(::htmlDecode)
                ?.trim()
                .orEmpty()
        }

        private fun absolutize(pathOrUrl: String, baseUrl: String): String {
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl
            return baseUrl.trimEnd('/') + if (pathOrUrl.startsWith('/')) pathOrUrl else "/$pathOrUrl"
        }

        private fun plainText(html: String): String = html
            .replace(Regex("""<(br|p|div|tr|li|hr)\b[^>]*>""", RegexOption.IGNORE_CASE), "\n")
            .replace(styleScriptRegex, "").replace(tagRegex, "").let(::htmlDecode)
            .replace(wsRegex, " ").lines().map { it.trim() }.filter { it.isNotBlank() }
            .joinToString("\n").replace(blankLinesRegex, "\n\n").trim()

        private fun htmlDecode(value: String): String = value
            .replace("&nbsp;", " ").replace("&ensp;", " ").replace("&emsp;", " ")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&ldquo;", "“").replace("&rdquo;", "”").replace("&mdash;", "—")
            .replace(Regex("""&#(\d+);""")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
            }
    }
}
