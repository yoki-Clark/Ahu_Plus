package com.ahu_plus.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketTopicDetailParserTest {

    @Test
    fun `topic detail is parsed from the data envelope`() {
        val topic = parseMarketTopicDetail(
            """{"status":"success","msg":"ok","data":{"id":42,"title":"Title","content":"Body"}}"""
        )

        assertEquals(42L, topic.id)
        assertEquals("Title", topic.title)
        assertEquals("Body", topic.content)
    }
}
