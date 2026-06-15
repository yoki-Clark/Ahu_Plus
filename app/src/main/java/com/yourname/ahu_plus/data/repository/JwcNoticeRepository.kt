package com.yourname.ahu_plus.data.repository

import com.yourname.ahu_plus.data.model.JwcNotice
import com.yourname.ahu_plus.data.model.JwcNoticeDetail
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import okhttp3.OkHttpClient
import okhttp3.Request

class JwcNoticeRepository(
    private val baseUrl: String = "https://jwc.ahu.edu.cn",
    private val channelId: String = "10314",
    private val client: OkHttpClient = SecureHttpClientFactory.create(
        followRedirects = true,
        disableGzip = false
    )
) {
    /** 通知公告频道首页（用于首页 preview 与分页第一页）。 */
    fun listUrl(page: Int = 1): String = absoluteUrl(pagePath(page))

    private fun pagePath(page: Int): String {
        val safePage = page.coerceAtLeast(1)
        return if (safePage == 1) "/$channelId/list.htm" else "/$channelId/list$safePage.htm"
    }

    suspend fun getNotices(limit: Int = 6): Result<List<JwcNotice>> {
        return getNoticesByPage(1, limit)
    }

    /**
     * 拉取指定页的通知公告。
     *
     * 注意：教务处网站带 JS 挑战 (`$_ss=` token),HTTP 直连大概率拿到 412/挑战页,
     * 真实使用应走 WebView 提取路径 (`JwcHtmlLoader`)。此方法保留作为兜底和单测。
     */
    suspend fun getNoticesByPage(page: Int, limit: Int = Int.MAX_VALUE): Result<List<JwcNotice>> {
        return try {
            val html = executeGet(listUrl(page))
            val notices = parseNoticeList(html, baseUrl).let { all ->
                if (limit == Int.MAX_VALUE) all else all.take(limit)
            }
            if (notices.isEmpty()) {
                Result.failure(Exception("未读取到通知公告"))
            } else {
                Result.success(notices)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNoticeDetail(notice: JwcNotice): Result<JwcNoticeDetail> {
        return try {
            val html = executeGet(notice.url)
            Result.success(parseNoticeDetail(html, notice))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception("教务处网站返回 HTTP ${response.code}")
            }
            if (body.contains("\$_ss=") && !body.contains("wp_news_w14")) {
                throw Exception("教务处网站返回了访问校验页，请稍后重试")
            }
            return body
        }
    }

    private fun absoluteUrl(pathOrUrl: String): String {
        return absolutize(pathOrUrl, baseUrl)
    }

    companion object {
        private val listItemRegex = Regex(
            """<li\b[^>]*class=["'][^"']*\bnews\b[^"']*["'][^>]*>(.*?)</li>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val anchorRegex = Regex(
            """<a\b[^>]*href=["']([^"']+)["'][^>]*?(?:title=["']([^"']*)["'])?[^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val postTimeRegex = Regex(
            """class=["'][^"']*\bpost_time\b[^"']*["'][^>]*>(.*?)</span>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val dateRegex = Regex("""\d{4}-\d{2}-\d{2}""")
        private val titleRegex = Regex(
            """<h1\b[^>]*class=["'][^"']*\barti_title\b[^"']*["'][^>]*>(.*?)</h1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val articleRegex = Regex(
            """<div\b[^>]*class=["'][^"']*\bwp_articlecontent\b[^"']*["'][^>]*>(.*?)</div>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val styleScriptRegex = Regex(
            """<(script|style)\b[^>]*>.*?</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val tagRegex = Regex("""<[^>]+>""")
        private val whitespaceLineRegex = Regex("""[ \t\x0B\f\r]+""")
        private val blankLinesRegex = Regex("""\n{3,}""")
        // 匹配分页区里的 list{N}.htm 链接,用于从 HTML 中检测可用的下一页/最大页
        private val paginationListRegex = Regex(
            """href=["']([^"']*?list(\d+)\.htm)["']""",
            setOf(RegexOption.IGNORE_CASE)
        )
        private val nextPageLabelRegex = Regex(
            """<a\b[^>]*?href=["']([^"']+?)["'][^>]*>([^<]*(?:下\s?一\s?页|next)[^<]*)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        fun parseNoticeList(html: String, baseUrl: String = "https://jwc.ahu.edu.cn"): List<JwcNotice> {
            return listItemRegex.findAll(html).mapNotNull { itemMatch ->
                val itemHtml = itemMatch.groupValues[1]
                val anchor = anchorRegex.find(itemHtml) ?: return@mapNotNull null
                val href = htmlDecode(anchor.groupValues[1]).trim()
                val titleFromAttr = anchor.groups[2]?.value.orEmpty()
                val title = plainText(titleFromAttr.ifBlank { anchor.groupValues[3] })
                val date = postTimeRegex.find(itemHtml)
                    ?.groupValues
                    ?.get(1)
                    ?.let(::plainText)
                    ?.let { dateRegex.find(it)?.value ?: it }
                    .orEmpty()

                if (title.isBlank() || href.isBlank()) {
                    null
                } else {
                    JwcNotice(
                        title = title,
                        date = date,
                        url = absolutize(href, baseUrl)
                    )
                }
            }.toList()
        }

        /**
         * 从列表页 HTML 中推断是否有下一页,并返回建议的下一页 URL(相对路径)。
         *
         * 解析优先级:
         *  1. 显式 "下一页" 链接 → 该 href 即下一页
         *  2. 任意 list{N}.htm 链接 → 取最大的 N
         *  3. 否则视为无更多页
         */
        fun parseNextPagePath(html: String): String? {
            val next = nextPageLabelRegex.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            if (next != null) {
                // "下一页"被禁用时常渲染为 "下一页" 或 "下页" 类的 <span>/无链接,这里加兜底判断
                if (!next.contains("javascript", ignoreCase = true)) return next
            }
            val pages = paginationListRegex.findAll(html)
                .mapNotNull { it.groupValues[2].toIntOrNull() }
                .toList()
            return pages.maxOrNull()?.let { if (it >= 2) "list$it.htm" else null }
        }

        fun parseNoticeDetail(html: String, fallback: JwcNotice): JwcNoticeDetail {
            val parsedTitle = titleRegex.find(html)?.groupValues?.get(1)?.let(::plainText)
            val articleHtml = articleRegex.find(html)?.groupValues?.get(1)
            val content = articleHtml
                ?.let(::plainText)
                ?.takeIf { it.isNotBlank() }
                ?: plainText(html).take(1000)
            val parsedDate = dateRegex.find(html)?.value ?: fallback.date.takeIf { it.isNotBlank() }

            return JwcNoticeDetail(
                title = parsedTitle?.takeIf { it.isNotBlank() } ?: fallback.title,
                date = parsedDate,
                content = content,
                url = fallback.url
            )
        }

        private fun absolutize(pathOrUrl: String, baseUrl: String): String {
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                return pathOrUrl
            }
            val normalizedBase = baseUrl.trimEnd('/')
            val normalizedPath = if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"
            return normalizedBase + normalizedPath
        }

        private fun plainText(html: String): String {
            return html
                .replace(Regex("""<(br|p|div|tr|li)\b[^>]*>""", RegexOption.IGNORE_CASE), "\n")
                .replace(styleScriptRegex, "")
                .replace(tagRegex, "")
                .let(::htmlDecode)
                .replace(whitespaceLineRegex, " ")
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .replace(blankLinesRegex, "\n\n")
                .trim()
        }

        private fun htmlDecode(value: String): String {
            return value
                .replace("&nbsp;", " ")
                .replace("&ensp;", " ")
                .replace("&emsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&ldquo;", "“")
                .replace("&rdquo;", "”")
                .replace("&mdash;", "—")
                .replace(Regex("""&#(\d+);""")) { match ->
                    match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
                }
        }
    }
}
