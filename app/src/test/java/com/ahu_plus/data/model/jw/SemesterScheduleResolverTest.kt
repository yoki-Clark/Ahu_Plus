package com.ahu_plus.data.model.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 课表「假期感知」纯逻辑测试:
 *  - 学期中 → 展示当前学期,使用服务器周次;
 *  - 寒暑假 → 默认展示下一个学期,当前周记 0(不误算成当天有课)。
 */
class SemesterScheduleResolverTest {

    private fun semester(id: Int, start: String?, end: String?): SemesterInfo =
        SemesterInfo(
            id = id,
            nameZh = "学期$id",
            code = null,
            schoolYear = null,
            startDate = start,
            endDate = end,
            season = null,
        )

    private val springEnded = semester(112, "2026-02-23", "2026-07-19")
    private val autumnUpcoming = semester(113, "2026-09-07", "2027-01-17")

    @Test
    fun `in semester keeps current semester and server week`() {
        val today = LocalDate.of(2026, 10, 12)
        val resolution = SemesterScheduleResolver.resolveDefaultSemester(
            semesters = listOf(autumnUpcoming, springEnded),
            knownDatedSemesters = listOf(autumnUpcoming),
            today = today,
            fallbackId = 112,
        )
        assertEquals(113, resolution.semesterId)
        assertFalse(resolution.vacation)
        assertEquals(
            8,
            SemesterScheduleResolver.effectiveCurrentWeek(autumnUpcoming, 8, today),
        )
    }

    @Test
    fun `summer vacation defaults to next semester and zeroes current week`() {
        val today = LocalDate.of(2026, 7, 31)
        val resolution = SemesterScheduleResolver.resolveDefaultSemester(
            semesters = listOf(autumnUpcoming, springEnded),
            knownDatedSemesters = listOf(springEnded),
            today = today,
            fallbackId = 112,
        )
        assertEquals(113, resolution.semesterId)
        assertTrue(resolution.vacation)
        assertEquals(
            0,
            SemesterScheduleResolver.effectiveCurrentWeek(autumnUpcoming, 1, today),
        )
    }

    @Test
    fun `winter vacation defaults to next semester`() {
        val autumnEnded = semester(113, "2026-09-07", "2027-01-17")
        val springUpcoming = semester(114, "2027-02-22", "2027-07-18")
        val today = LocalDate.of(2027, 1, 25)
        val resolution = SemesterScheduleResolver.resolveDefaultSemester(
            semesters = listOf(springUpcoming, autumnEnded),
            knownDatedSemesters = listOf(autumnEnded),
            today = today,
            fallbackId = 113,
        )
        assertEquals(114, resolution.semesterId)
        assertTrue(resolution.vacation)
    }

    @Test
    fun `upcoming semester with known start date is picked directly`() {
        val today = LocalDate.of(2026, 7, 31)
        val resolution = SemesterScheduleResolver.resolveDefaultSemester(
            semesters = listOf(autumnUpcoming, springEnded),
            knownDatedSemesters = listOf(springEnded, autumnUpcoming),
            today = today,
            fallbackId = 112,
        )
        assertEquals(113, resolution.semesterId)
        assertTrue(resolution.vacation)
    }

    @Test
    fun `first day of semester is not vacation and keeps server week`() {
        val today = LocalDate.of(2026, 9, 7)
        assertEquals(
            1,
            SemesterScheduleResolver.effectiveCurrentWeek(autumnUpcoming, 1, today),
        )
        val resolution = SemesterScheduleResolver.resolveDefaultSemester(
            semesters = listOf(autumnUpcoming, springEnded),
            knownDatedSemesters = listOf(autumnUpcoming),
            today = today,
            fallbackId = 112,
        )
        assertFalse(resolution.vacation)
    }

    @Test
    fun `effective week trusts server when dates missing`() {
        val today = LocalDate.of(2026, 7, 31)
        assertEquals(12, SemesterScheduleResolver.effectiveCurrentWeek(null, 12, today))
        assertEquals(
            12,
            SemesterScheduleResolver.effectiveCurrentWeek(
                semester(112, null, null),
                12,
                today,
            ),
        )
    }

    @Test
    fun `server week at or above 20 is always vacation`() {
        val today = LocalDate.of(2026, 10, 12)
        // 即使日期齐全且今天看起来在学期内,周次 >= 20 也必然是假期
        assertEquals(
            0,
            SemesterScheduleResolver.effectiveCurrentWeek(autumnUpcoming, 20, today),
        )
        assertEquals(
            0,
            SemesterScheduleResolver.effectiveCurrentWeek(autumnUpcoming, 21, today),
        )
        // 日期缺失时同样适用
        assertEquals(
            0,
            SemesterScheduleResolver.effectiveCurrentWeek(null, 21, today),
        )
        assertFalse(SemesterScheduleResolver.isVacationWeek(19))
        assertTrue(SemesterScheduleResolver.isVacationWeek(20))
        assertTrue(SemesterScheduleResolver.isVacationWeek(25))
    }

    @Test
    fun `server week below 1 means semester not started`() {
        val today = LocalDate.of(2026, 7, 31)
        // 学校接口对新学期返回负周次(-6)表示未开学,同样按假期处理
        assertEquals(
            0,
            SemesterScheduleResolver.effectiveCurrentWeek(null, 0, today),
        )
        assertEquals(
            0,
            SemesterScheduleResolver.effectiveCurrentWeek(null, -6, today),
        )
    }

    @Test
    fun `overrun semester without dates still picks next semester`() {
        val today = LocalDate.of(2026, 7, 31)
        // 两个学期都没有日期,但缓存学期服务器周次已 >= 20(已超教学周) → 必是假期
        val resolution = SemesterScheduleResolver.resolveDefaultSemester(
            semesters = listOf(autumnUpcoming, springEnded),
            knownDatedSemesters = listOf(semester(112, null, null)),
            today = today,
            fallbackId = 112,
            overrunSemesterIds = setOf(112),
        )
        assertEquals(113, resolution.semesterId)
        assertTrue(resolution.vacation)
    }

    @Test
    fun `real world id gaps resolve next semester by id order`() {
        // 学校学期 id 步长 20(132=2026-2027-1, 112=2025-2026-2),按 id 顺序取下一个
        val today = LocalDate.of(2026, 7, 31)
        val realList = listOf(
            semester(132, null, null),
            semester(112, null, null),
            semester(92, null, null),
        )
        val resolution = SemesterScheduleResolver.resolveDefaultSemester(
            semesters = realList,
            knownDatedSemesters = emptyList(),
            today = today,
            fallbackId = 112,
            overrunSemesterIds = setOf(112),
        )
        assertEquals(132, resolution.semesterId)
        assertTrue(resolution.vacation)
    }

    @Test
    fun `overrun flag alone marks vacation when no successor exists`() {
        val today = LocalDate.of(2026, 7, 31)
        val resolution = SemesterScheduleResolver.resolveDefaultSemester(
            semesters = listOf(springEnded),
            knownDatedSemesters = emptyList(),
            today = today,
            fallbackId = 112,
            overrunSemesterIds = setOf(112),
        )
        assertEquals(112, resolution.semesterId)
        assertTrue(resolution.vacation)
    }

    @Test
    fun `no date info falls back without vacation flag`() {
        val today = LocalDate.of(2026, 7, 31)
        val resolution = SemesterScheduleResolver.resolveDefaultSemester(
            semesters = listOf(autumnUpcoming, springEnded),
            knownDatedSemesters = emptyList(),
            today = today,
            fallbackId = 112,
        )
        assertEquals(112, resolution.semesterId)
        assertFalse(resolution.vacation)
    }

    @Test
    fun `only past semester with no successor falls back but keeps vacation flag`() {
        val today = LocalDate.of(2026, 7, 31)
        val resolution = SemesterScheduleResolver.resolveDefaultSemester(
            semesters = listOf(springEnded),
            knownDatedSemesters = listOf(springEnded),
            today = today,
            fallbackId = 112,
        )
        assertEquals(112, resolution.semesterId)
        assertTrue(resolution.vacation)
    }
}
