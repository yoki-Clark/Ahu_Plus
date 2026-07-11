package com.ahu_plus.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DataRefreshPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun missingTimestampIsStale() {
        assertTrue(DataRefreshPolicy.isStale(0L, 1_000L, nowMillis = 10_000L))
    }

    @Test
    fun freshnessUsesSuccessfulUpdateTimestamp() {
        assertFalse(DataRefreshPolicy.isStale(9_500L, 1_000L, nowMillis = 10_000L))
        assertTrue(DataRefreshPolicy.isStale(9_000L, 1_000L, nowMillis = 10_000L))
    }

    @Test
    fun todayIsBasedOnLocalCalendarDate() {
        val beforeMidnight = ZonedDateTime.of(2026, 7, 11, 23, 59, 0, 0, zone)
            .toInstant().toEpochMilli()
        val afterMidnight = ZonedDateTime.of(2026, 7, 12, 0, 1, 0, 0, zone)
            .toInstant().toEpochMilli()

        assertTrue(DataRefreshPolicy.wasUpdatedToday(beforeMidnight, beforeMidnight, zone))
        assertFalse(DataRefreshPolicy.wasUpdatedToday(beforeMidnight, afterMidnight, zone))
    }
}
