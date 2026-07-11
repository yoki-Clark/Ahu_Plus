package com.ahu_plus.ui.screen.evaluation

import com.ahu_plus.data.model.evaluation.EvaluationAnswer
import com.ahu_plus.data.model.evaluation.EvaluationCommentOption
import com.ahu_plus.data.model.evaluation.EvaluationOption
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

/** 内置的「无」评语选项。 */
internal val DefaultCommentOption = EvaluationCommentOption(
    id = "default",
    text = "无",
    builtIn = true,
)

/**
 * 单选题策略:固定「6 优 + 1 良」,唯一的「良」放在前 7 道单选里随机挑。
 * < 7 道时,只取实际数量减一为优、随机一道为良;
 * > 7 道时,前 7 道里随机一良,其余全优(等价于「第 7 道以外一律优」)。
 * 题型识别:optionScore 最大为「优」,次大为「良」;找不到次大时退化为「优」。
 * 文本题统一填 [commentText]。
 */
internal fun applyFillPreset(
    questionnaire: EvaluationQuestionnaire,
    commentText: String,
    random: java.util.Random,
): Map<String, EvaluationAnswer> {
    val radioQuestions = questionnaire.questions.filter { it.type == 1 }
    val sevenCandidatePool = radioQuestions.take(7)
    val goodIndexInPool = if (sevenCandidatePool.isNotEmpty()) {
        random.nextInt(sevenCandidatePool.size)
    } else -1

    val result = LinkedHashMap<String, EvaluationAnswer>(questionnaire.questions.size)
    questionnaire.questions.forEach { question ->
        when (question.type) {
            1 -> {
                val excellentOpt = pickExcellentOption(question.options)
                val goodOpt = pickGoodOption(question.options)
                val poolIndex = sevenCandidatePool.indexOf(question)
                val pick = if (poolIndex >= 0 && poolIndex == goodIndexInPool && goodOpt != null) {
                    goodOpt
                } else {
                    excellentOpt
                }
                if (pick != null) {
                    result[question.questionId] = EvaluationAnswer.Option(
                        questionId = question.questionId,
                        optionId = pick.optionId,
                        optionScore = pick.optionScore,
                    )
                }
            }
            4 -> result[question.questionId] = EvaluationAnswer.Text(
                questionId = question.questionId,
                text = commentText,
            )
        }
    }
    return result
}

/** optionScore 最大者;并列时取列表第一个。空列表返回 null。 */
private fun pickExcellentOption(options: List<EvaluationOption>): EvaluationOption? =
    options.maxByOrNull { it.optionScore }

/** 「良」= 次大 optionScore。找不到(选项数 < 2 或所有并列)时退化为最大者;再不行返回 null。 */
private fun pickGoodOption(options: List<EvaluationOption>): EvaluationOption? {
    if (options.size < 2) return null
    val sorted = options.sortedByDescending { it.optionScore }
    val maxScore = sorted.first().optionScore
    val secondOrSame = sorted.firstOrNull { it.optionScore < maxScore } ?: sorted.first()
    return if (secondOrSame.optionScore < maxScore) secondOrSame else sorted.first()
}

/**
 * 把评语选项列表(用户自定义 + 内置默认)拼接好,默认永远在最前,
 * 且去重(防用户手动加了 id="default" 的脏数据)。
 */
internal fun mergeCommentOptions(custom: List<EvaluationCommentOption>): List<EvaluationCommentOption> {
    val customOnly = custom.filter { it.id != DefaultCommentOption.id }
    return listOf(DefaultCommentOption) + customOnly
}
