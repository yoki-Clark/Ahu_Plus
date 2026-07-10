package com.ahu_plus.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CProgResponseParserTest {

    @Test
    fun `parses achievement result rows`() {
        val page = CProgResponseParser.parseResultPage(
            body = """
                {
                  "total": 2,
                  "records": 11,
                  "page": 1,
                  "rows": [{
                    "examId": "exam-1",
                    "examCaption": "C language exercise",
                    "subjectCaption": "C language",
                    "examUserGrade": 92.5,
                    "examUserTime": "2026-07-10 16:30:00",
                    "examUserStatus": 2,
                    "recordCounts": 1,
                    "examStatus": 1,
                    "examHistoryLook": "0"
                  }]
                }
            """.trimIndent(),
            subjectId = "subject-1",
        )

        assertEquals(11, page.records)
        assertEquals("subject-1", page.rows.single().subjectId)
        assertEquals(92.5, page.rows.single().grade, 0.001)
        assertEquals(1, page.rows.single().recordCounts)
    }

    @Test
    fun `parses submitted attempt rows`() {
        val page = CProgResponseParser.parseAttemptPage(
            """
                {
                  "total": 1,
                  "records": 1,
                  "page": 1,
                  "rows": [{
                    "id": "attempt-1",
                    "examId": "exam-1",
                    "status": 2,
                    "grade": 92.5,
                    "time": 650,
                    "createTime": "2026-07-10 16:00:00",
                    "submitTime": "2026-07-10 16:10:50",
                    "subjectCaption": "C language",
                    "examCaption": "C language exercise"
                  }]
                }
            """.trimIndent()
        )

        val attempt = page.rows.single()
        assertEquals("attempt-1", attempt.id)
        assertEquals(650, attempt.durationSeconds)
        assertEquals("2026-07-10 16:10:50", attempt.submitTime)
    }

    @Test
    fun `extracts paper id embedded by details init page`() {
        val html = """
            <script>
              ${'$'}.post('/site/achievement/full/history/details/result',
                {eid:"exam-1",pid:"paper-1",userId:"user-1",id:"attempt-1"}, callback);
            </script>
        """.trimIndent()

        assertEquals("paper-1", CProgResponseParser.extractPaperId(html))
        assertNull(CProgResponseParser.extractPaperId("<html><body>No paper here</body></html>"))
    }

    @Test
    fun `parses read only paper with choices and submitted answer`() {
        val paper = CProgResponseParser.parsePaperResponse(
            """
                {
                  "errCode": "0",
                  "data": {
                    "examId": "exam-1",
                    "examCaption": "C language exercise",
                    "subjectCaption": "C language",
                    "paperQuestionCount": 1,
                    "paperQuestionTypeCount": 1,
                    "paperGrade": 5,
                    "studentTotalGrade": 5,
                    "studentPaperQuestionTypeVoList": [{
                      "questionTypeCaption": "Single choice",
                      "baseQuestionType": "S_C",
                      "questionCount": 1,
                      "studentPaperItemVoList": [{
                        "questionId": "question-1",
                        "text": "Which option is correct?",
                        "answer": "A",
                        "analysis": "<p>Explanation</p>",
                        "knowledgeCaption": "Basics",
                        "answerJson": "{\"answerList\":[{\"answer\":\"A\",\"optionCount\":\"1\",\"desc\":\"<p>Option A</p>\"},{\"answer\":\"B\",\"optionCount\":\"2\",\"desc\":\"<p>Option B</p>\"}]}",
                        "options": "",
                        "studentQuestionAnswer": "A",
                        "stutdenQuestionGrade": 5
                      }]
                    }]
                  }
                }
            """.trimIndent()
        )

        val question = paper.questionTypes.single().items.single()
        assertEquals("A", question.studentAnswer)
        assertEquals(5.0, question.studentGrade ?: -1.0, 0.001)
        assertEquals("Explanation", question.analysis)
        assertEquals(listOf("A", "B"), question.options.map { it.code })
        assertEquals(listOf("Option A", "Option B"), question.options.map { it.content })
        assertTrue(paper.questionTypes.single().items.isNotEmpty())
    }

    @Test
    fun `drops generic question analysis placeholder`() {
        val paper = CProgResponseParser.parsePaperResponse(
            """
                {
                  "errCode": "0",
                  "data": {
                    "examId": "exam-1",
                    "examCaption": "Exercise",
                    "studentPaperQuestionTypeVoList": [{
                      "questionTypeCaption": "Single choice",
                      "studentPaperItemVoList": [{
                        "questionId": "question-1",
                        "text": "Question",
                        "analysis": "<p>试题解析...</p>"
                      }]
                    }]
                  }
                }
            """.trimIndent()
        )

        assertNull(paper.questionTypes.single().items.single().analysis)
    }
}
