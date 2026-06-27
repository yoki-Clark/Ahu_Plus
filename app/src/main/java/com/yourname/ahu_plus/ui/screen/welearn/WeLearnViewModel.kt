package com.yourname.ahu_plus.ui.screen.welearn

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.AhuPlusApplication
import com.yourname.ahu_plus.data.model.WeLearnCourse
import com.yourname.ahu_plus.data.model.WeLearnStudyUiState
import com.yourname.ahu_plus.data.repository.WeLearnRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * WeLearn Tab ViewModel (2026-06-27)。
 *
 * 三件事:
 *  1. 登录(账密 → AuthRepository.login,落 SessionManager)
 *  2. 拉课程(下拉刷新)
 *  3. 暴露 StudyRepository.studyState 给 StudyScreen 显示进度
 */
class WeLearnViewModel(
    private val app: AhuPlusApplication,
) : ViewModel() {

    private val authRepo = app.weLearnAuthRepository
    private val queryRepo = app.weLearnRepository
    private val studyRepo = app.weLearnStudyRepository

    // 课程列表 + 加载状态
    data class CoursesUiState(
        val loading: Boolean = false,
        val courses: List<WeLearnCourse> = emptyList(),
        val error: String? = null,
        val needsLogin: Boolean = false,
    )

    private val _coursesState = MutableStateFlow(CoursesUiState())
    val coursesState: StateFlow<CoursesUiState> = _coursesState.asStateFlow()

    // 透传 StudyRepository.studyState 给 UI
    val studyState: StateFlow<WeLearnStudyUiState> = studyRepo.studyState
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeLearnStudyUiState())

    // 最近一次登录结果(成功: null,失败: 错误信息)
    private val _lastLoginError = MutableStateFlow<String?>(null)
    val lastLoginError: StateFlow<String?> = _lastLoginError.asStateFlow()
    private val _loggingIn = MutableStateFlow(false)
    val loggingIn: StateFlow<Boolean> = _loggingIn.asStateFlow()

    val isLoggedIn: Boolean get() = authRepo.isLoggedIn()

    init {
        authRepo.loadPersistedCookies()
        if (isLoggedIn) refreshCourses()
    }

    fun refreshCourses() {
        viewModelScope.launch {
            _coursesState.value = _coursesState.value.copy(loading = true, error = null)
            val res = queryRepo.getCourses()
            _coursesState.value = res.fold(
                onSuccess = { CoursesUiState(loading = false, courses = it) },
                onFailure = {
                    // 401/未登录 → 引导登录
                    val msg = it.message.orEmpty()
                    CoursesUiState(loading = false, error = msg, needsLogin = msg.contains("HTTP 4") || msg.contains("登录"))
                },
            )
        }
    }

    /**
     * 登录。结果通过 lastLoginError 暴露(成功为 null)。
     */
    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loggingIn.value = true
            _lastLoginError.value = null
            val r = authRepo.login(username, password)
            r.fold(
                onSuccess = { _lastLoginError.value = null; refreshCourses() },
                onFailure = { _lastLoginError.value = it.message ?: "登录失败" },
            )
            _loggingIn.value = false
        }
    }

    /** 开始刷课(供 StudyScreen 调用,内部启动 Service) */
    fun startStudying(context: Context, cid: String, accuracy: String = "100") {
        com.yourname.ahu_plus.service.WeLearnStudyService.start(context, cid, accuracy)
    }

    fun stopStudying(context: Context) {
        com.yourname.ahu_plus.service.WeLearnStudyService.stop(context)
    }

    fun clearLogin() {
        viewModelScope.launch {
            authRepo.clearCookies()
            sessionManagerClear()
            _coursesState.value = CoursesUiState(needsLogin = true)
        }
    }

    /** 登录成功后,UI 切换到课程列表时调一下,清掉错误态 */
    fun consumeLoginResult() {
        _lastLoginError.value = null
    }

    private suspend fun sessionManagerClear() {
        app.sessionManager.clearWeLearnCredentials()
        app.sessionManager.saveWeLearnCookies("")
    }
}