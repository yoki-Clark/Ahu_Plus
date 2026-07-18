package com.ahu_plus.data.network

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/**
 * Bridges OkHttp's callback API to coroutines without leaving a blocking
 * execute() call behind when a ViewModel or Service is cancelled.
 */
@OptIn(InternalCoroutinesApi::class)
suspend fun Call.awaitResponse(): Response {
    val bodyRef = AtomicReference<ResponseBody?>(null)
    return suspendCancellableCoroutine { continuation ->
        val completionHandle = continuation.context[Job]?.invokeOnCompletion(
            onCancelling = true,
            invokeImmediately = true,
        ) {
                cancel()
                bodyRef.getAndSet(null)?.close()
            }
        continuation.invokeOnCancellation {
            cancel()
            bodyRef.getAndSet(null)?.close()
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                completionHandle?.dispose()
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    completionHandle?.dispose()
                    response.close()
                    return
                }

                val originalBody = response.body
                if (originalBody == null) {
                    completionHandle?.dispose()
                    continuation.resume(response)
                    return
                }

                bodyRef.set(originalBody)
                val wrappedResponse = response.newBuilder()
                    .body(CancellableResponseBody(originalBody) {
                        bodyRef.compareAndSet(originalBody, null)
                        completionHandle?.dispose()
                    })
                    .build()
                continuation.resume(wrappedResponse) { _, undeliveredResponse, _ ->
                    undeliveredResponse.close()
                    completionHandle?.dispose()
                }
            }
        })
    }
}

private class CancellableResponseBody(
    private val delegate: ResponseBody,
    private val onClosed: () -> Unit,
) : ResponseBody() {
    private val released = AtomicBoolean(false)
    private val source: BufferedSource = object : ForwardingSource(delegate.source()) {
        override fun read(sink: okio.Buffer, byteCount: Long): Long = try {
            val count = super.read(sink, byteCount)
            if (count == -1L) release()
            count
        } catch (error: Throwable) {
            release()
            throw error
        }

        override fun close() {
            try {
                super.close()
            } finally {
                release()
            }
        }
    }.buffer()

    private fun release() {
        if (released.compareAndSet(false, true)) onClosed()
    }

    override fun contentType() = delegate.contentType()

    override fun contentLength() = delegate.contentLength()

    override fun source(): BufferedSource = source

    override fun close() {
        try {
            source.close()
        } finally {
            delegate.close()
            release()
        }
    }
}
