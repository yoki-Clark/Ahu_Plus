package com.ahu_plus.ui.screen.evaluation

import com.ahu_plus.data.model.evaluation.EvaluationAnswer
import com.ahu_plus.data.model.evaluation.EvaluationCommentOption
import com.ahu_plus.data.model.evaluation.EvaluationOption
import com.ahu_plus.data.model.evaluation.EvaluationQuestion
import com.ahu_plus.data.model.evaluation.EvaluationQuestionnaire
import com.ahu_plus.data.model.evaluation.EvaluationSemester
import com.ahu_plus.data.model.evaluation.TeacherEvaluationTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

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

    @Test
    fun `applyFillPreset picks 6 excellent and 1 good across 7 radio questions`() {
        val q = questionnaireOfRadio(radioIds = (1..7).map { it.toString() })
        val result = applyFillPreset(q, "无", Random(42))

        val optionAnswers = q.questions
            .map { result[it.questionId] as EvaluationAnswer.Option }
        val goodCount = optionAnswers.count { it.optionScore == 8.0 }
        val excellentCount = optionAnswers.count { it.optionScore == 10.0 }

        assertEquals("7 题应选 1 良", 1, goodCount)
        assertEquals("7 题应选 6 优", 6, excellentCount)
        assertEquals("共 7 题答案", 7, optionAnswers.size)
    }

    @Test
    fun `applyFillPreset randomizes the good position inside the first 7`() {
        val q = questionnaireOfRadio(radioIds = (1..7).map { it.toString() })
        val seenIndices = mutableSetOf<Int>()
        repeat(50) { seed ->
            val result = applyFillPreset(q, "无", Random(seed.toLong()))
            val goodIndex = q.questions.indexOfFirst {
                (result[it.questionId] as EvaluationAnswer.Option).optionScore == 8.0
            }
            seenIndices += goodIndex
        }
        assertTrue("50 次随机里良应分布在多个题目,实际只看到 $seenIndices", seenIndices.size >= 3)
    }

    @Test
    fun `applyFillPreset leaves questions beyond the first 7 all excellent`() {
        val q = questionnaireOfRadio(radioIds = (1..10).map { it.toString() })
        val result = applyFillPreset(q, "无", Random(7))

        val goodIndices = q.questions.mapIndexedNotNull { idx, question ->
            val ans = result[question.questionId] as EvaluationAnswer.Option
            if (ans.optionScore == 8.0) idx else null
        }
        assertEquals("只应该有 1 良", 1, goodIndices.size)
        assertTrue("良应在前 7 题(索引 0..6),实际 ${goodIndices.first()}", goodIndices.first() < 7)
        for (idx in 7..9) {
            val ans = result[q.questions[idx].questionId] as EvaluationAnswer.Option
            assertEquals("第 ${idx + 1} 题应为优", 10.0, ans.optionScore, 0.0)
        }
    }

    @Test
    fun `applyFillPreset fills text questions with provided commentText`() {
        val q = EvaluationQuestionnaire(
            questionnaireId = "qid",
            questionnaireName = "评教",
            enable = true,
            questions = listOf(
                question("1", type = 4, required = false),
            ),
        )
        val result = applyFillPreset(q, "", Random(1))

        assertEquals("", (result["1"] as EvaluationAnswer.Text).text)
    }

    @Test
    fun `applyFillPreset uses non-empty commentText on all text questions`() {
        val q = EvaluationQuestionnaire(
            questionnaireId = "qid",
            questionnaireName = "评教",
            enable = true,
            questions = listOf(
                question("1", type = 4, required = false),
                question("2", type = 4, required = false),
            ),
        )
        val result = applyFillPreset(q, "老师讲得很认真,收获很大。", Random(0))

        assertEquals("老师讲得很认真,收获很大。", (result["1"] as EvaluationAnswer.Text).text)
        assertEquals("老师讲得很认真,收获很大。", (result["2"] as EvaluationAnswer.Text).text)
    }

    @Test
    fun `applyFillPreset with under 7 radio questions still puts 1 good`() {
        val q = questionnaireOfRadio(radioIds = listOf("1", "2", "3"))
        val result = applyFillPreset(q, "无", Random(99))

        val optionAnswers = q.questions.map { result[it.questionId] as EvaluationAnswer.Option }
        assertEquals("3 题里应该有 1 良", 1, optionAnswers.count { it.optionScore == 8.0 })
        assertEquals("3 题里应该有 2 优", 2, optionAnswers.count { it.optionScore == 10.0 })
    }

    @Test
    fun `mergeCommentOptions puts default first and deduplicates`() {
        val custom = listOf(
            DefaultCommentOption, // 同 id,会被去重
            EvaluationCommentOption("u1", "无评语", builtIn = false),
            EvaluationCommentOption("u2", "鼓励", builtIn = false),
        )
        val merged = mergeCommentOptions(custom)
        assertEquals(3, merged.size)
        assertEquals(DefaultCommentOption.id, merged.first().id)
        // default 只出现一次
        assertEquals(1, merged.count { it.id == DefaultCommentOption.id })
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

    /** 生成「n 道单选」问卷,每题两选项 优秀(10)/ 良好(8),方便断言。 */
    private fun questionnaireOfRadio(radioIds: List<String>): EvaluationQuestionnaire =
        EvaluationQuestionnaire(
            questionnaireId = "qid",
            questionnaireName = "评教",
            enable = true,
            questions = radioIds.map { id -> question(id, type = 1, required = true) },
        )
}
