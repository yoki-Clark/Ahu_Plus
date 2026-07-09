package com.ahu_plus.ui.screen.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 收藏网格 hit-test 的核心算术检查。
 *
 * 覆盖旧引擎栽过的坑:①跨列命中(旧版 targetCol 恒等 sourceCol,右列永远够不到)
 * ②越界 clamp(拖出网格 / 拖到空位区)。
 *
 * 几何:slot 100x100,gap 10。列中心 x ≈ 50 / 160 / 270;行中心 y ≈ 50 / 160。
 */
class FavoritesGridIndexTest {

    private val w = 100
    private val h = 100
    private val gap = 10f

    private fun at(x: Float, y: Float, rowCount: Int, size: Int) =
        favIndexAt(x, y, w, h, gap, rowCount, size)

    @Test
    fun `每列独立命中 — 首行三列`() {
        // 满 6 项,2 行。第一行左/中/右 → 0/1/2(旧引擎右列会错算成源列)。
        assertEquals(0, at(50f, 50f, rowCount = 2, size = 6))
        assertEquals(1, at(160f, 50f, rowCount = 2, size = 6))
        assertEquals(2, at(270f, 50f, rowCount = 2, size = 6))
    }

    @Test
    fun `跨行跨列 — 第二行`() {
        assertEquals(3, at(50f, 160f, rowCount = 2, size = 6))
        assertEquals(4, at(160f, 160f, rowCount = 2, size = 6))
        assertEquals(5, at(270f, 160f, rowCount = 2, size = 6))
    }

    @Test
    fun `横向越界 clamp 到首末列`() {
        assertEquals(0, at(-999f, 50f, rowCount = 2, size = 6))   // 拖到最左外
        assertEquals(2, at(9999f, 50f, rowCount = 2, size = 6))   // 拖到最右外
    }

    @Test
    fun `纵向拖到空位区 clamp 到最后真实项`() {
        // 4 项(2 行:满行 + 单项),rowCount=2。拖到很下面 → 最后真实项 index 3。
        assertEquals(3, at(270f, 9999f, rowCount = 2, size = 4))
    }

    @Test
    fun `单行不越界到第二行`() {
        // 3 项 1 行,rowCount=1。往下拖 y 很大仍 clamp 在第 0 行。
        assertEquals(2, at(270f, 9999f, rowCount = 1, size = 3))
    }

    @Test
    fun `未测量时返回 0 兜底`() {
        assertEquals(0, favIndexAt(50f, 50f, slotW = 0, slotH = 0, gapPx = gap, rowCount = 1, size = 6))
        assertEquals(0, at(50f, 50f, rowCount = 1, size = 0))
    }
}
