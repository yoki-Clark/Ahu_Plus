package com.ahu_plus.data.developer

import com.ahu_plus.data.network.SecureHttpClientFactory
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface NetworkDnsResolver {
    suspend fun resolve(host: String): List<String>
}

object SystemNetworkDnsResolver : NetworkDnsResolver {
    override suspend fun resolve(host: String): List<String> = runInterruptible {
        InetAddress.getAllByName(host)
            .mapNotNull(InetAddress::getHostAddress)
            .distinct()
    }
}

/**
 * Runs a cookie-free, read-only DNS and HTTPS probe for developer diagnostics.
 * Progress callbacks are invoked from [ioDispatcher]. Cancellation is propagated to the OkHttp call.
 */
class NetworkDiagnosticEngine(
    private val dnsResolver: NetworkDnsResolver = SystemNetworkDnsResolver,
    private val clientFactory: (NetworkHostSpec) -> OkHttpClient = ::createDiagnosticClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    suspend fun run(
        hostSpec: NetworkHostSpec,
        onProgress: (NetworkDiagnosticResult) -> Unit = {},
    ): NetworkDiagnosticResult = withContext(ioDispatcher) {
        val startedAtMillis = currentTimeMillis()
        val startedAtNanos = nanoTime()
        var result = NetworkDiagnosticResult(
            hostSpec = hostSpec,
            status = NetworkDiagnosticStatus.RUNNING,
            startedAtEpochMillis = startedAtMillis,
            dns = NetworkDnsResult(status = NetworkDiagnosticStatus.RUNNING),
        )
        onProgress(result)

        val dnsStartedAt = nanoTime()
        try {
            val addresses = withTimeout(DNS_TIMEOUT_MILLIS) {
                dnsResolver.resolve(hostSpec.host)
            }
            currentCoroutineContext().ensureActive()
            if (addresses.isEmpty()) throw UnknownHostException("DNS returned no address for ${hostSpec.host}")

            result = result.copy(
                dns = NetworkDnsResult(
                    status = NetworkDiagnosticStatus.SUCCEEDED,
                    addresses = addresses,
                    durationMillis = elapsedMillis(dnsStartedAt),
                ),
                http = result.http.copy(status = NetworkDiagnosticStatus.RUNNING),
            )
            onProgress(result)
        } catch (timeout: TimeoutCancellationException) {
            return@withContext finishDnsFailure(result, timeout, dnsStartedAt, startedAtNanos, onProgress)
        } catch (cancelled: CancellationException) {
            onProgress(cancelDns(result, dnsStartedAt, startedAtNanos))
            throw cancelled
        } catch (failure: Throwable) {
            return@withContext finishDnsFailure(result, failure, dnsStartedAt, startedAtNanos, onProgress)
        }

        val httpStartedAt = nanoTime()
        try {
            if (hostSpec.requiresTls12) AdwmhDiagnosticRateLimiter.awaitPermit(currentTimeMillis)

            val request = Request.Builder()
                .url(hostSpec.url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .method(hostSpec.method.name, null)
                .build()

            val response = withTimeout(HTTP_TIMEOUT_MILLIS) {
                clientFactory(hostSpec).newCall(request).awaitResponse()
            }
            response.use {
                currentCoroutineContext().ensureActive()
                val phaseStatus = if (it.code in 200..399) {
                    NetworkDiagnosticStatus.SUCCEEDED
                } else {
                    NetworkDiagnosticStatus.WARNING
                }
                result = result.copy(
                    status = phaseStatus,
                    completedAtEpochMillis = currentTimeMillis(),
                    totalDurationMillis = elapsedMillis(startedAtNanos),
                    http = it.toDiagnosticResult(hostSpec, phaseStatus, elapsedMillis(httpStartedAt)),
                )
            }
            onProgress(result)
            result
        } catch (timeout: TimeoutCancellationException) {
            finishHttpFailure(result, timeout, httpStartedAt, startedAtNanos, onProgress)
        } catch (cancelled: CancellationException) {
            onProgress(cancelHttp(result, httpStartedAt, startedAtNanos))
            throw cancelled
        } catch (failure: Throwable) {
            finishHttpFailure(result, failure, httpStartedAt, startedAtNanos, onProgress)
        }
    }

    /** Runs the selected probes sequentially to avoid accidental request bursts. */
    suspend fun runAll(
        hostSpecs: List<NetworkHostSpec> = NetworkDiagnosticHosts.all,
        onResult: (NetworkDiagnosticResult) -> Unit = {},
    ): List<NetworkDiagnosticResult> {
        val results = ArrayList<NetworkDiagnosticResult>(hostSpecs.size)
        for (hostSpec in hostSpecs) {
            currentCoroutineContext().ensureActive()
            val result = run(hostSpec)
            results += result
            onResult(result)
        }
        return results
    }

    private fun finishDnsFailure(
        result: NetworkDiagnosticResult,
        failure: Throwable,
        dnsStartedAt: Long,
        startedAt: Long,
        onProgress: (NetworkDiagnosticResult) -> Unit,
    ): NetworkDiagnosticResult {
        val completed = result.copy(
            status = NetworkDiagnosticStatus.FAILED,
            completedAtEpochMillis = currentTimeMillis(),
            totalDurationMillis = elapsedMillis(startedAt),
            dns = NetworkDnsResult(
                status = NetworkDiagnosticStatus.FAILED,
                durationMillis = elapsedMillis(dnsStartedAt),
                error = failure.toDiagnosticError(),
            ),
            http = result.http.copy(status = NetworkDiagnosticStatus.SKIPPED),
        )
        onProgress(completed)
        return completed
    }

    private fun finishHttpFailure(
        result: NetworkDiagnosticResult,
        failure: Throwable,
        httpStartedAt: Long,
        startedAt: Long,
        onProgress: (NetworkDiagnosticResult) -> Unit,
    ): NetworkDiagnosticResult {
        val completed = result.copy(
            status = NetworkDiagnosticStatus.FAILED,
            completedAtEpochMillis = currentTimeMillis(),
            totalDurationMillis = elapsedMillis(startedAt),
            http = result.http.copy(
                status = NetworkDiagnosticStatus.FAILED,
                durationMillis = elapsedMillis(httpStartedAt),
                error = failure.toDiagnosticError(),
            ),
        )
        onProgress(completed)
        return completed
    }

    private fun cancelDns(
        result: NetworkDiagnosticResult,
        dnsStartedAt: Long,
        startedAt: Long,
    ): NetworkDiagnosticResult = result.copy(
        status = NetworkDiagnosticStatus.CANCELLED,
        completedAtEpochMillis = currentTimeMillis(),
        totalDurationMillis = elapsedMillis(startedAt),
        dns = NetworkDnsResult(
            status = NetworkDiagnosticStatus.CANCELLED,
            durationMillis = elapsedMillis(dnsStartedAt),
            error = CancellationException("DNS diagnostic cancelled").toDiagnosticError(),
        ),
        http = result.http.copy(status = NetworkDiagnosticStatus.SKIPPED),
    )

    private fun cancelHttp(
        result: NetworkDiagnosticResult,
        httpStartedAt: Long,
        startedAt: Long,
    ): NetworkDiagnosticResult = result.copy(
        status = NetworkDiagnosticStatus.CANCELLED,
        completedAtEpochMillis = currentTimeMillis(),
        totalDurationMillis = elapsedMillis(startedAt),
        http = result.http.copy(
            status = NetworkDiagnosticStatus.CANCELLED,
            durationMillis = elapsedMillis(httpStartedAt),
            error = CancellationException("HTTPS diagnostic cancelled").toDiagnosticError(),
        ),
    )

    private fun elapsedMillis(startedAtNanos: Long): Long =
        ((nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND).coerceAtLeast(0L)

    companion object {
        const val DNS_TIMEOUT_MILLIS = 5_000L
        const val HTTP_TIMEOUT_MILLIS = 15_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val USER_AGENT = "AhuPlus-DeveloperDiagnostics/1.0"
    }
}

private fun createDiagnosticClient(hostSpec: NetworkHostSpec): OkHttpClient =
    SecureHttpClientFactory.create(
        followRedirects = false,
        connectTimeoutSec = 8,
        readTimeoutSec = 8,
        trustAll = hostSpec.usesAhuCertificateCompatibility,
        tls12Only = hostSpec.requiresTls12,
    ).newBuilder()
        .callTimeout(NetworkDiagnosticEngine.HTTP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .build()

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) {
                continuation.resume(response)
            } else {
                response.close()
            }
        }
    })
}

private fun Response.toDiagnosticResult(
    hostSpec: NetworkHostSpec,
    status: NetworkDiagnosticStatus,
    durationMillis: Long,
): NetworkHttpResult {
    val handshake = handshake
    return NetworkHttpResult(
        status = status,
        method = hostSpec.method,
        requestedUrl = hostSpec.redactedUrl,
        finalUrl = NetworkDiagnosticUrlRedactor.redact(request.url.toString()),
        redirectLocation = header("Location")?.let(NetworkDiagnosticUrlRedactor::redact),
        httpStatusCode = code,
        httpStatusMessage = message,
        protocol = protocol.toString(),
        durationMillis = durationMillis,
        tlsVersion = handshake?.tlsVersion?.javaName,
        cipherSuite = handshake?.cipherSuite?.javaName,
        peerCertificates = handshake?.peerCertificates
            ?.mapNotNull { it as? X509Certificate }
            ?.map(X509Certificate::toDiagnosticSummary)
            .orEmpty(),
    )
}

private fun X509Certificate.toDiagnosticSummary(): NetworkCertificateSummary =
    NetworkCertificateSummary(
        subject = subjectX500Principal.name,
        issuer = issuerX500Principal.name,
        serialNumberHex = serialNumber.toString(16).uppercase(Locale.US),
        sha256Fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(encoded)
            .joinToString(":") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0').uppercase(Locale.US)
            },
        validFromEpochMillis = notBefore.time,
        validUntilEpochMillis = notAfter.time,
    )

private fun Throwable.toDiagnosticError(): NetworkDiagnosticError {
    val kind = when (this) {
        is UnknownHostException -> NetworkDiagnosticErrorKind.DNS
        is TimeoutCancellationException, is SocketTimeoutException -> NetworkDiagnosticErrorKind.TIMEOUT
        is SSLException -> NetworkDiagnosticErrorKind.TLS
        is IOException -> NetworkDiagnosticErrorKind.NETWORK_IO
        is IllegalArgumentException -> NetworkDiagnosticErrorKind.INVALID_CONFIGURATION
        is CancellationException -> NetworkDiagnosticErrorKind.CANCELLED
        else -> NetworkDiagnosticErrorKind.UNKNOWN
    }
    return NetworkDiagnosticError(
        kind = kind,
        type = this::class.java.simpleName.ifBlank { "UnknownError" },
        message = NetworkDiagnosticUrlRedactor.sanitizeErrorMessage(message),
    )
}

private object AdwmhDiagnosticRateLimiter {
    private const val MIN_REQUEST_GAP_MILLIS = 1_500L
    private val mutex = Mutex()
    private var lastRequestAtMillis = 0L

    suspend fun awaitPermit(currentTimeMillis: () -> Long) = mutex.withLock {
        val now = currentTimeMillis()
        val remaining = MIN_REQUEST_GAP_MILLIS - (now - lastRequestAtMillis)
        if (lastRequestAtMillis > 0L && remaining > 0L) delay(remaining)
        lastRequestAtMillis = currentTimeMillis()
    }
}
