package com.ahu_plus.data.repository

import com.ahu_plus.data.model.MarketTopic
import com.ahu_plus.data.remote.market.MarketApi
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketReadOnlyHotTopicsTest {

    @Test
    fun readOnlyHotTopicsUrl_includesAnhuiCircleSchoolFilter() {
        assertEquals(
            "https://api.zxs-bbs.cn/api/client/topics/top?school_id=10681",
            MarketApi.readOnlyHotTopicsUrl(10681L),
        )
    }

    @Test
    fun parser_preservesRank_deduplicatesAndCapsRows() {
        val body = buildString {
            append("{\"status\":\"success\",\"code\":200,\"data\":[")
            repeat(11) { index ->
                if (index > 0) append(',')
                val id = if (index == 5) 100L else index + 100L
                append("{\"id\":$id,\"title\":\"t$index\",\"content\":\"c$index\"}")
            }
            append("]}")
        }

        val rows = parseReadOnlyHotTopics(body)

        assertEquals(10, rows.size)
        assertEquals(
            listOf(100L, 101L, 102L, 103L, 104L, 106L, 107L, 108L, 109L, 110L),
            rows.map { it.id },
        )
        assertEquals("c0", rows.first().content)
    }

    @Test
    fun parser_acceptsSourceRankWhenCreateTimesAreOutOfOrder() {
        val body = """
            {"status":"success","code":200,"data":[
              {"id":2,"content":"second","createTime":"2026-08-05 12:00:00"},
              {"id":1,"content":"first","createTime":"2026-08-05 13:00:00"}
            ]}
        """.trimIndent()

        val rows = parseReadOnlyHotTopics(body)

        assertEquals(listOf(2L, 1L), rows.map { it.id })
    }
}
