package com.ahu_plus.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DataStatusFooterTest {
    @Test
    fun recentDataTimesUseRelativeDayLabels() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 7, 17, 12, 0).atZone(zone).toInstant().toEpochMilli()

        assertEquals("今天 09:30", formatDataTime(at(2026, 7, 17, 9, 30, zone), now))
        assertEquals("昨天 09:30", formatDataTime(at(2026, 7, 16, 9, 30, zone), now))
        assertEquals("前天 09:30", formatDataTime(at(2026, 7, 15, 9, 30, zone), now))
        assertEquals("2026-07-14 09:30", formatDataTime(at(2026, 7, 14, 9, 30, zone), now))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
}
