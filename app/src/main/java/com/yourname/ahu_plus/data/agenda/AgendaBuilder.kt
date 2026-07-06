package com.yourname.ahu_plus.data.agenda

import com.yourname.ahu_plus.data.model.agenda.AgendaEvent
import com.yourname.ahu_plus.data.model.agenda.AgendaSource
import com.yourname.ahu_plus.data.model.jw.CourseDisplayItem
import com.yourname.ahu_plus.data.model.jw.CourseUnit
import com.yourname.ahu_plus.data.model.jw.Exam
import com.yourname.ahu_plus.data.model.jw.ScheduleData
import com.yourname.ahu_plus.data.repository.CourseRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 把课表 / 考试展开成带**具体日历日期**的 [AgendaEvent]。
 *
 * 纯函数集合(无网络、无 Android 依赖,除了正则),便于 JVM 单测覆盖坐标数学。
 *
 * 课程日期换算的锚点与 `WeekGrid.computeWeekMonday` 一致:服务器返回的 `currentWeek`
 * 表示"今天是第 currentWeek 周"。把某个目标日期对齐到它所在周的周一,与"今天所在周
 * 的周一"比较相差几周,即可反推目标日期的周次。
 */
object AgendaBuilder {

    /**
     * 反推 [date] 是第几教学周。
     *
     * [today] 处于第 [currentWeek] 周。两个日期各自对齐到本周一,按 7 天为一周求差。
     * 例:today=周三&currentWeek=17,date=下周一 → (下周一 - 本周一)/7 = 1 → 第 18 周。
     */
    fun weekOfDate(date: LocalDate, currentWeek: Int, today: LocalDate): Int {
        val todayMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dateMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeksBetween = java.time.temporal.ChronoUnit.WEEKS.between(todayMonday, dateMonday).toInt()
        return currentWeek + weeksBetween
    }

    /**
     * 把课表在 [from]..[to](含端点)范围内每一天展开成课程事件。
     *
     * 对每一天:算出它的周次 → 走与完整课表同一管道的 [CourseRepository.toDisplayItems]
     * (自动按周次过滤 weekIndexes) → 再筛出 weekday 与当天匹配的课。
     */
    fun expandCourses(
        data: ScheduleData,
        today: LocalDate,
        from: LocalDate,
        to: LocalDate,
    ): List<AgendaEvent> {
        if (from.isAfter(to)) return emptyList()
        val colorMap = CourseRepository.buildColorMap(data.activities)
        val unitTimes = data.unitTimes
        val result = ArrayList<AgendaEvent>()
        var day = from
        while (!day.isAfter(to)) {
            val week = weekOfDate(day, data.currentWeek, today)
            if (week >= 1) {
                val weekday = day.dayOfWeek.value // 1=周一 .. 7=周日
                val items = CourseRepository.toDisplayItems(
                    activities = data.activities,
                    selectedWeek = week,
                    getDataLessons = data.lessons,
                    colorMap = colorMap,
                ).filter { it.weekday == weekday }
                for (item in items) {
                    result.add(courseEvent(item, day, unitTimes))
                }
            }
            day = day.plusDays(1)
        }
        return result
    }

    private fun courseEvent(
        item: CourseDisplayItem,
        date: LocalDate,
        unitTimes: List<CourseUnit>,
    ): AgendaEvent {
        val unitMap = unitTimes.associateBy { it.indexNo }
        val startMin = item.startTime?.let { parseClockMinutes(it) }
            ?: unitMap[item.startUnit]?.let { minutesOf(it.startTime) }
        val endMin = item.endTime?.let { parseClockMinutes(it) }
            ?: unitMap[item.endUnit]?.let { minutesOf(it.endTime) }
        return AgendaEvent(
            id = "course:${item.courseName}|$date|${item.startUnit}",
            source = AgendaSource.COURSE,
            date = date,
            title = item.courseName,
            location = item.room?.takeIf { it.isNotBlank() },
            startMinutes = startMin,
            endMinutes = endMin,
            colorIndex = item.colorIndex,
        )
    }

    /**
     * 把考试列表转为事件。考试自带具体日期(examTime 形如 "2026-05-24 14:00~15:40"),
     * 与教学周无关。落在 [from]..[to] 范围外的丢弃。
     */
    fun expandExams(
        exams: List<Exam>,
        from: LocalDate,
        to: LocalDate,
    ): List<AgendaEvent> {
        return exams.mapNotNull { e ->
            val parsed = parseExam(e.examTime) ?: return@mapNotNull null
            val (date, startMin, endMin) = parsed
            if (date.isBefore(from) || date.isAfter(to)) return@mapNotNull null
            AgendaEvent(
                id = "exam:${e.id}",
                source = AgendaSource.EXAM,
                date = date,
                title = e.displayCourse,
                location = e.displayLocation,
                startMinutes = startMin,
                endMinutes = endMin,
                colorIndex = 0,
                sourceId = e.id,
            )
        }
    }

    /** 解析 "yyyy-MM-dd HH:mm~HH:mm" → (日期, 起始分钟, 结束分钟)。结束可缺省。 */
    fun parseExam(examTime: String): Triple<LocalDate, Int?, Int?>? {
        val m = Regex("""(\d{4})-(\d{2})-(\d{2})\s+(\d{1,2}):(\d{2})(?:~(\d{1,2}):(\d{2}))?""")
            .find(examTime) ?: return null
        val g = m.groupValues
        return runCatching {
            val date = LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
            val start = g[4].toInt() * 60 + g[5].toInt()
            val end = if (g[6].isNotBlank()) g[6].toInt() * 60 + g[7].toInt() else null
            Triple(date, start, end)
        }.getOrNull()
    }

    /** CourseUnit.startTime 是 Int(如 800=08:00);转当天分钟数。 */
    private fun minutesOf(hhmm: Int?): Int? {
        if (hhmm == null) return null
        return (hhmm / 100) * 60 + (hhmm % 100)
    }

    /** 解析 "HH:mm" / "H:mm" 时钟串为当天分钟数。 */
    private fun parseClockMinutes(text: String): Int? {
        val parts = text.split(":")
        if (parts.size < 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val mnt = parts[1].trim().toIntOrNull() ?: return null
        return h * 60 + mnt
    }
}
