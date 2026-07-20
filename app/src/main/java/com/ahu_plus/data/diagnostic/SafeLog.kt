package com.ahu_plus.data.diagnostic

import android.util.Log as AndroidLog
import com.ahu_plus.data.developer.NetworkDiagnosticUrlRedactor
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DiagnosticLevel { DEBUG, INFO, WARNING, ERROR }

data class DiagnosticEntry(
    val timestampMillis: Long,
    val level: DiagnosticLevel,
    val tag: String,
    val message: String,
)

object DiagnosticBuffer {
    private const val MAX_ENTRIES = 500
    private val lock = Any()
    private val ring = ArrayDeque<DiagnosticEntry>(MAX_ENTRIES)
    private val _entries = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val entries: StateFlow<List<DiagnosticEntry>> = _entries.asStateFlow()

    fun record(level: DiagnosticLevel, tag: String, message: String) {
        val entry = DiagnosticEntry(
            timestampMillis = System.currentTimeMillis(),
            level = level,
            tag = SafeLog.sanitize(tag).take(80),
            message = SafeLog.sanitize(message).take(2_000),
        )
        synchronized(lock) {
            if (ring.size == MAX_ENTRIES) ring.removeFirst()
            ring.addLast(entry)
            _entries.value = ring.toList()
        }
    }

    fun snapshot(): List<DiagnosticEntry> = synchronized(lock) { ring.toList() }

    fun clear() = synchronized(lock) {
        ring.clear()
        _entries.value = emptyList()
    }
}

/** The only production bridge to Android Log. */
object SafeLog {
    private val namedPersonalData = Regex(
        "(?i)\\b(studentid|student_id|xh|phone|mobile|telphone|room|roomname|building|dorm)" +
            "(\\s*[:=]\\s*)([^\\s,;\\t]+)",
    )
    private val sessionId = Regex("(?i)\\b(jsessionid)(\\s*[:=]\\s*)([^\\s,;\\t]+)")
    private val jsonBody = Regex("(?s)(body|response|响应正文)(\\s*[:=]\\s*)[\\[{].*")

    fun sanitize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return NetworkDiagnosticUrlRedactor.sanitizeDiagnosticText(value)
            .replace(sessionId) { "${it.groupValues[1]}${it.groupValues[2]}REDACTED" }
            .replace(namedPersonalData) { "${it.groupValues[1]}${it.groupValues[2]}REDACTED" }
            .replace(jsonBody) { "${it.groupValues[1]}${it.groupValues[2]}REDACTED" }
    }

    fun d(tag: String, message: String?): Int = write(DiagnosticLevel.DEBUG, tag, message)
    fun i(tag: String, message: String?): Int = write(DiagnosticLevel.INFO, tag, message)
    fun w(tag: String, message: String?): Int = write(DiagnosticLevel.WARNING, tag, message)
    fun e(tag: String, message: String?): Int = write(DiagnosticLevel.ERROR, tag, message)

    fun d(tag: String, message: String?, error: Throwable?): Int =
        write(DiagnosticLevel.DEBUG, tag, withError(message, error))
    fun i(tag: String, message: String?, error: Throwable?): Int =
        write(DiagnosticLevel.INFO, tag, withError(message, error))
    fun w(tag: String, message: String?, error: Throwable?): Int =
        write(DiagnosticLevel.WARNING, tag, withError(message, error))
    fun e(tag: String, message: String?, error: Throwable?): Int =
        write(DiagnosticLevel.ERROR, tag, withError(message, error))

    fun w(tag: String, error: Throwable?): Int = write(DiagnosticLevel.WARNING, tag, withError(null, error))

    private fun withError(message: String?, error: Throwable?): String = buildString {
        append(message.orEmpty())
        if (error != null) {
            if (isNotEmpty()) append(" · ")
            append(error.javaClass.simpleName.ifBlank { "Error" })
            error.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
        }
    }

    private fun write(level: DiagnosticLevel, tag: String, message: String?): Int {
        val safeTag = sanitize(tag).take(80)
        val safeMessage = sanitize(message).take(2_000)
        DiagnosticBuffer.record(level, safeTag, safeMessage)
        return when (level) {
            DiagnosticLevel.DEBUG -> AndroidLog.d(safeTag, safeMessage)
            DiagnosticLevel.INFO -> AndroidLog.i(safeTag, safeMessage)
            DiagnosticLevel.WARNING -> AndroidLog.w(safeTag, safeMessage)
            DiagnosticLevel.ERROR -> AndroidLog.e(safeTag, safeMessage)
        }
    }
}
