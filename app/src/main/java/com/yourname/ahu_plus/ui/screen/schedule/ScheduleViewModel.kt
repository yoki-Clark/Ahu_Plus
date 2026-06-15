package com.yourname.ahu_plus.ui.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.local.CourseNoteRepository
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.jw.CourseActivity
import com.yourname.ahu_plus.data.model.jw.CourseDisplayItem
import com.yourname.ahu_plus.data.model.jw.CourseUnit
import com.yourname.ahu_plus.data.model.jw.GetDataLesson
import com.yourname.ahu_plus.data.model.jw.SemesterInfo
import com.yourname.ahu_plus.data.repository.CourseRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduleViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val courseRepository: CourseRepository,
    private val noteRepository: CourseNoteRepository,
    private val sessionManager: SessionManager? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        // 从持久化恢复课表布局偏好
        val sm = sessionManager
        if (sm != null) {
            _uiState.update {
                it.copy(
                    colWidthDp = sm.getScheduleColWidth(),
                    rowHeightDp = sm.getScheduleRowHeight(),
                    fontScale = sm.getScheduleFontScale(),
                )
            }
        }
        viewModelScope.launch {
            loadScheduleData()
        }
    }

    /**
     * 加载本学期课表。
     */
    private suspend fun loadScheduleData() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        val wasLoaded = _uiState.value.allActivities.isNotEmpty()
        try {
            withContext(Dispatchers.IO) {
                // Step 1: 认证
                val authResult = jwAuthRepository.authenticate()
                if (authResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            // 仅当本地无数据时才触发 needsLogin，避免刷新时跳转登录页
                            needsLogin = !wasLoaded,
                            error = "教务处认证失败: ${authResult.exceptionOrNull()?.message}"
                        )
                    }
                    return@withContext
                }

                // Step 2: 获取课表
                val result = courseRepository.getSchedule()
                result.fold(
                    onSuccess = { data ->
                        val displayItems = CourseRepository.toDisplayItems(
                            activities = data.activities,
                            selectedWeek = data.currentWeek,
                            getDataLessons = data.lessons
                        )
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = null,
                                studentName = data.studentName,
                                className = data.className,
                                department = data.department,
                                credits = data.credits,
                                allActivities = data.activities,
                                displayItems = displayItems,
                                unitTimes = data.unitTimes,
                                semester = data.semester,
                                currentWeek = data.currentWeek,
                                selectedWeek = data.currentWeek,
                                weekIndices = data.weekIndices,
                                lessons = data.lessons,
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isLoading = false, error = "课表加载失败: ${e.message}")
                        }
                    }
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isLoading = false, error = "未知错误: ${e.message}")
            }
        }
    }

    // ── 周切换 ─────────────────────────────────────

    fun onPreviousWeek() {
        val current = _uiState.value.selectedWeek
        val minWeek = _uiState.value.weekIndices.minOrNull() ?: 1
        if (current > minWeek) setSelectedWeek(current - 1)
    }

    fun onNextWeek() {
        val current = _uiState.value.selectedWeek
        val maxWeek = _uiState.value.weekIndices.maxOrNull() ?: 20
        if (current < maxWeek) setSelectedWeek(current + 1)
    }

    fun onWeekSelected(week: Int) {
        setSelectedWeek(week)
    }

    fun onRefresh() {
        viewModelScope.launch {
            loadScheduleData()
        }
    }

    // ── 课表显示设置 ─────────────────────────────────

    fun onToggleSettings() {
        _uiState.update { it.copy(showSettings = !it.showSettings) }
    }

    fun onColWidthChanged(value: Float) {
        _uiState.update { it.copy(colWidthDp = value) }
        viewModelScope.launch { sessionManager?.saveScheduleColWidth(value) }
    }

    fun onRowHeightChanged(value: Float) {
        _uiState.update { it.copy(rowHeightDp = value) }
        viewModelScope.launch { sessionManager?.saveScheduleRowHeight(value) }
    }

    fun onFontScaleChanged(value: Float) {
        _uiState.update { it.copy(fontScale = value) }
        viewModelScope.launch { sessionManager?.saveScheduleFontScale(value) }
    }

    fun onResetSettings() {
        _uiState.update { it.copy(colWidthDp = 64f, rowHeightDp = 56f, fontScale = 1.0f) }
        viewModelScope.launch {
            sessionManager?.saveScheduleColWidth(64f)
            sessionManager?.saveScheduleRowHeight(56f)
            sessionManager?.saveScheduleFontScale(1.0f)
        }
    }

    private fun setSelectedWeek(week: Int) {
        val data = _uiState.value
        val items = CourseRepository.toDisplayItems(
            activities = data.allActivities,
            selectedWeek = week,
            getDataLessons = data.lessons
        )
        _uiState.update { it.copy(selectedWeek = week, displayItems = items) }
    }

    // ── 课程详情 + 备注 ─────────────────────────────

    fun onCourseClicked(item: CourseDisplayItem) {
        viewModelScope.launch {
            val note = noteRepository.observeNote(item.lessonId).first()
            _uiState.update {
                it.copy(
                    selectedCourseDetail = CourseDetailUiModel(
                        item = item,
                        lessonDetail = item.lessonDetail,
                        noteDraft = note,
                    )
                )
            }
        }
    }

    fun onNoteDraftChanged(text: String) {
        _uiState.update {
            it.copy(
                selectedCourseDetail = it.selectedCourseDetail?.copy(noteDraft = text)
            )
        }
    }

    fun onNoteSave() {
        val detail = _uiState.value.selectedCourseDetail ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(selectedCourseDetail = detail.copy(isSaving = true))
            }
            noteRepository.saveNote(detail.item.lessonId, detail.noteDraft)
            _uiState.update { it.copy(selectedCourseDetail = null) }
        }
    }

    fun onDismissSheet() {
        _uiState.update { it.copy(selectedCourseDetail = null) }
    }
}

data class ScheduleUiState(
    val needsLogin: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val studentName: String? = null,
    val className: String? = null,
    val department: String? = null,
    val credits: Double? = null,
    val allActivities: List<CourseActivity> = emptyList(),
    val displayItems: List<CourseDisplayItem> = emptyList(),
    val unitTimes: List<CourseUnit> = emptyList(),
    val semester: SemesterInfo? = null,
    val currentWeek: Int = 1,
    val selectedWeek: Int = 1,
    val weekIndices: List<Int> = emptyList(),
    val lessons: List<GetDataLesson>? = null,

    /** 当前展示的课程详情 (null 表示 BottomSheet 不显示) */
    val selectedCourseDetail: CourseDetailUiModel? = null,

    // ── 课表显示设置 ─────────────────────────────────
    val colWidthDp: Float = 64f,
    val rowHeightDp: Float = 56f,
    val fontScale: Float = 1.0f,
    val showSettings: Boolean = false,
)

/**
 * 课程详情 UI 模型。
 *
 * 把 [CourseDisplayItem] + [GetDataLesson] 增强数据 + 备注草稿 合并为一个不可变快照,
 * 供 [CourseDetailSheet] 渲染。
 */
data class CourseDetailUiModel(
    val item: CourseDisplayItem,
    val lessonDetail: GetDataLesson?,
    val noteDraft: String = "",
    val isSaving: Boolean = false,
)
