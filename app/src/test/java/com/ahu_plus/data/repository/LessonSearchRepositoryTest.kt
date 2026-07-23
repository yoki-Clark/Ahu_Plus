package com.ahu_plus.data.repository

import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.model.jw.LessonAdminClass
import com.ahu_plus.data.model.jw.LessonBuilding
import com.ahu_plus.data.model.jw.LessonCourseUnit
import com.ahu_plus.data.model.jw.LessonCourseUnitEnvelope
import com.ahu_plus.data.model.jw.LessonDepartment
import com.ahu_plus.data.model.jw.LessonFilterOption
import com.ahu_plus.data.model.jw.LessonInlineOptions
import com.ahu_plus.data.model.jw.LessonMajorNode
import com.ahu_plus.data.model.jw.LessonNamed
import com.ahu_plus.data.model.jw.LessonSearchFilter
import com.ahu_plus.data.model.jw.LessonSearchMode
import com.ahu_plus.data.model.jw.LessonSearchResponse
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全校开课查询单测（纯函数 + Gson 解析，无 IO / 无 Android）。
 *
 * 覆盖三处最易回归点：
 * 1. dataId 提取正则（Location 优先 / body 回退 / 取不到）
 * 2. 搜索 URL query 拼装（分页格式、关键字映射、浏览全部省略过滤）
 * 3. 响应 JSON 反序列化 + LessonRecord 派生方法（含 Gson 建对象绕过 Kotlin 默认值时的 null 集合防护）
 *
 * 样本为**脱敏虚构**数据（非真实学生/教师信息），仅用于验证字段映射。
 */
class LessonSearchRepositoryTest {

    private val gson = GsonProvider.instance

    // ── dataId 提取 ────────────────────────────────────────────

    @Test
    fun `extractDataId reads id from 302 Location`() {
        val loc = "https://jw.ahu.edu.cn/student/for-std/lesson-search/index/99166"
        assertEquals("99166", LessonSearchRepository.extractDataId(loc, null))
    }

    @Test
    fun `extractDataId falls back to body when Location absent`() {
        val body = """<a href="/student/for-std/lesson-search/index/12345?x=1">go</a>"""
        assertEquals("12345", LessonSearchRepository.extractDataId(null, body))
    }

    @Test
    fun `extractDataId prefers Location over body`() {
        val loc = "https://jw.ahu.edu.cn/student/for-std/lesson-search/index/777"
        val body = "lesson-search/index/888"
        assertEquals("777", LessonSearchRepository.extractDataId(loc, body))
    }

    @Test
    fun `extractDataId returns null when neither matches`() {
        assertNull(LessonSearchRepository.extractDataId("https://one.ahu.edu.cn/cas/login", "no id here"))
        assertNull(LessonSearchRepository.extractDataId(null, null))
    }

    // ── 搜索 URL 拼装 ──────────────────────────────────────────

    @Test
    fun `buildSearchUrl encodes page pagination and fixed params`() {
        val url = LessonSearchRepository.buildSearchUrl(
            semesterId = 132,
            dataId = "99166",
            mode = LessonSearchMode.NAME,
            keyword = "",
            page = 1,
            rowsPerPage = 30,
        )
        assertTrue(url.encodedPath.endsWith("/lesson-search/semester/132/search/99166"))
        assertEquals("1,30", url.queryParameter("queryPage__"))
        assertEquals("2", url.queryParameter("bizTypeAssoc"))
        // 浏览全部：不带任何 Like 过滤
        assertNull(url.queryParameter("nameZhLike"))
        assertNull(url.queryParameter("codeLike"))
        // assembleFields 存在且包含关键关联
        val assemble = url.queryParameter("assembleFields").orEmpty()
        assertTrue(assemble.contains("openDepartment"))
        assertTrue(assemble.contains("teacherAssignmentList"))
    }

    @Test
    fun `buildSearchUrl maps NAME keyword to nameZhLike`() {
        val url = LessonSearchRepository.buildSearchUrl(
            132, "99166", LessonSearchMode.NAME, "统计", 2, 30,
        )
        assertEquals("统计", url.queryParameter("nameZhLike"))
        assertNull(url.queryParameter("codeLike"))
        assertEquals("2,30", url.queryParameter("queryPage__"))
    }

    @Test
    fun `buildSearchUrl maps CODE keyword to codeLike and trims`() {
        val url = LessonSearchRepository.buildSearchUrl(
            132, "99166", LessonSearchMode.CODE, "  GG17008 ", 1, 50,
        )
        assertEquals("GG17008", url.queryParameter("codeLike"))
        assertNull(url.queryParameter("nameZhLike"))
    }

    @Test
    fun `LessonSearchMode param names match verified filters`() {
        assertEquals("nameZhLike", LessonSearchMode.NAME.paramName)
        assertEquals("codeLike", LessonSearchMode.CODE.paramName)
    }

    @Test
    fun `buildSearchUrl omits openDepartmentAssocs when no departments`() {
        val url = LessonSearchRepository.buildSearchUrl(
            132, "99166", LessonSearchMode.NAME, "", 1, 30,
        )
        assertTrue(url.queryParameterValues("openDepartmentAssocs").isEmpty())
    }

    @Test
    fun `buildSearchUrl adds one openDepartmentAssocs per department id`() {
        val url = LessonSearchRepository.buildSearchUrl(
            semesterId = 132,
            dataId = "99166",
            mode = LessonSearchMode.NAME,
            keyword = "",
            page = 1,
            rowsPerPage = 30,
            departmentIds = listOf(2L, 22L, 76L),
        )
        // 多值：每个学院 id 一个同名 query（HAR 实测 openDepartmentAssocs=2&openDepartmentAssocs=22）
        assertEquals(listOf("2", "22", "76"), url.queryParameterValues("openDepartmentAssocs"))
        // 与固定口径共存
        assertEquals("2", url.queryParameter("bizTypeAssoc"))
    }

    @Test
    fun `buildSearchUrl dedupes department ids and coexists with keyword`() {
        val url = LessonSearchRepository.buildSearchUrl(
            132, "99166", LessonSearchMode.CODE, " GG ", 2, 50, listOf(7L, 7L, 2L),
        )
        assertEquals(listOf("7", "2"), url.queryParameterValues("openDepartmentAssocs"))
        assertEquals("GG", url.queryParameter("codeLike"))
        assertEquals("2,50", url.queryParameter("queryPage__"))
    }

    // ── 学院列表解析 ───────────────────────────────────────────

    @Test
    fun `parse departments top-level array maps id and name`() {
        // getAllByIsOpenCourse 返回顶层数组（虚构脱敏样本）
        val json = """
            [
              {"id":2,"nameZh":"数学科学学院"},
              {"id":7,"nameZh":"计算机科学与技术学院"},
              {"id":76,"nameZh":"材料科学与工程学院"}
            ]
        """.trimIndent()
        val type = object : TypeToken<List<LessonDepartment>>() {}.type
        val list: List<LessonDepartment> = gson.fromJson(json, type)
        assertEquals(3, list.size)
        assertEquals(7L, list[1].id)
        assertEquals("计算机科学与技术学院", list[1].nameZh)
        assertTrue(list.all { it.isUsable() })
    }

    @Test
    fun `department isUsable filters incomplete entries`() {
        val json = """[{"id":2,"nameZh":"数学科学学院"},{"id":null,"nameZh":"坏"},{"id":9,"nameZh":""}]"""
        val type = object : TypeToken<List<LessonDepartment>>() {}.type
        val list: List<LessonDepartment> = gson.fromJson(json, type)
        assertEquals(1, list.count { it.isUsable() })
    }

    // ── 响应解析 ───────────────────────────────────────────────

    @Test
    fun `parse full response maps records page and helpers`() {
        val json = """
            {
              "data": [
                {
                  "id": 217507,
                  "code": "202620271-GG17008.010",
                  "nameZh": "2024级示例班",
                  "course": {"code":"GG17008","credits":1,"nameZh":"职业规划","id":24173},
                  "minorCourse": {"nameZh":"职业规划（五）"},
                  "openDepartment": {"nameZh":"示例学院"},
                  "teacherAssignmentList": [
                    {"role":"MAJOR","person":{"nameZh":"张老师"}},
                    {"role":"HELPER","person":{"nameZh":"李老师"}}
                  ],
                  "stdCount": 51,
                  "limitCount": 51,
                  "courseType": {"nameZh":"实践课"},
                  "courseProperty": {"nameZh":"必修"},
                  "examMode": {"nameZh":"考查"},
                  "teachLang": {"nameZh":"中文"},
                  "scheduleText": {
                    "dateTimePlacePersonText": {"textZh":"11~12周 星期二 6~7节 磬苑校区 博学南楼A102 张老师"},
                    "roomSeatText": {"textZh":"博学南楼A102(124)"}
                  },
                  "requiredPeriodInfo": {"total":7,"weeks":7,"periodsPerWeek":1}
                }
              ],
              "_page_": {"currentPage":1,"rowsInPage":100,"rowsPerPage":100,"totalRows":5170,"totalPages":52}
            }
        """.trimIndent()

        val resp = gson.fromJson(json, LessonSearchResponse::class.java)
        assertEquals(1, resp.data?.size)
        assertEquals(1, resp.page?.currentPage)
        assertEquals(5170, resp.page?.totalRows)
        assertEquals(52, resp.page?.totalPages)

        val r = resp.data!!.first()
        assertEquals("职业规划", r.courseName())
        assertEquals("GG17008", r.course?.code)
        assertEquals(1.0, r.course?.credits ?: 0.0, 0.0001)
        assertEquals("示例学院", r.openDepartment?.nameZh)
        assertEquals("张老师、李老师", r.teacherNames())
        assertTrue(r.isFull()) // 51/51
        assertEquals("必修", r.courseProperty?.nameZh)
        assertEquals("考查", r.examMode?.nameZh)
        assertEquals("中文", r.teachLang?.nameZh)
        assertTrue(r.scheduleZh().contains("博学南楼A102"))
        assertEquals(7, r.requiredPeriodInfo?.total)
    }

    @Test
    fun `isFull is false when not at limit or counts missing`() {
        val json = """
            {"data":[
              {"id":1,"nameZh":"a","stdCount":10,"limitCount":40},
              {"id":2,"nameZh":"b","stdCount":40,"limitCount":40},
              {"id":3,"nameZh":"c"}
            ]}
        """.trimIndent()
        val resp = gson.fromJson(json, LessonSearchResponse::class.java)
        val list = resp.data!!
        assertFalse(list[0].isFull()) // 10/40
        assertTrue(list[1].isFull())  // 40/40
        assertFalse(list[2].isFull()) // 无人数信息
    }

    @Test
    fun `record with missing teacher list does not crash and returns empty`() {
        // Gson 用 Unsafe 建对象不调 Kotlin 构造器,非空 List 会是 null;teacherNames 必须 .orEmpty() 防护
        val json = """{"data":[{"id":9,"nameZh":"无教师班","course":{"nameZh":"某课"}}]}"""
        val resp = gson.fromJson(json, LessonSearchResponse::class.java)
        val r = resp.data!!.first()
        assertEquals("", r.teacherNames())
        assertEquals("某课", r.courseName())
        assertEquals("", r.scheduleZh())
        assertFalse(r.isFull())
    }

    @Test
    fun `empty data array parses to empty list`() {
        val json = """{"data":[],"_page_":{"currentPage":1,"totalRows":0,"totalPages":0}}"""
        val resp = gson.fromJson(json, LessonSearchResponse::class.java)
        assertEquals(0, resp.data?.size)
        assertEquals(0, resp.page?.totalRows)
    }

    // ── 全量筛选 buildSearchUrl(filter) ────────────────────────

    @Test
    fun `buildSearchUrl filter emits all single-value inferred params`() {
        val filter = LessonSearchFilter(
            semesterId = 132,
            courseTypeId = 1L,
            campusId = 2L,
            compulsory = "COMPULSORY",
            examModeId = 2L,
            teachLangId = 21L,
            buildingId = 55L,
            roomNameLike = " 博北 ",
            creditsGte = 2.0,
            creditsLte = 4.5,
            adminClassId = 9001L,
        )
        val url = LessonSearchRepository.buildSearchUrl(filter, "99166", 1, 30)
        assertEquals("1", url.queryParameter("courseTypeAssoc"))
        assertEquals("2", url.queryParameter("campusAssoc"))
        assertEquals("COMPULSORY", url.queryParameter("compulsory"))
        assertEquals("2", url.queryParameter("examModeAssoc"))
        assertEquals("21", url.queryParameter("teachLangAssoc"))
        assertEquals("55", url.queryParameter("buildingAssoc"))
        assertEquals("博北", url.queryParameter("roomNameLike")) // 去空白
        assertEquals("2", url.queryParameter("creditsGte"))      // 2.0 → "2"
        assertEquals("4.5", url.queryParameter("creditsLte"))
        assertEquals("9001", url.queryParameter("adminClassAssoc"))
    }

    @Test
    fun `buildSearchUrl filter emits multi-value params one per id`() {
        val filter = LessonSearchFilter(
            semesterId = 132,
            departmentIds = listOf(2L, 7L),
            courseUnitIndexes = listOf(1, 2),
            grades = listOf("2024", "2023"),
            majorDeptIds = listOf(31L),
            majorIds = listOf(500L, 501L),
        )
        val url = LessonSearchRepository.buildSearchUrl(filter, "99166", 1, 30)
        assertEquals(listOf("2", "7"), url.queryParameterValues("openDepartmentAssocs"))
        assertEquals(listOf("1", "2"), url.queryParameterValues("courseIndexs"))
        assertEquals(listOf("2024", "2023"), url.queryParameterValues("grades"))
        assertEquals(listOf("31"), url.queryParameterValues("departmentAssocs"))
        assertEquals(listOf("500", "501"), url.queryParameterValues("majorAssoc"))
    }

    @Test
    fun `buildSearchUrl filter converts ISO weekday to server encoding`() {
        // ISO 周一=1..周日=7 → 服务端 周日=1、周一=2..周六=7
        val filter = LessonSearchFilter(
            semesterId = 132,
            weekdays = listOf(1, 7), // 周一, 周日
        )
        val url = LessonSearchRepository.buildSearchUrl(filter, "99166", 1, 30)
        // 周一(1)→(1%7)+1=2 ; 周日(7)→(7%7)+1=1
        assertEquals(listOf("2", "1"), url.queryParameterValues("weekIndexs"))
    }

    @Test
    fun `buildSearchUrl empty filter emits only fixed params plus keyword absent`() {
        val filter = LessonSearchFilter(semesterId = 132)
        val url = LessonSearchRepository.buildSearchUrl(filter, "99166", 1, 30)
        assertEquals("2", url.queryParameter("bizTypeAssoc"))
        assertEquals("1,30", url.queryParameter("queryPage__"))
        assertNull(url.queryParameter("courseTypeAssoc"))
        assertNull(url.queryParameter("adminClassAssoc"))
        assertNull(url.queryParameter("nameZhLike"))
        assertTrue(url.queryParameterValues("openDepartmentAssocs").isEmpty())
        assertTrue(url.queryParameterValues("grades").isEmpty())
    }

    @Test
    fun `buildSearchUrl filter carries keyword by mode`() {
        val nameUrl = LessonSearchRepository.buildSearchUrl(
            LessonSearchFilter(semesterId = 132, mode = LessonSearchMode.NAME, keyword = " 高数 "),
            "99166", 1, 30,
        )
        assertEquals("高数", nameUrl.queryParameter("nameZhLike"))
        val codeUrl = LessonSearchRepository.buildSearchUrl(
            LessonSearchFilter(semesterId = 132, mode = LessonSearchMode.CODE, keyword = "MATH01"),
            "99166", 1, 30,
        )
        assertEquals("MATH01", codeUrl.queryParameter("codeLike"))
    }

    // ── LessonSearchFilter 派生 ────────────────────────────────

    @Test
    fun `serverWeekday maps ISO to server domain`() {
        assertEquals(2, LessonSearchFilter.serverWeekday(1)) // 周一
        assertEquals(3, LessonSearchFilter.serverWeekday(2))
        assertEquals(7, LessonSearchFilter.serverWeekday(6)) // 周六
        assertEquals(1, LessonSearchFilter.serverWeekday(7)) // 周日
    }

    @Test
    fun `activeCount ignores keyword mode semester but counts filter dims`() {
        val base = LessonSearchFilter(semesterId = 132, mode = LessonSearchMode.CODE, keyword = "x")
        assertEquals(0, base.activeCount)
        val filtered = base.copy(
            departmentIds = listOf(2L),
            campusId = 1L,
            grades = listOf("2024"),
            adminClassId = 9L,
        )
        assertEquals(4, filtered.activeCount)
    }

    @Test
    fun `isSingleAdminClass only when adminClassId set`() {
        assertFalse(LessonSearchFilter(semesterId = 132).isSingleAdminClass)
        assertTrue(LessonSearchFilter(semesterId = 132, adminClassId = 1L).isSingleAdminClass)
    }

    // ── 级联选项模型解析 ───────────────────────────────────────

    @Test
    fun `major node strips code prefix in displayName`() {
        val node = LessonMajorNode(id = 31L, nameZh = "31：数学科学学院")
        assertEquals("数学科学学院", node.displayName)
        val opt = node.toOption()
        assertEquals(31L, opt?.id)
        assertEquals("数学科学学院", opt?.nameZh)
    }

    @Test
    fun `major node without colon keeps whole name`() {
        val node = LessonMajorNode(id = 5L, nameZh = "计算机学院")
        assertEquals("计算机学院", node.displayName)
    }

    @Test
    fun `major node null id yields null option`() {
        assertNull(LessonMajorNode(id = null, nameZh = "x").toOption())
    }

    @Test
    fun `admin class toOption prefers nameZh falls back to code`() {
        assertEquals("2024级数学1班", LessonAdminClass(1L, "2024级数学1班", "MATH2401", "2024").toOption()?.nameZh)
        assertEquals("MATH2401", LessonAdminClass(1L, "", "MATH2401", "2024").toOption()?.nameZh)
        assertNull(LessonAdminClass(null, "x", "y", "2024").toOption())
    }

    @Test
    fun `building toOption maps id and name`() {
        val opt = LessonBuilding(id = 55L, nameZh = "博学楼", code = "BX").toOption()
        assertEquals(55L, opt?.id)
        assertEquals("博学楼", opt?.nameZh)
    }

    @Test
    fun `course unit envelope parses wrapped data shape`() {
        val wrapped = gson.fromJson(
            """{"data":[{"nameZh":"1","indexNo":1},{"nameZh":"2","indexNo":2}]}""",
            LessonCourseUnitEnvelope::class.java,
        )
        assertEquals(2, wrapped.data?.size)
        assertEquals(1, wrapped.data?.first()?.indexNo)
    }

    @Test
    fun `course unit top-level array shape parses via TypeToken`() {
        // 与仓库 getCourseUnits 的顶层数组分支一致
        val type = object : TypeToken<List<LessonCourseUnit>>() {}.type
        val units: List<LessonCourseUnit> = gson.fromJson(
            """[{"nameZh":"1","indexNo":1},{"nameZh":"2","indexNo":2},{"nameZh":"x","indexNo":null}]""",
            type,
        )
        assertEquals(3, units.size)
        // 仓库随后会 filter { indexNo != null }
        assertEquals(2, units.count { it.indexNo != null })
    }

    @Test
    fun `LessonFilterOption of requires id and name`() {
        assertEquals(7L, LessonFilterOption.of(LessonNamed(nameZh = "计院", id = 7L))?.id)
        assertNull(LessonFilterOption.of(LessonNamed(nameZh = "无id", id = null)))
        assertNull(LessonFilterOption.of(LessonNamed(nameZh = "", id = 3L)))
        assertNull(LessonFilterOption.of(null))
    }

    @Test
    fun `inline options expose expected fixed enums`() {
        assertTrue(LessonInlineOptions.COURSE_TYPES.any { it.nameZh == "理论课" })
        assertTrue(LessonInlineOptions.CAMPUSES.any { it.nameZh == "磬苑校区" })
        assertEquals(2, LessonInlineOptions.COMPULSORY.size)
        // grades：当前年往前 8 届共 9 个，且降序
        val grades = LessonInlineOptions.grades(2026)
        assertEquals("2026", grades.first())
        assertEquals("2018", grades.last())
        assertEquals(9, grades.size)
    }
}
