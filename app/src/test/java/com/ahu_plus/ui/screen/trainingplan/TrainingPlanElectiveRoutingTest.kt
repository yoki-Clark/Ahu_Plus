package com.ahu_plus.ui.screen.trainingplan

import com.ahu_plus.data.model.jw.CompletionCourse
import com.ahu_plus.data.model.jw.PlanCourse
import com.ahu_plus.data.model.jw.PlanCourseInfo
import com.ahu_plus.data.model.jw.PlanEnumValue
import com.ahu_plus.data.model.jw.PlanModuleNode
import com.ahu_plus.data.model.jw.PlanRequireInfo
import com.ahu_plus.data.model.jw.PlanTypeInfo
import com.ahu_plus.data.model.jw.ResultTypeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证通识选修未匹配课程按 typeId 精确归属到子分类模块的路由逻辑。
 *
 * Bug 背景：旧实现用名称启发式找到唯一「通识选修」主模块，把所有未匹配课程堆在其下；
 * 修复后优先用 CompletionCourse.typeId（来自 completion preview HTML 模块的 typeId）
 * 路由到对应子分类（按 `typeId ↔ PlanModuleNode.type.id` 匹配），仅无法匹配的才回退到吸收桶。
 *
 * 注意：module.id 与 type.id 是不同字段。测试树刻意用不同的值（module.id=100, type.id=1000）
 * 来验证匹配确实走 type.id 而非 module.id。
 */
class TrainingPlanElectiveRoutingTest {

    // ── 测试用模块树 ──────────────────────────────────────────────
    // 通识选修 (module.id=100, type.id=1000)
    //   ├── 人文社科类 (module.id=101, type.id=1001)
    //   ├── 自然科学类 (module.id=102, type.id=1002)
    //   └── 艺术审美类 (module.id=103, type.id=1003)
    // 专业必修 (module.id=200, type.id=2000)

    private fun buildModuleTree(): List<PlanModuleNode> = listOf(
        PlanModuleNode(
            id = 100,
            type = PlanTypeInfo(nameZh = "通识选修", id = 1000),
            requireInfo = PlanRequireInfo(requiredCredits = 10.0),
            children = listOf(
                PlanModuleNode(id = 101, type = PlanTypeInfo(nameZh = "人文社科类", id = 1001)),
                PlanModuleNode(id = 102, type = PlanTypeInfo(nameZh = "自然科学类", id = 1002)),
                PlanModuleNode(id = 103, type = PlanTypeInfo(nameZh = "艺术审美类", id = 1003))
            )
        ),
        PlanModuleNode(
            id = 200,
            type = PlanTypeInfo(nameZh = "专业必修", id = 2000),
            requireInfo = PlanRequireInfo(requiredCredits = 40.0)
        )
    )

    private fun passedCourse(
        code: String,
        typeId: Int? = null,
        credits: Double = 2.0,
        moduleName: String? = null
    ) = CompletionCourse(
        code = code,
        nameZh = "课程_$code",
        credits = credits,
        finalResultType = ResultTypeEntry(name = "PASSED")
    ).apply {
        this.typeId = typeId
        this.moduleName = moduleName
    }

    // ── groupUnmatchedByModule 测试 ─────────────────────────────

    @Test
    fun groupByModule_emptyCourses_returnsEmptyMap() {
        val tree = buildModuleTree()
        val result = groupUnmatchedByModule(tree, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun groupByModule_courseMatchingChildType_routedToChild() {
        val tree = buildModuleTree()
        // 课程 typeId=1001 → 人文社科类 (type.id=1001)
        val courses = listOf(passedCourse("TX001", typeId = 1001))

        val result = groupUnmatchedByModule(tree, courses)

        assertEquals(1, result.size)
        assertEquals(listOf("TX001"), result[1001]?.map { it.code })
        assertNull(result[FALLBACK_KEY])
    }

    @Test
    fun groupByModule_courseMatchingTopLevelType_routedToTopLevel() {
        val tree = buildModuleTree()
        // 课程 typeId=1000 → 通识选修 (type.id=1000)
        val courses = listOf(passedCourse("TX001", typeId = 1000))

        val result = groupUnmatchedByModule(tree, courses)

        assertEquals(listOf("TX001"), result[1000]?.map { it.code })
        assertNull(result[FALLBACK_KEY])
    }

    @Test
    fun groupByModule_nullTypeId_goesToFallback() {
        val tree = buildModuleTree()
        val courses = listOf(passedCourse("TX001", typeId = null))

        val result = groupUnmatchedByModule(tree, courses)

        assertNull(result[1001])
        assertEquals(listOf("TX001"), result[FALLBACK_KEY]?.map { it.code })
    }

    @Test
    fun groupByModule_unmatchedTypeId_goesToFallback() {
        val tree = buildModuleTree()
        // typeId=9999 不在树中
        val courses = listOf(passedCourse("TX001", typeId = 9999))

        val result = groupUnmatchedByModule(tree, courses)

        assertEquals(listOf("TX001"), result[FALLBACK_KEY]?.map { it.code })
    }

    @Test
    fun groupByModule_doesNotMatchByModuleId_onlyByTypeId() {
        // 关键回归测试：typeId 匹配走 type.id，不走 module.id
        // 课程 typeId=101（等于人文社科类的 module.id 但不等于任何 type.id）→ 应回退
        val tree = buildModuleTree()
        val courses = listOf(passedCourse("TX001", typeId = 101))

        val result = groupUnmatchedByModule(tree, courses)

        // 101 是 module.id 不是 type.id，不应命中
        assertNull(result[101])
        assertEquals(listOf("TX001"), result[FALLBACK_KEY]?.map { it.code })
    }

    @Test
    fun groupByModule_multipleCoursesDifferentModules_groupedCorrectly() {
        val tree = buildModuleTree()
        val courses = listOf(
            passedCourse("TX001", typeId = 1001),  // 人文社科类
            passedCourse("TX002", typeId = 1002),  // 自然科学类
            passedCourse("TX003", typeId = 1003),  // 艺术审美类
            passedCourse("TX004", typeId = null),  // 回退
            passedCourse("TX005", typeId = 9999)   // 未命中 → 回退
        )

        val result = groupUnmatchedByModule(tree, courses)

        assertEquals(listOf("TX001"), result[1001]?.map { it.code })
        assertEquals(listOf("TX002"), result[1002]?.map { it.code })
        assertEquals(listOf("TX003"), result[1003]?.map { it.code })
        assertEquals(setOf("TX004", "TX005"), result[FALLBACK_KEY]?.map { it.code }?.toSet())
    }

    @Test
    fun groupByModule_multipleCoursesSameModule_groupedTogether() {
        val tree = buildModuleTree()
        val courses = listOf(
            passedCourse("TX001", typeId = 1001),
            passedCourse("TX002", typeId = 1001),
            passedCourse("TX003", typeId = 1001)
        )

        val result = groupUnmatchedByModule(tree, courses)

        assertEquals(3, result[1001]?.size)
        assertEquals(listOf("TX001", "TX002", "TX003"), result[1001]?.map { it.code })
    }

    // ── findFlexAbsorberId 测试 ─────────────────────────────────

    @Test
    fun flexAbsorber_findsGeneralElective() {
        val tree = buildModuleTree()
        val absorberId = findFlexAbsorberId(tree)
        // 应该命中「通识选修」(idOrHash = module.id = 100)
        assertEquals(100, absorberId)
    }

    @Test
    fun flexAbsorber_returnsNullWhenNoElective() {
        val tree = listOf(
            PlanModuleNode(id = 200, type = PlanTypeInfo(nameZh = "专业必修", id = 2000))
        )
        assertNull(findFlexAbsorberId(tree))
    }

    // ── buildPlanRows 端到端测试 ────────────────────────────────

    @Test
    fun buildRows_courseWithSubcategoryTypeId_placedUnderSubcategory() {
        val tree = buildModuleTree()
        val courses = listOf(passedCourse("TX001", typeId = 1001)) // 人文社科类

        // 展开通识选修(100)及其所有子模块
        val expanded = setOf(100, 101, 102, 103)
        val rows = buildPlanRows(tree, expanded, courses)

        // 找到「人文社科类」模块行
        val humanitiesRow = rows.filterIsInstance<PlanRow.Module>().first { it.module.displayName == "人文社科类" }
        assertEquals(1, humanitiesRow.moduleUnmatchedCourses.size)
        assertEquals("TX001", humanitiesRow.moduleUnmatchedCourses.first().code)

        // 「通识选修」主模块不应该有精确归属的课程（仅可能有回退课程）
        val generalRow = rows.filterIsInstance<PlanRow.Module>().first { it.module.displayName == "通识选修" }
        assertTrue("通识选修主栏目不应包含精确归属的子分类课程",
            generalRow.moduleUnmatchedCourses.none { it.code == "TX001" })
    }

    @Test
    fun buildRows_courseWithoutTypeId_placedUnderFlexAbsorber() {
        val tree = buildModuleTree()
        val courses = listOf(passedCourse("TX001", typeId = null))

        val expanded = setOf(100, 101, 102, 103)
        val rows = buildPlanRows(tree, expanded, courses)

        // 通识选修主模块应包含回退课程
        val generalRow = rows.filterIsInstance<PlanRow.Module>().first { it.module.displayName == "通识选修" }
        assertEquals(1, generalRow.moduleUnmatchedCourses.size)
        assertEquals("TX001", generalRow.moduleUnmatchedCourses.first().code)

        // 子分类模块不应有此课程
        val childRows = rows.filterIsInstance<PlanRow.Module>()
            .filter { it.module.displayName in listOf("人文社科类", "自然科学类", "艺术审美类") }
        assertTrue(childRows.all { it.moduleUnmatchedCourses.isEmpty() })
    }

    @Test
    fun buildRows_mixedCourses_splitCorrectly() {
        val tree = buildModuleTree()
        val courses = listOf(
            passedCourse("TX001", typeId = 1001),  // → 人文社科类
            passedCourse("TX002", typeId = 1002),  // → 自然科学类
            passedCourse("TX003", typeId = null),  // → 通识选修(回退)
            passedCourse("TX004", typeId = 9999)   // → 通识选修(回退,未命中)
        )

        val expanded = setOf(100, 101, 102, 103)
        val rows = buildPlanRows(tree, expanded, courses)

        val moduleRows = rows.filterIsInstance<PlanRow.Module>().associateBy { it.module.displayName }

        // 人文社科类只有 TX001
        assertEquals(setOf("TX001"), moduleRows["人文社科类"]!!.moduleUnmatchedCourses.map { it.code }.toSet())
        // 自然科学类只有 TX002
        assertEquals(setOf("TX002"), moduleRows["自然科学类"]!!.moduleUnmatchedCourses.map { it.code }.toSet())
        // 艺术审美类为空
        assertTrue(moduleRows["艺术审美类"]!!.moduleUnmatchedCourses.isEmpty())
        // 通识选修(回退)有 TX003, TX004
        assertEquals(setOf("TX003", "TX004"), moduleRows["通识选修"]!!.moduleUnmatchedCourses.map { it.code }.toSet())
    }

    @Test
    fun buildRows_mainElectiveModuleDoesNotShowSubcategoryCourses() {
        val tree = buildModuleTree()
        // 所有课程都有明确的子分类 typeId，无回退课程
        val courses = listOf(
            passedCourse("TX001", typeId = 1001),
            passedCourse("TX002", typeId = 1002),
            passedCourse("TX003", typeId = 1003)
        )

        val expanded = setOf(100, 101, 102, 103)
        val rows = buildPlanRows(tree, expanded, courses)

        val generalRow = rows.filterIsInstance<PlanRow.Module>().first { it.module.displayName == "通识选修" }
        // 主栏目不应直接显示任何课程（所有课程都已归属到子分类）
        assertTrue("通识选修主栏目应仅显示子分类，不应直接显示课程",
            generalRow.moduleUnmatchedCourses.isEmpty())
    }

    @Test
    fun buildRows_expandedModuleShowsUnmatchedCourseRows() {
        val tree = buildModuleTree()
        val courses = listOf(passedCourse("TX001", typeId = 1001))

        val expanded = setOf(100, 101)
        val rows = buildPlanRows(tree, expanded, courses)

        // 验证「人文社科类」展开后有 SectionLabel + Course 行
        val humanitiesIdx = rows.indexOfFirst {
            it is PlanRow.Module && it.module.displayName == "人文社科类"
        }
        assertTrue("应找到人文社科类模块行", humanitiesIdx >= 0)

        // 紧随模块行之后应有 SectionLabel("已选课程") 和 Course 行
        val afterModule = rows.drop(humanitiesIdx + 1)
        val sectionLabel = afterModule.filterIsInstance<PlanRow.SectionLabel>().firstOrNull()
        assertEquals("已选课程", sectionLabel?.text)

        val courseRow = afterModule.filterIsInstance<PlanRow.Course>().firstOrNull()
        assertEquals("TX001", courseRow?.course?.displayCode)
    }

    @Test
    fun buildRows_collapsedModuleDoesNotShowUnmatchedRows() {
        val tree = buildModuleTree()
        val courses = listOf(passedCourse("TX001", typeId = 1001))

        // 不展开任何模块
        val expanded = emptySet<Int>()
        val rows = buildPlanRows(tree, expanded, courses)

        // 应只有模块表头行，无 SectionLabel 或 Course 行
        val sectionLabels = rows.filterIsInstance<PlanRow.SectionLabel>()
        assertTrue(sectionLabels.isEmpty())
        val courseRows = rows.filterIsInstance<PlanRow.Course>()
        assertTrue(courseRows.isEmpty())
    }

    @Test
    fun buildRows_emptyUnmatched_noModuleHasUnmatchedCourses() {
        val tree = buildModuleTree()
        val expanded = setOf(100, 101, 102, 103)
        val rows = buildPlanRows(tree, expanded, emptyList())

        val moduleRows = rows.filterIsInstance<PlanRow.Module>()
        assertTrue(moduleRows.all { it.moduleUnmatchedCourses.isEmpty() })
    }

    // ── moduleName 回退匹配测试 ──────────────────────────────────

    @Test
    fun groupByModule_nullTypeIdButMatchingModuleName_routedToModule() {
        val tree = buildModuleTree()
        // typeId 为空，但 moduleName 匹配「人文社科类」
        val courses = listOf(passedCourse("TX001", typeId = null, moduleName = "人文社科类"))

        val result = groupUnmatchedByModule(tree, courses)

        // 应通过 moduleName 回退匹配到 type.id=1001
        assertEquals(listOf("TX001"), result[1001]?.map { it.code })
        assertNull(result[FALLBACK_KEY])
    }

    @Test
    fun groupByModule_unmatchedTypeIdButMatchingModuleName_routedToModule() {
        val tree = buildModuleTree()
        // typeId=9999 不在树中，但 moduleName 匹配「艺术审美类」
        val courses = listOf(passedCourse("TX001", typeId = 9999, moduleName = "艺术审美类"))

        val result = groupUnmatchedByModule(tree, courses)

        assertEquals(listOf("TX001"), result[1003]?.map { it.code })
        assertNull(result[FALLBACK_KEY])
    }

    @Test
    fun groupByModule_bothTypeIdAndModuleNameNull_goesToFallback() {
        val tree = buildModuleTree()
        val courses = listOf(passedCourse("TX001", typeId = null, moduleName = null))

        val result = groupUnmatchedByModule(tree, courses)

        assertEquals(listOf("TX001"), result[FALLBACK_KEY]?.map { it.code })
    }

    @Test
    fun groupByModule_bothTypeIdAndModuleNameUnmatched_goesToFallback() {
        val tree = buildModuleTree()
        // typeId 和 moduleName 都不在树中
        val courses = listOf(passedCourse("TX001", typeId = 9999, moduleName = "不存在的模块"))

        val result = groupUnmatchedByModule(tree, courses)

        assertEquals(listOf("TX001"), result[FALLBACK_KEY]?.map { it.code })
    }

    @Test
    fun groupByModule_typeIdPreferredOverModuleName() {
        val tree = buildModuleTree()
        // typeId 指向人文社科类(1001)，但 moduleName 指向艺术审美类
        // 应优先 typeId 匹配
        val courses = listOf(passedCourse("TX001", typeId = 1001, moduleName = "艺术审美类"))

        val result = groupUnmatchedByModule(tree, courses)

        assertEquals(listOf("TX001"), result[1001]?.map { it.code })
        assertNull(result[1003])
    }

    @Test
    fun groupByModule_mixedTypeIdAndModuleNameMatching_allRouted() {
        val tree = buildModuleTree()
        val courses = listOf(
            passedCourse("TX001", typeId = 1001),                          // typeId → 人文社科类
            passedCourse("TX002", typeId = null, moduleName = "自然科学类"), // moduleName → 自然科学类
            passedCourse("TX003", typeId = null, moduleName = null),       // 回退
            passedCourse("TX004", typeId = 9999, moduleName = "艺术审美类")  // typeId 未命中, moduleName → 艺术审美类
        )

        val result = groupUnmatchedByModule(tree, courses)

        assertEquals(listOf("TX001"), result[1001]?.map { it.code })
        assertEquals(listOf("TX002"), result[1002]?.map { it.code })
        assertEquals(listOf("TX004"), result[1003]?.map { it.code })
        assertEquals(listOf("TX003"), result[FALLBACK_KEY]?.map { it.code })
    }

    @Test
    fun buildRows_moduleNameFallback_routedToCorrectSubcategory() {
        val tree = buildModuleTree()
        // typeId 为空，靠 moduleName 匹配到人文社科类
        val courses = listOf(passedCourse("TX001", typeId = null, moduleName = "人文社科类"))

        val expanded = setOf(100, 101)
        val rows = buildPlanRows(tree, expanded, courses)

        val humanitiesRow = rows.filterIsInstance<PlanRow.Module>().first { it.module.displayName == "人文社科类" }
        assertEquals(1, humanitiesRow.moduleUnmatchedCourses.size)
        assertEquals("TX001", humanitiesRow.moduleUnmatchedCourses.first().code)

        val generalRow = rows.filterIsInstance<PlanRow.Module>().first { it.module.displayName == "通识选修" }
        assertTrue("通识选修主栏目不应包含 moduleName 回退匹配的课程",
            generalRow.moduleUnmatchedCourses.none { it.code == "TX001" })
    }

    // ── subtreeExtraPassed 进度条修复测试 ──────────────────────────

    @Test
    fun buildRows_parentModuleSubtreeExtraPassed_includesChildCredits() {
        val tree = buildModuleTree()
        // 3 门已通过课程，分别归属到 3 个子分类
        val courses = listOf(
            passedCourse("TX001", typeId = 1001, credits = 2.0),  // 人文社科类
            passedCourse("TX002", typeId = 1002, credits = 3.0),  // 自然科学类
            passedCourse("TX003", typeId = 1003, credits = 1.5)   // 艺术审美类
        )

        val expanded = setOf(100, 101, 102, 103)
        val rows = buildPlanRows(tree, expanded, courses)

        // 父模块「通识选修」自身的 moduleUnmatchedCourses 应为空（课程都在子分类）
        val generalRow = rows.filterIsInstance<PlanRow.Module>().first { it.module.displayName == "通识选修" }
        assertTrue("通识选修主栏目不应直接包含子分类课程",
            generalRow.moduleUnmatchedCourses.isEmpty())

        // 但 subtreeExtraPassed 应包含所有子分类的已通过学分: 2.0 + 3.0 + 1.5 = 6.5
        assertEquals(6.5, generalRow.subtreeExtraPassed, 0.001)
    }

    @Test
    fun buildRows_parentModuleSubtreeExtraPassed_emptyWhenNoChildCourses() {
        val tree = buildModuleTree()
        // 无未匹配课程
        val rows = buildPlanRows(tree, setOf(100), emptyList())

        val generalRow = rows.filterIsInstance<PlanRow.Module>().first { it.module.displayName == "通识选修" }
        assertEquals(0.0, generalRow.subtreeExtraPassed, 0.001)
    }

    @Test
    fun buildRows_parentModuleSubtreeExtraPassed_includesOwnAndChildCredits() {
        val tree = buildModuleTree()
        // 一门课程回退到父模块（typeId 不匹配任何子分类），一门课程归属到子分类
        val courses = listOf(
            passedCourse("TX001", typeId = 9999, credits = 2.0),  // 回退到通识选修
            passedCourse("TX002", typeId = 1001, credits = 3.0)   // 人文社科类
        )

        val expanded = setOf(100, 101)
        val rows = buildPlanRows(tree, expanded, courses)

        val generalRow = rows.filterIsInstance<PlanRow.Module>().first { it.module.displayName == "通识选修" }
        // 父模块自身有 1 门回退课程 (2.0)，子分类有 1 门 (3.0)，subtreeExtraPassed = 2.0 + 3.0 = 5.0
        assertEquals(5.0, generalRow.subtreeExtraPassed, 0.001)
        // 父模块自身的 moduleUnmatchedCourses 应只有回退课程
        assertEquals(1, generalRow.moduleUnmatchedCourses.size)
        assertEquals("TX001", generalRow.moduleUnmatchedCourses.first().code)
    }

    @Test
    fun buildRows_subtreeExtraPassed_notAffectedByNonPassedCourses() {
        val tree = buildModuleTree()
        // 混合已通过和未通过课程
        val courses = listOf(
            passedCourse("TX001", typeId = 1001, credits = 2.0),  // 已通过
            CompletionCourse(
                code = "TX002",
                nameZh = "未通过课程",
                credits = 3.0,
                finalResultType = ResultTypeEntry(name = "NOT_PASSED")
            ).apply { this.typeId = 1002 }  // 自然科学类，未通过
        )

        val expanded = setOf(100, 101, 102)
        val rows = buildPlanRows(tree, expanded, courses)

        val generalRow = rows.filterIsInstance<PlanRow.Module>().first { it.module.displayName == "通识选修" }
        // subtreeExtraPassed 只计算已通过课程: 2.0（TX002 未通过不计入）
        assertEquals(2.0, generalRow.subtreeExtraPassed, 0.001)
    }
}
