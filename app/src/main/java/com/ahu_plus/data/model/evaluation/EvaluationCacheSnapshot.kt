package com.ahu_plus.data.model.evaluation

/**
 * Local cache for the evaluation list and questionnaires.
 *
 * Gson reflects the generic fields in this DTO, so it belongs to data.model
 * where the release shrinker retains its fields and type signatures.
 */
data class EvaluationCacheSnapshot(
    val semesters: List<EvaluationSemester> = emptyList(),
    val semestersUpdatedAt: Long = 0L,
    val tasks: Map<String, List<TeacherEvaluationTask>> = emptyMap(),
    val taskUpdatedAt: Map<String, Long> = emptyMap(),
    val questionnaires: Map<String, EvaluationQuestionnaire> = emptyMap(),
)
