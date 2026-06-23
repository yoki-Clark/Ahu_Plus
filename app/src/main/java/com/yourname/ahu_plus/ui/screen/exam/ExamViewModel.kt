package com.yourname.ahu_plus.ui.screen.exam

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.model.jw.Exam
import com.yourname.ahu_plus.data.repository.ExamRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.SessionExpiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExamViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val examRepository: ExamRepository,
    private val sessionManager: com.yourname.ahu_plus.data.local.SessionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    private val gson = com.yourname.ahu_plus.data.GsonProvider.instance

    init {
        viewModelScope.launch {
            val cached = loadFromCache()
            if (cached) {
                launch { loadExams(isRefresh = true) }
            } else {
                loadExams(isRefresh = false)
            }
        }
    }

    fun onRefresh() {
        viewModelScope.launch { loadExams(isRefresh = false) }
    }

    /** 从 SessionManager 恢复缓存的考试数据 */
    private suspend fun loadFromCache(): Boolean {
        val sm = sessionManager ?: return false
        val json = sm.getExamsJson() ?: return false
        return try {
            withContext(Dispatchers.IO) {
                val list = gson.fromJson(json, Array<Exam>::class.java).toList()
                _uiState.update {
                    it.copy(isLoading = false, exams = list, error = null, needsLogin = false)
                }
            }
            true
        } catch (_: Exception) { false }
    }

    private suspend fun loadExams(isRefresh: Boolean) {
        if (!isRefresh) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }
        val wasLoaded = _uiState.value.exams.isNotEmpty()
        try {
            withContext(Dispatchers.IO) {
                val authResult = jwAuthRepository.authenticate()
                if (authResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            needsLogin = !wasLoaded,
                            error = if (!wasLoaded) "教务处认证失败: ${authResult.exceptionOrNull()?.message}" else null
                        )
                    }
                    return@withContext
                }

                examRepository.getExams().fold(
                    onSuccess = { list ->
                        // 缓存到本地
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
                    },
                    onFailure = { e ->
                        // 2026-06-23: SessionExpiredException 时尝试后台静默重连 + 重试一次
                        if (e is SessionExpiredException) {
                            val retry = retryAfterSilentReauth()
                            if (retry != null) {
                                handleExamsResult(retry, wasLoaded)
                                return@fold
                            }
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = if (!wasLoaded) (e.message ?: "考试安排加载失败") else it.error,
                                needsLogin = !wasLoaded && e is SessionExpiredException
                            )
                        }
                    }
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = if (!wasLoaded) "未知错误: ${e.message}" else it.error,
                    needsLogin = !wasLoaded && e is SessionExpiredException
                )
            }
        }
    }

    private companion object {
        private const val TAG = "ExamVM"
    }

    /** 2026-06-23: SessionExpiredException 后尝试静默重连+重试一次。 */
    private suspend fun retryAfterSilentReauth(): List<com.yourname.ahu_plus.data.model.jw.Exam>? {
        return try {
            jwAuthRepository.clearCookies()
            val authOk = jwAuthRepository.authenticate().isSuccess
            if (!authOk) {
                Log.w(TAG, "retryAfterSilentReauth: 静默重连失败,放弃重试")
                return null
            }
            examRepository.getExams().getOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "retryAfterSilentReauth 异常: ${e.message}")
            null
        }
    }

    /** 应用重连+重试后的考试结果。 */
    private suspend fun handleExamsResult(
        list: List<com.yourname.ahu_plus.data.model.jw.Exam>,
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
    val exams: List<Exam> = emptyList(),
    val error: String? = null,
    val needsLogin: Boolean = false
)
