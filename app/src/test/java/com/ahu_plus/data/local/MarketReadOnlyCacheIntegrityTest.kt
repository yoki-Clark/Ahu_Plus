package com.ahu_plus.data.local

import com.ahu_plus.data.model.MarketTopic
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MarketReadOnlyCacheIntegrityTest {
    private fun topic(id: Long, createTime: String): MarketTopic =
        MarketTopic(
            id = id,
            createTime = createTime,
            title = "Test $id",
            content = "",
            status = "normal",
            imgs = emptyList(),
            node = "",
            isAnon = 0,
            viewCount = 0,
            isTop = 0,
            userInfo = null,
            schoolSubAddress = null,
            likeCount = 0,
            commentCount = 0,
            source = "",
            capturedAt = "",
            topComments = emptyList(),
        )

    private fun now(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.set(year, month - 1, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun merge_futureTimestampIsFiltered() {
        // 验证时间戳在未来 1 天以上的帖子会被过滤
        val nowMs = now(2026, 8, 4)
        val fresh = listOf(
            topic(1, "2026-08-03 10:00:00") to "圈子",  // 昨天,正常
            topic(2, "2026-08-05 10:00:00") to "圈子",  // 明天,在允许范围内
            topic(3, "2026-08-06 10:00:00") to "圈子",  // 后天,超过 1 天,应被过滤
        )
        val merged = MarketReadOnlyCache.mergeEntries(emptyList(), fresh, nowMs)
        val ids = merged.map { it.topic.id }
        assertTrue(1L in ids)
        assertTrue(2L in ids)
        assertFalse(3L in ids)
    }

    @Test
    fun merge_invalidIdIsFiltered() {
        // 验证 id <= 0 的帖子会被过滤
        val fresh = listOf(
            topic(0, "2026-08-02 10:00:00") to "圈子",
            topic(-1, "2026-08-02 11:00:00") to "圈子",
            topic(1, "2026-08-02 12:00:00") to "圈子",
        )
        val merged = MarketReadOnlyCache.mergeEntries(emptyList(), fresh, now(2026, 8, 4))
        assertEquals(1, merged.size)
        assertEquals(1L, merged.first().topic.id)
    }
}

