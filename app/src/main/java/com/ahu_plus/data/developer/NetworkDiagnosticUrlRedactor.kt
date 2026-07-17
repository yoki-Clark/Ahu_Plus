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
        return urlInText.replace(message) { match -> redact(match.value) }
            .take(MAX_ERROR_LENGTH)
    }

    private fun redact(url: String, depth: Int): String {
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null) {
            return if ('?' in url) "${url.substringBefore('?')}?$REDACTED_VALUE" else url
        }

        val builder = parsed.newBuilder()
            .username("")
            .password("")
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

    private fun String.looksLikeHttpUrl(): Boolean =
        startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)
}
