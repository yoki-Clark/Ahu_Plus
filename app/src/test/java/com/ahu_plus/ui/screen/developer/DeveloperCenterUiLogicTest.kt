package com.ahu_plus.ui.screen.developer

import org.junit.Assert.assertEquals
import org.junit.Test

class DeveloperCenterUiLogicTest {

    @Test
    fun `overview formats install timestamps`() {
        assertEquals(
            "formatted:1234",
            developerOverviewDisplayValue("first_install", "1234") { "formatted:$it" },
        )
        assertEquals(
            "formatted:5678",
            developerOverviewDisplayValue("last_update", "5678") { "formatted:$it" },
        )
    }

    @Test
    fun `overview preserves non timestamp and invalid values`() {
        assertEquals(
            "arm64-v8a",
            developerOverviewDisplayValue("abi", "arm64-v8a") { error("must not format") },
        )
        assertEquals(
            "unknown",
            developerOverviewDisplayValue("first_install", "unknown") { error("must not format") },
        )
        assertEquals(
            "0",
            developerOverviewDisplayValue("last_update", "0") { error("must not format") },
        )
    }

    @Test
    fun `data page distinguishes empty report from empty filter result`() {
        assertEquals(
            DeveloperDataResultState.EMPTY,
            developerDataResultState(totalEntryCount = 0, visibleEntryCount = 0),
        )
        assertEquals(
            DeveloperDataResultState.NO_MATCH,
            developerDataResultState(totalEntryCount = 8, visibleEntryCount = 0),
        )
        assertEquals(
            DeveloperDataResultState.HAS_RESULTS,
            developerDataResultState(totalEntryCount = 8, visibleEntryCount = 3),
        )
    }
}
