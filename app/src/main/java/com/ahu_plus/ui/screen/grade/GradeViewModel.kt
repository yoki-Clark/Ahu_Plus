package com.ahu_plus.ui.screen.grade

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.model.jw.GpaMetadata
import com.ahu_plus.data.model.jw.Grade
import com.ahu_plus.data.model.jw.GradeResponse
import com.ahu_plus.data.model.jw.SemesterInfo
import com.ahu_plus.data.local.DataRefreshPolicy
import com.ahu_plus.data.local.DataSnapshotStatus
import com.ahu_plus.data.repository.GradeRepository
import com.ahu_plus.data.repository.JwAuthException
import com.ahu_plus.data.repository.JwAuthRepository
import com.ahu_plus.data.repository.SessionExpiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

class GradeViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val gradeRepository: GradeRepository,
    private val sessionManager: com.ahu_plus.data.local.SessionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(GradeUiState())
    val uiState: StateFlow<GradeUiState> = _uiState.asStateFlow()

    private val gson = com.ahu_plus.data.GsonProvider.instance
    private var refreshJob: Job? = null
    private var requestGeneration = 0L

    init {
        viewModelScope.launch { loadFromCache() }
    }

    fun activate() {
        val updatedAt = sessionManager?.getGradesUpdatedAt() ?: 0L
        if (!DataRefreshPolicy.isStale(updatedAt, 12L * 60 * 60 * 1000)) return
        startRefresh()
    }

    fun onRefresh() {
        startRefresh()
    }

    private fun startRefresh() {
        val generation = ++requestGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loadGrades(isRefresh = _uiState.value.gradesBySemester.isNotEmpty(), generation)
        }
    }

    fun selectSemester(semesterId: Int) {
        _uiState.update { it.withSelectedSemester(semesterId) }
    }

    fun onGradeClicked(grade: Grade) {
        _uiState.update { it.copy(selectedGrade = grade) }
    }

    fun onDismissGradeDetail() {
        _uiState.update { it.copy(selectedGrade = null) }
    }

    /** 从 SessionManager 恢复缓存的成绩数据 */
    private suspend fun loadFromCache(): Boolean {
        val sm = sessionManager ?: return false
        val gradesJson = sm.getGradesJson() ?: return false
        return try {
            withContext(Dispatchers.IO) {
                val resp = gson.fromJson(gradesJson, com.ahu_plus.data.model.jw.GradeResponse::class.java)
                val gpaJson = sm.getGpaMetadataJson()
                val gpa = if (gpaJson != null) {
                    runCatching { gson.fromJson(gpaJson, GpaMetadata::class.java) }.getOrNull()
                } else null

                val semesterIds = resp.semesterId2studentGrades?.keys
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.sortedDescending()
                    ?: emptyList()
                val gradesBySem = resp.semesterId2studentGrades.orEmpty()
                val defaultSem = semesterIds.firstOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        gradesBySemester = gradesBySem,
                        availableSemesterIds = semesterIds,
                        academicSemesters = resp.academicSemesters(),
                        selectedSemesterId = defaultSem,
                        semesterName = defaultSem?.let { id ->
                            gradesBySem[id.toString()]?.firstOrNull()?.semesterName
                                ?: resp.semesters?.firstOrNull { s -> s.id == id }?.nameZh
                        },
                        gpaMetadata = gpa,
                        dataStatus = DataSnapshotStatus.cache(sm.getGradesUpdatedAt()),
                        error = null,
                        needsLogin = false
                    )
                }
            }
            true
        } catch (_: Exception) { Log.w(TAG, "Failed to load cached grades"); false }
    }

    private suspend fun loadGrades(isRefresh: Boolean, generation: Long) {
        if (!isRefresh) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        } else {
            _uiState.update { it.copy(isRefreshing = true) }
        }
        val wasLoaded = _uiState.value.gradesBySemester.isNotEmpty()
        try {
            withContext(Dispatchers.IO) {
                // 并发请求：grades JSON + GPA HTML
                val (gradesResult, gpaResult) = coroutineScope {
                    val gradesDeferred = async {
                        jwAuthRepository.executeWithSessionRetry { gradeRepository.getGrades() }
                    }
                    val gpaDeferred = async {
                        jwAuthRepository.executeWithSessionRetry { gradeRepository.getGpaMetadata() }
                    }
                    gradesDeferred.await() to gpaDeferred.await()
                }

                gradesResult.fold(
                    onSuccess = { resp ->
                        if (generation != requestGeneration) return@fold
                        // 缓存到本地
                        val sm = sessionManager
                        if (sm != null) {
                            try {
                                sm.saveGradesJson(
                                    gson.toJson(resp),
                                    gpaResult.getOrNull()?.let { gson.toJson(it) }
                                )
                            } catch (_: Exception) { Log.w(TAG, "Failed to cache grades JSON") }
                        }

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
                                academicSemesters = resp.academicSemesters(),
                                selectedSemesterId = defaultSem,
                                semesterName = defaultSem?.let { id ->
                                    gradesBySem[id.toString()]?.firstOrNull()?.semesterName
                                        ?: resp.semesters?.firstOrNull { s -> s.id == id }?.nameZh
                                },
                                // I-012 fix: GPA fetch 失败时保留缓存的旧值，不用 null 覆盖
                                gpaMetadata = gpaResult.getOrNull() ?: it.gpaMetadata,
                                isRefreshing = false,
                                dataStatus = DataSnapshotStatus.network(
                                    sm?.getGradesUpdatedAt() ?: System.currentTimeMillis()
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
                                error = if (!wasLoaded) (e.message ?: "成绩加载失败") else it.error,
                                dataStatus = it.dataStatus?.withFailedRefresh(),
                                needsLogin = !wasLoaded &&
                                    (e is SessionExpiredException || e is JwAuthException),
                                // I-012 fix: grades 失败时不覆盖已缓存的 gpaMetadata
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
        private const val TAG = "GradeVM"
    }

    /**
     * 2026-06-23: SessionExpiredException 后,先尝试后台静默重连 + 重试一次。
     * 重连策略与 EmptyClassroomViewModel 一致:清 JW cookie → authenticate() →
     * trySimplifiedSso (用 CASTGC 换新 JW SESSION) → 失败时 fallback 到 performFullLogin。
     * 返回 null 表示重连失败,让原 onFailure 继续走 needsLogin 路径。
     */
    /** 应用重连+重试后的成绩结果。GPA 仍用之前缓存的值(I-012)。 */
    private suspend fun handleGradesResult(
        resp: com.ahu_plus.data.model.jw.GradeResponse,
        @Suppress("UNUSED_PARAMETER") wasLoaded: Boolean
    ) {
        val sm = sessionManager
        if (sm != null) {
            try {
                sm.saveGradesJson(gson.toJson(resp), null)
            } catch (_: Exception) { Log.w(TAG, "Failed to cache grades JSON") }
        }
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
                academicSemesters = resp.academicSemesters(),
                selectedSemesterId = defaultSem,
                semesterName = defaultSem?.let { id ->
                    gradesBySem[id.toString()]?.firstOrNull()?.semesterName
                        ?: resp.semesters?.firstOrNull { s -> s.id == id }?.nameZh
                },
                error = null,
                needsLogin = false
            )
        }
    }
}

private fun GradeResponse.academicSemesters(): List<SemesterInfo> =
    (semesters.orEmpty() + id2semesters.orEmpty().values)
        .sortedWith(
            compareByDescending<SemesterInfo> {
                !it.startDate.isNullOrBlank() && !it.endDate.isNullOrBlank()
            }.thenByDescending { it.id },
        )
        .distinctBy { it.id ?: it.nameZh }
        .sortedByDescending { it.id }

data class GradeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val gradesBySemester: Map<String, List<Grade>> = emptyMap(),
    val availableSemesterIds: List<Int> = emptyList(),
    val academicSemesters: List<SemesterInfo> = emptyList(),
    val selectedSemesterId: Int? = null,
    val semesterName: String? = null,
    val gpaMetadata: GpaMetadata? = null,
    val selectedGrade: Grade? = null,
    val error: String? = null,
    val dataStatus: DataSnapshotStatus? = null,
    val needsLogin: Boolean = false
) {
    val currentGrades: List<Grade>
        get() = selectedSemesterId?.let { gradesBySemester[it.toString()].orEmpty() }
            .orEmpty()
            .sortedBy { it.courseName.orEmpty() }
}

internal fun GradeUiState.withSelectedSemester(semesterId: Int): GradeUiState = copy(
    selectedSemesterId = semesterId,
    semesterName = gradesBySemester[semesterId.toString()]
        ?.firstNotNullOfOrNull { it.semesterName?.takeIf(String::isNotBlank) }
        ?: "学期 $semesterId",
)
