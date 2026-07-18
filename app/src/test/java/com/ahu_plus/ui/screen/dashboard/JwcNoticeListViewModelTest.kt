package com.ahu_plus.ui.screen.dashboard

import com.ahu_plus.data.local.JwcNoticeCache
import com.ahu_plus.data.local.JwcWafCookie
import com.ahu_plus.data.local.JwcWafCookieStorage
import com.ahu_plus.data.repository.JwcNoticeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JwcNoticeListViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `waf cookie capture automatically retries the blocked first page`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(412))
        server.enqueue(listResponse()) // Cookie validation.
        server.enqueue(listResponse()) // Automatic retry of the blocked operation.
        server.start()
        try {
            val repository = JwcNoticeRepository(
                cookieStorage = InMemoryWafCookieStorage(),
                baseUrl = server.url("/").toString().trimEnd('/'),
                clientFactory = { cookieJar ->
                    OkHttpClient.Builder().cookieJar(cookieJar).build()
                },
            )
            val viewModel = JwcNoticeListViewModel(repository, InMemoryJwcNoticeCache())

            viewModel.activate()

            val blocked = withTimeout(5_000) {
                viewModel.uiState.first { it.wafChallengeUrl != null }
            }
            assertTrue(blocked.isLoading)

            viewModel.onWafCookieCaptured("EdaP18tkVMlRP=fresh-pass")

            val recovered = withTimeout(5_000) {
                viewModel.uiState.first {
                    it.currentPage == 1 && !it.isLoading && it.wafChallengeUrl == null
                }
            }
            assertEquals(1, recovered.notices.size)
            assertFalse(recovered.isLoading)
            assertNull(recovered.error)
            assertEquals(3, server.requestCount)
            server.takeRequest()
            repeat(2) {
                assertTrue(
                    server.takeRequest().getHeader("Cookie").orEmpty()
                        .contains("EdaP18tkVMlRP=fresh-pass"),
                )
            }
        } finally {
            server.shutdown()
        }
    }

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

    private class InMemoryWafCookieStorage : JwcWafCookieStorage {
        private var cookie: JwcWafCookie? = null

        override suspend fun read(): JwcWafCookie? = cookie
        override suspend fun save(cookie: JwcWafCookie) {
            this.cookie = cookie
        }
        override suspend fun clear() {
            cookie = null
        }
    }

    private class InMemoryJwcNoticeCache : JwcNoticeCache {
        private var noticesJson: String? = null
        private var detailsJson: String? = null

        override fun getJwcNoticeJson(): String? = noticesJson
        override fun getJwcNoticeUpdatedAt(): Long = 0L
        override suspend fun saveJwcNoticeJson(json: String) {
            noticesJson = json
        }
        override fun getJwcNoticeDetailsJson(): String? = detailsJson
        override suspend fun saveJwcNoticeDetailsJson(json: String) {
            detailsJson = json
        }
    }
}
