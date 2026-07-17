package com.ahu_plus.data.developer

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperRuntimeTest {
    @After
    fun tearDown() {
        DeveloperRuntime.setDeveloperEnabled(false)
        DeveloperEventRecorder.clear()
    }

    @Test
    fun `disabling developer mode clears active overrides`() {
        DeveloperRuntime.setDeveloperEnabled(true)
        DeveloperRuntime.configureNetworkFault(
            DeveloperNetworkFault.HTTP_500,
            targetHost = "jw.ahu.edu.cn",
        )

        assertTrue(DeveloperRuntime.state.value.hasActiveOverrides)

        DeveloperRuntime.setDeveloperEnabled(false)

        assertFalse(DeveloperRuntime.state.value.developerEnabled)
        assertEquals(DeveloperNetworkFault.NONE, DeveloperRuntime.state.value.networkFault)
        assertEquals("", DeveloperRuntime.state.value.targetHost)
    }

    @Test
    fun `resetting overrides restores network without disabling developer mode`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(204))
            start()
        }
        try {
            DeveloperRuntime.setDeveloperEnabled(true)
            DeveloperRuntime.configureNetworkFault(
                DeveloperNetworkFault.OFFLINE,
                targetHost = server.hostName,
            )

            DeveloperRuntime.resetOverrides()

            assertTrue(DeveloperRuntime.state.value.developerEnabled)
            assertFalse(DeveloperRuntime.state.value.hasActiveOverrides)
            assertEquals("", DeveloperRuntime.state.value.targetHost)
            client().newCall(Request.Builder().url(server.url("/cas/login")).build()).execute().use {
                assertEquals(204, it.code)
            }
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `http auth fault is injected inside the network layer`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(204))
            start()
        }
        try {
            DeveloperRuntime.setDeveloperEnabled(true)
            DeveloperRuntime.configureNetworkFault(DeveloperNetworkFault.HTTP_401)
            val response = client().newCall(Request.Builder().url(server.url("/private")).build()).execute()

            response.use {
                assertEquals(401, it.code)
                assertEquals("true", it.header("X-AhuPlus-Developer-Fault"))
            }
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `target host prevents fault from affecting other hosts`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(204))
            start()
        }
        try {
            DeveloperRuntime.setDeveloperEnabled(true)
            DeveloperRuntime.configureNetworkFault(
                DeveloperNetworkFault.HTTP_500,
                targetHost = "jw.ahu.edu.cn",
            )

            client().newCall(Request.Builder().url(server.url("/health")).build()).execute().use {
                assertEquals(204, it.code)
            }
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `simulated 401 invokes authenticator once then allows retry`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(204))
            enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            start()
        }
        val authenticateCalls = AtomicInteger(0)
        try {
            DeveloperRuntime.setDeveloperEnabled(true)
            DeveloperRuntime.configureNetworkFault(DeveloperNetworkFault.HTTP_401)
            val client = OkHttpClient.Builder()
                .authenticator(Authenticator { _, response ->
                    authenticateCalls.incrementAndGet()
                    response.request.newBuilder().header("Authorization", "test").build()
                })
                .addInterceptor(DeveloperNetworkInterceptor())
                .addNetworkInterceptor(DeveloperAuthenticationFaultInterceptor())
                .build()

            client.newCall(Request.Builder().url(server.url("/private")).build()).execute().use {
                assertEquals(200, it.code)
                assertEquals("ok", it.body?.string())
            }
            assertEquals(1, authenticateCalls.get())
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test(expected = IOException::class)
    fun `offline fault throws io exception`() {
        DeveloperRuntime.setDeveloperEnabled(true)
        DeveloperRuntime.configureNetworkFault(DeveloperNetworkFault.OFFLINE)

        client().newCall(Request.Builder().url("https://example.com/").build()).execute()
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(DeveloperNetworkInterceptor())
        .addNetworkInterceptor(DeveloperAuthenticationFaultInterceptor())
        .build()
}
