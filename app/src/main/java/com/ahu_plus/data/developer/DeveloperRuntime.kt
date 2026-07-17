package com.ahu_plus.data.developer

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

enum class DeveloperNetworkFault(val title: String) {
    NONE("关闭"),
    OFFLINE("模拟离线"),
    LATENCY("增加延迟"),
    HTTP_401("HTTP 401"),
    HTTP_403("HTTP 403"),
    HTTP_412("HTTP 412"),
    HTTP_500("HTTP 500"),
    MALFORMED_JSON("畸形 JSON"),
    CAS_LOGIN_HTML("CAS 登录页响应"),
}

data class DeveloperRuntimeState(
    val developerEnabled: Boolean = false,
    val networkFault: DeveloperNetworkFault = DeveloperNetworkFault.NONE,
    val latencyMillis: Long = 1_500L,
    /** Empty means every host; otherwise exact host or domain suffix. */
    val targetHost: String = "",
) {
    val hasActiveOverrides: Boolean
        get() = networkFault != DeveloperNetworkFault.NONE
}

/** Process-local developer overrides. They intentionally reset after process death. */
object DeveloperRuntime {
    private val _state = MutableStateFlow(DeveloperRuntimeState())
    val state: StateFlow<DeveloperRuntimeState> = _state.asStateFlow()

    fun setDeveloperEnabled(enabled: Boolean) {
        _state.update { current ->
            if (enabled) current.copy(developerEnabled = true)
            else DeveloperRuntimeState(developerEnabled = false)
        }
        if (enabled) {
            DeveloperEventRecorder.record(category = "开发者模式", message = "开发者功能已启用")
        } else {
            DeveloperEventRecorder.clear()
        }
    }

    fun configureNetworkFault(
        fault: DeveloperNetworkFault,
        latencyMillis: Long = _state.value.latencyMillis,
        targetHost: String = _state.value.targetHost,
    ) {
        _state.update {
            it.copy(
                networkFault = fault,
                latencyMillis = latencyMillis.coerceIn(0L, 10_000L),
                targetHost = targetHost.trim().lowercase(),
            )
        }
        DeveloperEventRecorder.record(
            category = "故障模拟",
            message = "网络场景：${fault.title}",
            detail = targetHost.ifBlank { "全部主机" },
        )
    }

    fun resetOverrides() {
        _state.update { it.copy(networkFault = DeveloperNetworkFault.NONE, targetHost = "") }
        DeveloperEventRecorder.record(category = "故障模拟", message = "已恢复正常网络")
    }
}

enum class DeveloperLogLevel { INFO, WARNING, ERROR }

data class DeveloperLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val level: DeveloperLogLevel,
    val category: String,
    val message: String,
    val detail: String = "",
)

object DeveloperEventRecorder {
    private const val MAX_ENTRIES = 500
    private val nextId = AtomicLong(1L)
    private val _entries = MutableStateFlow<List<DeveloperLogEntry>>(emptyList())
    val entries: StateFlow<List<DeveloperLogEntry>> = _entries.asStateFlow()

    fun record(
        category: String,
        message: String,
        detail: String = "",
        level: DeveloperLogLevel = DeveloperLogLevel.INFO,
    ) {
        val entry = DeveloperLogEntry(
            id = nextId.getAndIncrement(),
            timestampMillis = System.currentTimeMillis(),
            level = level,
            category = category,
            message = message.take(500),
            detail = detail.take(2_000),
        )
        _entries.update { current -> (current + entry).takeLast(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}

/**
 * Global no-op-by-default interceptor used by every client from SecureHttpClientFactory.
 * It records request metadata while developer mode is active and can inject deterministic failures.
 */
class DeveloperNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var requestToProceed = request
        val snapshot = DeveloperRuntime.state.value
        val host = request.url.host.lowercase()
        val applies = snapshot.developerEnabled && hostMatches(host, snapshot.targetHost)
        val startedAt = System.nanoTime()

        if (applies) {
            when (snapshot.networkFault) {
                DeveloperNetworkFault.OFFLINE -> {
                    recordFailure(request.method, host, "模拟离线")
                    throw IOException("开发者模式：模拟离线")
                }
                DeveloperNetworkFault.LATENCY -> sleepInterruptibly(snapshot.latencyMillis)
                DeveloperNetworkFault.HTTP_401 -> {
                    requestToProceed = if (request.method in SAFE_AUTH_TEST_METHODS) {
                        request.newBuilder()
                            .tag(DeveloperAuthenticationFaultTag::class.java, DeveloperAuthenticationFaultTag(401))
                            .build()
                    } else {
                        return syntheticResponse(request, 401, "Unauthorized")
                    }
                }
                DeveloperNetworkFault.HTTP_403 -> return syntheticResponse(request, 403, "Forbidden")
                DeveloperNetworkFault.HTTP_412 -> return syntheticResponse(request, 412, "Precondition Failed")
                DeveloperNetworkFault.HTTP_500 -> return syntheticResponse(request, 500, "Internal Server Error")
                DeveloperNetworkFault.MALFORMED_JSON -> return syntheticResponse(
                    request = request,
                    code = 200,
                    message = "OK",
                    body = "{\"developer_fault\":",
                    mediaType = "application/json",
                )
                DeveloperNetworkFault.CAS_LOGIN_HTML -> return syntheticResponse(
                    request = request,
                    code = 200,
                    message = "OK",
                    body = CAS_LOGIN_BODY,
                    mediaType = "text/html",
                )
                DeveloperNetworkFault.NONE -> Unit
            }
        }

        return try {
            chain.proceed(requestToProceed).also { response ->
                if (snapshot.developerEnabled) {
                    val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
                    DeveloperEventRecorder.record(
                        category = "网络",
                        message = "${request.method} $host -> ${response.code}",
                        detail = "${request.url.encodedPath} · ${elapsedMs}ms",
                        level = if (response.isSuccessful) DeveloperLogLevel.INFO else DeveloperLogLevel.WARNING,
                    )
                }
            }
        } catch (error: IOException) {
            if (snapshot.developerEnabled) recordFailure(request.method, host, error.message ?: "I/O error")
            throw error
        }
    }

    private fun syntheticResponse(
        request: okhttp3.Request,
        code: Int,
        message: String,
        body: String = "{\"developer_fault\":$code}",
        mediaType: String = "application/json",
    ): Response {
        DeveloperEventRecorder.record(
            category = "故障模拟",
            message = "${request.method} ${request.url.host} -> $code",
            detail = request.url.encodedPath,
            level = DeveloperLogLevel.WARNING,
        )
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .header("X-AhuPlus-Developer-Fault", "true")
            .body(body.toResponseBody(mediaType.toMediaType()))
            .build()
    }

    private fun recordFailure(method: String, host: String, detail: String) {
        DeveloperEventRecorder.record(
            category = "网络",
            message = "$method $host 失败",
            detail = detail,
            level = DeveloperLogLevel.ERROR,
        )
    }

    private fun sleepInterruptibly(delayMillis: Long) {
        if (delayMillis <= 0L) return
        try {
            Thread.sleep(delayMillis)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("开发者网络延迟被取消", interrupted)
        }
    }

    private fun hostMatches(host: String, configuredTarget: String): Boolean {
        if (configuredTarget.isBlank()) return true
        val target = configuredTarget.removePrefix("*.").trimStart('.')
        return host == target || host.endsWith(".$target")
    }

    private companion object {
        val SAFE_AUTH_TEST_METHODS = setOf("GET", "HEAD")
        const val CAS_LOGIN_BODY = """
            <!doctype html><html><body><form action="/cas/login" method="post">
            <input type="hidden" name="lt" value="developer-test" />
            <input name="username" /><input name="password" type="password" />
            </form></body></html>
        """
    }
}

private class DeveloperAuthenticationFaultTag(
    val statusCode: Int,
) {
    val injected = AtomicBoolean(false)
}

/**
 * Places a synthetic 401 inside OkHttp's retry/follow-up layer so the configured Authenticator
 * receives it. A real read-only response is obtained first to satisfy network-interceptor rules;
 * write methods are never routed through this interceptor.
 */
class DeveloperAuthenticationFaultInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val tag = request.tag(DeveloperAuthenticationFaultTag::class.java)
            ?: return chain.proceed(request)

        if (!tag.injected.compareAndSet(false, true)) {
            return chain.proceed(
                request.newBuilder()
                    .tag(DeveloperAuthenticationFaultTag::class.java, null)
                    .build(),
            )
        }

        chain.proceed(request).close()

        DeveloperEventRecorder.record(
            category = "故障模拟",
            message = "${request.method} ${request.url.host} -> ${tag.statusCode}",
            detail = "已注入认证层响应",
            level = DeveloperLogLevel.WARNING,
        )
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(tag.statusCode)
            .message("Unauthorized")
            .header("WWW-Authenticate", "AhuPlus-Developer-Test")
            .header("X-AhuPlus-Developer-Fault", "true")
            .body("{\"developer_fault\":${tag.statusCode}}".toResponseBody("application/json".toMediaType()))
            .build()
    }
}
