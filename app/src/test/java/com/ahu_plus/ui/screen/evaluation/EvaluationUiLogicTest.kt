package com.ahu_plus.ui.screen.evaluation

import com.ahu_plus.data.model.evaluation.EvaluationAnswer
import com.ahu_plus.data.model.evaluation.EvaluationOption
import com.ahu_plus.data.model.evaluation.EvaluationQuestion
import com.ahu_plus.data.model.evaluation.EvaluationQuestionnaire
import com.ahu_plus.data.model.evaluation.EvaluationSemester
import com.ahu_plus.data.model.evaluation.TeacherEvaluationTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationUiLogicTest {

    @Test
    fun `current semester is selected without reordering chips`() {
        val semesters = listOf(
            EvaluationSemester("132", "2026-2027-1"),
            EvaluationSemester("112", "2025-2026-2"),
            EvaluationSemester("92", "2025-2026-1"),
        )

        val selected = selectInitialEvaluationSemester(semesters, currentSemesterId = "112")

        assertEquals("112", selected?.id)
        assertEquals(listOf("132", "112", "92"), semesters.map { it.id })
    }

    @Test
    fun `teachers from same course are grouped into one course card`() {
        val tasks = listOf(
            task("task-a", "COURSE-1", "课程一", "教师甲"),
            task("task-b", "COURSE-1", "课程一", "教师乙"),
            task("task-c", "COURSE-2", "课程二", "教师丙"),
        )

        val groups = groupEvaluationTasks(tasks)

        assertEquals(2, groups.size)
        assertEquals(listOf("教师甲", "教师乙"), groups.first().tasks.map { it.teacherName })
    }

    @Test
    fun `blank comment blocks submission even when backend marks it optional`() {
        val questionnaire = questionnaire()
        val answers = mapOf<String, EvaluationAnswer>(
            "1" to EvaluationAnswer.Option("1", "1", 10.0),
            "2" to EvaluationAnswer.Option("2", "2", 8.0),
        )

        val result = validateEvaluationDraft(questionnaire, answers)

        assertEquals("请填写评语后再提交", result.message)
        assertTrue("3" in result.questionIds)
    }

    @Test
    fun `all radio answers using same option are rejected before submit`() {
        val questionnaire = questionnaire()
        val answers = mapOf<String, EvaluationAnswer>(
            "1" to EvaluationAnswer.Option("1", "1", 10.0),
            "2" to EvaluationAnswer.Option("2", "1", 10.0),
            "3" to EvaluationAnswer.Text("3", "无"),
        )

        val result = validateEvaluationDraft(questionnaire, answers)

        assertEquals("本问卷单选题至少有一题选项与其他题目不同", result.message)
    }

    private fun questionnaire() = EvaluationQuestionnaire(
        questionnaireId = "qid",
        questionnaireName = "评教",
        enable = true,
        questions = listOf(
            question("1", type = 1, required = true),
            question("2", type = 1, required = true),
            question("3", type = 4, required = false),
        ),
    )

    private fun question(id: String, type: Int, required: Boolean) = EvaluationQuestion(
        questionId = id,
        content = "题目$id",
        type = type,
        required = required,
        orderNum = id.toInt(),
        score = 10.0,
        options = if (type == 1) listOf(
            EvaluationOption("1", "优秀", 10.0),
            EvaluationOption("2", "良好", 8.0),
        ) else emptyList(),
    )

    private fun task(
        id: String,
        courseCode: String,
        courseName: String,
        teacherName: String,
    ) = TeacherEvaluationTask(
        stdSumTaskId = id,
        courseName = courseName,
        courseCode = courseCode,
        teacherName = teacherName,
        teacherId = null,
        semesterId = "112",
        evaluationQuestionnaireId = "qid",
        evaluationQuestionnaireName = "评教",
        status = "TO_REVIEW",
    )
}
