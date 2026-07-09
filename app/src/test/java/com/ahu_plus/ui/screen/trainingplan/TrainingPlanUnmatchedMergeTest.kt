package com.ahu_plus.ui.screen.trainingplan

import com.ahu_plus.data.model.jw.CompletionCourse
import com.ahu_plus.data.model.jw.ResultTypeEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingPlanUnmatchedMergeTest {

    @Test
    fun mergesByCourseCodeAndDedupes() {
        // applyCompletionData 拿到的: TX001 (TX-prefixed 通识选修历史课, completion preview 漏掉)
        val completionUnmatched = listOf(
            CompletionCourse(code = "GG61017", nameZh = "形势与政策", credits = 2.0,
                finalResultType = ResultTypeEntry(name = "PASSED")),
            CompletionCourse(code = "TX001", nameZh = "课程A", credits = 2.0,
                finalResultType = ResultTypeEntry(name = "PASSED")),
        )
        // applyGrades 兜底的: TX001 (重复,验证去重) + TX002 (新,验证补全)
        val gradeUnmatched = listOf(
            CompletionCourse(code = "TX001", nameZh = "课程A-来自grades", credits = 2.0,
                finalResultType = ResultTypeEntry(name = "PASSED")),
            CompletionCourse(code = "TX002", nameZh = "课程B", credits = 1.5,
                finalResultType = ResultTypeEntry(name = "PASSED")),
        )

        val merged = uniqueUnmatched(completionUnmatched, gradeUnmatched)

        // 期望: 3 门,按 a 优先(applyCompletionData 的 GG61017/TX001 保留,grade 的 TX002 新增,TX001 重复去重)
        assertEquals(3, merged.size)
        assertEquals(setOf("GG61017", "TX001", "TX002"), merged.mapNotNull { it.code }.toSet())
        // 验证同 code 时保留 a 中的版本
        val tx001 = merged.first { it.code == "TX001" }
        assertEquals("课程A", tx001.nameZh)
    }

    @Test
    fun handlesEmptyInputs() {
        assertEquals(emptyList<CompletionCourse>(), uniqueUnmatched(emptyList(), emptyList()))
        val one = listOf(CompletionCourse(code = "TX001", finalResultType = ResultTypeEntry(name = "PASSED")))
        assertEquals(one, uniqueUnmatched(one, emptyList()))
        assertEquals(one, uniqueUnmatched(emptyList(), one))
    }
}