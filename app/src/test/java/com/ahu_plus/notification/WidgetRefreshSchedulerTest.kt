package com.ahu_plus.notification

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetRefreshSchedulerTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `plan targets next midnight plus five seconds and course boundary`() {
        val now = LocalDateTime.of(2026, 7, 20, 23, 59, 50).atZone(zone).toInstant().toEpochMilli()
        val boundary = now + 30_000L

        val plan = WidgetRefreshScheduler.buildPlan(now, boundary, zone)

        assertEquals(15_000L, plan.midnightDelayMillis)
        assertEquals(30_000L, plan.courseBoundaryDelayMillis)
    }

    @Test
    fun `plan omits boundary work when no course node exists`() {
        val now = LocalDateTime.of(2026, 7, 20, 10, 0).atZone(zone).toInstant().toEpochMilli()

        assertNull(WidgetRefreshScheduler.buildPlan(now, null, zone).courseBoundaryDelayMillis)
    }
}
