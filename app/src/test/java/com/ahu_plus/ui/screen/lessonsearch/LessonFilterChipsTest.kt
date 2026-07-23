package com.ahu_plus.ui.screen.lessonsearch

import com.ahu_plus.data.model.jw.LessonFilterOption
import com.ahu_plus.data.model.jw.LessonSearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 生效筛选 chip 构造 + 维度清除的纯逻辑单测（无 IO / 无 Android / 无 VM 实例）。
 *
 * 覆盖新双模式重构的两处用户可见逻辑：
 * 1. [buildActiveFilterChips]：id→名解析、多值/降级文案、维度映射、周次/节次/学分格式化。
 * 2. [clearFilterDimension]：与 chip 对称——移除某维度后该维度不再产出 chip；CAMPUS 联动清教学楼。
 *
 * 样本为脱敏虚构数据（学院名为占位），仅验证映射与格式化。
 */
class LessonFilterChipsTest {

    private val base = LessonSearchFilter(semesterId = 112)

    private val deptOptions = listOf(
        LessonFilterOption(101L, "计算机科学与技术学院"),
        LessonFilterOption(102L, "数学科学学院"),
        LessonFilterOption(103L, "物理与材料科学学院"),
    )
    private val buildingOptions = listOf(
        LessonFilterOption(9001L, "博学北楼"),
        LessonFilterOption(9002L, "博学南楼"),
    )

    private fun chips(filter: LessonSearchFilter) =
        buildActiveFilterChips(filter, deptOptions, buildingOptions)

    // ── 空态 ──────────────────────────────────────────────────

    @Test
    fun `no filter yields no chips`() {
        assertTrue(chips(base).isEmpty())
        assertEquals(0, base.activeCount)
    }

    // ── 单维度 chip 文案 + 维度映射 ────────────────────────────

    @Test
    fun `single department resolves to its name`() {
        val c = chips(base.copy(departmentIds = listOf(101L)))
        assertEquals(1, c.size)
        assertEquals(LessonFilterDimension.DEPARTMENTS, c[0].dimension)
        assertEquals("计算机科学与技术学院", c[0].label)
    }

    @Test
    fun `multiple departments summarise with count`() {
        val c = chips(base.copy(departmentIds = listOf(101L, 102L)))
        assertEquals(1, c.size)
        assertEquals("计算机科学与技术学院 等 2 个学院", c[0].label)
    }

    @Test
    fun `unresolved department ids fall back to count label`() {
        val c = chips(base.copy(departmentIds = listOf(9999L)))
        assertEquals("学院 1 项", c[0].label)
    }

    @Test
    fun `course type campus compulsory exam lang resolve from inline options`() {
        assertEquals("理论课", chips(base.copy(courseTypeId = 1L)).single().label)
        assertEquals("磬苑校区", chips(base.copy(campusId = 1L)).single().label)
        assertEquals("必修", chips(base.copy(compulsory = "COMPULSORY")).single().label)
        assertEquals("考试", chips(base.copy(examModeId = 1L)).single().label)
        assertEquals("中文", chips(base.copy(teachLangId = 2L)).single().label)
    }

    @Test
    fun `weekdays sorted and joined with slash`() {
        val c = chips(base.copy(weekdays = listOf(3, 1)))
        assertEquals(LessonFilterDimension.WEEKDAYS, c.single().dimension)
        assertEquals("周一/三", c.single().label)
    }

    @Test
    fun `course units sorted and joined with comma`() {
        assertEquals("第 1,2,3 节", chips(base.copy(courseUnitIndexes = listOf(3, 1, 2))).single().label)
    }

    @Test
    fun `building resolves from building options`() {
        assertEquals("博学北楼", chips(base.copy(buildingId = 9001L)).single().label)
    }

    @Test
    fun `room keyword prefixed`() {
        assertEquals("教室:A101", chips(base.copy(roomNameLike = "A101")).single().label)
    }

    @Test
    fun `credit range label variants`() {
        assertEquals("2~4 学分", chips(base.copy(creditsGte = 2.0, creditsLte = 4.0)).single().label)
        assertEquals("≥3 学分", chips(base.copy(creditsGte = 3.0)).single().label)
        assertEquals("≤5 学分", chips(base.copy(creditsLte = 5.0)).single().label)
        // 非整学分保留小数。
        assertEquals("1.5~2 学分", chips(base.copy(creditsGte = 1.5, creditsLte = 2.0)).single().label)
    }

    @Test
    fun `blank room and compulsory produce no chip`() {
        assertTrue(chips(base.copy(roomNameLike = "")).isEmpty())
        assertTrue(chips(base.copy(compulsory = "")).isEmpty())
    }

    // ── chip 与 clearFilterDimension 对称性 ────────────────────

    @Test
    fun `every produced chip dimension is cleared by clearFilterDimension`() {
        // 一个覆盖所有维度的满筛选。
        val full = base.copy(
            departmentIds = listOf(101L),
            courseTypeId = 1L,
            campusId = 1L,
            compulsory = "COMPULSORY",
            examModeId = 1L,
            teachLangId = 2L,
            weekdays = listOf(1, 2),
            courseUnitIndexes = listOf(1, 2),
            buildingId = 9001L,
            roomNameLike = "A101",
            creditsGte = 2.0,
            creditsLte = 4.0,
        )
        val produced = chips(full)
        // 每个维度都产出了 chip。
        assertEquals(produced.map { it.dimension }.toSet().size, produced.size)
        // 逐个移除后，该维度不再产出 chip。
        produced.forEach { chip ->
            val cleared = clearFilterDimension(full, chip.dimension)
            val stillHas = buildActiveFilterChips(cleared, deptOptions, buildingOptions)
                .any { it.dimension == chip.dimension }
            assertTrue("移除 ${chip.dimension} 后不应再有该维度 chip", !stillHas)
        }
    }

    @Test
    fun `clearing campus also clears building`() {
        val f = base.copy(campusId = 1L, buildingId = 9001L)
        val cleared = clearFilterDimension(f, LessonFilterDimension.CAMPUS)
        assertNull(cleared.campusId)
        assertNull(cleared.buildingId)
    }

    @Test
    fun `clearing all produced dimensions yields empty filter chips`() {
        val full = base.copy(
            departmentIds = listOf(101L), courseTypeId = 1L, campusId = 1L,
            weekdays = listOf(1), buildingId = 9001L, creditsGte = 2.0,
        )
        var f = full
        buildActiveFilterChips(full, deptOptions, buildingOptions).forEach {
            f = clearFilterDimension(f, it.dimension)
        }
        assertTrue(buildActiveFilterChips(f, deptOptions, buildingOptions).isEmpty())
    }
}
