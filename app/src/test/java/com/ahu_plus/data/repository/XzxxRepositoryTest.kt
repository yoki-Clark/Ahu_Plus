package com.ahu_plus.data.repository

import com.ahu_plus.data.local.XzxxWafCookie
import com.ahu_plus.data.local.XzxxWafCookieStorage
import com.ahu_plus.data.model.XzxxSubmitRequest
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class XzxxRepositoryTest {

    @Test
    fun `list request decodes GBK and parses pagination`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(gbkResponse(listHtml(nextPage = 2)))
        server.start()
        try {
            val repository = repository(server)

            val page = repository.getLetters(1)

            assertEquals(1, page.letters.size)
            assertEquals("关于校园服务的建议", page.letters.single().title)
            assertEquals("123", page.letters.single().contentId)
            assertTrue(page.hasMore)
            assertEquals("/xzxx/list.asp", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `detail parser retains Chinese content and reply`() {
        val detail = XzxxRepository.parseLetterDetail(
            """
            <table><tbody>
              <tr><td>主 题</td><td>测试主题</td></tr>
              <tr><td>内 容</td><td>正文第一行<br>正文第二行</td></tr>
              <tr><td>已回复</td><td>感谢您的建议</td></tr>
            </tbody></table>
            """.trimIndent(),
        )

        assertNotNull(detail)
        assertEquals("测试主题", detail?.title)
        assertTrue(detail?.content.orEmpty().contains("正文第二行"))
        assertEquals("感谢您的建议", detail?.replyContent)
    }

    @Test
    fun `412 clears persisted WAF cookie and requests bootstrap`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(412).setBody("<script>\$_ss={}</script>"))
        server.start()
        val storage = FakeWafCookieStorage(XzxxWafCookie("expired", Long.MAX_VALUE))
        try {
            val repository = repository(server, storage)

            assertThrows(XzxxWafChallengeRequiredException::class.java) {
                runBlocking { repository.getLetters(1) }
            }
            assertEquals(null, storage.cookie)
            assertTrue(storage.clearCount > 0)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `accepted browser WAF cookie is persisted and sent by OkHttp`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(gbkResponse(listHtml(nextPage = null)))
        server.start()
        val storage = FakeWafCookieStorage()
        try {
            val repository = repository(server, storage)

            val accepted = repository.acceptWafCookies(
                "ASPSESSIONIDQQCTCABS=browser-session; EdaP18tkVMlRP=waf-token",
            )

            assertTrue(accepted)
            assertEquals("waf-token", storage.cookie?.value)
            assertTrue(server.takeRequest().getHeader("Cookie").orEmpty().contains("EdaP18tkVMlRP=waf-token"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `compose flow keeps ASP session and submits GBK form`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            gbkResponse("<html><form name='form1'></form></html>")
                .addHeader("Set-Cookie", "ASPSESSIONIDQQCTCABS=asp-session; Path=/"),
        )
        val bitmap = byteArrayOf(0x42, 0x4D, 0x01, 0x02)
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "image/BMP").setBody(Buffer().write(bitmap)))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/xzxx/list.asp"))
        server.start()
        try {
            val repository = repository(server)

            val captcha = repository.prepareCompose()
            val result = repository.submitLetter(
                XzxxSubmitRequest(
                    uname = "测试者",
                    checkcode = "1234",
                    telnum = "test@example.com",
                    title = "编码测试",
                    content = "中文内容 A&B",
                ),
            )

            assertTrue(bitmap.contentEquals(captcha.bytes))
            assertTrue(result.success)
            val addRequest = server.takeRequest()
            val captchaRequest = server.takeRequest()
            val submitRequest = server.takeRequest()
            assertEquals("/xzxx/add.asp", addRequest.path)
            assertTrue(captchaRequest.path.orEmpty().startsWith("/xzxx/checkcode.asp?_="))
            assertTrue(captchaRequest.getHeader("Cookie").orEmpty().contains("ASPSESSIONIDQQCTCABS=asp-session"))
            assertEquals("/xzxx/save.asp", submitRequest.path)
            assertEquals(
                "uname=%B2%E2%CA%D4%D5%DF&checkcode=1234&telnum=test%40example.com&" +
                    "depname=%D0%A3%B0%EC&title=%B1%E0%C2%EB%B2%E2%CA%D4&" +
                    "content=%D6%D0%CE%C4%C4%DA%C8%DD%20A%26B",
                submitRequest.body.readUtf8(),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `cookie extraction requires exact cookie name`() {
        val header = "enable_EdaP18tkVMlR=true; EdaP18tkVMlRP=real-value"

        assertEquals("real-value", XzxxRepository.extractCookieValue(header, "EdaP18tkVMlRP"))
        assertEquals(null, XzxxRepository.extractCookieValue(header, "missing"))
    }

    private fun repository(
        server: MockWebServer,
        storage: XzxxWafCookieStorage = FakeWafCookieStorage(),
    ): XzxxRepository = XzxxRepository(
        cookieStorage = storage,
        baseUrl = server.url("/").toString().trimEnd('/'),
        clientFactory = { cookieJar ->
            OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(false)
                .build()
        },
        nowMillis = { 1_700_000_000_000L },
    )

    private fun gbkResponse(html: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/html")
        .setBody(Buffer().write(html.toByteArray(GBK)))

    private fun listHtml(nextPage: Int?): String = """
        <html><body><table><tbody>
          <tr><td>42</td><td><a href="show.asp?contentid=123">关于校园服务的建议</a></td><td>2026-07-16</td><td>2026-07-17</td></tr>
        </tbody></table>
        ${nextPage?.let { "<a href=\"list.asp?page=$it\">下一页</a>" }.orEmpty()}
        </body></html>
    """.trimIndent()

    private class FakeWafCookieStorage(
        var cookie: XzxxWafCookie? = null,
    ) : XzxxWafCookieStorage {
        var clearCount = 0

        override suspend fun read(): XzxxWafCookie? = cookie

        override suspend fun save(cookie: XzxxWafCookie) {
            this.cookie = cookie
        }

        override suspend fun clear() {
            cookie = null
            clearCount++
        }
    }

    private companion object {
        val GBK: Charset = Charset.forName("GBK")
    }
}
