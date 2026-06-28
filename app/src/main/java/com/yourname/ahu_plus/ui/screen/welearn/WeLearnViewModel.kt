package com.yourname.ahu_plus.ui.screen.welearn

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.AhuPlusApplication
import com.yourname.ahu_plus.data.model.WeLearnCourse
import com.yourname.ahu_plus.data.model.WeLearnStudyUiState
import com.yourname.ahu_plus.data.model.WeLearnUnitScos
import com.yourname.ahu_plus.data.repository.WeLearnRepository
import java.io.IOException
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

    // 课程详情(单元+章节树)
    data class TreeUiState(
        val loading: Boolean = false,
        // 当前树所属的课程;相同 cid 不重复拉(避免"去刷课→返回"时整树重置 + 折叠态丢失)
        val cid: String? = null,
        val units: List<WeLearnUnitScos> = emptyList(),
        // 折叠态存 VM(UnitBlock 重组 / 屏重挂都不丢)
        val collapsedUnits: Set<Int> = emptySet(),
        val error: String? = null,
        val needsLogin: Boolean = false,
    )

    private val _treeState = MutableStateFlow(TreeUiState())
    val treeState: StateFlow<TreeUiState> = _treeState.asStateFlow()

    // 透传 StudyRepository.studyState 给 UI
    val studyState: StateFlow<WeLearnStudyUiState> = studyRepo.studyState
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeLearnStudyUiState())

    // 最近一次登录结果(成功: null,失败: 错误信息)
    private val _lastLoginError = MutableStateFlow<String?>(null)
    val lastLoginError: StateFlow<String?> = _lastLoginError.asStateFlow()
    private val _loggingIn = MutableStateFlow(false)
    val loggingIn: StateFlow<Boolean> = _loggingIn.asStateFlow()

    val isLoggedIn: Boolean get() = authRepo.isLoggedIn()

    /** 是否存有账密可供自动登录(对齐 Chaoxing 的 hasCxCredentials 语义) */
    val hasSavedCredentials: Boolean get() = app.sessionManager.hasWeLearnCredentials()

    /** 保存的账号(手机号),供登录卡预填 */
    val savedUsername: String? get() = app.sessionManager.getWeLearnUsername()

    init {
        when {
            isLoggedIn -> refreshCourses()
            // 没登录但有账密 → 静默自动登录(对齐 Chaoxing.checkLogin 自动续期)
            hasSavedCredentials -> silentRelogin()
        }
    }

    /**
     * 用 SessionManager 里存的账密自动登录。
     * 与 [WeLearnAuthRepository.autoLoginIfPossible] 同语义,但走 VM 协程,
     * 失败会写回 lastLoginError 供 UI 提示。
     */
    fun silentRelogin() {
        viewModelScope.launch {
            _loggingIn.value = true
            _lastLoginError.value = null
            val ok = authRepo.autoLoginIfPossible()
            _loggingIn.value = false
            if (ok) {
                refreshCourses()
            } else {
                _lastLoginError.value = "自动登录失败，请重新输入密码"
            }
        }
    }

    fun refreshCourses() {
        viewModelScope.launch {
            _coursesState.value = _coursesState.value.copy(loading = true, error = null)
            val res = retryWithRelogin { queryRepo.getCourses() }
            _coursesState.value = res.fold(
                onSuccess = { CoursesUiState(loading = false, courses = it) },
                onFailure = {
                    // session 过期 → 静默重登已失败,引导登录
                    val msg = it.message.orEmpty()
                    CoursesUiState(
                        loading = false,
                        error = msg.takeIf { !it.startsWith(WeLearnRepository.SESSION_EXPIRED_PREFIX) },
                        needsLogin = msg.startsWith(WeLearnRepository.SESSION_EXPIRED_PREFIX),
                    )
                },
            )
        }
    }

    /**
     * 失败时若异常 message 以 [WeLearnRepository.SESSION_EXPIRED_PREFIX] 开头且有账密,
     * 静默调 [WeLearnAuthRepository.autoLoginIfPossible] 重登一次再重试。
     * 重登也失败或原始异常非 session 过期 → 透传原 Result。
     * ponytail: 不加 Mutex,小概率并发双重 POST 接受(login idempotent,cookie jar 后写覆盖)。
     */
    private suspend fun <T> retryWithRelogin(block: suspend () -> Result<T>): Result<T> {
        val first = block()
        if (first.isSuccess) return first
        val msg = first.exceptionOrNull()?.message.orEmpty()
        if (!msg.startsWith(WeLearnRepository.SESSION_EXPIRED_PREFIX) || !hasSavedCredentials) return first
        if (!authRepo.autoLoginIfPossible()) {
            return Result.failure(IOException("${WeLearnRepository.SESSION_EXPIRED_PREFIX} relogin-fail"))
        }
        return block()  // 重试一次,仍失败透传
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

    /**
     * 加载课程下全部单元+章节,供课程详情页(WeLearnCourseDetailScreen)显示。
     * 单元×scoLeaves 失败由 Repository 兜底为空,这里只关心整体成功/失败。
     */
    fun loadCourseTree(cid: String) {
        // ponytail: 同一 cid 已加载过则跳过;Detail 屏重挂(去刷课→返回)时不再重置树
        if (_treeState.value.cid == cid && _treeState.value.units.isNotEmpty()) return
        viewModelScope.launch {
            // 切换课程:清折叠态(单元 idx 只在课程内唯一,跨课复用会误折叠)
            _treeState.value = _treeState.value.copy(
                loading = true, cid = cid, error = null, needsLogin = false, collapsedUnits = emptySet(),
            )
            val res = retryWithRelogin { queryRepo.getCourseTreeWithScos(cid) }
            val msg = res.exceptionOrNull()?.message.orEmpty()
            _treeState.value = res.fold(
                onSuccess = {
                    _treeState.value.copy(loading = false, cid = cid, units = it, error = null, needsLogin = false)
                },
                onFailure = {
                    _treeState.value.copy(
                        loading = false,
                        cid = cid,
                        error = msg.takeIf { !it.startsWith(WeLearnRepository.SESSION_EXPIRED_PREFIX) } ?: "登录已过期",
                        needsLogin = msg.startsWith(WeLearnRepository.SESSION_EXPIRED_PREFIX),
                    )
                },
            )
        }
    }

    fun toggleUnitExpanded(unitIdx: Int) {
        val cur = _treeState.value.collapsedUnits
        _treeState.value = _treeState.value.copy(
            collapsedUnits = if (unitIdx in cur) cur - unitIdx else cur + unitIdx,
        )
    }

    /** 开始刷课(供 StudyScreen 调用,内部启动 Service) */
    fun startStudying(
        context: Context,
        cid: String,
        accuracy: String = "100",
        fullMode: Boolean = false,
        unitIndices: IntArray? = null,  // 2026-06-28:null=全部单元,IntArray=选中的单元 idx
    ) {
        com.yourname.ahu_plus.service.WeLearnStudyService.start(context, cid, accuracy, fullMode, unitIndices)
    }

    fun stopStudying(context: Context) {
        com.yourname.ahu_plus.service.WeLearnStudyService.stop(context)
    }

    fun clearLogin() {
        viewModelScope.launch {
            authRepo.clearCookies()
            app.sessionManager.clearWeLearnCredentials()
            app.sessionManager.saveWeLearnCookies("")
            _coursesState.value = CoursesUiState(needsLogin = true)
        }
    }

    /** 登录成功后,UI 切换到课程列表时调一下,清掉错误态 */
    fun consumeLoginResult() {
        _lastLoginError.value = null
    }
}