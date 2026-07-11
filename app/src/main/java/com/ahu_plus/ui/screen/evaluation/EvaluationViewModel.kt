package com.ahu_plus.ui.screen.evaluation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.evaluation.EvaluationAnswer
import com.ahu_plus.data.model.evaluation.EvaluationCommentOption
import com.ahu_plus.data.model.evaluation.EvaluationQuestionnaire
import com.ahu_plus.data.model.evaluation.EvaluationSemester
import com.ahu_plus.data.model.evaluation.SubmissionPayload
import com.ahu_plus.data.model.evaluation.TeacherEvaluationTask
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.repository.EvaluationApiException
import com.ahu_plus.data.repository.EvaluationAuthException
import com.ahu_plus.data.repository.EvaluationRepository
import com.ahu_plus.data.repository.JwAuthRepository
import com.ahu_plus.data.repository.SessionExpiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 评教 ViewModel — 列表 + 详情两个状态。
 *
 * 懒创建: 仅在 AppHub 进入评教页时才被 `viewModel(factory = factory)` 实例化。
 *
 * 数据流:
 *   init → loadSemesters() + loadTasks(currentSemester)
 *   onSemesterSelected → loadTasks
 *   openTask(task) → loadQuestionnaire(task)
 *   setAnswer(questionId, answer) → 更新草稿
 *   submit(anonymous) → 三步走:badword-batch → check-submit → submit
 */
class EvaluationViewModel(
    private val evaluationRepository: EvaluationRepository,
    private val jwAuthRepository: JwAuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val gson = GsonProvider.instance

    private val _listState = MutableStateFlow(EvaluationListUiState())
    val listState: StateFlow<EvaluationListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(EvaluationDetailUiState())
    val detailState: StateFlow<EvaluationDetailUiState> = _detailState.asStateFlow()

    private var activated = false

    fun activate() {
        if (activated) return
        activated = true
        viewModelScope.launch {
            val cache = readCache()
            val initial = selectInitialEvaluationSemester(
                cache.semesters,
                evaluationRepository.getCurrentSemesterId(),
            )
            if (cache.semesters.isNotEmpty()) {
                _listState.update {
                    it.copy(
                        semesters = cache.semesters,
                        selectedSemesterId = initial?.id,
                        tasks = initial?.let { sem -> cache.tasks[sem.id] }.orEmpty(),
                    )
                }
            }
            val now = System.currentTimeMillis()
            val semestersFresh = now - cache.semestersUpdatedAt < 24L * 60 * 60 * 1000
            val tasksFresh = initial != null &&
                now - (cache.taskUpdatedAt[initial.id] ?: 0L) < 5L * 60 * 1000
            if (!semestersFresh || !tasksFresh) loadSemesters()
        }
    }

    // ════════════════════════════════════════════════════════
    // 列表页
    // ════════════════════════════════════════════════════════

    private fun loadSemesters() {
        viewModelScope.launch {
            evaluationRepository.getSemesters().fold(
                onSuccess = { semesters ->
                    val initial = selectInitialEvaluationSemester(
                        semesters = semesters,
                        currentSemesterId = evaluationRepository.getCurrentSemesterId(),
                    )
                    _listState.update {
                        it.copy(semesters = semesters, selectedSemesterId = initial?.id)
                    }
                    val cache = readCache().copy(
                        semesters = semesters,
                        semestersUpdatedAt = System.currentTimeMillis(),
                    )
                    saveCache(cache)
                    if (initial != null) loadTasks(initial.id)
                },
                onFailure = { e ->
                    handleListError(e)
                }
            )
        }
    }

    fun selectSemester(semesterId: String) {
        if (semesterId == _listState.value.selectedSemesterId) return
        _listState.update { it.copy(selectedSemesterId = semesterId, tasks = emptyList()) }
        loadTasks(semesterId)
    }

    fun refreshList() {
        val sid = _listState.value.selectedSemesterId ?: return
        loadTasks(sid, isRefresh = true)
    }

    private fun loadTasks(semesterId: String, isRefresh: Boolean = false) {
        val wasLoaded = _listState.value.tasks.isNotEmpty()
        if (!isRefresh) {
            _listState.update { it.copy(isLoading = true, error = null, needsLogin = false) }
        }
        viewModelScope.launch {
            evaluationRepository.getTasks(semesterId, evaluated = false).fold(
                onSuccess = { tasks ->
                    _listState.update {
                        it.copy(
                            isLoading = false,
                            tasks = tasks,
                            error = null,
                            needsLogin = false,
                        )
                    }
                    val cache = readCache()
                    saveCache(cache.copy(
                        tasks = cache.tasks + (semesterId to tasks),
                        taskUpdatedAt = cache.taskUpdatedAt +
                            (semesterId to System.currentTimeMillis()),
                    ))
                },
                onFailure = { e ->
                    if (!wasLoaded && e is EvaluationAuthException) {
                        _listState.update { it.copy(isLoading = false, needsLogin = true) }
                        return@fold
                    }
                    handleListError(e)
                }
            )
        }
    }

    private fun handleListError(e: Throwable) {
        val msg = e.message ?: "未知错误"
        Log.w(TAG, "列表操作失败: $msg", e)
        _listState.update {
            it.copy(isLoading = false, error = msg, needsLogin = e is SessionExpiredException)
        }
    }

    // ════════════════════════════════════════════════════════
    // 详情页
    // ════════════════════════════════════════════════════════

    fun openTask(task: TeacherEvaluationTask) {
        _detailState.value = EvaluationDetailUiState(
            isLoading = true,
            task = task,
        )
        viewModelScope.launch {
            readCache().questionnaires[task.evaluationQuestionnaireId]?.let { cached ->
                _detailState.update {
                    it.copy(isLoading = false, questionnaire = cached, error = null)
                }
                return@launch
            }
            evaluationRepository.getQuestionnaire(task.evaluationQuestionnaireId).fold(
                onSuccess = { q ->
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            questionnaire = q,
                            answers = emptyMap(),
                            error = null,
                            needsLogin = false,
                        )
                    }
                    val cache = readCache()
                    saveCache(cache.copy(
                        questionnaires = cache.questionnaires +
                            (task.evaluationQuestionnaireId to q)
                    ))
                },
                onFailure = { e ->
                    Log.w(TAG, "拉问卷失败: ${e.message}", e)
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "加载问卷失败",
                            needsLogin = e is EvaluationAuthException,
                        )
                    }
                }
            )
        }
    }

    fun setOptionAnswer(questionId: String, optionId: String, optionScore: Double) {
        _detailState.update { state ->
            state.copy(
                answers = state.answers + (questionId to EvaluationAnswer.Option(
                    questionId, optionId, optionScore
                )),
                submitError = null,
                missingQuestionIds = state.missingQuestionIds - questionId,
            )
        }
    }

    fun setTextAnswer(questionId: String, text: String) {
        _detailState.update { state ->
            state.copy(
                answers = state.answers + (questionId to EvaluationAnswer.Text(questionId, text)),
                submitError = null,
                missingQuestionIds = state.missingQuestionIds - questionId,
            )
        }
    }

    fun resetDetail() {
        _detailState.value = EvaluationDetailUiState()
    }

    /**
     * 一键填写:用指定评语文本批量填好整张问卷(只动 state.answers,不提交)。
     * UI 端在弹窗点「确定」时调用。
     */
    fun applyFillPreset(commentText: String) {
        val q = _detailState.value.questionnaire ?: return
        val newAnswers = applyFillPreset(
            questionnaire = q,
            commentText = commentText,
            random = java.util.Random(),
        )
        _detailState.update {
            it.copy(
                answers = newAnswers,
                submitError = null,
                missingQuestionIds = emptySet(),
            )
        }
    }

    /** 当前可见的评语选项列表(内置「无」+ 用户自定义)。 */
    fun currentCommentOptions(): List<EvaluationCommentOption> {
        val custom = parseCommentOptions(sessionManager.getEvaluationCommentOptionsJson())
        return mergeCommentOptions(custom)
    }

    /** 添加一个用户自定义评语选项,立刻写回 SessionManager。 */
    suspend fun addCustomCommentOption(text: String): EvaluationCommentOption {
        val option = EvaluationCommentOption(
            id = UUID.randomUUID().toString(),
            text = text.trim().take(500),
        )
        val current = parseCommentOptions(sessionManager.getEvaluationCommentOptionsJson())
        sessionManager.saveEvaluationCommentOptionsJson(gson.toJson(current + option))
        return option
    }

    /** 删除一个用户自定义评语选项(id 不存在或为内置 id 时静默忽略)。 */
    suspend fun removeCustomCommentOption(id: String) {
        if (id == DefaultCommentOption.id) return
        val current = parseCommentOptions(sessionManager.getEvaluationCommentOptionsJson())
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return
        sessionManager.saveEvaluationCommentOptionsJson(gson.toJson(next))
    }

    private fun parseCommentOptions(json: String): List<EvaluationCommentOption> =
        if (json.isBlank()) emptyList()
        else runCatching {
            gson.fromJson(json, Array<EvaluationCommentOption>::class.java).toList()
                .filter { it.id.isNotBlank() && it.id != DefaultCommentOption.id }
        }.getOrDefault(emptyList())

    /**
     * 提交评教,anonymous=false=实名,=true=匿名。
     *
     * 三步走:
     *   1. 评语内容校验
     *   2. checkSubmit:业务预校验(连选/低分),文案直接显示给用户
     *   3. submit:真正落库
     *
     * 任意一步失败:返回 Result.failure 并把错误信息写到 detailState.error,
     * UI 层 Snackbar/Text 显示。
     */
    fun submit(anonymous: Boolean, onSuccess: () -> Unit) {
        val state = _detailState.value
        val task = state.task ?: return
        val questionnaire = state.questionnaire ?: return

        val validation = validateEvaluationDraft(questionnaire, state.answers)
        if (!validation.isValid) {
            _detailState.update {
                it.copy(
                    submitting = false,
                    submitError = validation.message,
                    submitErrorVersion = it.submitErrorVersion + 1,
                    missingQuestionIds = validation.questionIds,
                )
            }
            return
        }

        _detailState.update {
            it.copy(submitting = true, submitError = null, missingQuestionIds = emptySet())
        }
        viewModelScope.launch {
            val textAnswers = state.answers.values
                .filterIsInstance<EvaluationAnswer.Text>()
                .map { it.text }
            val payload: SubmissionPayload = evaluationRepository.buildPayload(
                task = task,
                questionnaire = questionnaire,
                anonymous = anonymous,
                draft = state.answers,
            )

            // Step 1: 敏感词(只对 type=4 文本题)
            val badwordResult = evaluationRepository.testBadwordBatch(textAnswers)
            if (badwordResult.isFailure) {
                failSubmit("评语校验失败: ${badwordResult.exceptionOrNull()?.message}")
                return@launch
            }

            // Step 2: 业务预校验
            val checkResult = evaluationRepository.checkSubmit(payload)
            if (checkResult.isFailure) {
                failSubmit("校验请求失败: ${checkResult.exceptionOrNull()?.message}")
                return@launch
            }
            val check = checkResult.getOrThrow()
            if (!check.pass) {
                failSubmit(check.message?.takeIf { it.isNotBlank() } ?: "问卷校验未通过")
                return@launch
            }

            // Step 3: 真正提交
            val submitResult = evaluationRepository.submit(payload)
            if (submitResult.isFailure) {
                failSubmit("提交失败: ${submitResult.exceptionOrNull()?.message}")
                return@launch
            }

            _detailState.update {
                it.copy(submitting = false, submitSuccess = true, submitError = null)
            }
            refreshList()
            onSuccess()
        }
    }

    private fun failSubmit(message: String) {
        Log.w(TAG, "提交失败: $message")
        _detailState.update {
            it.copy(
                submitting = false,
                submitError = message,
                submitErrorVersion = it.submitErrorVersion + 1,
            )
        }
    }

    fun clearSubmitError() {
        _detailState.update { it.copy(submitError = null) }
    }

    companion object {
        private const val TAG = "EvaluationVM"
    }

    private fun readCache(): EvaluationCacheSnapshot {
        val raw = sessionManager.getEvaluationDataJson() ?: return EvaluationCacheSnapshot()
        return runCatching { gson.fromJson(raw, EvaluationCacheSnapshot::class.java) }
            .getOrNull() ?: EvaluationCacheSnapshot()
    }

    private suspend fun saveCache(cache: EvaluationCacheSnapshot) {
        sessionManager.saveEvaluationDataJson(gson.toJson(cache))
    }
}

private data class EvaluationCacheSnapshot(
    val semesters: List<EvaluationSemester> = emptyList(),
    val semestersUpdatedAt: Long = 0L,
    val tasks: Map<String, List<TeacherEvaluationTask>> = emptyMap(),
    val taskUpdatedAt: Map<String, Long> = emptyMap(),
    val questionnaires: Map<String, EvaluationQuestionnaire> = emptyMap(),
)

// ──────────────────────────────────────────────────────────
// UI State
// ──────────────────────────────────────────────────────────

data class EvaluationListUiState(
    val isLoading: Boolean = false,
    val semesters: List<EvaluationSemester> = emptyList(),
    val selectedSemesterId: String? = null,
    val tasks: List<TeacherEvaluationTask> = emptyList(),
    val error: String? = null,
    val needsLogin: Boolean = false,
)

data class EvaluationDetailUiState(
    val isLoading: Boolean = false,
    val task: TeacherEvaluationTask? = null,
    val questionnaire: EvaluationQuestionnaire? = null,
    val answers: Map<String, EvaluationAnswer> = emptyMap(),
    val submitting: Boolean = false,
    val submitError: String? = null,
    /** 每次失败递增，确保同一错误文案重复出现时 Snackbar 仍会再次弹出。 */
    val submitErrorVersion: Int = 0,
    val submitSuccess: Boolean = false,
    /** 必填未填的 questionId 集合,UI 用于红色高亮。 */
    val missingQuestionIds: Set<String> = emptySet(),
    val error: String? = null,
    val needsLogin: Boolean = false,
)
