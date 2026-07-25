package com.ahu_plus.data.model.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LessonScheduleParser] 纯逻辑单测。
 *
 * 覆盖 HAR 实测格式变体：范围周 / 离散周 / 单双周 / 多时段(`; `) / 单节 /
 * 星期日与天 / 无教室 / 空文本 / 解析失败落桶。
 */
class LessonScheduleParserTest {

    @Test
    fun `range week single slot parses geometry and room`() {
        val r = LessonScheduleParser.parseText("1~16周 星期一 1~2节 博北201")
        assertEquals(1, r.slots.size)
        val s = r.slots[0]
        assertEquals(1, s.weekday)
        assertEquals(1, s.startUnit)
        assertEquals(2, s.endUnit)
        assertEquals((1..16).toList(), s.weekIndexes)
        assertEquals("博北201", s.room)
        assertFalse(r.hasUnparsed)
    }

    @Test
    fun `hyphen week separator equivalent to tilde`() {
        val r = LessonScheduleParser.parseText("1-8周 星期三 3-4节")
        assertEquals(1, r.slots.size)
        assertEquals((1..8).toList(), r.slots[0].weekIndexes)
        assertEquals(3, r.slots[0].weekday)
    }

    @Test
    fun `discrete weeks parse to exact set`() {
        val r = LessonScheduleParser.parseText("1,3,5,7周 星期二 5~6节 教学楼A101")
        assertEquals(listOf(1, 3, 5, 7), r.slots[0].weekIndexes)
        assertEquals(2, r.slots[0].weekday)
    }

    @Test
    fun `odd week qualifier filters to odd weeks`() {
        val r = LessonScheduleParser.parseText("1~16单周 星期四 7~8节")
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), r.slots[0].weekIndexes)
    }

    @Test
    fun `even week qualifier filters to even weeks`() {
        val r = LessonScheduleParser.parseText("2~16双周 星期五 1~2节")
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), r.slots[0].weekIndexes)
    }

    @Test
    fun `parenthesized odd qualifier also filters`() {
        val weeks = LessonScheduleParser.parseWeeks("1~16(单)")
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), weeks)
    }

    @Test
    fun `multi slot separated by semicolon space`() {
        val r = LessonScheduleParser.parseText("1~16周 星期一 1~2节 博北201; 1~16周 星期三 3~4节 博北202")
        assertEquals(2, r.slots.size)
        assertEquals(1, r.slots[0].weekday)
        assertEquals(3, r.slots[1].weekday)
        assertEquals("博北202", r.slots[1].room)
        assertFalse(r.hasUnparsed)
    }

    @Test
    fun `fullwidth semicolon also splits`() {
        val r = LessonScheduleParser.parseText("1~16周 星期一 1~2节；1~16周 星期二 3~4节")
        assertEquals(2, r.slots.size)
    }

    @Test
    fun `single period unit collapses end to start`() {
        val r = LessonScheduleParser.parseText("1~16周 星期三 5节")
        assertEquals(1, r.slots.size)
        assertEquals(5, r.slots[0].startUnit)
        assertEquals(5, r.slots[0].endUnit)
    }

    @Test
    fun `sunday maps to iso 7 via ri`() {
        val r = LessonScheduleParser.parseText("1~16周 星期日 1~2节")
        assertEquals(7, r.slots[0].weekday)
    }

    @Test
    fun `sunday maps to iso 7 via tian`() {
        val r = LessonScheduleParser.parseText("1~16周 星期天 1~2节")
        assertEquals(7, r.slots[0].weekday)
    }

    @Test
    fun `saturday maps to iso 6`() {
        val r = LessonScheduleParser.parseText("1~16周 星期六 9~10节")
        assertEquals(6, r.slots[0].weekday)
    }

    @Test
    fun `no room falls back to provided room`() {
        val r = LessonScheduleParser.parseText("1~16周 星期一 1~2节", fallbackRoom = "综合楼305")
        assertEquals("综合楼305", r.slots[0].room)
    }

    @Test
    fun `no room and no fallback yields null room`() {
        val r = LessonScheduleParser.parseText("1~16周 星期一 1~2节")
        assertEquals(null, r.slots[0].room)
    }

    @Test
    fun `empty text yields no slots and no unparsed`() {
        val r = LessonScheduleParser.parseText("")
        assertFalse(r.hasSlots)
        assertFalse(r.hasUnparsed)
    }

    @Test
    fun `null text yields empty result`() {
        val r = LessonScheduleParser.parseText(null)
        assertTrue(r.slots.isEmpty())
        assertTrue(r.unparsedSegments.isEmpty())
    }

    @Test
    fun `unparseable segment goes to bucket not crash`() {
        val r = LessonScheduleParser.parseText("时间地点待定")
        assertFalse(r.hasSlots)
        assertTrue(r.hasUnparsed)
        assertEquals("时间地点待定", r.unparsedSegments[0])
    }

    @Test
    fun `mixed parseable and unparseable in one text`() {
        val r = LessonScheduleParser.parseText("1~16周 星期一 1~2节 博北201; 集中安排")
        assertEquals(1, r.slots.size)
        assertEquals(1, r.unparsedSegments.size)
        assertEquals("集中安排", r.unparsedSegments[0])
    }

    @Test
    fun `mixed range and discrete weeks union`() {
        val weeks = LessonScheduleParser.parseWeeks("1~4,7,9~11")
        assertEquals(listOf(1, 2, 3, 4, 7, 9, 10, 11), weeks)
    }

    @Test
    fun `out of range weeks are dropped`() {
        val weeks = LessonScheduleParser.parseWeeks("0,1,99,16")
        assertEquals(listOf(1, 16), weeks)
    }

    @Test
    fun `reversed unit range treated as unparsed`() {
        val r = LessonScheduleParser.parseText("1~16周 星期一 4~2节")
        assertFalse(r.hasSlots)
        assertTrue(r.hasUnparsed)
    }

    @Test
    fun `displayItemsFor emits only slots active in target week`() {
        val record = LessonRecord(
            id = 42L, code = "L001", nameZh = "教学班",
            course = LessonCourse(code = "C001", nameZh = "高等数学", credits = 4.0, id = 9L),
            minorCourse = null, openDepartment = null,
            teacherAssignmentList = listOf(
                LessonTeacher(role = "MAJOR", person = LessonNamed(nameZh = "张三", id = 1L))
            ),
            stdCount = 30, limitCount = 60,
            courseType = LessonNamed(nameZh = "理论课", id = 1L),
            courseProperty = null, examMode = null, teachLang = null,
            scheduleText = LessonScheduleText(
                dateTimePlacePersonText = null,
                dateTimePlaceText = LessonText("1,3,5周 星期一 1~2节 博北201", null),
                dateTimeText = null, roomSeatText = null,
            ),
            requiredPeriodInfo = null, remark = null,
        )
        val result = LessonScheduleParser.parse(record)
        assertEquals(listOf(1, 3, 5), result.slots[0].weekIndexes)

        val wk1 = LessonScheduleParser.displayItemsFor(record, result, week = 1)
        assertEquals(1, wk1.size)
        assertEquals("高等数学", wk1[0].courseName)
        assertEquals("张三", wk1[0].teacherNames)
        assertEquals("C001", wk1[0].courseCode)
        assertEquals("博北201", wk1[0].room)
        assertEquals(1, wk1[0].weekday)

        val wk2 = LessonScheduleParser.displayItemsFor(record, result, week = 2)
        assertTrue(wk2.isEmpty())
    }

    @Test
    fun `maxWeek reflects largest covered week`() {
        val r = LessonScheduleParser.parseText("1~16周 星期一 1~2节; 1~18周 星期三 3~4节")
        assertEquals(18, LessonScheduleParser.maxWeek(r))
    }
}
