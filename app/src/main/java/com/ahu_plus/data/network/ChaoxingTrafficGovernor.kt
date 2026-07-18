package com.ahu_plus.data.network

import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.jsoup.Jsoup

/** Account and host identity used by the traffic governor. */
data class ChaoxingTrafficKey(
    val account: String,
    val host: String,
) {
    companion object {
        fun of(account: String, host: String): ChaoxingTrafficKey {
            val normalizedAccount = account.trim().ifBlank { "anonymous" }
            return ChaoxingTrafficKey(normalizedAccount, normalizeHost(host))
        }

        private fun normalizeHost(rawHost: String): String {
            val value = rawHost.trim().lowercase(Locale.US)
            if (value.isBlank()) return "unknown"
            val parsedHost = runCatching {
                if (value.contains("://")) URI(value).host else null
            }.getOrNull()
            val normalized = if (!parsedHost.isNullOrBlank()) {
                parsedHost.lowercase(Locale.US)
            } else {
                value.substringBefore('/').substringBefore(':').ifBlank { "unknown" }
            }
            return if (normalized == "chaoxing.com" || normalized.endsWith(".chaoxing.com")) {
                "chaoxing.com"
            } else {
                normalized
            }
        }
    }
}

enum class ChaoxingResponseKind {
    VALID,
    AUTH_EXPIRED,
    FORBIDDEN,
    RATE_LIMITED,
    RISK_CHALLENGE,
    TRANSIENT,
    PERMANENT,
}

data class ChaoxingResponseClassification(
    val kind: ChaoxingResponseKind,
    val statusCode: Int,
    val retryAfterMillis: Long? = null,
    val reason: String? = null,
    val cooldownMillis: Long = 0L,
) {
    val isValid: Boolean get() = kind == ChaoxingResponseKind.VALID
    val isRetryableRead: Boolean get() = kind == ChaoxingResponseKind.TRANSIENT
}

/** Persistable subset of one active account/traffic cooldown. */
data class ChaoxingTrafficStateSnapshot(
    val key: ChaoxingTrafficKey,
    val consecutiveFailures: Int,
    val cooldownUntilMillis: Long,
    val classification: ChaoxingResponseClassification?,
)

open class ChaoxingTrafficException(
    message: String,
    val key: ChaoxingTrafficKey? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

open class ChaoxingTrafficCooldownException(
    key: ChaoxingTrafficKey,
    val remainingMillis: Long,
    val classification: ChaoxingResponseClassification?,
    message: String = "Chaoxing traffic is cooling down for ${remainingMillis}ms",
) : ChaoxingTrafficException(message, key)

class ChaoxingRateLimitedException(
    key: ChaoxingTrafficKey,
    remainingMillis: Long,
    classification: ChaoxingResponseClassification?,
) : ChaoxingTrafficCooldownException(
    key,
    remainingMillis,
    classification,
    "Chaoxing rate limit is active for ${remainingMillis}ms",
)

class ChaoxingRiskChallengeException(
    key: ChaoxingTrafficKey,
    remainingMillis: Long,
    classification: ChaoxingResponseClassification?,
) : ChaoxingTrafficCooldownException(
    key,
    remainingMillis,
    classification,
    "Chaoxing risk challenge is active for ${remainingMillis}ms",
)

class ChaoxingAuthExpiredException(
    key: ChaoxingTrafficKey,
    remainingMillis: Long,
    classification: ChaoxingResponseClassification?,
) : ChaoxingTrafficCooldownException(
    key,
    remainingMillis,
    classification,
    "Chaoxing authentication refresh is required",
)

class ChaoxingForbiddenException(
    key: ChaoxingTrafficKey,
    remainingMillis: Long,
    classification: ChaoxingResponseClassification?,
) : ChaoxingTrafficCooldownException(
    key,
    remainingMillis,
    classification,
    "Chaoxing request was forbidden",
)

/** A second request for the same account/host was attempted while one is in flight. */
class ChaoxingTrafficBusyException(
    key: ChaoxingTrafficKey,
) : ChaoxingTrafficException("Chaoxing request is already in flight for ${key.host}", key)

/** Pure JVM response classifier. It only examines status, headers and a bounded body sample. */
object ChaoxingResponseClassifier {
    private const val MAX_BODY_SAMPLE = 16 * 1024
    private const val MAX_RETRY_AFTER_MILLIS = 24L * 60L * 60L * 1000L

    private val riskMarkers = listOf(
        "captcha",
        "cf-chl-",
        "cloudflare",
        "just a moment",
        "security check",
        "access denied",
        "rate limit",
        "too many requests",
        "robot check",
        "verification required",
        "challenge-platform",
        "verification code",
        "risk control",
        "frequent access",
        "frequent operation",
        "security verification",
        "complete verification",
    )

    fun classify(
        statusCode: Int,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): ChaoxingResponseClassification {
        val retryAfter = parseRetryAfter(header(headers, "Retry-After"), nowMillis)
        val contentType = header(headers, "Content-Type").orEmpty()
        val location = header(headers, "Location").orEmpty().lowercase(Locale.US)
        val rawSample = body.orEmpty().take(MAX_BODY_SAMPLE)
        val sample = rawSample.lowercase(Locale.US)
        val structuredKind = structuredResponseKind(rawSample)
        val riskPage = isRiskPage(statusCode, contentType, sample)
        val authPage = isAuthPage(sample)
        val authRedirect = statusCode in 300..399 &&
            (location.contains("login") || location.contains("passport") || location.contains("fanyalogin"))
        val kind = when {
            statusCode == 429 -> ChaoxingResponseKind.RATE_LIMITED
            structuredKind == ChaoxingResponseKind.RATE_LIMITED -> ChaoxingResponseKind.RATE_LIMITED
            riskPage -> ChaoxingResponseKind.RISK_CHALLENGE
            statusCode == 401 -> ChaoxingResponseKind.AUTH_EXPIRED
            statusCode == 403 -> ChaoxingResponseKind.FORBIDDEN
            structuredKind != null -> structuredKind
            authPage || authRedirect -> ChaoxingResponseKind.AUTH_EXPIRED
            statusCode in 200..299 -> ChaoxingResponseKind.VALID
            statusCode == 408 || statusCode == 425 || statusCode in 500..599 ->
                ChaoxingResponseKind.TRANSIENT
            else -> ChaoxingResponseKind.PERMANENT
        }
        return ChaoxingResponseClassification(
            kind = kind,
            statusCode = statusCode,
            retryAfterMillis = retryAfter,
            reason = reasonFor(kind, statusCode),
        )
    }

    fun classifyHeaders(
        statusCode: Int,
        headers: Headers,
        body: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): ChaoxingResponseClassification {
        val values = headers.names().associateWith { name -> headers[name].orEmpty() }
        return classify(statusCode, values, body, nowMillis)
    }

    /** Returns a duration in milliseconds for either delta-seconds or an HTTP date. */
    fun parseRetryAfter(value: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        raw.toLongOrNull()?.let { seconds ->
            return seconds.coerceIn(0L, MAX_RETRY_AFTER_MILLIS / 1000L) * 1000L
        }
        raw.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }?.let { seconds ->
            return min((seconds * 1000.0).toLong(), MAX_RETRY_AFTER_MILLIS)
        }
        val epochMillis = runCatching {
            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
        }.getOrNull() ?: parseLegacyHttpDate(raw)
        return epochMillis?.let { (it - nowMillis).coerceIn(0L, MAX_RETRY_AFTER_MILLIS) }
    }

    private fun parseLegacyHttpDate(raw: String): Long? {
        val patterns = arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy",
        )
        for (pattern in patterns) {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("GMT")
            }
            val position = ParsePosition(0)
            val parsed: Date? = parser.parse(raw, position)
            if (parsed != null && position.index == raw.length) return parsed.time
        }
        return null
    }

    private fun header(headers: Map<String, String>, name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun structuredResponseKind(sample: String): ChaoxingResponseKind? {
        val trimmed = sample.trim()
        if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) return null
        val root = runCatching { JsonParser.parseString(trimmed) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return null
        return sequenceOf("code", "status")
            .mapNotNull { field ->
                root.get(field)
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asJsonPrimitive
                    ?.let { value -> runCatching { value.asString.trim().toInt() }.getOrNull() }
            }
            .mapNotNull { code ->
                when (code) {
                    401 -> ChaoxingResponseKind.AUTH_EXPIRED
                    403 -> ChaoxingResponseKind.FORBIDDEN
                    429 -> ChaoxingResponseKind.RATE_LIMITED
                    else -> null
                }
            }
            .firstOrNull()
    }

    private fun isRiskPage(statusCode: Int, contentType: String, sample: String): Boolean {
        if (sample.isBlank()) return false
        val isHtml = contentType.contains("text/html", ignoreCase = true) ||
            sample.contains("<html") || sample.contains("<!doctype") || sample.contains("<head")
        val unicodeRiskMarkers = listOf(
            String(charArrayOf(0x9a8c.toChar(), 0x8bc1.toChar(), 0x7801.toChar())),
            String(charArrayOf(0x98ce.toChar(), 0x63a7.toChar())),
            String(charArrayOf(0x8bbf.toChar(), 0x95ee.toChar(), 0x9891.toChar(), 0x7e41.toChar())),
            String(charArrayOf(0x64cd.toChar(), 0x4f5c.toChar(), 0x9891.toChar(), 0x7e41.toChar())),
            String(charArrayOf(0x8bbf.toChar(), 0x95ee.toChar(), 0x8fc7.toChar(), 0x4e8e.toChar(), 0x9891.toChar(), 0x7e41.toChar())),
            String(charArrayOf(0x64cd.toChar(), 0x4f5c.toChar(), 0x8fc7.toChar(), 0x4e8e.toChar(), 0x9891.toChar(), 0x7e41.toChar())),
            String(charArrayOf(0x8bbf.toChar(), 0x95ee.toChar(), 0x53d7.toChar(), 0x9650.toChar())),
            String(charArrayOf(0x8d26.toChar(), 0x53f7.toChar(), 0x5f02.toChar(), 0x5e38.toChar())),
            String(charArrayOf(0x5b89.toChar(), 0x5168.toChar(), 0x9a8c.toChar(), 0x8bc1.toChar())),
            String(charArrayOf(0x8bf7.toChar(), 0x5b8c.toChar(), 0x6210.toChar(), 0x9a8c.toChar(), 0x8bc1.toChar())),
        )
        val allMarkers = riskMarkers + unicodeRiskMarkers
        val marker = allMarkers.any(sample::contains)
        val htmlSignals = if (isHtml) runCatching {
            val document = Jsoup.parse(sample)
            document.select("script, style, noscript, template").remove()
            val visibleText = document.text().lowercase(Locale.US)
            val title = document.title().lowercase(Locale.US)
            val challengeForm = document.select("form").any { form ->
                val formText = buildString {
                    append(form.attr("action"))
                    append(' ')
                    append(form.text())
                    append(' ')
                    append(form.html())
                }.lowercase(Locale.US)
                allMarkers.any(formText::contains) ||
                    formText.contains("verify") || formText.contains("challenge")
            }
            Triple(visibleText, title, challengeForm)
        }.getOrNull() else null
        val visibleText = htmlSignals?.first.orEmpty()
        val strongVisibleMarkers = listOf(
            "rate limit",
            "too many requests",
            "access denied",
            "robot check",
            "frequent access",
            "frequent operation",
            "risk control",
            "security verification",
            "complete verification",
            unicodeRiskMarkers[1], // 风控
            unicodeRiskMarkers[2], // 访问频繁
            unicodeRiskMarkers[3], // 操作频繁
            unicodeRiskMarkers[4], // 访问过于频繁
            unicodeRiskMarkers[5], // 操作过于频繁
            unicodeRiskMarkers[6], // 访问受限
            unicodeRiskMarkers[7], // 账号异常
            unicodeRiskMarkers[8], // 安全验证
        )
        val visibleMarker = allMarkers.any(visibleText::contains)
        val visibleStrongMarker = strongVisibleMarkers.any(visibleText::contains)
        val titleHasMarker = allMarkers.any(htmlSignals?.second.orEmpty()::contains)
        val challengeForm = htmlSignals?.third == true
        val htmlRisk = isHtml && (
            visibleStrongMarker && (titleHasMarker || challengeForm || visibleText.length <= 1_024) ||
                visibleMarker && (titleHasMarker || challengeForm)
            )
        val jsonLike = contentType.contains("json", ignoreCase = true) ||
            sample.trimStart().startsWith("{") || sample.trimStart().startsWith("[")
        val structuredError = if (jsonLike) {
            val errorFields = listOf("\"error\"", "\"err\"", "\"message\"", "\"msg\"", "\"reason\"")
            val markerInErrorField = errorFields.any { field ->
                var fieldIndex = sample.indexOf(field)
                var matched = false
                while (fieldIndex >= 0 && !matched) {
                    val colonIndex = sample.indexOf(':', fieldIndex + field.length)
                    if (colonIndex >= 0) {
                        val commaIndex = sample.indexOf(',', colonIndex + 1).takeIf { it >= 0 }
                        val braceIndex = sample.indexOf('}', colonIndex + 1).takeIf { it >= 0 }
                        val valueEnd = listOfNotNull(commaIndex, braceIndex).minOrNull()
                            ?: min(sample.length, colonIndex + 257)
                        val value = sample.substring(colonIndex + 1, min(valueEnd, colonIndex + 257))
                        matched = allMarkers.any(value::contains)
                    }
                    fieldIndex = sample.indexOf(field, fieldIndex + field.length)
                }
                matched
            }
            val compact = sample.filterNot(Char::isWhitespace)
            val riskStatus = listOf(
                "\"code\":403", "\"code\":429", "\"status\":403", "\"status\":429",
                "\"code\":\"403\"", "\"code\":\"429\"", "\"status\":\"403\"", "\"status\":\"429\"",
            ).any(compact::contains)
            markerInErrorField || (riskStatus && marker)
        } else {
            false
        }
        return (statusCode in 400..499 && marker) || structuredError || htmlRisk
    }

    private fun isAuthPage(sample: String): Boolean {
        if (sample.isBlank()) return false
        val hasUserField = sample.contains("name=\"uname\"") ||
            sample.contains("name='uname'") ||
            sample.contains("name=\"username\"") ||
            sample.contains("name='username'")
        val hasPasswordField = sample.contains("name=\"password\"") ||
            sample.contains("name='password'") ||
            sample.contains("name=\"pwd\"") ||
            sample.contains("name='pwd'")
        val loginForm = sample.contains("<form") && hasUserField && hasPasswordField
        val chaoxingLogin = sample.contains("<form") && sample.contains("fanyalogin") &&
            hasUserField && hasPasswordField
        return loginForm || chaoxingLogin
    }

    private fun reasonFor(kind: ChaoxingResponseKind, statusCode: Int): String = when (kind) {
        ChaoxingResponseKind.VALID -> "http_$statusCode"
        ChaoxingResponseKind.AUTH_EXPIRED -> "authentication_required"
        ChaoxingResponseKind.FORBIDDEN -> "forbidden"
        ChaoxingResponseKind.RATE_LIMITED -> "rate_limited"
        ChaoxingResponseKind.RISK_CHALLENGE -> "risk_challenge"
        ChaoxingResponseKind.TRANSIENT -> "transient_http_error"
        ChaoxingResponseKind.PERMANENT -> "permanent_http_error"
    }
}

/**
 * Account/traffic-scope admission control. Restriction states fail fast; normal requests use
 * a small deterministic pacing interval before entering OkHttp so sequential batch work does
 * not become a burst.
 */
class ChaoxingTrafficGovernor(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val authCooldownMillis: Long = DEFAULT_AUTH_COOLDOWN_MILLIS,
    private val forbiddenCooldownMillis: Long = DEFAULT_FORBIDDEN_COOLDOWN_MILLIS,
    private val rateLimitCooldownMillis: Long = DEFAULT_RATE_LIMIT_COOLDOWN_MILLIS,
    private val riskCooldownMillis: Long = DEFAULT_RISK_COOLDOWN_MILLIS,
    private val maxCooldownMillis: Long = DEFAULT_MAX_COOLDOWN_MILLIS,
    private val minRequestIntervalMillis: Long = DEFAULT_MIN_REQUEST_INTERVAL_MILLIS,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val onStateChanged: () -> Unit = {},
) {
    init {
        require(authCooldownMillis >= 0L)
        require(forbiddenCooldownMillis >= 0L)
        require(rateLimitCooldownMillis >= 0L)
        require(riskCooldownMillis >= 0L)
        require(maxCooldownMillis >= 0L)
        require(minRequestIntervalMillis >= 0L)
    }

    companion object {
        const val DEFAULT_AUTH_COOLDOWN_MILLIS = 30_000L
        const val DEFAULT_FORBIDDEN_COOLDOWN_MILLIS = 60_000L
        const val DEFAULT_RATE_LIMIT_COOLDOWN_MILLIS = 60_000L
        const val DEFAULT_RISK_COOLDOWN_MILLIS = 5L * 60_000L
        const val DEFAULT_MAX_COOLDOWN_MILLIS = 30L * 60_000L
        const val DEFAULT_MIN_REQUEST_INTERVAL_MILLIS = 750L
        private const val MAX_FAILURE_EXPONENT = 8
    }

    private data class State(
        var consecutiveFailures: Int = 0,
        var cooldownUntilMillis: Long = 0L,
        var lastClassification: ChaoxingResponseClassification? = null,
    )

    private data class FlightKey(
        val trafficKey: ChaoxingTrafficKey,
        val operation: String,
    )

    private data class EntryTrafficTag(val key: ChaoxingTrafficKey)

    private val stateGuard = Any()
    private val states = ConcurrentHashMap<ChaoxingTrafficKey, State>()
    private val requestLocks = ConcurrentHashMap<ChaoxingTrafficKey, Mutex>()
    private val inFlight = ConcurrentHashMap<FlightKey, CompletableDeferred<Any?>>()
    private val interceptorGates = ConcurrentHashMap<ChaoxingTrafficKey, Semaphore>()
    private val pacingGuard = Any()
    private val lastRequestAtMillis = ConcurrentHashMap<String, Long>()

    fun beforeRequest(account: String, host: String): ChaoxingRequestAdmission =
        beforeRequest(ChaoxingTrafficKey.of(account, host))

    fun beforeRequest(key: ChaoxingTrafficKey): ChaoxingRequestAdmission {
        val now = clock()
        synchronized(stateGuard) {
            val state = states[key]
            val remaining = (state?.cooldownUntilMillis ?: 0L) - now
            if (remaining > 0L) {
                throw cooldownException(key, remaining, state?.lastClassification)
            }
            if (state != null && state.cooldownUntilMillis != 0L) state.cooldownUntilMillis = 0L
        }
        return ChaoxingRequestAdmission(key, now)
    }

    fun isRequestAllowed(account: String, host: String): Boolean = runCatching {
        beforeRequest(account, host)
        true
    }.getOrDefault(false)

    fun cooldownRemainingMillis(account: String, host: String): Long =
        cooldownRemainingMillis(ChaoxingTrafficKey.of(account, host))

    fun cooldownRemainingMillis(key: ChaoxingTrafficKey): Long {
        val remaining = synchronized(stateGuard) {
            (states[key]?.cooldownUntilMillis ?: 0L) - clock()
        }
        return remaining.coerceAtLeast(0L)
    }

    fun recordResponse(
        account: String,
        host: String,
        statusCode: Int,
        headers: Headers? = null,
        body: String? = null,
    ): ChaoxingResponseClassification = recordResponse(
        ChaoxingTrafficKey.of(account, host), statusCode, headers, body,
    )

    fun recordResponse(
        key: ChaoxingTrafficKey,
        statusCode: Int,
        headers: Headers? = null,
        body: String? = null,
    ): ChaoxingResponseClassification {
        val now = clock()
        val classification = if (headers == null) {
            ChaoxingResponseClassifier.classify(statusCode, body = body, nowMillis = now)
        } else {
            ChaoxingResponseClassifier.classifyHeaders(statusCode, headers, body, now)
        }
        val recorded: ChaoxingResponseClassification
        var shouldNotifyStateChanged = false
        synchronized(stateGuard) {
            val state = states.getOrPut(key) { State() }
            val previousCooldownUntil = state.cooldownUntilMillis
            val previousClassification = state.lastClassification
            val cooldown = cooldownFor(classification, state.consecutiveFailures)
            when (classification.kind) {
                ChaoxingResponseKind.VALID -> {
                    state.consecutiveFailures = 0
                    state.cooldownUntilMillis = 0L
                }
                ChaoxingResponseKind.AUTH_EXPIRED,
                ChaoxingResponseKind.FORBIDDEN,
                ChaoxingResponseKind.RATE_LIMITED,
                ChaoxingResponseKind.RISK_CHALLENGE,
                -> {
                    state.consecutiveFailures =
                        (state.consecutiveFailures + 1).coerceAtMost(MAX_FAILURE_EXPONENT + 1)
                    state.cooldownUntilMillis = safeAdd(now, cooldown)
                }
                ChaoxingResponseKind.TRANSIENT -> {
                    state.cooldownUntilMillis = if (cooldown > 0L) safeAdd(now, cooldown) else 0L
                }
                ChaoxingResponseKind.PERMANENT -> state.cooldownUntilMillis = 0L
            }
            recorded = classification.copy(cooldownMillis = cooldown)
            state.lastClassification = recorded
            shouldNotifyStateChanged =
                (previousCooldownUntil > now || state.cooldownUntilMillis > now) &&
                    (previousCooldownUntil != state.cooldownUntilMillis || previousClassification != recorded)
        }
        if (shouldNotifyStateChanged) notifyStateChanged()
        return recorded
    }

    /** Returns only cooldowns that are still active at the time of the snapshot. */
    fun snapshot(): List<ChaoxingTrafficStateSnapshot> {
        val now = clock()
        return synchronized(stateGuard) {
            states.entries.mapNotNull { (key, state) ->
                if (state.cooldownUntilMillis <= now) return@mapNotNull null
                ChaoxingTrafficStateSnapshot(
                    key = key,
                    consecutiveFailures = state.consecutiveFailures,
                    cooldownUntilMillis = state.cooldownUntilMillis,
                    classification = state.lastClassification,
                )
            }.sortedWith(compareBy({ it.key.account }, { it.key.host }))
        }
    }

    /** Restores active cooldowns, capping untrusted persisted deadlines to [maxCooldownMillis]. */
    fun restore(snapshots: Iterable<ChaoxingTrafficStateSnapshot>) {
        val now = clock()
        synchronized(stateGuard) {
            for (snapshot in snapshots) {
                runCatching {
                    val remaining = (snapshot.cooldownUntilMillis - now)
                        .coerceAtMost(maxCooldownMillis)
                    if (remaining <= 0L) return@runCatching
                    val key = ChaoxingTrafficKey.of(snapshot.key.account, snapshot.key.host)
                    val restoredUntil = safeAdd(now, remaining)
                    val current = states[key]
                    if (current != null && current.cooldownUntilMillis >= restoredUntil) return@runCatching
                    states[key] = State(
                        consecutiveFailures = snapshot.consecutiveFailures
                            .coerceIn(1, MAX_FAILURE_EXPONENT + 1),
                        cooldownUntilMillis = restoredUntil,
                        lastClassification = snapshot.classification?.copy(cooldownMillis = remaining),
                    )
                }
            }
        }
    }

    suspend fun <T> withRequestLock(
        account: String,
        host: String,
        block: suspend () -> T,
    ): T = withRequestLock(ChaoxingTrafficKey.of(account, host), block)

    suspend fun <T> withRequestLock(
        key: ChaoxingTrafficKey,
        block: suspend () -> T,
    ): T {
        val lock = requestLocks.computeIfAbsent(key) { Mutex() }
        return lock.withLock {
            beforeRequest(key)
            block()
        }
    }

    suspend fun <T> singleFlight(
        account: String,
        host: String,
        operation: String,
        block: suspend () -> T,
    ): T = singleFlight(ChaoxingTrafficKey.of(account, host), operation, block)

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> singleFlight(
        key: ChaoxingTrafficKey,
        operation: String,
        block: suspend () -> T,
    ): T {
        val flightKey = FlightKey(key, operation.trim().ifBlank { "default" })
        val candidate = CompletableDeferred<Any?>()
        val existing = inFlight.putIfAbsent(flightKey, candidate)
        if (existing != null) return existing.await() as T
        try {
            beforeRequest(key)
            val result = block()
            candidate.complete(result)
            return result
        } catch (error: Throwable) {
            candidate.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(flightKey, candidate)
        }
    }

    /**
     * Optional OkHttp interceptor. It performs fail-fast admission, applies deterministic pacing,
     * classifies a bounded response sample, and throws on auth/risk/rate-limit responses. It never
     * retries. Install this as a network interceptor so every redirect hop is observed.
     */
    fun asRequestInterceptor(
        accountProvider: (Request) -> String,
        peekBodyBytes: Long = 16L * 1024L,
    ): Interceptor {
        require(peekBodyBytes > 0L)
        return Interceptor { chain ->
            val request = chain.request()
            val requestKey = ChaoxingTrafficKey.of(accountProvider(request), request.url.host)
            val entryKey = request.tag(EntryTrafficTag::class.java)?.key ?: requestKey
            val gate = interceptorGates.computeIfAbsent(requestKey) { Semaphore(1) }
            if (!gate.tryAcquire()) throw ChaoxingTrafficBusyException(requestKey)
            var releaseNow = true
            try {
                beforeRequest(requestKey)
                val pacingDelay = reservePacing(requestKey.account, clock())
                if (pacingDelay > 0L) {
                    try {
                        sleeper(pacingDelay)
                    } catch (interrupted: InterruptedException) {
                        synchronized(pacingGuard) { lastRequestAtMillis.remove(requestKey.account) }
                        Thread.currentThread().interrupt()
                        throw IOException("Chaoxing request pacing was interrupted", interrupted)
                    }
                }
                markPacingStart(requestKey.account, clock())
                val response = chain.proceed(request)
                val responseKey = ChaoxingTrafficKey.of(requestKey.account, response.request.url.host)
                val sample = runCatching { response.peekBody(peekBodyBytes).string() }.getOrNull()
                val classification = recordResponse(
                    responseKey,
                    response.code,
                    response.headers,
                    sample,
                )
                if (responseKey != entryKey && classification.kind !in setOf(
                        ChaoxingResponseKind.VALID,
                        ChaoxingResponseKind.PERMANENT,
                    )
                ) {
                    recordResponse(entryKey, response.code, response.headers, sample)
                }
                when (classification.kind) {
                    ChaoxingResponseKind.AUTH_EXPIRED,
                    ChaoxingResponseKind.FORBIDDEN,
                    ChaoxingResponseKind.RATE_LIMITED,
                    ChaoxingResponseKind.RISK_CHALLENGE,
                    -> {
                        response.close()
                        throw cooldownException(
                            responseKey,
                            classification.cooldownMillis.coerceAtLeast(1L),
                            classification,
                        )
                    }
                    else -> {
                        val body = response.body
                        if (body == null) {
                            response
                        } else {
                            val gatedResponse = response.newBuilder()
                                .body(GatedResponseBody(body) { gate.release() })
                                .build()
                            releaseNow = false
                            gatedResponse
                        }
                    }
                }
            } finally {
                if (releaseNow) gate.release()
            }
        }
    }

    /** Convenience overload for callers with one known account identity. */
    fun asInterceptor(
        account: String,
        peekBodyBytes: Long = 16L * 1024L,
    ): Interceptor = asRequestInterceptor({ account }, peekBodyBytes)

    /** Convenience overload for a provider that does not need to inspect the request. */
    fun asInterceptor(
        accountProvider: () -> String = { "anonymous" },
        peekBodyBytes: Long = 16L * 1024L,
    ): Interceptor = asRequestInterceptor({ accountProvider() }, peekBodyBytes)

    /** Tags the original host before OkHttp follows redirects; no request is admitted here. */
    fun asEntryTagInterceptor(accountProvider: (Request) -> String): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val key = ChaoxingTrafficKey.of(accountProvider(request), request.url.host)
        chain.proceed(request.newBuilder().tag(EntryTrafficTag::class.java, EntryTrafficTag(key)).build())
    }

    fun asEntryTagInterceptor(account: String): Interceptor =
        asEntryTagInterceptor { account }

    private fun cooldownFor(
        classification: ChaoxingResponseClassification,
        previousFailures: Int,
    ): Long {
        if (classification.retryAfterMillis != null && classification.kind in setOf(
                ChaoxingResponseKind.AUTH_EXPIRED,
                ChaoxingResponseKind.FORBIDDEN,
                ChaoxingResponseKind.RATE_LIMITED,
                ChaoxingResponseKind.RISK_CHALLENGE,
                ChaoxingResponseKind.TRANSIENT,
            )
        ) {
            classification.retryAfterMillis.takeIf { it > 0L }?.let { retryAfter ->
                return retryAfter.coerceAtMost(maxCooldownMillis)
            }
        }
        val base = when (classification.kind) {
            ChaoxingResponseKind.AUTH_EXPIRED -> authCooldownMillis
            ChaoxingResponseKind.FORBIDDEN -> forbiddenCooldownMillis
            ChaoxingResponseKind.RATE_LIMITED -> rateLimitCooldownMillis
            ChaoxingResponseKind.RISK_CHALLENGE -> riskCooldownMillis
            else -> 0L
        }
        if (base <= 0L) return 0L
        var result = base
        repeat(previousFailures.coerceIn(0, MAX_FAILURE_EXPONENT)) {
            result = if (result >= maxCooldownMillis / 2L) maxCooldownMillis else result * 2L
        }
        return result.coerceAtMost(maxCooldownMillis)
    }

    private fun reservePacing(account: String, now: Long): Long {
        if (minRequestIntervalMillis <= 0L) return 0L
        synchronized(pacingGuard) {
            val previous = lastRequestAtMillis[account]
            val earliest = if (previous == null) now else safeAdd(previous, minRequestIntervalMillis)
            return (earliest - now).coerceAtLeast(0L)
        }
    }

    private fun markPacingStart(account: String, now: Long) {
        if (minRequestIntervalMillis <= 0L) return
        synchronized(pacingGuard) { lastRequestAtMillis[account] = now }
    }

    private fun cooldownException(
        key: ChaoxingTrafficKey,
        remaining: Long,
        classification: ChaoxingResponseClassification?,
    ): ChaoxingTrafficCooldownException = when (classification?.kind) {
        ChaoxingResponseKind.AUTH_EXPIRED -> ChaoxingAuthExpiredException(key, remaining, classification)
        ChaoxingResponseKind.FORBIDDEN -> ChaoxingForbiddenException(key, remaining, classification)
        ChaoxingResponseKind.RATE_LIMITED -> ChaoxingRateLimitedException(key, remaining, classification)
        ChaoxingResponseKind.RISK_CHALLENGE -> ChaoxingRiskChallengeException(key, remaining, classification)
        else -> ChaoxingTrafficCooldownException(key, remaining, classification)
    }

    private fun safeAdd(now: Long, duration: Long): Long {
        if (duration <= 0L) return 0L
        if (Long.MAX_VALUE - now < duration) return Long.MAX_VALUE
        return now + duration
    }

    private fun notifyStateChanged() {
        runCatching(onStateChanged)
    }

    /** Holds the host gate until callers finish consuming the response body. */
    private class GatedResponseBody(
        private val delegate: ResponseBody,
        release: () -> Unit,
    ) : ResponseBody() {
        private val released = AtomicBoolean(false)
        private val releaseOnce = { if (released.compareAndSet(false, true)) release() }
        private val gatedSource: BufferedSource = object : ForwardingSource(delegate.source()) {
            override fun read(sink: okio.Buffer, byteCount: Long): Long = try {
                val count = super.read(sink, byteCount)
                if (count == -1L) releaseOnce()
                count
            } catch (error: Throwable) {
                releaseOnce()
                throw error
            }

            override fun close() {
                try {
                    super.close()
                } finally {
                    releaseOnce()
                }
            }
        }.buffer()

        override fun contentType() = delegate.contentType()

        override fun contentLength() = delegate.contentLength()

        override fun source(): BufferedSource = gatedSource

        override fun close() {
            try {
                gatedSource.close()
            } finally {
                delegate.close()
                releaseOnce()
            }
        }
    }
}

data class ChaoxingRequestAdmission(
    val key: ChaoxingTrafficKey,
    val checkedAtMillis: Long,
)
