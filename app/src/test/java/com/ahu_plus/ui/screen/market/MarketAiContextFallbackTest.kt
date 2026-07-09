package com.ahu_plus.ui.screen.market

import com.ahu_plus.data.model.MarketComment
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketAiContextFallbackTest {
    @Test
    fun `uses loaded comments when full comment request is rate limited`() {
        val loaded = listOf(MarketComment(id = 1, content = "页面已有评论"))

        val selected = selectAiContextComments(
            fullResult = Result.failure(Exception("集市加载失败 HTTP 429")),
            loadedComments = loaded
        )

        assertEquals(loaded, selected)
    }

    @Test
    fun `allows generation with no comments when comment request fails`() {
        val selected = selectAiContextComments(
            fullResult = Result.failure(Exception("network error")),
            loadedComments = emptyList()
        )

        assertEquals(emptyList<MarketComment>(), selected)
    }
}
