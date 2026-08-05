package com.ahu_plus.ui.screen.market

import com.ahu_plus.data.model.MarketTopic
import com.ahu_plus.data.model.MarketIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketHotUiLogicTest {

    @Test
    fun readonlyHotDisplayUsesReadonlyFeedAndLabels() {
        val state = MarketUiState(
            readOnlyHotTopics = listOf(MarketTopic(id = 7L)),
            readOnlyHotLoading = true,
            readOnlyHotError = "readonly-error",
            hotTopics = listOf(MarketTopic(id = 8L)),
        )

        val display = marketHotDisplay(state)

        assertEquals("安大热榜", display.title)
        assertEquals(listOf(7L), display.topics.map { it.id })
        assertTrue(display.loading)
        assertEquals("readonly-error", display.error)
        assertTrue(display.readOnly)
    }

    @Test
    fun authenticatedHotDisplayUsesAuthenticatedFeed() {
        val state = MarketUiState(
            hasSavedIdentity = true,
            identities = listOf(MarketIdentity(id = "test", token = "token")),
            hotTopics = listOf(MarketTopic(id = 8L)),
            hotLoading = true,
            hotError = "hot-error",
        )

        val display = marketHotDisplay(state)

        assertEquals("集市热榜", display.title)
        assertEquals(listOf(8L), display.topics.map { it.id })
        assertTrue(display.loading)
        assertEquals("hot-error", display.error)
        assertFalse(display.readOnly)
    }
}
