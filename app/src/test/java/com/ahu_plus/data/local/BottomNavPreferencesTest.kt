package com.ahu_plus.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomNavPreferencesTest {
    @Test
    fun defaultsUseFirstTwoEnabledServicesInStableOrder() {
        assertEquals(
            listOf(BottomNavService.MARKET, BottomNavService.WELEARN),
            defaultBottomNavServices(
                setOf(BottomNavService.WELEARN, BottomNavService.MARKET),
            ),
        )
    }

    @Test
    fun normalizationPreservesUserOrderAndLimitsSelection() {
        assertEquals(
            listOf(BottomNavService.WELEARN, BottomNavService.MARKET),
            normalizeBottomNavServices(
                selected = listOf(
                    BottomNavService.WELEARN,
                    BottomNavService.WELEARN,
                    "unknown",
                    BottomNavService.MARKET,
                    BottomNavService.CHAOXING,
                ),
                enabled = BottomNavService.all.toSet(),
            ),
        )
    }

    @Test
    fun normalizationRemovesDisabledServicesWithoutAutoFilling() {
        assertEquals(
            listOf(BottomNavService.CHAOXING),
            normalizeBottomNavServices(
                selected = listOf(BottomNavService.MARKET, BottomNavService.CHAOXING),
                enabled = setOf(BottomNavService.CHAOXING, BottomNavService.WELEARN),
            ),
        )
    }
}
