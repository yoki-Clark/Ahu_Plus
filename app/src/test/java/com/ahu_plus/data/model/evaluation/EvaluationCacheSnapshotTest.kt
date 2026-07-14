package com.ahu_plus.data.model.evaluation

import com.ahu_plus.data.GsonProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluationCacheSnapshotTest {

    @Test
    fun `cache snapshot restores typed nested collections`() {
        val snapshot = EvaluationCacheSnapshot(
            semesters = listOf(EvaluationSemester("112", "2025-2026-2")),
            tasks = mapOf(
                "112" to listOf(
                    TeacherEvaluationTask(
                        stdSumTaskId = "task-1",
                        courseName = "Kotlin",
                        courseCode = "CS101",
                        teacherName = "Teacher",
                        teacherId = "t-1",
                        semesterId = "112",
                        evaluationQuestionnaireId = "questionnaire-1",
                        evaluationQuestionnaireName = "Course evaluation",
                        status = "TO_REVIEW",
                    )
                )
            ),
        )

        val restored = GsonProvider.instance.fromJson(
            GsonProvider.instance.toJson(snapshot),
            EvaluationCacheSnapshot::class.java,
        )

        assertEquals("112", restored.semesters.single().id)
        assertEquals("task-1", restored.tasks["112"]!!.single().stdSumTaskId)
    }
}
