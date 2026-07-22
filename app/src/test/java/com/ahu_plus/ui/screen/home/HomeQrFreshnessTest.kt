package com.ahu_plus.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeQrFreshnessTest {
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
