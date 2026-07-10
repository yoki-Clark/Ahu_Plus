package com.ahu_plus.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class EvaluationResponseParserTest {

    @Test
    fun `semester response keeps server chronological order and exposes current id`() {
        val semesters = EvaluationResponseParser.parseSemesters(
            """{"code":0,"data":[{"id":"132","nameZh":"2026-2027-1"},{"id":"112","nameZh":"2025-2026-2"}]}"""
        )
        val jwt = jwt("""{"exp":4102444800,"currentSemesterId":"112"}""")

        assertEquals(listOf("132", "112"), semesters.map { it.id })
        assertEquals("112", EvaluationResponseParser.currentSemesterId(jwt))
        assertTrue(EvaluationResponseParser.isJwtUsable(jwt, nowEpochSeconds = 1_800_000_000))
    }

    @Test
    fun `task response expands every teacher with teacher task id and status`() {
        val body = """
            {"code":0,"data":{"data":[{
              "courseName":"习近平新时代中国特色社会主义思想概论（上）",
              "lessonNameZh":"24级经济统计学2班;24级统计学1班",
              "lessonCode":"202520262-GG61116.069",
              "taskList":[{
                "stdSumTaskId":"first-task",
                "evaluationQuestionnaireId":"questionnaire",
                "evaluationQuestionnaireName":"学生评教-理论课",
                "teachers":[
                  {"stdSumTaskId":"teacher-a-task","teacherId":"1","teacherName":"余欢欢","status":"TO_REVIEW"},
                  {"stdSumTaskId":"teacher-b-task","teacherId":"2","teacherName":"李腾飞","status":"TO_REVIEW"}
                ]
              }]
            }]}}
        """.trimIndent()

        val tasks = EvaluationResponseParser.parseTasks(body)

        assertEquals(2, tasks.size)
        assertEquals(listOf("teacher-a-task", "teacher-b-task"), tasks.map { it.stdSumTaskId })
        assertEquals(listOf("余欢欢", "李腾飞"), tasks.map { it.teacherName })
        assertEquals("习近平新时代中国特色社会主义思想概论（上）", tasks.first().courseName)
        assertEquals("TO_REVIEW", tasks.first().status)
    }

    @Test
    fun `questionnaire uses submission question id and keeps enable true`() {
        val questions = """[{"index":1,"attribute":{"id":1,"questionItemId":"metric-uuid","name":"教师注重教书育人","typeId":1,"typeName":"radio","required":true,"score":15},"options":[{"optionId":1,"optionScore":14.7,"value":"优秀"}]}]"""
            .replace("\\", "\\\\").replace("\"", "\\\"")
        val body = """{"code":0,"data":{"id":"qid","nameZh":"学生评教-实验课","status":false,"questions":"$questions"}}"""

        val questionnaire = EvaluationResponseParser.parseQuestionnaire(body)

        assertTrue(questionnaire.enable)
        assertEquals("1", questionnaire.questions.single().questionId)
        assertEquals("1", questionnaire.questions.single().options.single().optionId)
    }

    @Test
    fun `check submit code zero with warning is not pass`() {
        val warning = EvaluationResponseParser.parseCheckResult(
            """{"code":0,"data":"本问卷单选题至少有一题选项与其他题目不同","ok":true}"""
        )
        val accepted = EvaluationResponseParser.parseCheckResult(
            """{"code":0,"data":null,"ok":true}"""
        )

        assertFalse(warning.pass)
        assertTrue(accepted.pass)
    }

    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf("{}", payload, "signature")
            .map { encoder.encodeToString(it.toByteArray()) }
            .joinToString(".")
    }
}
