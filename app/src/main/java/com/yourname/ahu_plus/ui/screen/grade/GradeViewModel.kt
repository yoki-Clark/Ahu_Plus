package com.yourname.ahu_plus.ui.screen.grade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.model.jw.GpaMetadata
import com.yourname.ahu_plus.data.model.jw.Grade
import com.yourname.ahu_plus.data.repository.GradeRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.SessionExpiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GradeViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val gradeRepository: GradeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GradeUiState())
    val uiState: StateFlow<GradeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadGrades() }
    }

    fun onRefresh() {
        viewModelScope.launch { loadGrades() }
    }

    fun selectSemester(semesterId: Int) {
        _uiState.update { it.copy(selectedSemesterId = semesterId) }
    }

    fun onGradeClicked(grade: Grade) {
        _uiState.update { it.copy(selectedGrade = grade) }
    }

    fun onDismissGradeDetail() {
        _uiState.update { it.copy(selectedGrade = null) }
    }

    private suspend fun loadGrades() {
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

                // 并发请求：grades JSON + GPA HTML
                val gradesResult = gradeRepository.getGrades()
                val gpaResult = gradeRepository.getGpaMetadata()

                gradesResult.fold(
                    onSuccess = { resp ->
                        val semesterIds = resp.semesterId2studentGrades?.keys
                            ?.mapNotNull { it.toIntOrNull() }
                            ?.sortedDescending()
                            ?: emptyList()
                        val gradesBySem = resp.semesterId2studentGrades.orEmpty()
                        val defaultSem = _uiState.value.selectedSemesterId
                            ?.takeIf { it in semesterIds }
                            ?: semesterIds.firstOrNull()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                gradesBySemester = gradesBySem,
                                availableSemesterIds = semesterIds,
                                selectedSemesterId = defaultSem,
                                semesterName = defaultSem?.let { id ->
                                    gradesBySem[id.toString()]?.firstOrNull()?.semesterName
                                        ?: resp.semesters?.firstOrNull { s -> s.id == id }?.nameZh
                                },
                                gpaMetadata = gpaResult.getOrNull(),
                                error = null,
                                needsLogin = false
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "成绩加载失败",
                                needsLogin = e is SessionExpiredException,
                                gpaMetadata = gpaResult.getOrNull(),
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

data class GradeUiState(
    val isLoading: Boolean = true,
    val gradesBySemester: Map<String, List<Grade>> = emptyMap(),
    val availableSemesterIds: List<Int> = emptyList(),
    val selectedSemesterId: Int? = null,
    val semesterName: String? = null,
    val gpaMetadata: GpaMetadata? = null,
    val selectedGrade: Grade? = null,
    val error: String? = null,
    val needsLogin: Boolean = false
) {
    val currentGrades: List<Grade>
        get() = selectedSemesterId?.let { gradesBySemester[it.toString()].orEmpty() }
            .orEmpty()
            .sortedBy { it.courseName.orEmpty() }
}
