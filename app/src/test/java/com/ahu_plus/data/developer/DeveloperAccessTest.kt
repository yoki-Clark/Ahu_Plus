package com.ahu_plus.data.developer

import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperTapUnlockerTest {
    @Test
    fun `seven consecutive taps unlock and report remaining count`() {
        var now = 1_000L
        val unlocker = DeveloperTapUnlocker { now }

        repeat(6) { index ->
            val result = unlocker.onTap()
            assertEquals(6 - index, result.remainingTapCount)
            assertFalse(result.justUnlocked)
            assertFalse(result.isUnlocked)
            now += 500L
        }

        val unlocked = unlocker.onTap()
        assertEquals(0, unlocked.remainingTapCount)
        assertTrue(unlocked.justUnlocked)
        assertTrue(unlocked.isUnlocked)
    }

    @Test
    fun `tap exactly two seconds later continues sequence`() {
        var now = 10_000L
        val unlocker = DeveloperTapUnlocker { now }

        assertEquals(6, unlocker.onTap().remainingTapCount)
        now += DeveloperTapUnlocker.MAX_TAP_INTERVAL_MILLIS

        assertEquals(5, unlocker.onTap().remainingTapCount)
    }

    @Test
    fun `tap after two seconds starts a new sequence`() {
        var now = 10_000L
        val unlocker = DeveloperTapUnlocker { now }

        unlocker.onTap()
        now += DeveloperTapUnlocker.MAX_TAP_INTERVAL_MILLIS + 1L

        val restarted = unlocker.onTap()
        assertEquals(6, restarted.remainingTapCount)
        assertFalse(restarted.justUnlocked)
    }

    @Test
    fun `backwards time starts a new sequence`() {
        var now = 10_000L
        val unlocker = DeveloperTapUnlocker { now }

        unlocker.onTap()
        now -= 1L

        assertEquals(6, unlocker.onTap().remainingTapCount)
    }

    @Test
    fun `unlock event is emitted once and reset clears all progress`() {
        var now = 0L
        val unlocker = DeveloperTapUnlocker { now }

        repeat(DeveloperTapUnlocker.REQUIRED_TAP_COUNT) {
            unlocker.onTap()
            now += 100L
        }

        val subsequentTap = unlocker.onTap()
        assertTrue(subsequentTap.isUnlocked)
        assertFalse(subsequentTap.justUnlocked)
        assertEquals(0, subsequentTap.remainingTapCount)

        unlocker.reset()

        assertFalse(unlocker.isUnlocked)
        assertEquals(6, unlocker.onTap().remainingTapCount)
    }
}

class DeveloperTimePasswordValidatorTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun `accepts current time and inclusive five second boundaries`() {
        val validator = validatorAt(2026, 7, 17, 15, 7, 41)

        assertTrue(validator.isValid("150741"))
        assertTrue(validator.isValid("150736"))
        assertTrue(validator.isValid("150746"))
        assertFalse(validator.isValid("150735"))
        assertFalse(validator.isValid("150747"))
    }

    @Test
    fun `window crosses minute boundary`() {
        val validator = validatorAt(2026, 7, 17, 15, 0, 2)

        assertTrue(validator.isValid("145957"))
        assertTrue(validator.isValid("150007"))
        assertFalse(validator.isValid("145956"))
        assertFalse(validator.isValid("150008"))
    }

    @Test
    fun `window crosses both sides of midnight`() {
        val afterMidnight = validatorAt(2026, 7, 18, 0, 0, 2)
        val beforeMidnight = validatorAt(2026, 7, 17, 23, 59, 58)

        assertTrue(afterMidnight.isValid("235957"))
        assertTrue(afterMidnight.isValid("000007"))
        assertTrue(beforeMidnight.isValid("235953"))
        assertTrue(beforeMidnight.isValid("000003"))
    }

    @Test
    fun `requires exactly six ascii digits`() {
        val validator = validatorAt(2026, 7, 17, 15, 7, 41)

        listOf(
            "15074",
            "1507410",
            "15:07:41",
            "\uFF11\uFF15\uFF10\uFF17\uFF14\uFF11",
            " 150741",
            "15074a",
        ).forEach { invalid ->
            assertFalse("Expected '$invalid' to be rejected", validator.isValid(invalid))
        }
    }

    @Test
    fun `uses the clock local zone`() {
        val localDateTime = LocalDateTime.of(2026, 7, 17, 15, 7, 41)
        val instant = localDateTime.atZone(shanghai).toInstant()
        val shanghaiValidator = DeveloperTimePasswordValidator(Clock.fixed(instant, shanghai))
        val utcValidator = DeveloperTimePasswordValidator(Clock.fixed(instant, ZoneId.of("UTC")))

        assertTrue(shanghaiValidator.isValid("150741"))
        assertTrue(utcValidator.isValid("070741"))
        assertFalse(utcValidator.isValid("150741"))
    }

    private fun validatorAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): DeveloperTimePasswordValidator {
        val instant = LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(shanghai)
            .toInstant()
        return DeveloperTimePasswordValidator(Clock.fixed(instant, shanghai))
    }
}
