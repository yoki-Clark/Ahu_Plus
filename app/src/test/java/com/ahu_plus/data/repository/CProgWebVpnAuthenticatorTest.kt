package com.ahu_plus.data.repository

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CProgWebVpnAuthenticatorTest {
    private lateinit var server: MockWebServer
    private val cookieStore = mutableListOf<Cookie>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/portal" -> MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/proxy/cas/login?service=portal")
                    .addHeader("Set-Cookie", "wengine_vpn_ticketwvpn_ahu_edu_cn=session; Path=/")

                request.method == "GET" && request.path == "/proxy/cas/login?service=portal" ->
                    MockResponse().setBody(
                        """
                            <form>
                              <input name="lt" value="LT-1" />
                              <input name="execution" value="EX-1" />
                            </form>
                        """.trimIndent()
                    )

                request.path == "/proxy/cas/device?vpn-12-o2-one.ahu.edu.cn" ->
                    MockResponse().setBody("{\"info\":\"ok\"}")

                request.method == "POST" && request.path == "/proxy/cas/login?service=portal" ->
                    MockResponse().setResponseCode(302).addHeader("Location", "/proxy/service?ticket=ST-1")

                request.path == "/proxy/service?ticket=ST-1" ->
                    MockResponse().setResponseCode(302).addHeader("Location", "/proxy/service")

                request.path == "/proxy/service" -> MockResponse().setBody("portal ready")

                request.path == "/vpn-login" -> MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/proxy/cas/sso?service=vpn")

                request.path == "/proxy/cas/sso?service=vpn" -> MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/vpn-service?ticket=ST-2")

                request.path == "/vpn-service?ticket=ST-2" -> MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/wengine-vpn-token-login?token=T-1")

                request.path == "/wengine-vpn-token-login?token=T-1" -> MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/token-login?token=T-1")

                request.path == "/token-login?token=T-1" -> MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/")

                request.path == "/" -> MockResponse().setBody("vpn ready")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `authenticates proxied cas and keeps webvpn cookie across redirects`() {
        val client = createClient()
        val authenticator = CProgWebVpnAuthenticator(
            client = client,
            portalUrl = server.url("/portal"),
            webVpnLoginUrl = server.url("/vpn-login"),
            credentials = { "student" to "password" },
        )

        authenticator.authenticate()

        val requests = List(12) { server.takeRequest() }
        assertEquals("/proxy/cas/device?vpn-12-o2-one.ahu.edu.cn", requests[2].path)
        assertTrue(requests[1].getHeader("Cookie").orEmpty().contains("wengine_vpn_ticketwvpn_ahu_edu_cn"))
        assertTrue(requests[2].body.readUtf8().contains("rsa="))
        assertEquals(server.url("/proxy/cas/login?service=portal").toString(), requests[4].getHeader("Referer"))
        assertEquals("/vpn-login", requests[6].path)
        requests.drop(7).forEach {
            assertEquals(server.url("/portal").toString(), it.getHeader("Referer"))
        }
        assertEquals("/", requests.last().path)
    }

    @Test
    fun `follows webvpn login before discovering cas form`() {
        val casGetCount = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/portal" -> MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/vpn-login")
                    .addHeader("Set-Cookie", "wengine_vpn_ticketwvpn_ahu_edu_cn=session; Path=/")

                request.path == "/vpn-login" -> MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/proxy/cas/login?service=vpn")

                request.method == "GET" && request.path == "/proxy/cas/login?service=vpn" -> {
                    if (casGetCount.getAndIncrement() == 0) {
                        MockResponse().setBody(
                            """
                                <form>
                                  <input name="lt" value="LT-1" />
                                  <input name="execution" value="EX-1" />
                                </form>
                            """.trimIndent()
                        )
                    } else {
                        MockResponse().setResponseCode(302)
                            .addHeader("Location", "/vpn-service?ticket=ST-2")
                    }
                }

                request.path == "/proxy/cas/device?vpn-12-o2-one.ahu.edu.cn" ->
                    MockResponse().setBody("{\"info\":\"ok\"}")

                request.method == "POST" && request.path == "/proxy/cas/login?service=vpn" ->
                    MockResponse().setResponseCode(302).addHeader("Location", "/vpn-service?ticket=ST-1")

                request.path?.startsWith("/vpn-service?ticket=") == true ->
                    MockResponse().setResponseCode(302).addHeader("Location", "/token-login?token=T")

                request.path == "/token-login?token=T" ->
                    MockResponse().setResponseCode(302).addHeader("Location", "/")

                request.path == "/" -> MockResponse().setBody("vpn ready")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val authenticator = CProgWebVpnAuthenticator(
            client = createClient(),
            portalUrl = server.url("/portal"),
            webVpnLoginUrl = server.url("/vpn-login"),
            credentials = { "student" to "password" },
        )

        authenticator.authenticate()

        assertEquals(2, casGetCount.get())
    }

    private fun createClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookies.forEach { cookie ->
                    cookieStore.removeAll { it.name == cookie.name }
                    cookieStore += cookie
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore.toList()
        })
        .build()
}
