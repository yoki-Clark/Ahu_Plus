package com.ahu_plus.data.network

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaoxingTrafficGovernorTest {

    @Test
    fun `200 response is valid and leaves no cooldown`() {
        var now = 1_000L
        val governor = ChaoxingTrafficGovernor(clock = { now })

        val classification = governor.recordResponse("u1", "https://mooc1.chaoxing.com/path", 200)

        assertEquals(ChaoxingResponseKind.VALID, classification.kind)
        assertEquals(0L, classification.cooldownMillis)
        assertEquals(0L, governor.cooldownRemainingMillis("u1", "mooc1.chaoxing.com"))
        assertNotNull(governor.beforeRequest("u1", "mooc1.chaoxing.com"))
    }

    @Test
    fun `401 and 403 open separate auth and forbidden cooldowns`() {
        var now = 2_000L
        val governor = ChaoxingTrafficGovernor(
            clock = { now },
            authCooldownMillis = 1_000L,
            forbiddenCooldownMillis = 2_000L,
        )

        val auth = governor.recordResponse("u1", "passport2.chaoxing.com", 401)
        assertEquals(ChaoxingResponseKind.AUTH_EXPIRED, auth.kind)
        assertThrows(ChaoxingAuthExpiredException::class.java) {
            governor.beforeRequest("u1", "passport2.chaoxing.com")
        }

        val forbidden = governor.recordResponse("u1", "mooc1.chaoxing.com", 403)
        assertEquals(ChaoxingResponseKind.FORBIDDEN, forbidden.kind)
        assertThrows(ChaoxingForbiddenException::class.java) {
            governor.beforeRequest("u1", "mooc1.chaoxing.com")
        }
        assertTrue(governor.cooldownRemainingMillis("u1", "mooc1.chaoxing.com") >= 2_000L)
    }

    @Test
    fun `429 honors Retry-After delta seconds`() {
        var now = 3_000L
        val governor = ChaoxingTrafficGovernor(clock = { now }, maxCooldownMillis = 60_000L)
        val headers = Headers.Builder().add("Retry-After", "7").build()

        val classification = governor.recordResponse(
            account = "u1",
            host = "mobilelearn.chaoxing.com",
            statusCode = 429,
            headers = headers,
        )

        assertEquals(ChaoxingResponseKind.RATE_LIMITED, classification.kind)
        assertEquals(7_000L, classification.retryAfterMillis)
        assertEquals(7_000L, classification.cooldownMillis)
        val error = assertThrows(ChaoxingRateLimitedException::class.java) {
            governor.beforeRequest("u1", "mobilelearn.chaoxing.com")
        }
        assertEquals(7_000L, error.remainingMillis)
    }

    @Test
    fun `zero and expired Retry-After values fall back to nonzero cooldowns`() {
        val now = 1_700_000_000_000L
        val governor = ChaoxingTrafficGovernor(clock = { now })

        val zero = governor.recordResponse(
            account = "u1",
            host = "mobilelearn.chaoxing.com",
            statusCode = 429,
            headers = Headers.Builder().add("Retry-After", "0").build(),
        )
        val expiredChallenge = governor.recordResponse(
            account = "u2",
            host = "mooc1.chaoxing.com",
            statusCode = 200,
            headers = Headers.Builder()
                .add("Retry-After", "Wed, 21 Oct 2015 07:28:00 GMT")
                .add("Content-Type", "text/html")
                .build(),
            body = "<html><title>captcha</title></html>",
        )

        assertEquals(0L, zero.retryAfterMillis)
        assertEquals(
            ChaoxingTrafficGovernor.DEFAULT_RATE_LIMIT_COOLDOWN_MILLIS,
            zero.cooldownMillis,
        )
        assertEquals(0L, expiredChallenge.retryAfterMillis)
        assertEquals(ChaoxingResponseKind.RISK_CHALLENGE, expiredChallenge.kind)
        assertEquals(
            ChaoxingTrafficGovernor.DEFAULT_RISK_COOLDOWN_MILLIS,
            expiredChallenge.cooldownMillis,
        )
    }

    @Test
    fun `risk html is classified including Chinese marker`() {
        val chineseCaptcha = String(charArrayOf(0x9a8c.toChar(), 0x8bc1.toChar(), 0x7801.toChar()))
        val body = "<html><head><title>$chineseCaptcha</title></head><body>challenge</body></html>"

        val classification = ChaoxingResponseClassifier.classify(
            statusCode = 200,
            headers = mapOf("Content-Type" to "text/html; charset=utf-8"),
            body = body,
        )

        assertEquals(ChaoxingResponseKind.RISK_CHALLENGE, classification.kind)
    }

    @Test
    fun `risk body takes precedence over HTTP auth and forbidden status`() {
        val headers = mapOf("Content-Type" to "text/html; charset=utf-8")
        val body = "<html><head><title>captcha</title></head><body>verify</body></html>"

        assertEquals(
            ChaoxingResponseKind.RISK_CHALLENGE,
            ChaoxingResponseClassifier.classify(401, headers, body).kind,
        )
        assertEquals(
            ChaoxingResponseKind.RISK_CHALLENGE,
            ChaoxingResponseClassifier.classify(403, headers, body).kind,
        )
    }

    @Test
    fun `captcha text inside a script does not freeze a normal html page`() {
        val body = "<html><head><script>const captcha = 'course-help';</script></head>" +
            "<body><h1>Course</h1></body></html>"

        val classification = ChaoxingResponseClassifier.classify(
            statusCode = 200,
            headers = mapOf("Content-Type" to "text/html; charset=utf-8"),
            body = body,
        )

        assertEquals(ChaoxingResponseKind.VALID, classification.kind)
    }

    @Test
    fun `fanyalogin reference inside a script is not an auth page`() {
        val body = "<html><head><script>const endpoint = '/fanyalogin';</script></head>" +
            "<body><h1>Course</h1></body></html>"

        val classification = ChaoxingResponseClassifier.classify(
            statusCode = 200,
            headers = mapOf("Content-Type" to "text/html; charset=utf-8"),
            body = body,
        )

        assertEquals(ChaoxingResponseKind.VALID, classification.kind)
    }

    @Test
    fun `short html restriction message in visible body is classified`() {
        val frequent = String(charArrayOf(
            0x8bbf.toChar(), 0x95ee.toChar(), 0x8fc7.toChar(), 0x4e8e.toChar(),
            0x9891.toChar(), 0x7e41.toChar(),
        ))
        val body = "<html><head><title>notice</title></head><body>$frequent</body></html>"

        val classification = ChaoxingResponseClassifier.classify(
            statusCode = 200,
            headers = mapOf("Content-Type" to "text/html; charset=utf-8"),
            body = body,
        )

        assertEquals(ChaoxingResponseKind.RISK_CHALLENGE, classification.kind)
    }

    @Test
    fun `weak captcha word in ordinary short html is not enough`() {
        val body = "<html><body>This lesson explains captcha validation.</body></html>"

        val classification = ChaoxingResponseClassifier.classify(
            statusCode = 200,
            headers = mapOf("Content-Type" to "text/html; charset=utf-8"),
            body = body,
        )

        assertEquals(ChaoxingResponseKind.VALID, classification.kind)
    }

    @Test
    fun `expanded chinese frequency marker is classified`() {
        val frequent = String(charArrayOf(
            0x8bbf.toChar(), 0x95ee.toChar(), 0x8fc7.toChar(), 0x4e8e.toChar(),
            0x9891.toChar(), 0x7e41.toChar(),
        ))
        val classification = ChaoxingResponseClassifier.classify(
            statusCode = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\"message\":\"$frequent\"}",
        )

        assertEquals(ChaoxingResponseKind.RISK_CHALLENGE, classification.kind)
    }

    @Test
    fun chaoxingLoginFormIsAuthExpiry() {
        val body = """
            <html><form action="/fanyalogin">
              <input name="uname"><input name="password">
            </form></html>
        """.trimIndent()

        val classification = ChaoxingResponseClassifier.classify(200, body = body)

        assertEquals(ChaoxingResponseKind.AUTH_EXPIRED, classification.kind)
    }

    @Test
    fun jsonRateLimitIsClassifiedButUserContentIsNot() {
        val frequent = String(charArrayOf(0x8bbf.toChar(), 0x95ee.toChar(), 0x9891.toChar(), 0x7e41.toChar()))
        val blocked = ChaoxingResponseClassifier.classify(
            statusCode = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\"code\":429,\"message\":\"$frequent\"}",
        )
        val normal = ChaoxingResponseClassifier.classify(
            statusCode = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\"code\":0,\"content\":\"$frequent\"}",
        )

        assertEquals(ChaoxingResponseKind.RATE_LIMITED, blocked.kind)
        assertEquals(ChaoxingResponseKind.VALID, normal.kind)
    }

    @Test
    fun `HTTP 200 JSON code and status classify auth forbidden and rate limits`() {
        val expected = mapOf(
            401 to ChaoxingResponseKind.AUTH_EXPIRED,
            403 to ChaoxingResponseKind.FORBIDDEN,
            429 to ChaoxingResponseKind.RATE_LIMITED,
        )

        for ((code, kind) in expected) {
            for (field in listOf("code", "status")) {
                for (value in listOf(code.toString(), "\"$code\"")) {
                    val classification = ChaoxingResponseClassifier.classify(
                        statusCode = 200,
                        headers = mapOf("Content-Type" to "application/json"),
                        body = "{\"$field\":$value}",
                    )
                    assertEquals("$field=$value", kind, classification.kind)
                }
            }
        }
    }

    @Test
    fun `redirect restriction is attributed to both final and entry hosts`() {
        val server = MockWebServer()
        server.start()
        try {
            val entryUrl = server.url("/entry").newBuilder().host("localhost").build()
            val finalUrl = server.url("/limited").newBuilder().host("127.0.0.1").build()
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", finalUrl),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .addHeader("Content-Type", "application/json")
                    .setBody("{\"status\":429}"),
            )
            val governor = ChaoxingTrafficGovernor()
            val client = OkHttpClient.Builder()
                .addInterceptor(governor.asEntryTagInterceptor("u1"))
                .addNetworkInterceptor(governor.asInterceptor("u1"))
                .build()

            val error = assertThrows(ChaoxingRateLimitedException::class.java) {
                client.newCall(Request.Builder().url(entryUrl).build()).execute().use { }
            }

            assertEquals("127.0.0.1", error.key?.host)
            assertFalse(governor.isRequestAllowed("u1", "127.0.0.1"))
            assertFalse(governor.isRequestAllowed("u1", "localhost"))
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `chaoxing subdomains share one account traffic scope`() {
        val first = ChaoxingTrafficKey.of("u1", "mooc1.chaoxing.com")
        val second = ChaoxingTrafficKey.of("u1", "notice.chaoxing.com")

        assertEquals(first, second)
        assertEquals("chaoxing.com", first.host)

        val governor = ChaoxingTrafficGovernor()
        governor.recordResponse(first, statusCode = 429)
        assertFalse(governor.isRequestAllowed("u1", "mobilelearn.chaoxing.com"))
    }

    @Test
    fun `403 and transient Retry-After responses open cooldowns`() {
        var now = 5_000L
        val governor = ChaoxingTrafficGovernor(clock = { now }, maxCooldownMillis = 60_000L)
        val retryAfter = Headers.Builder().add("Retry-After", "3").build()

        val forbidden = governor.recordResponse("u1", "mooc1.chaoxing.com", 403, retryAfter)
        assertEquals(3_000L, forbidden.cooldownMillis)
        assertThrows(ChaoxingForbiddenException::class.java) {
            governor.beforeRequest("u1", "mooc2-ans.chaoxing.com")
        }

        now += 3_001L
        val transient = governor.recordResponse("u1", "mooc1.chaoxing.com", 503, retryAfter)
        assertEquals(ChaoxingResponseKind.TRANSIENT, transient.kind)
        assertEquals(3_000L, transient.cooldownMillis)
        assertThrows(ChaoxingTrafficCooldownException::class.java) {
            governor.beforeRequest("u1", "notice.chaoxing.com")
        }
    }

    @Test
    fun `normal requests use a deterministic minimum interval`() {
        var now = 0L
        val delays = mutableListOf<Long>()
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("one"))
            server.enqueue(MockResponse().setBody("two"))
            val governor = ChaoxingTrafficGovernor(
                clock = { now },
                minRequestIntervalMillis = 100L,
                sleeper = { delays += it },
            )
            val client = OkHttpClient.Builder()
                .addInterceptor(governor.asInterceptor("u1"))
                .build()
            val request = Request.Builder().url(server.url("/paced")).build()

            client.newCall(request).execute().use { }
            now = 10L
            client.newCall(request).execute().use { }

            assertEquals(listOf(90L), delays)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `host gate remains held until response body is closed`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("first"))
            server.enqueue(MockResponse().setBody("second"))
            val governor = ChaoxingTrafficGovernor()
            val client = OkHttpClient.Builder()
                .addInterceptor(governor.asInterceptor("u1"))
                .build()
            val request = Request.Builder().url(server.url("/body")).build()

            val first = client.newCall(request).execute()
            assertThrows(ChaoxingTrafficBusyException::class.java) {
                client.newCall(request).execute()
            }
            first.close()

            client.newCall(request).execute().use { response ->
                assertEquals("second", response.body?.string())
            }
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `cooldown rejects until fake clock advances`() {
        var now = 10_000L
        val governor = ChaoxingTrafficGovernor(clock = { now }, forbiddenCooldownMillis = 500L)
        governor.recordResponse("u1", "mooc2.chaoxing.com", 403)

        assertFalse(governor.isRequestAllowed("u1", "mooc2.chaoxing.com"))
        now += 499L
        assertTrue(governor.cooldownRemainingMillis("u1", "mooc2.chaoxing.com") > 0L)
        now += 2L
        assertTrue(governor.isRequestAllowed("u1", "mooc2.chaoxing.com"))
    }

    @Test
    fun `active cooldown can be restored after a process restart`() {
        var now = 20_000L
        val first = ChaoxingTrafficGovernor(clock = { now }, maxCooldownMillis = 60_000L)
        first.recordResponse("u1", "mooc1.chaoxing.com", 429)
        val snapshot = first.snapshot()

        val restored = ChaoxingTrafficGovernor(clock = { now }, maxCooldownMillis = 60_000L)
        restored.restore(snapshot)

        assertEquals(first.cooldownRemainingMillis("u1", "notice.chaoxing.com"), restored.cooldownRemainingMillis("u1", "notice.chaoxing.com"))
        assertThrows(ChaoxingRateLimitedException::class.java) {
            restored.beforeRequest("u1", "mobilelearn.chaoxing.com")
        }
    }

    @Test
    fun `single flight shares one owner result`() = runTest {
        val governor = ChaoxingTrafficGovernor()
        var calls = 0
        val first = async {
            governor.singleFlight("u1", "mooc1.chaoxing.com", "course-list") {
                calls++
                delay(10)
                "ok"
            }
        }
        val second = async {
            governor.singleFlight("u1", "mooc1.chaoxing.com", "course-list") {
                calls++
                "wrong"
            }
        }

        assertEquals("ok", first.await())
        assertEquals("ok", second.await())
        assertEquals(1, calls)
    }
}
