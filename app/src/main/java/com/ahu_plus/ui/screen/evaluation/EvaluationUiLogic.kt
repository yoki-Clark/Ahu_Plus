package com.ahu_plus.ui.screen.evaluation

import com.ahu_plus.data.model.evaluation.EvaluationAnswer
import com.ahu_plus.data.model.evaluation.EvaluationQuestionnaire
import com.ahu_plus.data.model.evaluation.EvaluationSemester
import com.ahu_plus.data.model.evaluation.TeacherEvaluationTask

internal data class EvaluationCourseGroup(
    val key: String,
    val courseName: String,
    val courseCode: String?,
    val tasks: List<TeacherEvaluationTask>,
)

internal data class EvaluationDraftValidation(
    val message: String? = null,
    val questionIds: Set<String> = emptySet(),
) {
    val isValid: Boolean get() = message == null
}

internal fun selectInitialEvaluationSemester(
    semesters: List<EvaluationSemester>,
    currentSemesterId: String?,
): EvaluationSemester? = semesters.firstOrNull { it.id == currentSemesterId }
    ?: semesters.firstOrNull()

internal fun groupEvaluationTasks(
    tasks: List<TeacherEvaluationTask>,
): List<EvaluationCourseGroup> {
    val grouped = linkedMapOf<String, MutableList<TeacherEvaluationTask>>()
    tasks.forEach { task ->
        val key = task.courseCode?.takeIf { it.isNotBlank() } ?: task.courseName
        grouped.getOrPut(key) { mutableListOf() }.add(task)
    }
    return grouped.map { (key, courseTasks) ->
        val first = courseTasks.first()
        EvaluationCourseGroup(
            key = key,
            courseName = first.courseName,
            courseCode = first.courseCode,
            tasks = courseTasks,
        )
    }
}

/** 提交前的确定性限制；服务端 check-submit 仍负责低分等动态规则。 */
internal fun validateEvaluationDraft(
    questionnaire: EvaluationQuestionnaire,
    answers: Map<String, EvaluationAnswer>,
): EvaluationDraftValidation {
    val blankTextQuestions = questionnaire.questions
        .filter { it.type == 4 }
        .filter { question ->
            (answers[question.questionId] as? EvaluationAnswer.Text)?.text.isNullOrBlank()
        }
    if (blankTextQuestions.isNotEmpty()) {
        return EvaluationDraftValidation(
            message = "请填写评语后再提交",
            questionIds = blankTextQuestions.mapTo(linkedSetOf()) { it.questionId },
        )
    }

    val missingRequired = questionnaire.questions
        .filter { it.required && it.type != 4 }
        .filter { question -> answers[question.questionId] !is EvaluationAnswer.Option }
    if (missingRequired.isNotEmpty()) {
        return EvaluationDraftValidation(
            message = "有 ${missingRequired.size} 道必填题未完成",
            questionIds = missingRequired.mapTo(linkedSetOf()) { it.questionId },
        )
    }

    val radioQuestions = questionnaire.questions.filter { it.type == 1 }
    val selectedOptions = radioQuestions.mapNotNull { question ->
        (answers[question.questionId] as? EvaluationAnswer.Option)?.optionId
    }
    if (radioQuestions.size > 1 &&
        selectedOptions.size == radioQuestions.size &&
        selectedOptions.distinct().size == 1
    ) {
        return EvaluationDraftValidation(
            message = "本问卷单选题至少有一题选项与其他题目不同",
            questionIds = radioQuestions.mapTo(linkedSetOf()) { it.questionId },
        )
    }

    return EvaluationDraftValidation()
}
