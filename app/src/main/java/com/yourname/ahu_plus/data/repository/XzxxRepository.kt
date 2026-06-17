package com.yourname.ahu_plus.data.repository

import com.yourname.ahu_plus.data.model.XzxxLetter
import com.yourname.ahu_plus.data.model.XzxxLetterDetail

class XzxxRepository(
    private val baseUrl: String = "https://www6.ahu.edu.cn"
) {
    fun listUrl(page: Int = 1): String {
        val safe = page.coerceAtLeast(1)
        val path = if (safe == 1) "/xzxx/list.asp" else "/xzxx/list.asp?page=$safe"
        return absolutize(path, baseUrl)
    }

    fun writeUrl(): String = absolutize("/xzxx/add.asp", baseUrl)

    fun detailUrl(contentId: String): String =
        absolutize("/xzxx/show.asp?contentid=$contentId", baseUrl)

    companion object {
        // ── shared regex ──
        private val rowRegex = Regex(
            """<tr\b[^>]*>(.*?)</tr>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val cellRegex = Regex(
            """<td\b[^>]*>(.*?)</td>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val anchorRegex = Regex(
            """<a\b[^>]*?href=["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val contentIdRegex = Regex("""contentid=(\d+)""", RegexOption.IGNORE_CASE)
        private val tagRegex = Regex("""<[^>]+>""")
        private val styleScriptRegex = Regex(
            """<(script|style)\b[^>]*>.*?</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val wsRegex = Regex("""\s+""")
        private val blankLinesRegex = Regex("""\n{3,}""")
        private val paginationAspRegex = Regex(
            """href=["']([^"']*?list\.asp\?page=(\d+))["']""", RegexOption.IGNORE_CASE
        )
        private val paginationHtmRegex = Regex(
            """href=["']([^"']*?list(\d+)\.htm)["']""", RegexOption.IGNORE_CASE
        )
        private val nextPageLabelRegex = Regex(
            """<a\b[^>]*?href=["']([^"']+?)["'][^>]*>([^<]*(?:下\s?一\s?页|next)[^<]*)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        // ── list ──

        fun parseLetterList(html: String, baseUrl: String = "https://www6.ahu.edu.cn"): List<XzxxLetter> {
            val tbody = Regex(
                """<tbody\b[^>]*>(.*?)</tbody>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(html)?.groupValues?.get(1) ?: return emptyList()

            return rowRegex.findAll(tbody).mapNotNull { rm ->
                val rh = rm.groupValues[1]
                if (rh.contains("<th", true) || rh.contains("colspan", true)
                    || rh.contains("#41b6f7", true)
                ) return@mapNotNull null
                val cells = cellRegex.findAll(rh).map { it.groupValues[1] }.toList()
                if (cells.size < 4) return@mapNotNull null
                val vc = plainText(cells[0]).trim()
                val title = extractAnchorText(cells[1])
                val href = extractAnchorHref(cells[1])
                val wd = plainText(cells[2]).trim()
                val rd = plainText(cells[3]).trim()
                val cid = contentIdRegex.find(href)?.groupValues?.get(1).orEmpty()
                if (title.isBlank() || cid.isBlank()) return@mapNotNull null
                XzxxLetter(contentId = cid, viewCount = vc, title = title,
                    writeDate = wd, replyDate = rd, url = absolutize(href, baseUrl))
            }.toList()
        }

        fun parseNextPageUrl(html: String, current: Int): String? {
            nextPageLabelRegex.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                ?.let { if (!it.contains("javascript", true)) return absolutize(it, "/xzxx/") }
            paginationAspRegex.findAll(html)
                .mapNotNull { it.groupValues[2].toIntOrNull() }.filter { it > current }
                .minOrNull()?.let { return absolutize("list.asp?page=$it", "/xzxx/") }
            paginationHtmRegex.findAll(html)
                .mapNotNull { it.groupValues[2].toIntOrNull() }.filter { it > current }
                .minOrNull()?.let { return absolutize("list$it.htm", "/xzxx/") }
            return null
        }

        // ── detail (show.asp) ──

        /**
         * 解析 show.asp 详情页。
         *
         * 页面结构(见 tools/xzxx_detail.html):
         *  - banner 行(colspan)
         *  - 致辞行(colspan)      ← 跳过
         *  - 导航行(colspan, #41b6f7) ← 跳过
         *  - "主 题" / title
         *  - 分隔线(colspan)
         *  - "内 容" / content
         *  - 分隔线(colspan)
         *  - "已回复"/"未回复" / reply
         *  - 关闭行(colspan, #41b6f7) ← 跳过
         */
        fun parseLetterDetail(html: String): XzxxLetterDetail? {
            val tbody = Regex(
                """<tbody\b[^>]*>(.*?)</tbody>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(html)?.groupValues?.get(1) ?: return null

            var title = ""
            var content = ""
            var replyLabel = ""
            var replyContent = ""

            for (rm in rowRegex.findAll(tbody)) {
                val rh = rm.groupValues[1]
                // 跳过模板行(colspan、导航栏)
                if (rh.contains("colspan", true)) continue
                if (rh.contains("#41b6f7", true)) continue
                // 跳过致辞(纯文本含"校长信箱致辞")
                if (rh.contains("校长信箱致辞")) continue
                // 跳过 banner img
                if (rh.contains(".jpg") || rh.contains(".png") || rh.contains(".gif")) continue

                val cells = cellRegex.findAll(rh).map { it.groupValues[1] }.toList()
                if (cells.size < 2) continue
                val rawLabel = plainText(cells[0]).trim()
                val rawValue = plainText(cells[1]).trim()
                if (rawValue.isBlank()) continue
                // 标签内常有空格/全角空格(如 "主 题"、"内 容"),统一去掉后再匹配
                val label = rawLabel.replace(Regex("""[\s　]+"""), "")

                when {
                    label.contains("主题") || label.contains("标题") -> title = rawValue
                    label.contains("内容") -> content = rawValue
                    label.contains("回复") || label.contains("已回") || label.contains("未回")
                        || label.contains("答复") || label.contains("已答") -> {
                        replyLabel = rawLabel
                        replyContent = rawValue
                    }
                }
            }
            return if (content.isNotBlank()) {
                XzxxLetterDetail(title = title, content = content,
                    replyLabel = replyLabel, replyContent = replyContent)
            } else null
        }

        // ── helpers ──

        private fun extractAnchorText(cellHtml: String): String =
            plainText(anchorRegex.find(cellHtml)?.groupValues?.get(2) ?: cellHtml)

        private fun extractAnchorHref(cellHtml: String): String =
            anchorRegex.find(cellHtml)?.groupValues?.get(1)?.let(::htmlDecode)?.trim().orEmpty()

        private fun absolutize(pathOrUrl: String, baseUrl: String): String {
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://"))
                return pathOrUrl
            val nb = baseUrl.trimEnd('/')
            val np = if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"
            return nb + np
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
            .replace(Regex("""&#(\d+);""")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
            }
    }
}
