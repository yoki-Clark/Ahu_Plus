package com.ahu_plus.data.repository

import com.ahu_plus.data.local.JwcWafCookie
import com.ahu_plus.data.local.JwcWafCookieStorage
import com.ahu_plus.data.model.JwcNotice
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwcNoticeRepositoryTest {

    @Test
    fun `parseNoticeList extracts notices from jwc homepage block`() {
        val html = """
            <div id="wp_news_w14">
              <ul class="news_list clearfix">
                <li class="news n1 clearfix">
                  <span class="news_title">
                    <a href="/2026/0615/c10314a394874/page.htm" target="_blank"
                       title="关于2026年端午节放假安排的通知">关于2026年端午节放假安排的通知</a>
                  </span>
                  <span class="news_meta post_time">
                    <!--[FieldCycleBegin]-->2026-06-15<!--[FieldCycleEnd]-->
                  </span>
                </li>
                <li class="news n2 clearfix">
                  <span class="news_title">
                    <a href="/2026/0612/c10314a394692/page.htm" target="_blank"
                       title="安徽大学2026年秋季微专业招生信息">安徽大学2026年秋季微专业招生信息</a>
                  </span>
                  <span class="news_meta post_time">2026-06-12</span>
                </li>
              </ul>
            </div>
        """.trimIndent()

        val notices = JwcNoticeRepository.parseNoticeList(html)

        assertEquals(2, notices.size)
        assertEquals("关于2026年端午节放假安排的通知", notices[0].title)
        assertEquals("2026-06-15", notices[0].date)
        assertEquals("https://jwc.ahu.edu.cn/2026/0615/c10314a394874/page.htm", notices[0].url)
    }

    @Test
    fun `parseNoticeDetail extracts article title date and text`() {
        val html = """
            <html>
              <body>
                <h1 class="arti_title">关于考试安排的通知</h1>
                <span class="arti_update">2026-06-10</span>
                <div class="wp_articlecontent">
                  <p>各学院：</p>
                  <p>请按要求做好考试组织工作。&nbsp;</p>
                </div>
              </body>
            </html>
        """.trimIndent()
        val fallback = JwcNotice(
            title = "旧标题",
            date = "2026-06-09",
            url = "https://jwc.ahu.edu.cn/detail.htm"
        )

        val detail = JwcNoticeRepository.parseNoticeDetail(html, fallback)

        assertEquals("关于考试安排的通知", detail.title)
        assertEquals("2026-06-10", detail.date)
        assertTrue(detail.content.contains("各学院"))
        assertTrue(detail.content.contains("请按要求做好考试组织工作。"))
    }

    @Test
    fun `parseNoticeDetail extracts downloadable attachments`() {
        val html = """
            <html>
              <body>
                <h1 class="arti_title">Attachment notice</h1>
                <div class="wp_articlecontent">
                  <p>Please download the file.</p>
                  <p><a href="/_upload/article/files/aa/bb/example.docx">Attachment 1: example.docx</a></p>
                  <p><a href="https://example.com/not-a-file.htm">Related page</a></p>
                </div>
              </body>
            </html>
        """.trimIndent()
        val fallback = JwcNotice(
            title = "Fallback",
            date = "2026-06-09",
            url = "https://jwc.ahu.edu.cn/2026/0610/c10314a394111/page.htm"
        )

        val detail = JwcNoticeRepository.parseNoticeDetail(html, fallback)

        assertEquals(1, detail.attachments.size)
        assertEquals("Attachment 1: example.docx", detail.attachments[0].name)
        assertEquals(
            "https://jwc.ahu.edu.cn/_upload/article/files/aa/bb/example.docx",
            detail.attachments[0].url
        )
    }

    @Test
    fun `getNotices requests homepage and returns parsed list`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                    <ul class="news_list clearfix">
                      <li class="news n1 clearfix">
                        <a href="/2026/0615/c10314a394874/page.htm" title="通知一">通知一</a>
                        <span class="news_meta post_time">2026-06-15</span>
                      </li>
                    </ul>
                """.trimIndent()
            )
        )
        server.start()
        try {
            val repo = JwcNoticeRepository(
                baseUrl = server.url("/").toString().trimEnd('/'),
                clientFactory = { cookieJar -> OkHttpClient.Builder().cookieJar(cookieJar).build() },
            )

            val notices = repo.getNotices().getOrThrow()

            assertEquals(1, notices.size)
            assertEquals("通知一", notices[0].title)
            assertEquals("2026-06-15", notices[0].date)
            // 首页 preview 现在直接拉通知公告频道第 1 页(/10314/list.htm),不再是首页 /
            assertEquals("/10314/list.htm", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `parseNextPagePath prefers explicit next-page link`() {
        val html = """
            <div class="pagination">
              <a href="javascript:void(0)">下一页</a>
              <a href="list2.htm">下一页</a>
            </div>
        """.trimIndent()
        assertEquals("list2.htm", JwcNoticeRepository.parseNextPagePath(html))
    }

    @Test
    fun `parseNextPagePath falls back to max listN htm when no next label`() {
        val html = """
            <div class="pagination">
              <a href="list1.htm">1</a>
              <a href="list3.htm">3</a>
              <a href="list2.htm">2</a>
            </div>
        """.trimIndent()
        assertEquals("list3.htm", JwcNoticeRepository.parseNextPagePath(html))
    }

    @Test
    fun `parseNextPagePath returns null when only first page exists`() {
        val html = """
            <div class="pagination">
              <a href="list.htm">1</a>
            </div>
        """.trimIndent()
        assertEquals(null, JwcNoticeRepository.parseNextPagePath(html))
    }

    @Test
    fun `parseNextPagePath ignores older page links on last page`() {
        val html = """
            <div class="pagination">
              <a href="list1.htm">1</a>
              <a href="list39.htm">39</a>
              <span>40</span>
            </div>
        """.trimIndent()

        assertEquals(null, JwcNoticeRepository.parseNextPagePath(html, currentPage = 40))
    }

    @Test
    fun `listUrl generates deterministic listN htm pages`() {
        val repo = JwcNoticeRepository(baseUrl = "https://jwc.ahu.edu.cn")
        assertEquals("https://jwc.ahu.edu.cn/10314/list.htm", repo.listUrl(1))
        assertEquals("https://jwc.ahu.edu.cn/10314/list2.htm", repo.listUrl(2))
        assertEquals("https://jwc.ahu.edu.cn/10314/list5.htm", repo.listUrl(5))
        assertEquals("https://jwc.ahu.edu.cn/10314/list.htm", repo.listUrl(0))
    }

    @Test
    fun `parseNextPagePath recognizes jwc list2 absolute href`() {
        val html = """
            <html>
              <body>
                <ul class="news_list clearfix">
                  <li class="news n1 clearfix"><a href="/2026/0615/c10314a394874/page.htm">通知一</a></li>
                </ul>
                <div class="wp_paging">
                  <a href="https://jwc.ahu.edu.cn/10314/list2.htm">下一页</a>
                </div>
              </body>
            </html>
        """.trimIndent()

        assertEquals("https://jwc.ahu.edu.cn/10314/list2.htm", JwcNoticeRepository.parseNextPagePath(html))
    }

    @Test
    fun `parseNoticeDetail keeps content after nested divs and tables`() {
        val html = """
            <html><body>
              <h1 class="arti_title">嵌套正文</h1>
              <div class="wp_articlecontent">
                <div><p>第一段</p></div>
                <table><tr><td>表格内容</td></tr></table>
                <p>最后一段</p>
              </div>
            </body></html>
        """.trimIndent()
        val fallback = JwcNotice("旧标题", "2026-07-18", "https://jwc.ahu.edu.cn/detail.htm")

        val detail = JwcNoticeRepository.parseNoticeDetail(html, fallback)

        assertTrue(detail.content.contains("第一段"))
        assertTrue(detail.content.contains("表格内容"))
        assertTrue(detail.content.contains("最后一段"))
    }

    @Test
    fun `persistent waf cookie is restored for native requests`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(listResponse())
        server.start()
        try {
            val now = 1_000_000L
            val storage = FakeWafCookieStorage(JwcWafCookie("persisted-pass", now + 60_000))
            val repository = repository(server, storage, now)

            val notices = repository.getNotices().getOrThrow()

            assertEquals(1, notices.size)
            assertTrue(server.takeRequest().getHeader("Cookie").orEmpty().contains("EdaP18tkVMlRP=persisted-pass"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `http 412 invalidates persisted waf cookie`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(412))
        server.start()
        try {
            val now = 1_000_000L
            val storage = FakeWafCookieStorage(JwcWafCookie("stale-pass", now + 60_000))
            val repository = repository(server, storage, now)

            val error = repository.getNotices().exceptionOrNull()

            assertTrue(error is JwcWafChallengeRequiredException)
            assertEquals(null, storage.cookie)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `captured waf cookie is validated and persisted`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(listResponse())
        server.start()
        try {
            val now = 1_000_000L
            val storage = FakeWafCookieStorage()
            val repository = repository(server, storage, now)

            val accepted = repository.acceptWafCookies("JSESSIONID=ignored; EdaP18tkVMlRP=fresh-pass")

            assertTrue(accepted)
            assertEquals("fresh-pass", storage.cookie?.value)
            assertTrue(server.takeRequest().getHeader("Cookie").orEmpty().contains("EdaP18tkVMlRP=fresh-pass"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `failed validation does not persist captured waf cookie`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        server.start()
        try {
            val now = 1_000_000L
            val storage = FakeWafCookieStorage()
            val repository = repository(server, storage, now)

            val accepted = repository.acceptWafCookies("EdaP18tkVMlRP=invalid-pass")

            assertFalse(accepted)
            assertEquals(null, storage.cookie)
            assertFalse(repository.getCookieHeader(server.url("/").toString()).contains("invalid-pass"))
        } finally {
            server.shutdown()
        }
    }

    private fun repository(
        server: MockWebServer,
        storage: JwcWafCookieStorage,
        now: Long,
    ): JwcNoticeRepository = JwcNoticeRepository(
        cookieStorage = storage,
        baseUrl = server.url("/").toString().trimEnd('/'),
        clientFactory = { cookieJar -> OkHttpClient.Builder().cookieJar(cookieJar).build() },
        nowMillis = { now },
    )

    private fun listResponse(): MockResponse = MockResponse().setBody(
        """
        <ul class="news_list clearfix">
          <li class="news n1 clearfix">
            <a href="/notice.htm" title="通知一">通知一</a>
            <span class="news_meta post_time">2026-07-18</span>
          </li>
        </ul>
        """.trimIndent(),
    )

    private class FakeWafCookieStorage(
        var cookie: JwcWafCookie? = null,
    ) : JwcWafCookieStorage {
        override suspend fun read(): JwcWafCookie? = cookie
        override suspend fun save(cookie: JwcWafCookie) {
            this.cookie = cookie
        }
        override suspend fun clear() {
            cookie = null
        }
    }
}
