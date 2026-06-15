package com.yourname.ahu_plus.data.repository

import com.yourname.ahu_plus.data.model.JwcNotice
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
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
                client = OkHttpClient()
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
    fun `listUrl generates deterministic listN htm pages`() {
        val repo = JwcNoticeRepository(baseUrl = "https://jwc.ahu.edu.cn")
        assertEquals("https://jwc.ahu.edu.cn/10314/list.htm", repo.listUrl(1))
        assertEquals("https://jwc.ahu.edu.cn/10314/list2.htm", repo.listUrl(2))
        assertEquals("https://jwc.ahu.edu.cn/10314/list5.htm", repo.listUrl(5))
        assertEquals("https://jwc.ahu.edu.cn/10314/list.htm", repo.listUrl(0))
    }
}
