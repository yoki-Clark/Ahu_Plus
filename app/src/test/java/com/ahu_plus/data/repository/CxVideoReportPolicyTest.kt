package com.ahu_plus.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CxVideoReportPolicyTest {

    @Test
    fun `video speed supports previous one to two times range`() {
        assertEquals(1.0f, CxVideoReportPolicy.effectiveSpeed(0.5f))
        assertEquals(1.5f, CxVideoReportPolicy.effectiveSpeed(1.5f))
        assertEquals(2.0f, CxVideoReportPolicy.effectiveSpeed(3.0f))
    }

    @Test
    fun `invalid duration never schedules a report`() {
        assertFalse(
            CxVideoReportPolicy.shouldReport(
                currentSec = 0,
                lastReportedSec = 0,
                durationSec = 0,
                finalReportSent = false,
                intervalSec = 60,
            ),
        )
    }

    @Test
    fun `uses fixed server interval instead of random cadence`() {
        assertFalse(
            CxVideoReportPolicy.shouldReport(
                currentSec = 59,
                lastReportedSec = 0,
                durationSec = 120,
                finalReportSent = false,
                intervalSec = 60,
            ),
        )
        assertTrue(
            CxVideoReportPolicy.shouldReport(
                currentSec = 60,
                lastReportedSec = 0,
                durationSec = 120,
                finalReportSent = false,
                intervalSec = 60,
            ),
        )
    }

    @Test
    fun `terminal marker is emitted at most once`() {
        assertTrue(
            CxVideoReportPolicy.shouldReport(
                currentSec = 120,
                lastReportedSec = 60,
                durationSec = 120,
                finalReportSent = false,
                intervalSec = 60,
            ),
        )
        assertFalse(
            CxVideoReportPolicy.shouldReport(
                currentSec = 120,
                lastReportedSec = 120,
                durationSec = 120,
                finalReportSent = true,
                intervalSec = 60,
            ),
        )
    }
}
