package com.ahu_plus.data.diagnostic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafeLogTest {
    @Before
    fun clear() = DiagnosticBuffer.clear()

    @Test
    fun `sanitizes credentials personal data urls and response bodies`() {
        val jwt = listOf(
            "eyJhbGciOiJSUzI1NiJ9",
            "eyJzdHVkZW50SWQiOiIxMjMifQ",
            "signaturevalue",
        ).joinToString(".")
        val original = "Authorization=Bearer $jwt JSESSIONID=secret studentId=E12345678 " +
            "room=408 https://example.com/path?token=secret response={\"private\":true}"

        SafeLog.i("SecurityTest", original)

        val message = DiagnosticBuffer.snapshot().single().message
        assertFalse(message.contains(jwt))
        assertFalse(message.contains("secret"))
        assertFalse(message.contains("E12345678"))
        assertFalse(message.contains("408"))
        assertFalse(message.contains("private"))
        assertTrue(message.contains("REDACTED"))
    }

    @Test
    fun `diagnostic buffer is bounded`() {
        repeat(550) { SafeLog.d("Bounded", "event=$it") }

        assertTrue(DiagnosticBuffer.snapshot().size == 500)
    }
}
