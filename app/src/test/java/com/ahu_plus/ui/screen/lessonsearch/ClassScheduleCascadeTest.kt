package com.ahu_plus.ui.screen.lessonsearch

import com.ahu_plus.data.model.jw.LessonAdminClass
import com.ahu_plus.data.model.jw.LessonMajorNode
import com.ahu_plus.data.model.jw.LessonSearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 班级课表「学院→专业→行政班」级联派生逻辑单测（纯 UiState 计算属性，无 IO / 无 Android / 无 VM）。
 *
 * 覆盖 2026-07-23 重构修复的两处用户可见逻辑：
 * 1. [LessonSearchUiState.majorOptions]：预探 0 班的僵尸专业隐藏、真实专业标注「· N个班」、
 *    未探到计数的专业原样保留（避免探测失败误杀）。这是「选专业却查不到行政班」bug 的根因修复。
 * 2. [LessonSearchUiState.adminClassOptions]：年级为客户端过滤（服务端 grades= 不生效），
 *    从 [LessonSearchUiState.rawAdminClasses] 按对象 grade 字段收窄。
 *
 * 样本为脱敏虚构数据（专业/行政班名为占位），仅验证过滤与标注。
 */
class ClassScheduleCascadeTest {

    private val base = LessonSearchFilter(semesterId = 112)

    // ── majorOptions：僵尸专业过滤 + 班数标注 ──────────────────

    @Test
    fun `major with zero probed classes is hidden`() {
        // id=3 探到 0 班（僵尸/旧培养方案）→ 隐藏；id=194 探到 12 班 → 保留并标注。
        val state = LessonSearchUiState(
            scopedMajorNodes = listOf(
                LessonMajorNode(id = 3L, nameZh = "统计学"),
                LessonMajorNode(id = 194L, nameZh = "统计学"),
            ),
            majorClassCounts = mapOf(3L to 0, 194L to 12),
        )
        val opts = state.majorOptions
        assertEquals(1, opts.size)
        assertEquals(194L, opts.first().id)
        assertEquals("统计学 · 12个班", opts.first().nameZh)
    }

    @Test
    fun `major without probed count is kept unlabeled`() {
        // 未探到计数（探测失败/进行中）→ 不隐藏也不标注，避免误杀真实专业。
        val state = LessonSearchUiState(
            scopedMajorNodes = listOf(LessonMajorNode(id = 42L, nameZh = "应用统计学")),
            majorClassCounts = emptyMap(),
        )
        val opts = state.majorOptions
        assertEquals(1, opts.size)
        assertEquals(42L, opts.first().id)
        assertEquals("应用统计学", opts.first().nameZh) // 无「· N个班」后缀
    }

    @Test
    fun `major node code prefix is stripped before labeling`() {
        val state = LessonSearchUiState(
            scopedMajorNodes = listOf(LessonMajorNode(id = 7L, nameZh = "071201：统计学")),
            majorClassCounts = mapOf(7L to 3),
        )
        assertEquals("统计学 · 3个班", state.majorOptions.first().nameZh)
    }

    @Test
    fun `major with null id is dropped`() {
        val state = LessonSearchUiState(
            scopedMajorNodes = listOf(LessonMajorNode(id = null, nameZh = "坏专业")),
            majorClassCounts = emptyMap(),
        )
        assertTrue(state.majorOptions.isEmpty())
    }

    // ── adminClassOptions：年级客户端过滤 ──────────────────────

    private val rawClasses = listOf(
        LessonAdminClass(1L, "2024级统计学1班", "TJ2401", "2024"),
        LessonAdminClass(2L, "2024级统计学2班", "TJ2402", "2024"),
        LessonAdminClass(3L, "2023级统计学1班", "TJ2301", "2023"),
    )

    @Test
    fun `no grade selected shows all admin classes`() {
        val state = LessonSearchUiState(
            appliedFilter = base.copy(majorIds = listOf(194L)),
            rawAdminClasses = rawClasses,
        )
        assertEquals(3, state.adminClassOptions.size)
    }

    @Test
    fun `grade filter narrows admin classes client-side`() {
        val state = LessonSearchUiState(
            appliedFilter = base.copy(majorIds = listOf(194L), grades = listOf("2024")),
            rawAdminClasses = rawClasses,
        )
        val opts = state.adminClassOptions
        assertEquals(2, opts.size)
        assertTrue(opts.all { it.nameZh.startsWith("2024级") })
    }

    @Test
    fun `grade with no matching class yields empty options but keeps raw`() {
        val state = LessonSearchUiState(
            appliedFilter = base.copy(majorIds = listOf(194L), grades = listOf("2022")),
            rawAdminClasses = rawClasses,
        )
        assertTrue(state.adminClassOptions.isEmpty())
        assertFalse(state.rawAdminClasses.isEmpty()) // 原始列表保留，切回年级零网络重算
    }

    // ── 选中名解析（含标注） ───────────────────────────────────

    @Test
    fun `selected major name reflects labeled option`() {
        val state = LessonSearchUiState(
            appliedFilter = base.copy(majorDeptIds = listOf(2L), majorIds = listOf(194L)),
            scopedMajorNodes = listOf(LessonMajorNode(id = 194L, nameZh = "统计学")),
            majorClassCounts = mapOf(194L to 12),
        )
        assertEquals(194L, state.selectedMajorId)
        assertEquals("统计学 · 12个班", state.selectedMajorName)
    }

    @Test
    fun `selected admin class name resolves from filtered options`() {
        val state = LessonSearchUiState(
            appliedFilter = base.copy(majorIds = listOf(194L), grades = listOf("2024"), adminClassId = 2L),
            rawAdminClasses = rawClasses,
        )
        assertEquals("2024级统计学2班", state.selectedAdminClassName)
    }

    @Test
    fun `selected admin class name is null when filtered out by grade`() {
        // 选中的 2023 班在年级过滤为 2024 时不在候选内 → 名解析为空（不崩）。
        val state = LessonSearchUiState(
            appliedFilter = base.copy(majorIds = listOf(194L), grades = listOf("2024"), adminClassId = 3L),
            rawAdminClasses = rawClasses,
        )
        assertNull(state.selectedAdminClassName)
    }
}
