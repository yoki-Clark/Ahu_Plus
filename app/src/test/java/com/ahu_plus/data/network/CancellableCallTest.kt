package com.ahu_plus.data.network

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CancellableCallTest {
    @Test
    fun `response is closed when cancellation wins after callback resume`() = runTest {
        val request = Request.Builder().url("https://example.invalid/test").build()
        val call = CallbackCall(request)
        val result = async(start = CoroutineStart.UNDISPATCHED) { call.awaitResponse() }
        val body = TrackingResponseBody()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()

        call.respond(response)
        result.cancel()
        runCurrent()

        assertTrue(body.closed.get())
    }

    @Test
    fun `cancellation closes a body that is already being consumed`() = runBlocking {
        val request = Request.Builder().url("https://example.invalid/slow-body").build()
        val call = CallbackCall(request)
        val body = BlockingResponseBody()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
        val result = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            runCatching { call.awaitResponse().use { it.body?.string() } }.getOrNull()
        }

        call.respond(response)
        assertTrue(body.readStarted.await(2, TimeUnit.SECONDS))
        result.cancelAndJoin()

        assertTrue(body.closed.get())
    }

    private class CallbackCall(
        private val originalRequest: Request,
    ) : Call {
        private lateinit var callback: Callback
        private val cancelled = AtomicBoolean(false)

        fun respond(response: Response) {
            callback.onResponse(this, response)
        }

        override fun request(): Request = originalRequest

        override fun execute(): Response = throw UnsupportedOperationException()

        override fun enqueue(responseCallback: Callback) {
            callback = responseCallback
        }

        override fun cancel() {
            cancelled.set(true)
        }

        override fun isExecuted(): Boolean = ::callback.isInitialized

        override fun isCanceled(): Boolean = cancelled.get()

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = CallbackCall(originalRequest)
    }

    private class TrackingResponseBody : ResponseBody() {
        val closed = AtomicBoolean(false)
        private val trackedSource = object : ForwardingSource(Buffer().writeUtf8("ok")) {
            override fun close() {
                closed.set(true)
                super.close()
            }
        }.buffer()

        override fun contentType() = null

        override fun contentLength(): Long = 2L

        override fun source(): BufferedSource = trackedSource
    }

    private class BlockingResponseBody : ResponseBody() {
        val closed = AtomicBoolean(false)
        val readStarted = CountDownLatch(1)
        private val blockingSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                readStarted.countDown()
                while (!closed.get()) LockSupport.parkNanos(1_000_000L)
                return -1L
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() {
                closed.set(true)
            }
        }.buffer()

        override fun contentType() = null

        override fun contentLength(): Long = -1L

        override fun source(): BufferedSource = blockingSource
    }
}
