package com.yourname.ahu_plus.ui.screen.exam

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
    private val examRepository: ExamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadExams() }
    }

    fun onRefresh() {
        viewModelScope.launch { loadExams() }
    }

    private suspend fun loadExams() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            withContext(Dispatchers.IO) {
                val authResult = jwAuthRepository.authenticate()
                if (authResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            needsLogin = true,
                            error = "教务处认证失败: ${authResult.exceptionOrNull()?.message}"
                        )
                    }
                    return@withContext
                }

                examRepository.getExams().fold(
                    onSuccess = { list ->
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
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "考试安排加载失败",
                                needsLogin = e is SessionExpiredException
                            )
                        }
                    }
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "未知错误: ${e.message}",
                    needsLogin = e is SessionExpiredException
                )
            }
        }
    }
}

data class ExamUiState(
    val isLoading: Boolean = true,
    val exams: List<Exam> = emptyList(),
    val error: String? = null,
    val needsLogin: Boolean = false
)
