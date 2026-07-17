package com.ahu_plus.data.developer

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDiagnosticUrlRedactorTest {
    @Test
    fun `redacts sensitive values case insensitively and keeps safe parameters`() {
        val redacted = NetworkDiagnosticUrlRedactor.redact(
            "https://example.com/path?page=2&TOKEN=abc&password=pw&ticket=ST-123#section",
        ).toHttpUrl()

        assertEquals("2", redacted.queryParameter("page"))
        assertEquals("REDACTED", redacted.queryParameter("TOKEN"))
        assertEquals("REDACTED", redacted.queryParameter("password"))
        assertEquals("REDACTED", redacted.queryParameter("ticket"))
        assertEquals("section", redacted.fragment)
    }

    @Test
    fun `preserves duplicate query parameters while redacting every secret value`() {
        val redacted = NetworkDiagnosticUrlRedactor.redact(
            "https://example.com/?token=first&tag=a&token=second&tag=b",
        ).toHttpUrl()

        assertEquals(listOf("REDACTED", "REDACTED"), redacted.queryParameterValues("token"))
        assertEquals(listOf("a", "b"), redacted.queryParameterValues("tag"))
    }

    @Test
    fun `redacts secrets inside a nested callback URL`() {
        val source = "https://one.ahu.edu.cn/cas/login?service=" +
            "https%3A%2F%2Fclient.example%2Fcallback%3Fticket%3DST-SECRET%26page%3D1"

        val outer = NetworkDiagnosticUrlRedactor.redact(source).toHttpUrl()
        val nested = outer.queryParameter("service")!!.toHttpUrl()

        assertEquals("REDACTED", nested.queryParameter("ticket"))
        assertEquals("1", nested.queryParameter("page"))
    }

    @Test
    fun `malformed URL drops its whole query instead of leaking it`() {
        val redacted = NetworkDiagnosticUrlRedactor.redact("not a url?token=secret&safe=value")

        assertEquals("not a url?REDACTED", redacted)
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("value"))
    }

    @Test
    fun `error sanitizer removes query secrets from embedded URLs`() {
        val sanitized = NetworkDiagnosticUrlRedactor.sanitizeErrorMessage(
            "Request to https://example.com/api?access_token=secret&page=3 failed",
        )

        assertFalse(sanitized.contains("secret"))
        assertTrue(sanitized.contains("access_token=REDACTED"))
        assertTrue(sanitized.contains("page=3"))
    }
}
