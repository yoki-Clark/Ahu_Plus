package com.yourname.ahu_plus.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * AhuUnitTimes 工具方法单元测试。
 *
 * 覆盖:
 *  - collapseToSegments (折叠连续区间)
 *  - formatSegmentedRange (单段/多段/单节/空)
 *  - getCurrentUnit (节次边界: 07:59, 08:00, 08:45, 08:46, 12:15, 12:16, 21:25, 21:26)
 *  - formatUnitRange / formatUnitTime (向后兼容)
 */
class AhuUnitTimesTest {

    // ── collapseToSegments ───────────────────────────────────────

    @Test
    fun `collapseToSegments - 多段带单节`() {
        val segs = AhuUnitTimes.collapseToSegments(listOf(5, 6, 8, 9, 11))
        assertEquals(3, segs.size)
        assertEquals(5..6, segs[0])
        assertEquals(8..9, segs[1])
        assertEquals(11..11, segs[2])
    }

    @Test
    fun `collapseToSegments - 单段连续`() {
        val segs = AhuUnitTimes.collapseToSegments(listOf(5, 6, 7, 8))
        assertEquals(listOf(5..8), segs)
    }

    @Test
    fun `collapseToSegments - 单节`() {
        val segs = AhuUnitTimes.collapseToSegments(listOf(13))
        assertEquals(listOf(13..13), segs)
    }

    @Test
    fun `collapseToSegments - 空列表`() {
        assertTrue(AhuUnitTimes.collapseToSegments(emptyList()).isEmpty())
    }

    @Test
    fun `collapseToSegments - 自动排序`() {
        val segs = AhuUnitTimes.collapseToSegments(listOf(11, 5, 6, 8, 9))
        assertEquals(3, segs.size)
        assertEquals(5..6, segs[0])
        assertEquals(8..9, segs[1])
        assertEquals(11..11, segs[2])
    }

    @Test
    fun `collapseToSegments - 全部 13 节`() {
        val segs = AhuUnitTimes.collapseToSegments((1..13).toList())
        assertEquals(listOf(1..13), segs)
    }

    @Test
    fun `collapseToSegments - 相邻但不连续的两段`() {
        val segs = AhuUnitTimes.collapseToSegments(listOf(1, 2, 4, 5))
        assertEquals(listOf(1..2, 4..5), segs)
    }

    // ── formatSegmentedRange ─────────────────────────────────────

    @Test
    fun `formatSegmentedRange - 单段含时间`() {
        // 第 5-8 节 (11:30-16:35) -- 取首段起点 + 末段终点
        val text = AhuUnitTimes.formatSegmentedRange(listOf(5, 6, 7, 8))
        assertEquals("第 5-8 节 (11:30-16:35)", text)
    }

    @Test
    fun `formatSegmentedRange - 多段不带时间范围`() {
        val text = AhuUnitTimes.formatSegmentedRange(listOf(5, 6, 8, 9, 11))
        assertEquals("第 5-6 节，第 8-9 节，第 11 节", text)
    }

    @Test
    fun `formatSegmentedRange - 单节`() {
        val text = AhuUnitTimes.formatSegmentedRange(listOf(13))
        assertEquals("第 13 节 (20:40-21:25)", text)
    }

    @Test
    fun `formatSegmentedRange - 空列表`() {
        assertEquals("", AhuUnitTimes.formatSegmentedRange(emptyList()))
    }

    // ── getCurrentUnit 边界 ──────────────────────────────────────

    @Test
    fun `getCurrentUnit - 07_59 视为即将到来`() {
        // 在第一节开始前,应返回第一节
        assertEquals(1, AhuUnitTimes.getCurrentUnit(LocalTime.of(7, 59)))
    }

    @Test
    fun `getCurrentUnit - 08_00 第 1 节起点`() {
        assertEquals(1, AhuUnitTimes.getCurrentUnit(LocalTime.of(8, 0)))
    }

    @Test
    fun `getCurrentUnit - 08_45 仍在第 1 节`() {
        assertEquals(1, AhuUnitTimes.getCurrentUnit(LocalTime.of(8, 45)))
    }

    @Test
    fun `getCurrentUnit - 08_46 下一节是第 2 节`() {
        // 8:46 处于第 1-2 节之间的 5 分钟空档,应返回下一节 2
        assertEquals(2, AhuUnitTimes.getCurrentUnit(LocalTime.of(8, 46)))
    }

    @Test
    fun `getCurrentUnit - 12_15 仍在第 5 节`() {
        assertEquals(5, AhuUnitTimes.getCurrentUnit(LocalTime.of(12, 15)))
    }

    @Test
    fun `getCurrentUnit - 12_16 应返回下午第 6 节`() {
        // 午休时段返回下一节 6
        assertEquals(6, AhuUnitTimes.getCurrentUnit(LocalTime.of(12, 16)))
    }

    @Test
    fun `getCurrentUnit - 21_25 仍在第 13 节`() {
        assertEquals(13, AhuUnitTimes.getCurrentUnit(LocalTime.of(21, 25)))
    }

    @Test
    fun `getCurrentUnit - 21_26 已结束返回 null`() {
        assertNull(AhuUnitTimes.getCurrentUnit(LocalTime.of(21, 26)))
    }

    @Test
    fun `getCurrentUnit - 23_59 早已结束`() {
        assertNull(AhuUnitTimes.getCurrentUnit(LocalTime.of(23, 59)))
    }

    @Test
    fun `getCurrentUnit - 09_30 仍在第 2 节`() {
        // 09:30 处于第 2 节 (08:50-09:35) 中段
        assertEquals(2, AhuUnitTimes.getCurrentUnit(LocalTime.of(9, 30)))
    }

    @Test
    fun `getCurrentUnit - 09_50 第 3 节起点`() {
        assertEquals(3, AhuUnitTimes.getCurrentUnit(LocalTime.of(9, 50)))
    }

    // ── formatUnitRange / formatUnitTime 向后兼容 ────────────────

    @Test
    fun `formatUnitRange - 单段向后兼容`() {
        assertEquals(
            "第 5-8 节 (11:30-16:35)",
            AhuUnitTimes.formatUnitRange(listOf(5, 6, 7, 8))
        )
    }

    @Test
    fun `formatUnitTime - 单节`() {
        assertEquals("08:00-08:45", AhuUnitTimes.formatUnitTime(1))
    }

    // ── getRemainingUnits ────────────────────────────────────────

    @Test
    fun `getRemainingUnits - 从 5 开始返回 5-13`() {
        assertEquals(
            listOf(5, 6, 7, 8, 9, 10, 11, 12, 13),
            AhuUnitTimes.getRemainingUnits(5)
        )
    }

    @Test
    fun `getRemainingUnits - 超过最大节次返回空`() {
        assertTrue(AhuUnitTimes.getRemainingUnits(14).isEmpty())
    }

    // ── totalUnits ───────────────────────────────────────────────

    @Test
    fun `totalUnits 是 13`() {
        assertEquals(13, AhuUnitTimes.totalUnits())
    }
}