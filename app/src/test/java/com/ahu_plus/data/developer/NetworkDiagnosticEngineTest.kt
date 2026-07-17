package com.ahu_plus.data.developer

import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDiagnosticEngineTest {
    @Test
    fun `DNS failure skips HTTP and returns a structured error`() = runBlocking {
        var clientWasRequested = false
        val engine = NetworkDiagnosticEngine(
            dnsResolver = NetworkDnsResolver { throw UnknownHostException("missing.example") },
            clientFactory = {
                clientWasRequested = true
                OkHttpClient()
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = engine.run(testHost())

        assertEquals(NetworkDiagnosticStatus.FAILED, result.status)
        assertEquals(NetworkDiagnosticStatus.FAILED, result.dns.status)
        assertEquals(NetworkDiagnosticErrorKind.DNS, result.dns.error?.kind)
        assertEquals(NetworkDiagnosticStatus.SKIPPED, result.http.status)
        assertTrue(!clientWasRequested)
    }

    @Test
    fun `successful read-only probe reports progress and redacts its URL`() = runBlocking {
        val methods = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                methods += chain.request().method
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(204)
                    .message("No Content")
                    .body(ByteArray(0).toResponseBody())
                    .build()
            }
            .build()
        val engine = NetworkDiagnosticEngine(
            dnsResolver = NetworkDnsResolver { listOf("203.0.113.10") },
            clientFactory = { client },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val progress = mutableListOf<NetworkDiagnosticResult>()

        val result = engine.run(
            testHost(url = "https://example.com/health?token=secret&page=2"),
            onProgress = progress::add,
        )

        assertEquals(listOf("HEAD"), methods)
        assertEquals(NetworkDiagnosticStatus.SUCCEEDED, result.status)
        assertEquals(listOf("203.0.113.10"), result.dns.addresses)
        assertEquals(204, result.http.httpStatusCode)
        assertEquals("http/1.1", result.http.protocol)
        assertEquals("https://example.com/health?token=REDACTED&page=2", result.http.requestedUrl)
        assertNull(result.http.tlsVersion)
        assertEquals(
            listOf(
                NetworkDiagnosticStatus.RUNNING,
                NetworkDiagnosticStatus.RUNNING,
                NetworkDiagnosticStatus.SUCCEEDED,
            ),
            progress.map { it.status },
        )
        assertEquals(NetworkDiagnosticStatus.RUNNING, progress[0].dns.status)
        assertEquals(NetworkDiagnosticStatus.RUNNING, progress[1].http.status)
    }

    private fun testHost(url: String = "https://example.com/") = NetworkHostSpec(
        id = "test",
        displayName = "Test",
        url = url,
        category = NetworkDiagnosticCategory.PUBLIC_DATA,
    )
}
