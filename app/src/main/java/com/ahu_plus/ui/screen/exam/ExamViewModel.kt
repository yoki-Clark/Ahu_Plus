package com.ahu_plus.ui.screen.exam

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.model.jw.Exam
import com.ahu_plus.data.local.DataRefreshPolicy
import com.ahu_plus.data.local.DataSnapshotStatus
import com.ahu_plus.data.repository.ExamRepository
import com.ahu_plus.data.repository.JwAuthException
import com.ahu_plus.data.repository.JwAuthRepository
import com.ahu_plus.data.repository.SessionExpiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

class ExamViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val examRepository: ExamRepository,
    private val sessionManager: com.ahu_plus.data.local.SessionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    private val gson = com.ahu_plus.data.GsonProvider.instance
    private var refreshJob: Job? = null
    private var requestGeneration = 0L

    init {
        viewModelScope.launch { loadFromCache() }
    }

    fun activate() {
        val today = LocalDate.now()
        val hasUpcomingSoon = _uiState.value.exams.any { exam ->
            if (exam.isFinished) return@any false
            val date = runCatching { LocalDate.parse(exam.examTime.take(10)) }.getOrNull()
            date != null && !date.isBefore(today) && !date.isAfter(today.plusDays(45))
        }
        val maxAge = if (hasUpcomingSoon) {
            6L * 60 * 60 * 1000
        } else {
            24L * 60 * 60 * 1000
        }
        if (!DataRefreshPolicy.isStale(sessionManager?.getExamsUpdatedAt() ?: 0L, maxAge)) return
        startRefresh()
    }

    fun onRefresh() {
        startRefresh()
    }

    private fun startRefresh() {
        val generation = ++requestGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loadExams(isRefresh = _uiState.value.exams.isNotEmpty(), generation)
        }
    }

    /** 从 SessionManager 恢复缓存的考试数据 */
    private suspend fun loadFromCache(): Boolean {
        val sm = sessionManager ?: return false
        val json = sm.getExamsJson() ?: return false
        return try {
            withContext(Dispatchers.IO) {
                val list = gson.fromJson(json, Array<Exam>::class.java).toList()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        exams = list,
                        dataStatus = DataSnapshotStatus.cache(sm.getExamsUpdatedAt()),
                        error = null,
                        needsLogin = false,
                    )
                }
            }
            true
        } catch (_: Exception) { false }
    }

    private suspend fun loadExams(isRefresh: Boolean, generation: Long) {
        if (!isRefresh) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        } else {
            _uiState.update { it.copy(isRefreshing = true) }
        }
        val wasLoaded = _uiState.value.exams.isNotEmpty()
        try {
            withContext(Dispatchers.IO) {
                jwAuthRepository.executeWithSessionRetry { examRepository.getExams() }.fold(
                    onSuccess = { list ->
                        if (generation != requestGeneration) return@fold
                        // 缓存到本地
                        val sm = sessionManager
                        if (sm != null) {
                            try { sm.saveExamsJson(gson.toJson(list)) } catch (_: Exception) { Log.w(TAG, "Failed to cache exams JSON") }
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                exams = list,
                                dataStatus = DataSnapshotStatus.network(
                                    sm?.getExamsUpdatedAt() ?: System.currentTimeMillis()
                                ),
                                error = null,
                                needsLogin = false
                            )
                        }
                    },
                    onFailure = { e ->
                        if (generation != requestGeneration) return@fold
                        // 2026-06-23: SessionExpiredException 时尝试后台静默重连 + 重试一次
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = if (!wasLoaded) (e.message ?: "考试安排加载失败") else it.error,
                                dataStatus = it.dataStatus?.withFailedRefresh(),
                                needsLogin = !wasLoaded &&
                                    (e is SessionExpiredException || e is JwAuthException)
                            )
                        }
                    }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != requestGeneration) return
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = if (!wasLoaded) "未知错误: ${e.message}" else it.error,
                    dataStatus = it.dataStatus?.withFailedRefresh(),
                    needsLogin = !wasLoaded &&
                        (e is SessionExpiredException || e is JwAuthException)
                )
            }
        }
    }

    private companion object {
        private const val TAG = "ExamVM"
    }

    /** 2026-06-23: SessionExpiredException 后尝试静默重连+重试一次。 */
    /** 应用重连+重试后的考试结果。 */
    private suspend fun handleExamsResult(
        list: List<com.ahu_plus.data.model.jw.Exam>,
        @Suppress("UNUSED_PARAMETER") wasLoaded: Boolean
    ) {
        val sm = sessionManager
        if (sm != null) {
            try { sm.saveExamsJson(gson.toJson(list)) } catch (_: Exception) { Log.w(TAG, "Failed to cache exams JSON") }
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                exams = list,
                error = null,
                needsLogin = false
            )
        }
    }
}

data class ExamUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val exams: List<Exam> = emptyList(),
    val error: String? = null,
    val dataStatus: DataSnapshotStatus? = null,
    val needsLogin: Boolean = false
)
