package com.yourname.ahu_plus.data.agenda

import com.yourname.ahu_plus.data.model.agenda.AgendaSource
import com.yourname.ahu_plus.data.model.jw.CourseActivity
import com.yourname.ahu_plus.data.model.jw.CourseUnit
import com.yourname.ahu_plus.data.model.jw.Exam
import com.yourname.ahu_plus.data.model.jw.ScheduleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * AgendaBuilder 换算逻辑单测(纯函数,无 IO / 无 Android)。
 *
 * 覆盖三处最易回归的坐标数学:周次反推、课程落到正确日历日、考试时间解析。
 */
class AgendaBuilderTest {

    // 2026-07-06 是周一。用它当"今天",第 17 周。
    private val today = LocalDate.of(2026, 7, 6)

    @Test
    fun `weekOfDate returns currentWeek for today`() {
        assertEquals(17, AgendaBuilder.weekOfDate(today, 17, today))
    }

    @Test
    fun `weekOfDate advances by one for next monday`() {
        val nextMonday = today.plusDays(7)
        assertEquals(18, AgendaBuilder.weekOfDate(nextMonday, 17, today))
    }

    @Test
    fun `weekOfDate stays same within the current week`() {
        // 周日(同一 ISO 周内)仍是第 17 周
        assertEquals(17, AgendaBuilder.weekOfDate(today.plusDays(6), 17, today))
    }

    @Test
    fun `weekOfDate goes back for previous week`() {
        assertEquals(16, AgendaBuilder.weekOfDate(today.minusDays(7), 17, today))
    }

    @Test
    fun `expandCourses places a monday course on the correct date only`() {
        // 周一(weekday=1)、第 1..20 周都有的课
        val monCourse = activity(courseName = "高等数学", weekday = 1, weekIndexes = (1..20).toList())
        val data = scheduleData(activities = listOf(monCourse), currentWeek = 17)

        // 展开今天(周一)到本周日
        val events = AgendaBuilder.expandCourses(data, today, today, today.plusDays(6))

        // 只应落在周一这一天
        assertEquals(1, events.size)
        assertEquals(today, events[0].date)
        assertEquals("高等数学", events[0].title)
        assertEquals(AgendaSource.COURSE, events[0].source)
        // 第1节 08:00 → 480 分钟
        assertEquals(480, events[0].startMinutes)
    }

    @Test
    fun `expandCourses skips weeks the course is not scheduled`() {
        // 只在第 1 周上的课,当前是第 17 周 → 本周展开应为空
        val course = activity(weekday = 1, weekIndexes = listOf(1))
        val data = scheduleData(activities = listOf(course), currentWeek = 17)

        val events = AgendaBuilder.expandCourses(data, today, today, today.plusDays(6))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `expandCourses never emits events for weeks below one`() {
        // 展开到过去很久(周次 < 1)时应跳过,不崩
        val course = activity(weekday = 1, weekIndexes = (1..20).toList())
        val data = scheduleData(activities = listOf(course), currentWeek = 2)

        val past = today.minusWeeks(10)
        val events = AgendaBuilder.expandCourses(data, today, past, past.plusDays(6))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `parseExam extracts date and start-end minutes`() {
        val parsed = AgendaBuilder.parseExam("2026-05-24 14:00~15:40")
        assertEquals(LocalDate.of(2026, 5, 24), parsed?.first)
        assertEquals(14 * 60, parsed?.second)
        assertEquals(15 * 60 + 40, parsed?.third)
    }

    @Test
    fun `parseExam tolerates missing end time`() {
        val parsed = AgendaBuilder.parseExam("2026-05-24 09:00")
        assertEquals(LocalDate.of(2026, 5, 24), parsed?.first)
        assertEquals(9 * 60, parsed?.second)
        assertNull(parsed?.third)
    }

    @Test
    fun `parseExam returns null on garbage`() {
        assertNull(AgendaBuilder.parseExam("待定"))
    }

    @Test
    fun `expandExams keeps only exams inside range`() {
        val inRange = exam(id = "1", examTime = "2026-07-08 14:00~15:40")
        val outOfRange = exam(id = "2", examTime = "2026-09-01 14:00~15:40")
        val events = AgendaBuilder.expandExams(
            listOf(inRange, outOfRange),
            from = today,
            to = today.plusDays(30),
        )
        assertEquals(1, events.size)
        assertEquals("1", events[0].sourceId)
        assertEquals(LocalDate.of(2026, 7, 8), events[0].date)
    }

    // ── helpers ──────────────────────────────────────────

    private fun activity(
        courseName: String? = "测试课程",
        weekday: Int? = 1,
        weekIndexes: List<Int> = listOf(1),
        startUnit: Int? = 1,
        endUnit: Int? = 2,
    ) = CourseActivity(
        lessonId = courseName.hashCode().toLong(),
        lessonCode = null, lessonName = null,
        courseCode = courseName, courseName = courseName,
        weeksStr = null, weekIndexes = weekIndexes,
        room = "教101", building = null, campus = null,
        weekday = weekday, startUnit = startUnit, endUnit = endUnit,
        teachers = null, teacherNames = null,
        courseType = null, credits = null,
        startTime = null, endTime = null, periodInfo = null,
        stdCount = null, limitCount = null,
    )

    private fun scheduleData(
        activities: List<CourseActivity>,
        currentWeek: Int,
    ) = ScheduleData(
        studentName = null, className = null, department = null, credits = null,
        activities = activities,
        unitTimes = defaultUnits(),
        semester = null,
        currentWeek = currentWeek,
        weekIndices = (1..20).toList(),
        lessons = null,
    )

    private fun defaultUnits() = listOf(
        CourseUnit(nameZh = "第1节", indexNo = 1, startTime = 800, endTime = 845, dayPart = null, name = null),
        CourseUnit(nameZh = "第2节", indexNo = 2, startTime = 850, endTime = 935, dayPart = null, name = null),
    )

    private fun exam(id: String, examTime: String) = Exam(
        id = id, courseName = "考试课", examType = "期末", examTime = examTime,
        campus = "磬苑校区", building = "教学楼", room = "101", seatNumber = "12", isFinished = false,
    )
}
