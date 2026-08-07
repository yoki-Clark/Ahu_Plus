package com.ahu_plus.data.local

import com.ahu_plus.data.model.MarketReadOnlyCacheEntry
import com.ahu_plus.data.model.MarketTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 测试 [MarketReadOnlyCache.mergeEntries] 的去重 / 排序 / TTL / 裁剪逻辑。
 * 不依赖 DataStore / Context。
 */
class MarketReadOnlyCacheTest {

    private fun topic(id: Long, createTime: String, commentCount: Int = 0): MarketTopic =
        MarketTopic(id = id, content = "t$id", createTime = createTime, commentCount = commentCount)

    private fun now(year: Int, month: Int, day: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, 12, 0, 0)
        return cal.timeInMillis
    }

    @Test
    fun merge_dedupesById_keepsNewerSnapshot() {
        val existing = listOf(MarketReadOnlyCacheEntry(topic(1, "2026-08-01 10:00:00", 5), "圈子"))
        // 同 id 再次拉到,评论数从 5 变 10
        val fresh = listOf(topic(1, "2026-08-01 10:00:00", 10) to "圈子")
        val merged = MarketReadOnlyCache.mergeEntries(existing, fresh, now(2026, 8, 4))
        assertEquals(1, merged.size)
        assertEquals(10, merged.first().topic.commentCount)
    }

    @Test
    fun merge_sortsByCreateTimeDesc() {
        val fresh = listOf(
            topic(1, "2026-08-02 09:00:00") to "圈子",
            topic(2, "2026-08-04 18:00:00") to "校友圈",
            topic(3, "2026-08-03 12:00:00") to "圈子",
        )
        val merged = MarketReadOnlyCache.mergeEntries(emptyList(), fresh, now(2026, 8, 5))
        assertEquals(listOf(2L, 3L, 1L), merged.map { it.topic.id })
    }

    @Test
    fun merge_dropsEntriesOlderThanTtl() {
        // now = 2026-08-04; 30 天前 = 2026-07-05。7-04 的应被剔除,7-06 的保留
        val fresh = listOf(
            topic(1, "2026-07-04 10:00:00") to "圈子",
            topic(2, "2026-07-06 10:00:00") to "圈子",
        )
        val merged = MarketReadOnlyCache.mergeEntries(emptyList(), fresh, now(2026, 8, 4))
        assertEquals(listOf(2L), merged.map { it.topic.id })
    }

    @Test
    fun merge_capsToMaxEntries_keepingNewest() {
        val fresh = (1..600).map { i ->
            // 越大时间越新
            topic(i.toLong(), "2026-08-%02d 10:00:00".format(i % 28 + 1)) to "圈子"
        }
        val merged = MarketReadOnlyCache.mergeEntries(emptyList(), fresh, now(2026, 9, 1))
        assertEquals(MarketReadOnlyCache.MAX_ENTRIES, merged.size)
    }

    @Test
    fun merge_preservesCachedLabelWhenFreshLabelBlank() {
        val existing = listOf(MarketReadOnlyCacheEntry(topic(1, "2026-08-02 10:00:00"), "校友圈"))
        // fresh 带空标签(理论上不会,但兜底):应保留原标签
        val fresh = listOf(topic(1, "2026-08-02 10:00:00") to "")
        val merged = MarketReadOnlyCache.mergeEntries(existing, fresh, now(2026, 8, 4))
        assertEquals("校友圈", merged.first().label)
    }

    @Test
    fun merge_overwritesLabelWhenFreshProvidesOne() {
        val existing = listOf(MarketReadOnlyCacheEntry(topic(1, "2026-08-02 10:00:00"), "圈子"))
        val fresh = listOf(topic(1, "2026-08-02 10:00:00") to "校友圈")
        val merged = MarketReadOnlyCache.mergeEntries(existing, fresh, now(2026, 8, 4))
        assertEquals("校友圈", merged.first().label)
    }

    @Test
    fun merge_emptyFreshKeepsExisting() {
        val existing = listOf(MarketReadOnlyCacheEntry(topic(1, "2026-08-02 10:00:00"), "圈子"))
        val merged = MarketReadOnlyCache.mergeEntries(existing, emptyList(), now(2026, 8, 4))
        assertEquals(listOf(1L), merged.map { it.topic.id })
    }

    @Test
    fun merge_unparseableTimeTreatedAsNewest_keptAndTop() {
        val fresh = listOf(
            topic(1, "2026-08-02 10:00:00") to "圈子",
            topic(2, "not-a-date") to "圈子",
        )
        val merged = MarketReadOnlyCache.mergeEntries(emptyList(), fresh, now(2026, 8, 4))
        // 解析失败 -> 0L -> 被 TTL 剔除或排最后
        assertEquals(1L, merged.first().topic.id)
        assertEquals(1, merged.size)
    }

    @Test
    fun merge_emptyInput_returnsEmpty() {
        val merged = MarketReadOnlyCache.mergeEntries(emptyList(), emptyList(), now(2026, 8, 4))
        assertTrue(merged.isEmpty())
    }

    @Test
    fun parseCreateTimeMs_blankOrInvalid_returnsMax() {
        // 空白时间返回 0L(最旧),会被 TTL 剔除
        val fresh = listOf(topic(1, "") to "圈子")
        val merged = MarketReadOnlyCache.mergeEntries(emptyList(), fresh, now(2025, 1, 1))
        assertEquals(0, merged.size)
    }

    @Test
    fun ttl_boundary_30DaysOldIsKept() {
        // now = 2026-08-04 12:00; cutoff = 2026-07-05 12:00。07-05 12:00 恰好 == cutoff,应保留(>=)
        val fresh = listOf(topic(1, "2026-07-05 12:00:00") to "圈子")
        val merged = MarketReadOnlyCache.mergeEntries(emptyList(), fresh, now(2026, 8, 4))
        // 边界用 >=,等于 cutoff 的保留
        assertEquals(1, merged.size)
        // 07-05 11:00 在 cutoff 之前,剔除
        val fresh2 = listOf(topic(2, "2026-07-05 11:00:00") to "圈子")
        val merged2 = MarketReadOnlyCache.mergeEntries(emptyList(), fresh2, now(2026, 8, 4))
        assertNull(merged2.firstOrNull { it.topic.id == 2L })
    }
}
