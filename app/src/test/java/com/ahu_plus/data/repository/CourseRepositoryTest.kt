package com.ahu_plus.data.repository

import com.ahu_plus.data.model.jw.ArrangedCourse
import com.ahu_plus.data.model.jw.CourseActivity
import com.ahu_plus.data.model.jw.GetDataLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CourseRepository.toDisplayItems() 单元测试。
 *
 * 这是纯函数(无 IO),不需要 mock——直接验证输入/输出映射。
 */
class CourseRepositoryTest {

    @Test
    fun `toDisplayItems filters activities by selectedWeek`() {
        val activity1 = sampleActivity(lessonId = 100, weekIndexes = listOf(1, 2, 3))
        val activity2 = sampleActivity(lessonId = 101, weekIndexes = listOf(4, 5, 6))

        val items = CourseRepository.toDisplayItems(
            activities = listOf(activity1, activity2),
            selectedWeek = 2,
            getDataLessons = null
        )

        assertEquals(1, items.size)
        assertEquals(100L, items[0].lessonId)
    }

    @Test
    fun `toDisplayItems returns empty when no activity matches`() {
        val activity = sampleActivity(weekIndexes = listOf(1, 2, 3))
        val items = CourseRepository.toDisplayItems(
            activities = listOf(activity),
            selectedWeek = 10,
            getDataLessons = null
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun `toDisplayItems merges teacher names with comma separator`() {
        val activity = sampleActivity(weekIndexes = listOf(1), teacherNames = listOf("张三", "李四"))
        val items = CourseRepository.toDisplayItems(
            activities = listOf(activity),
            selectedWeek = 1,
            getDataLessons = null
        )
        assertEquals("张三、李四", items[0].teacherNames)
    }

    @Test
    fun `toDisplayItems uses getData lesson credits when activity has no credits`() {
        val activity = sampleActivity(lessonId = 100, weekIndexes = listOf(1))
        val lesson = GetDataLesson(
            id = 100,
            nameZh = null, code = null,
            course = ArrangedCourse(nameZh = "高数", code = null, credits = 4.0),
            scheduleText = null, teacherAssignmentList = null,
            suggestScheduleWeeks = null, courseType = null,
            credits = 4.0, campus = null, scheduleWeeksInfo = null,
            compulsorysStr = null, examMode = null,
            stdCount = null, limitCount = null
        )

        val items = CourseRepository.toDisplayItems(
            activities = listOf(activity),
            selectedWeek = 1,
            getDataLessons = listOf(lesson)
        )
        // credits 应优先用 getData lesson 的值
        assertEquals(4.0, items[0].credits!!, 0.001)
    }

    @Test
    fun `toDisplayItems sorts by weekday then startUnit`() {
        val aMon = sampleActivity(lessonId = 1, weekIndexes = listOf(1), weekday = 1, startUnit = 3, endUnit = 4)
        val aTue = sampleActivity(lessonId = 2, weekIndexes = listOf(1), weekday = 2, startUnit = 1, endUnit = 2)
        val aMonEarly = sampleActivity(lessonId = 3, weekIndexes = listOf(1), weekday = 1, startUnit = 1, endUnit = 2)

        val items = CourseRepository.toDisplayItems(
            activities = listOf(aMon, aTue, aMonEarly),
            selectedWeek = 1,
            getDataLessons = null
        )

        assertEquals(3L, items[0].lessonId)  // 周一第1节
        assertEquals(1L, items[1].lessonId)  // 周一第3节
        assertEquals(2L, items[2].lessonId)  // 周二第1节
    }

    @Test
    fun `colorIndex is stable for the same courseName`() {
        val a1 = sampleActivity(weekIndexes = listOf(1), courseName = "高等数学")
        val a2 = sampleActivity(weekIndexes = listOf(1), courseName = "高等数学")
        val items = CourseRepository.toDisplayItems(
            activities = listOf(a1, a2),
            selectedWeek = 1,
            getDataLessons = null
        )
        assertEquals(items[0].colorIndex, items[1].colorIndex)
    }

    @Test
    fun `default courseName is used when activity has no name`() {
        val activity = sampleActivity(weekIndexes = listOf(1), courseName = null)
        val items = CourseRepository.toDisplayItems(
            activities = listOf(activity),
            selectedWeek = 1,
            getDataLessons = null
        )
        assertEquals("未知课程", items[0].courseName)
    }

    private fun sampleActivity(
        lessonId: Long? = 1,
        weekIndexes: List<Int> = listOf(1),
        weekday: Int? = 1,
        startUnit: Int? = 1,
        endUnit: Int? = 2,
        teacherNames: List<String>? = null,
        courseName: String? = "测试课程"
    ): CourseActivity {
        return CourseActivity(
            lessonId = lessonId,
            lessonCode = null, lessonName = null,
            courseCode = null, courseName = courseName,
            weeksStr = null, weekIndexes = weekIndexes,
            room = null, building = null, campus = null,
            weekday = weekday, startUnit = startUnit, endUnit = endUnit,
            teachers = null, teacherNames = teacherNames,
            courseType = null, credits = null,
            startTime = null, endTime = null, periodInfo = null,
            stdCount = null, limitCount = null
        )
    }
}