package com.ahu_plus.ui.screen.cprog

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.data.model.CProgExamRow
import com.ahu_plus.data.model.CProgPaper
import com.ahu_plus.data.model.CProgSection
import com.ahu_plus.data.model.CProgSubject
import com.ahu_plus.data.repository.CProgRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 大学计算机平台 ViewModel。三层页面栈:登录 → 列表 → 整卷。
 *
 * 登录闭环:fetchCaptcha 展示验证码 → 用户输码 → login。
 * 列表:科目下拉 + 分类计数 + jqGrid 分页。
 * 整卷:仅练习(isPracticeSafe)可进,查看题干 + 参考答案。
 */
class CProgViewModel(
    private val app: AhuPlusApplication,
) : ViewModel() {

    private val auth = app.cProgAuthRepository
    private val repo = app.cProgRepository

    // ── 页面栈 ───────────────────────────────────────────────
    sealed class Page {
        data object Login : Page()
        data object List : Page()
        data class Paper(val exam: CProgExamRow) : Page()
    }

    private val _page = MutableStateFlow<Page>(if (auth.isLoggedIn()) Page.List else Page.Login)
    val page: StateFlow<Page> = _page.asStateFlow()

    // ── 登录态 ───────────────────────────────────────────────
    data class LoginUiState(
        val captcha: Bitmap? = null,
        val captchaLoading: Boolean = false,
        val loggingIn: Boolean = false,
        val error: String? = null,
        val username: String = "",
        val password: String = "",
    )

    private val _login = MutableStateFlow(
        LoginUiState(
            username = auth.savedUsername().orEmpty(),
            password = auth.savedPassword().orEmpty(),
        )
    )
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    // ── 列表态 ───────────────────────────────────────────────
    data class ListUiState(
        val loading: Boolean = false,
        val sections: List<CProgSection> = emptyList(),
        val subjects: List<CProgSubject> = emptyList(),
        val selectedSubjectId: String = "",      // ""=全部
        val exams: List<CProgExamRow> = emptyList(),
        val page: Int = 1,
        val records: Int = 0,
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val needsLogin: Boolean = false,
    )

    private val _list = MutableStateFlow(ListUiState())
    val list: StateFlow<ListUiState> = _list.asStateFlow()

    // ── 整卷态 ───────────────────────────────────────────────
    data class PaperUiState(
        val loading: Boolean = false,
        val paper: CProgPaper? = null,
        val error: String? = null,
        val needsLogin: Boolean = false,
    )

    private val _paper = MutableStateFlow(PaperUiState())
    val paper: StateFlow<PaperUiState> = _paper.asStateFlow()

    companion object {
        private const val PAGE_ROWS = 50
    }

    init {
        if (auth.isLoggedIn()) refreshList()
    }

    // ── 登录动作 ─────────────────────────────────────────────
    fun onUsernameChange(v: String) { _login.value = _login.value.copy(username = v) }
    fun onPasswordChange(v: String) { _login.value = _login.value.copy(password = v) }

    /**
     * 刷新验证码图。
     * @param keepError true=保留现有错误文案(登录失败后换图,不能把错误抹掉);
     *                  false=用户主动点图刷新,清掉旧错误
     */
    fun refreshCaptcha(keepError: Boolean = false) {
        viewModelScope.launch {
            _login.value = _login.value.copy(
                captchaLoading = true,
                error = if (keepError) _login.value.error else null,
            )
            val r = auth.fetchCaptcha()
            _login.value = r.fold(
                onSuccess = { _login.value.copy(captcha = it, captchaLoading = false) },
                onFailure = {
                    _login.value.copy(
                        captchaLoading = false,
                        // 验证码都拉不到 → 网络问题,覆盖任何旧错误(这是更根本的失败)
                        error = "验证码加载失败：${it.message ?: "请确认已连接校园网"}",
                    )
                },
            )
        }
    }

    fun login(captcha: String) {
        val s = _login.value
        if (s.username.isBlank() || s.password.isBlank() || captcha.isBlank()) {
            _login.value = s.copy(error = "用户名、密码、验证码都要填")
            return
        }
        viewModelScope.launch {
            _login.value = s.copy(loggingIn = true, error = null)
            val r = auth.login(s.username.trim(), s.password.trim(), captcha.trim())
            r.fold(
                onSuccess = {
                    _login.value = _login.value.copy(loggingIn = false, error = null, captcha = null)
                    _page.value = Page.List
                    refreshList()
                },
                onFailure = {
                    // 先把错误写进 state,再换验证码图(keepError 保住错误文案)
                    _login.value = _login.value.copy(loggingIn = false, error = it.message ?: "登录失败")
                    refreshCaptcha(keepError = true)
                },
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            auth.clearSession()
            _list.value = ListUiState()
            _paper.value = PaperUiState()
            _login.value = _login.value.copy(captcha = null, error = null)
            _page.value = Page.Login
        }
    }

    // ── 列表动作 ─────────────────────────────────────────────
    fun refreshList() {
        viewModelScope.launch {
            _list.value = _list.value.copy(loading = true, error = null, needsLogin = false)
            // 并行拉分类计数 + 科目(首次)
            val sections = repo.getSections().getOrDefault(emptyList())
            val subjects = if (_list.value.subjects.isEmpty())
                repo.getSubjects().getOrDefault(emptyList()) else _list.value.subjects
            val examRes = repo.listExams(subjectId = _list.value.selectedSubjectId, page = 1, rows = PAGE_ROWS)
            examRes.fold(
                onSuccess = { pg ->
                    _list.value = _list.value.copy(
                        loading = false,
                        sections = sections,
                        subjects = subjects,
                        exams = pg.rows,
                        page = 1,
                        records = pg.records,
                        hasMore = pg.rows.size < pg.records,
                        error = null,
                        needsLogin = false,
                    )
                },
                onFailure = { handleListError(it) },
            )
        }
    }

    fun loadMore() {
        val s = _list.value
        if (s.loadingMore || !s.hasMore || s.loading) return
        viewModelScope.launch {
            _list.value = s.copy(loadingMore = true)
            val next = s.page + 1
            repo.listExams(subjectId = s.selectedSubjectId, page = next, rows = PAGE_ROWS).fold(
                onSuccess = { pg ->
                    val merged = s.exams + pg.rows
                    _list.value = _list.value.copy(
                        loadingMore = false,
                        exams = merged,
                        page = next,
                        hasMore = merged.size < pg.records && pg.rows.isNotEmpty(),
                    )
                },
                onFailure = { _list.value = _list.value.copy(loadingMore = false) },
            )
        }
    }

    fun selectSubject(subjectId: String) {
        if (_list.value.selectedSubjectId == subjectId) return
        _list.value = _list.value.copy(selectedSubjectId = subjectId)
        refreshList()
    }

    private fun handleListError(t: Throwable) {
        val expired = t.message == CProgRepository.SESSION_EXPIRED
        _list.value = _list.value.copy(
            loading = false,
            error = if (expired) null else (t.message ?: "加载失败"),
            needsLogin = expired,
        )
        if (expired) _page.value = Page.Login
    }

    // ── 整卷动作 ─────────────────────────────────────────────
    fun openPaper(exam: CProgExamRow) {
        _page.value = Page.Paper(exam)
        _paper.value = PaperUiState(loading = true)
        viewModelScope.launch {
            repo.getPaper(exam.examId).fold(
                onSuccess = { _paper.value = PaperUiState(paper = it) },
                onFailure = {
                    val expired = it.message == CProgRepository.SESSION_EXPIRED
                    _paper.value = PaperUiState(
                        error = if (expired) null else (it.message ?: "试卷加载失败"),
                        needsLogin = expired,
                    )
                    if (expired) _page.value = Page.Login
                },
            )
        }
    }

    fun backToList() {
        _page.value = Page.List
        _paper.value = PaperUiState()
    }
}
