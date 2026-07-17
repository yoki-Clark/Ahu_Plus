package com.ahu_plus.data.developer

import java.time.Clock
import java.time.format.DateTimeFormatter

data class DeveloperUnlockResult(
    val remainingTapCount: Int,
    val justUnlocked: Boolean,
    val isUnlocked: Boolean,
)

/** Tracks the seven consecutive version-label taps for the current About screen visit. */
class DeveloperTapUnlocker(
    private val timeSourceMillis: () -> Long = System::currentTimeMillis,
) {
    private var consecutiveTapCount = 0
    private var lastTapAtMillis: Long? = null

    var isUnlocked: Boolean = false
        private set

    fun onTap(): DeveloperUnlockResult {
        if (isUnlocked) return result(justUnlocked = false)

        val now = timeSourceMillis()
        val previous = lastTapAtMillis
        val elapsed = previous?.let { now - it }
        val continuesSequence = previous != null &&
            now >= previous &&
            elapsed != null &&
            elapsed in 0..MAX_TAP_INTERVAL_MILLIS

        consecutiveTapCount = if (continuesSequence) consecutiveTapCount + 1 else 1
        lastTapAtMillis = now

        val justUnlocked = consecutiveTapCount >= REQUIRED_TAP_COUNT
        if (justUnlocked) {
            consecutiveTapCount = REQUIRED_TAP_COUNT
            isUnlocked = true
        }

        return result(justUnlocked)
    }

    fun reset() {
        consecutiveTapCount = 0
        lastTapAtMillis = null
        isUnlocked = false
    }

    private fun result(justUnlocked: Boolean) = DeveloperUnlockResult(
        remainingTapCount = (REQUIRED_TAP_COUNT - consecutiveTapCount).coerceAtLeast(0),
        justUnlocked = justUnlocked,
        isUnlocked = isUnlocked,
    )

    companion object {
        const val REQUIRED_TAP_COUNT = 7
        const val MAX_TAP_INTERVAL_MILLIS = 2_000L
    }
}

/** Validates a six-digit local-time password against the current time, with a five-second window. */
class DeveloperTimePasswordValidator(
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun isValid(password: String): Boolean {
        if (password.length != PASSWORD_LENGTH || password.any { it !in '0'..'9' }) {
            return false
        }

        val now = clock.instant()
        val zone = clock.zone
        return (-TOLERANCE_SECONDS..TOLERANCE_SECONDS).any { offsetSeconds ->
            now.plusSeconds(offsetSeconds)
                .atZone(zone)
                .format(PASSWORD_FORMATTER) == password
        }
    }

    companion object {
        const val PASSWORD_LENGTH = 6
        const val TOLERANCE_SECONDS = 5L

        private val PASSWORD_FORMATTER = DateTimeFormatter.ofPattern("HHmmss")
    }
}
