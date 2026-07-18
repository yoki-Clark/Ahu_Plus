package com.ahu_plus.ui.screen.profile

import com.ahu_plus.data.local.XzxxWafCookie
import com.ahu_plus.data.local.XzxxWafCookieStorage
import com.ahu_plus.data.repository.XzxxRepository
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class XzxxViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `duplicate next page stops pagination`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(listResponse(nextPage = 2))
        server.enqueue(listResponse(nextPage = 3))
        server.start()
        try {
            val repository = XzxxRepository(
                cookieStorage = EmptyWafCookieStorage,
                baseUrl = server.url("/").toString().trimEnd('/'),
                clientFactory = { cookieJar ->
                    OkHttpClient.Builder()
                        .cookieJar(cookieJar)
                        .followRedirects(false)
                        .build()
                },
            )
            val viewModel = XzxxViewModel(repository)
            withTimeout(5_000) {
                viewModel.uiState.first { it.currentPage == 1 && !it.isLoading }
            }

            viewModel.loadNextPage()

            val state = withTimeout(5_000) {
                viewModel.uiState.first { it.currentPage == 2 && !it.isLoadingMore }
            }
            assertEquals(1, state.letters.size)
            assertFalse(state.hasMore)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private fun listResponse(nextPage: Int?): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody(
            """
            <html><body><table><tbody>
              <tr><td>42</td><td><a href="show.asp?contentid=123">重复信件</a></td><td>2026-07-16</td><td>2026-07-17</td></tr>
            </tbody></table>
            ${nextPage?.let { "<a href=\"list.asp?page=$it\">下一页</a>" }.orEmpty()}
            </body></html>
            """.trimIndent(),
        )

    private object EmptyWafCookieStorage : XzxxWafCookieStorage {
        override suspend fun read(): XzxxWafCookie? = null
        override suspend fun save(cookie: XzxxWafCookie) = Unit
        override suspend fun clear() = Unit
    }
}
