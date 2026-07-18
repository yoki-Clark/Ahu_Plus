package com.ahu_plus.data.developer

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object NetworkDiagnosticUrlRedactor {
    private const val REDACTED_VALUE = "REDACTED"
    private const val MAX_NESTED_URL_DEPTH = 2
    private const val MAX_ERROR_LENGTH = 1_000

    private val sensitiveNames = setOf(
        "access_token",
        "api_key",
        "apikey",
        "appkey",
        "auth",
        "authorization",
        "code",
        "cookie",
        "email",
        "id_token",
        "identity",
        "jsessionid",
        "jwt",
        "key",
        "mobile",
        "passwd",
        "password",
        "phone",
        "pwd",
        "refresh_token",
        "secret",
        "session",
        "sessionid",
        "sign",
        "signature",
        "st",
        "state",
        "studentid",
        "synjones-auth",
        "ticket",
        "token",
        "uid",
        "user",
        "userid",
        "username",
        "xh",
    )

    private val urlInText = Regex("https?://[^\\s\\\"'<>]+", RegexOption.IGNORE_CASE)
    private val numericPathToken = Regex(".*\\d{4,}.*")
    private val opaquePathToken = Regex("(?i)(?:[0-9a-f]{12,}|[a-z0-9_=-]{24,})")
    private val bearerToken = Regex("(?i)\\bbearer\\s+[^\\s,;]+")
    private val jwtToken = Regex("\\b[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b")
    private val casTicket = Regex("(?i)\\b(?:ST|TGT)-[A-Za-z0-9._-]+")
    private val namedSecret = Regex(
        "(?i)\\b(token|password|passwd|pwd|cookie|authorization|secret|ticket|session|jwt|studentid|username|xh)" +
            "(\\s*[:=]\\s*)([^\\s,;\\t]+)",
    )

    fun redact(url: String): String = redact(url, depth = 0)

    fun isSensitiveParameter(name: String): Boolean {
        val normalized = name.trim().lowercase()
        return normalized in sensitiveNames ||
            normalized.endsWith("_token") ||
            normalized.endsWith("_secret") ||
            normalized.endsWith("_password") ||
            normalized.endsWith("_signature") ||
            normalized.endsWith("_api_key")
    }

    fun sanitizeErrorMessage(message: String?): String {
        if (message.isNullOrBlank()) return "No additional error details"
        return sanitizeDiagnosticText(message).take(MAX_ERROR_LENGTH)
    }

    fun sanitizeDiagnosticText(text: String): String = urlInText.replace(text) { match -> redact(match.value) }
        .replace(bearerToken, "Bearer $REDACTED_VALUE")
        .replace(jwtToken, REDACTED_VALUE)
        .replace(casTicket, REDACTED_VALUE)
        .replace(namedSecret) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}$REDACTED_VALUE"
        }

    private fun redact(url: String, depth: Int): String {
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null) {
            return if ('?' in url) "${url.substringBefore('?')}?$REDACTED_VALUE" else url
        }

        val builder = parsed.newBuilder()
            .username("")
            .password("")
            .encodedPath(redactPath(parsed.encodedPath))
            .query(null)

        for (index in 0 until parsed.querySize) {
            val name = parsed.queryParameterName(index)
            val value = parsed.queryParameterValue(index)
            val safeValue = when {
                isSensitiveParameter(name) -> REDACTED_VALUE
                depth < MAX_NESTED_URL_DEPTH && value?.looksLikeHttpUrl() == true ->
                    redact(value, depth + 1)
                else -> value
            }
            builder.addQueryParameter(name, safeValue)
        }
        return builder.build().toString()
    }

    /** A compact route for event logs. Query values and likely dynamic path identifiers are gone. */
    fun routeForLog(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return REDACTED_VALUE
        return redactPath(parsed.encodedPath)
    }

    private fun redactPath(encodedPath: String): String = encodedPath
        .split('/')
        .joinToString("/") { segment ->
            when {
                segment.isBlank() -> segment
                '%' in segment || numericPathToken.matches(segment) || opaquePathToken.matches(segment) -> REDACTED_VALUE
                else -> segment
            }
        }

    private fun String.looksLikeHttpUrl(): Boolean =
        startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)
}
