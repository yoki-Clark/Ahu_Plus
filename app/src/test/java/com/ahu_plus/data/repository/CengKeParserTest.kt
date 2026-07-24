package com.ahu_plus.data.repository

import com.ahu_plus.data.model.jw.LessonCourse
import com.ahu_plus.data.model.jw.LessonNamed
import com.ahu_plus.data.model.jw.LessonRecord
import com.ahu_plus.data.model.jwapp.RoomOccupationInfo
import com.ahu_plus.data.model.jwapp.RoomWithOccupancy
import com.ahu_plus.data.model.jwapp.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 蹭课纯逻辑单测:activityName 解析、时段分档、候选池去重、客户端过滤、随机"换一个"。
 * 全部走 [CengKeParser],无 Android/网络依赖。
 */
class CengKeParserTest {

    // ── 测试夹具 ────────────────────────────────────────────────
    private fun occ(
        activityType: String? = "Lesson",
        activityName: String? = "课程：高等数学(202620271-GG61015.087, 数学科学学院)",
        date: String? = "2026-07-23",
        startTime: Int = 800,
        startTimeString: String? = "08:00",
        endTimeString: String? = "09:40",
        teacherName: String? = "张老师",
    ) = RoomOccupationInfo(
        activityType = activityType,
        activityName = activityName,
        date = date,
        startTime = startTime,
        endTime = 0,
        startTimeString = startTimeString,
        endTimeString = endTimeString,
        teacherName = teacherName,
    )

    private fun room(
        id: Long = 1L,
        nameZh: String? = "博学北楼101",
        buildingId: Int? = 10,
        floor: Int? = 1,
        campusNameZh: String? = "磬苑校区",
        occupations: List<RoomOccupationInfo>? = listOf(occ()),
    ) = RoomWithOccupancy(
        id = id,
        nameZh = nameZh,
        buildingId = buildingId,
        floor = floor,
        campusNameZh = campusNameZh,
        occupations = occupations,
    )

    // ── parseActivityName ──────────────────────────────────────
    @Test
    fun `parseActivityName parses standard format`() {
        val result = CengKeParser.parseActivityName("课程：高等数学(202620271-GG61015.087, 数学科学学院)")
        assertNotNull(result)
        assertEquals("高等数学", result!!.first)
        assertEquals("202620271-GG61015.087", result.second)
        assertEquals("数学科学学院", result.third)
    }

    @Test
    fun `parseActivityName tolerates full-width parens in course name`() {
        val result = CengKeParser.parseActivityName("课程：大学英语（下）(202620271-GG61015.087, 外语学院)")
        assertNotNull(result)
        assertEquals("大学英语（下）", result!!.first)
        assertEquals("外语学院", result.third)
    }

    @Test
    fun `parseActivityName returns null for non-course activity`() {
        assertNull(CengKeParser.parseActivityName("借用：学院活动"))
        assertNull(CengKeParser.parseActivityName(""))
        assertNull(CengKeParser.parseActivityName(null))
    }

    @Test
    fun `parseActivityName returns null when full code malformed`() {
        // 缺少 .NNN 三位后缀,不满足 full_code
        assertNull(CengKeParser.parseActivityName("课程：某课(202620271-GG61015, 某学院)"))
    }

    @Test
    fun `parseActivityName allows empty college`() {
        val result = CengKeParser.parseActivityName("课程：体育(202620271-TY10001.001)")
        assertNotNull(result)
        assertEquals("体育", result!!.first)
        assertEquals("", result.third)
    }

    // ── timeSlotOf ─────────────────────────────────────────────
    @Test
    fun `timeSlotOf buckets by start time`() {
        assertEquals(TimeSlot.MORNING, CengKeParser.timeSlotOf(800))
        assertEquals(TimeSlot.MORNING, CengKeParser.timeSlotOf(1159))
        assertEquals(TimeSlot.AFTERNOON, CengKeParser.timeSlotOf(1200))
        assertEquals(TimeSlot.AFTERNOON, CengKeParser.timeSlotOf(1400))
        assertEquals(TimeSlot.AFTERNOON, CengKeParser.timeSlotOf(1759))
        assertEquals(TimeSlot.EVENING, CengKeParser.timeSlotOf(1800))
        assertEquals(TimeSlot.EVENING, CengKeParser.timeSlotOf(1900))
    }

    // ── courseCodeOf ───────────────────────────────────────────
    @Test
    fun `courseCodeOf extracts middle segment`() {
        assertEquals("GG61015", CengKeParser.courseCodeOf("202620271-GG61015.087"))
    }

    @Test
    fun `courseCodeOf falls back to whole string`() {
        assertEquals("garbage", CengKeParser.courseCodeOf("garbage"))
    }
    // ── parseCourses ───────────────────────────────────────────
    @Test
    fun `parseCourses keeps only Lesson activity`() {
        val rooms = listOf(
            room(occupations = listOf(
                occ(activityType = "Lesson"),
                occ(activityType = "Borrow", activityName = "借用：社团", startTime = 1400),
            )),
        )
        val courses = CengKeParser.parseCourses(rooms)
        assertEquals(1, courses.size)
        assertEquals("高等数学", courses.single().courseName)
    }

    @Test
    fun `parseCourses dedups same course same room same start`() {
        // 同一节课在 occupations 里重复出现(多段登记),应折叠成一条
        val dup = occ()
        val rooms = listOf(room(occupations = listOf(dup, dup.copy(endTimeString = "09:40"))))
        assertEquals(1, CengKeParser.parseCourses(rooms).size)
    }

    @Test
    fun `parseCourses tolerates null occupations`() {
        // 无课教室:接口返回 roomOccupationInfoVms=null,Gson 绕过默认值留 null。
        // 不得因迭代 null 崩溃(Attempt to invoke interface method java.util.Iterator)。
        val rooms = listOf(
            room(id = 1, nameZh = "空教室", occupations = null),
            room(id = 2, nameZh = "A101", occupations = listOf(occ())),
        )
        val courses = CengKeParser.parseCourses(rooms)
        assertEquals(1, courses.size)
        assertEquals("A101", courses.single().roomName)
    }

    @Test
    fun `parseCourses keeps distinct rooms and times`() {
        val rooms = listOf(
            room(id = 1, nameZh = "A101", occupations = listOf(occ(startTime = 800, startTimeString = "08:00"))),
            room(id = 2, nameZh = "A102", occupations = listOf(occ(startTime = 1400, startTimeString = "14:00"))),
        )
        val courses = CengKeParser.parseCourses(rooms)
        assertEquals(2, courses.size)
        assertEquals(setOf("A101", "A102"), courses.map { it.roomName }.toSet())
    }

    @Test
    fun `parseCourses applies building name map`() {
        val rooms = listOf(room(buildingId = 10))
        val courses = CengKeParser.parseCourses(rooms, buildingNames = mapOf(10 to "博学北楼"))
        assertEquals("博学北楼", courses.single().buildingName)
    }

    @Test
    fun `parseCourses leaves building name null when unmapped`() {
        val courses = CengKeParser.parseCourses(listOf(room(buildingId = 99)), buildingNames = emptyMap())
        assertNull(courses.single().buildingName)
    }

    @Test
    fun `parseCourses skips records missing date or start time`() {
        val rooms = listOf(
            room(occupations = listOf(
                occ(date = null),
                occ(startTimeString = null, startTime = 1400),
                occ(startTime = 1600, startTimeString = "16:00"),
            )),
        )
        val courses = CengKeParser.parseCourses(rooms)
        assertEquals(1, courses.size)
        assertEquals("16:00", courses.single().startTimeString)
    }

    @Test
    fun `parseCourses copies metadata onto course`() {
        val course = CengKeParser.parseCourses(listOf(room())).single()
        assertEquals("张老师", course.teacher)
        assertEquals("磬苑校区", course.campusName)
        assertEquals(TimeSlot.MORNING, course.timeSlot)
        assertEquals("GG61015", course.courseCode)
    }
    // ── distinctColleges ───────────────────────────────────────
    @Test
    fun `distinctColleges orders by frequency then name`() {
        val pool = listOf(
            courseOf(college = "外语学院"),
            courseOf(college = "数学科学学院"),
            courseOf(college = "数学科学学院", start = 1400),
            courseOf(college = ""),
        )
        // 数学(2) 在前;外语(1) 在后;空白被过滤
        assertEquals(listOf("数学科学学院", "外语学院"), CengKeParser.distinctColleges(pool))
    }

    @Test
    fun `distinctColleges breaks ties by name ascending`() {
        val pool = listOf(courseOf(college = "乙学院"), courseOf(college = "甲学院"))
        val result = CengKeParser.distinctColleges(pool)
        // 频次相同(各 1),按名称升序
        assertEquals("乙学院", result[0])
        assertEquals("甲学院", result[1])
    }

    // ── filter ─────────────────────────────────────────────────
    @Test
    fun `filter with empty sets returns all`() {
        val pool = listOf(courseOf(), courseOf(start = 1400))
        assertEquals(2, CengKeParser.filter(pool).size)
    }

    @Test
    fun `filter by time slot`() {
        val pool = listOf(
            courseOf(start = 800),   // MORNING
            courseOf(start = 1400),  // AFTERNOON
            courseOf(start = 1900),  // EVENING
        )
        val afternoon = CengKeParser.filter(pool, slots = setOf(TimeSlot.AFTERNOON))
        assertEquals(1, afternoon.size)
        assertEquals(TimeSlot.AFTERNOON, afternoon.single().timeSlot)
    }

    @Test
    fun `filter by college`() {
        val pool = listOf(courseOf(college = "数学科学学院"), courseOf(college = "外语学院", start = 1400))
        val math = CengKeParser.filter(pool, colleges = setOf("数学科学学院"))
        assertEquals(1, math.size)
        assertEquals("数学科学学院", math.single().college)
    }

    @Test
    fun `filter combines slot and college with AND`() {
        val pool = listOf(
            courseOf(college = "数学科学学院", start = 800),
            courseOf(college = "数学科学学院", start = 1400),
            courseOf(college = "外语学院", start = 1400),
        )
        val result = CengKeParser.filter(
            pool,
            slots = setOf(TimeSlot.AFTERNOON),
            colleges = setOf("数学科学学院"),
        )
        assertEquals(1, result.size)
        assertEquals("数学科学学院", result.single().college)
        assertEquals(TimeSlot.AFTERNOON, result.single().timeSlot)
    }

    // ── pickRandom ─────────────────────────────────────────────
    @Test
    fun `pickRandom returns null on empty pool`() {
        assertNull(CengKeParser.pickRandom(emptyList()))
    }

    @Test
    fun `pickRandom returns an element from the pool`() {
        val pool = listOf(courseOf(start = 800), courseOf(start = 1400))
        val picked = CengKeParser.pickRandom(pool, random = Random(42))
        assertNotNull(picked)
        assertTrue(picked in pool)
    }

    @Test
    fun `pickRandom avoids the excluded course`() {
        val a = courseOf(start = 800)
        val b = courseOf(start = 1400)
        val pool = listOf(a, b)
        // 反复"换一个",排除 a 时永远拿到 b
        repeat(20) { seed ->
            val next = CengKeParser.pickRandom(pool, exclude = a, random = Random(seed.toLong()))
            assertEquals(b.dedupKey, next!!.dedupKey)
        }
    }

    @Test
    fun `pickRandom returns excluded when it is the only option`() {
        val only = courseOf()
        val picked = CengKeParser.pickRandom(listOf(only), exclude = only, random = Random(1))
        assertNotNull(picked)
        assertEquals(only.dedupKey, picked!!.dedupKey)
    }

    // 直接构造 CengCourse(避开 parse 链)。start 同时决定时段。
    private fun courseOf(
        college: String = "数学科学学院",
        start: Int = 800,
        room: String = "A101",
    ) = com.ahu_plus.data.model.jwapp.CengCourse(
        courseName = "课程$start",
        fullCode = "202620271-GG61015.087",
        courseCode = "GG61015",
        college = college,
        teacher = "张老师",
        roomName = room,
        buildingId = 10,
        buildingName = "博学北楼",
        campusName = "磬苑校区",
        date = "2026-07-23",
        startTimeString = "%02d:00".format(start / 100),
        endTimeString = "",
        startTime = start,
        timeSlot = CengKeParser.timeSlotOf(start),
    )

    // ── matchDetail(开课查询富化) ──────────────────────────────
    // 只填 matchDetail 关心的字段,其余 LessonRecord 字段给 null。
    private fun lesson(
        code: String? = "202620271-GG61015.087",
        nameZh: String? = "高等数学-1班",
        credits: Double? = 4.0,
        courseProperty: String? = "必修",
        courseType: String? = "理论课",
        examMode: String? = "考试",
        teachLang: String? = "中文",
        stdCount: Int? = 58,
        limitCount: Int? = 60,
    ) = LessonRecord(
        id = null,
        code = code,
        nameZh = nameZh,
        course = credits?.let { LessonCourse(code = "GG61015", nameZh = "高等数学", credits = it, id = null) },
        minorCourse = null,
        openDepartment = null,
        teacherAssignmentList = null,
        stdCount = stdCount,
        limitCount = limitCount,
        courseType = courseType?.let { LessonNamed(nameZh = it, id = null) },
        courseProperty = courseProperty?.let { LessonNamed(nameZh = it, id = null) },
        examMode = examMode?.let { LessonNamed(nameZh = it, id = null) },
        teachLang = teachLang?.let { LessonNamed(nameZh = it, id = null) },
        scheduleText = null,
        requiredPeriodInfo = null,
        remark = null,
    )

    @Test
    fun `matchDetail maps every field on exact code hit`() {
        val detail = CengKeParser.matchDetail(listOf(lesson()), "202620271-GG61015.087")
        assertNotNull(detail)
        assertEquals(58, detail!!.stdCount)
        assertEquals(60, detail.limitCount)
        assertEquals(4.0, detail.credits!!, 0.0)
        assertEquals("必修", detail.courseProperty)
        assertEquals("高等数学-1班", detail.className)
        assertEquals("理论课", detail.courseType)
        assertEquals("考试", detail.examMode)
        assertEquals("中文", detail.teachLang)
    }

    @Test
    fun `matchDetail picks the record whose code equals fullCode`() {
        val records = listOf(
            lesson(code = "202620271-GG61015.001", nameZh = "别的班", stdCount = 10),
            lesson(code = "202620271-GG61015.087", nameZh = "目标班", stdCount = 58),
            lesson(code = "202620271-XX99999.001", nameZh = "无关课", stdCount = 20),
        )
        val detail = CengKeParser.matchDetail(records, "202620271-GG61015.087")
        assertNotNull(detail)
        assertEquals("目标班", detail!!.className)
        assertEquals(58, detail.stdCount)
    }

    @Test
    fun `matchDetail returns null when no code matches`() {
        assertNull(CengKeParser.matchDetail(listOf(lesson()), "202620271-ZZ00000.999"))
    }

    @Test
    fun `matchDetail returns null on empty result list`() {
        assertNull(CengKeParser.matchDetail(emptyList(), "202620271-GG61015.087"))
    }

    @Test
    fun `matchDetail returns null when matched record carries nothing`() {
        // codeLike 命中但该教学班所有富化字段都空 → hasAny 为假 → 静默丢弃,卡片保持蹭课原样
        val blank = lesson(
            credits = null, courseProperty = null, courseType = null,
            examMode = null, teachLang = null, stdCount = null, limitCount = null,
            nameZh = null,
        )
        assertNull(CengKeParser.matchDetail(listOf(blank), "202620271-GG61015.087"))
    }

    @Test
    fun `matchDetail blanks out whitespace-only named fields`() {
        val detail = CengKeParser.matchDetail(
            listOf(lesson(courseProperty = "   ", courseType = "")),
            "202620271-GG61015.087",
        )
        assertNotNull(detail)
        assertNull(detail!!.courseProperty)
        assertNull(detail.courseType)
        // 其它字段仍在,detail 整体有效
        assertEquals(58, detail.stdCount)
    }

    @Test
    fun `matchDetail isFull reflects enrollment`() {
        val full = CengKeParser.matchDetail(
            listOf(lesson(stdCount = 60, limitCount = 60)),
            "202620271-GG61015.087",
        )
        assertTrue(full!!.isFull())
        val open = CengKeParser.matchDetail(
            listOf(lesson(stdCount = 30, limitCount = 60)),
            "202620271-GG61015.087",
        )
        assertFalse(open!!.isFull())
    }
}
