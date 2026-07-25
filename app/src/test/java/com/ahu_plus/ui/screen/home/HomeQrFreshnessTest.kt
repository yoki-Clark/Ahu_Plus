package com.ahu_plus.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeQrFreshnessTest {
    @Test
    fun `opening a fresh code does not request another code`() {
        assertEquals(
            QrOpenRefreshDecision.NONE,
            qrOpenRefreshDecision(
                fetchedAtMillis = 10_000L,
                nowMillis = 40_000L,
            ),
        )
    }

    @Test
    fun `opening a near expiry code refreshes in background`() {
        assertEquals(
            QrOpenRefreshDecision.BACKGROUND,
            qrOpenRefreshDecision(
                fetchedAtMillis = 10_000L,
                nowMillis = 56_000L,
            ),
        )
    }

    @Test
    fun `opening an expired or missing code requires refresh`() {
        assertEquals(
            QrOpenRefreshDecision.REQUIRED,
            qrOpenRefreshDecision(
                fetchedAtMillis = null,
                nowMillis = 70_001L,
            ),
        )
        assertEquals(
            QrOpenRefreshDecision.REQUIRED,
            qrOpenRefreshDecision(
                fetchedAtMillis = 10_000L,
                nowMillis = 70_001L,
            ),
        )
    }

    @Test
    fun `automatic refresh backs off after consecutive failures`() {
        assertEquals(45, qrAutoRefreshDelaySeconds(consecutiveFailures = 0))
        assertEquals(60, qrAutoRefreshDelaySeconds(consecutiveFailures = 1))
        assertEquals(120, qrAutoRefreshDelaySeconds(consecutiveFailures = 2))
        assertEquals(120, qrAutoRefreshDelaySeconds(consecutiveFailures = 3))
        assertEquals(300, qrAutoRefreshDelaySeconds(consecutiveFailures = 4))
        assertEquals(300, qrAutoRefreshDelaySeconds(consecutiveFailures = 20))
    }

    @Test
    fun `new code waits until normal refresh age`() {
        assertEquals(
            35,
            qrNextRefreshDelaySeconds(
                consecutiveFailures = 0,
                fetchedAtMillis = 10_000L,
                nowMillis = 20_000L,
            ),
        )
        assertEquals(
            0,
            qrNextRefreshDelaySeconds(
                consecutiveFailures = 0,
                fetchedAtMillis = null,
                nowMillis = 20_000L,
            ),
        )
    }

    @Test
    fun `code remains fresh at the threshold`() {
        val freshness = resolveQrFreshness(
            fetchedAtMillis = 1_000L,
            nowMillis = 61_000L,
            staleThresholdMillis = 60_000L,
        )

        assertEquals(60, freshness.ageSeconds)
        assertFalse(freshness.isStale)
        assertNull(freshness.errorMessage)
    }

    @Test
    fun `expired code exposes readable refresh message`() {
        val freshness = resolveQrFreshness(
            fetchedAtMillis = 1_000L,
            nowMillis = 61_001L,
            staleThresholdMillis = 60_000L,
        )

        assertEquals(60, freshness.ageSeconds)
        assertTrue(freshness.isStale)
        assertEquals("支付码已过期，请刷新", freshness.errorMessage)
    }
}
