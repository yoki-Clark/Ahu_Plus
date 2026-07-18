package com.ahu_plus.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.local.DataRefreshPolicy
import com.ahu_plus.data.local.JwcNoticeCache
import com.ahu_plus.data.model.JwcNotice
import com.ahu_plus.data.model.JwcNoticeDetail
import com.ahu_plus.data.repository.JwcNoticeRepository
import com.ahu_plus.data.repository.JwcWafChallengeRequiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 首页教务通告预览，网络传输由 Repository 完成，WebView 仅用于按需 WAF 校验。 */
class JwcNoticeViewModel(
    private val repository: JwcNoticeRepository,
    private val cache: JwcNoticeCache,
) : ViewModel() {
    private val _uiState = MutableStateFlow(JwcNoticeUiState())
    val uiState: StateFlow<JwcNoticeUiState> = _uiState.asStateFlow()
    private val pendingWafRetries = linkedMapOf<String, () -> Unit>()
    private var noticesJob: Job? = null

    init {
        val cached = cache.getJwcNoticeJson()?.let { raw ->
            runCatching { GsonProvider.instance.fromJson(raw, Array<JwcNotice>::class.java).toList() }
                .getOrNull()
        }.orEmpty()
        if (cached.isNotEmpty()) _uiState.update { it.copy(notices = cached) }
        if (DataRefreshPolicy.isStale(
                cache.getJwcNoticeUpdatedAt(),
                30L * 60 * 1000,
            )
        ) {
            loadNotices()
        }
    }

    fun loadNotices() {
        if (noticesJob?.isActive == true) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        fetchNotices()
    }

    fun toggleNotice(notice: JwcNotice) {
        if (_uiState.value.expandedUrl == notice.url) {
            _uiState.update { it.copy(expandedUrl = null) }
            return
        }
        _uiState.update { it.copy(expandedUrl = notice.url) }
        if (_uiState.value.details[notice.url] !is NoticeDetailState.Success &&
            _uiState.value.details[notice.url] !is NoticeDetailState.Loading
        ) {
            loadDetail(notice)
        }
    }

    fun onWafCookieCaptured(cookieHeader: String) {
        viewModelScope.launch {
            val accepted = runCatching { repository.acceptWafCookies(cookieHeader) }.getOrDefault(false)
            if (!accepted) {
                failWafBootstrap("教务处安全校验凭证无效，请重试")
                return@launch
            }
            val retries = pendingWafRetries.values.toList()
            pendingWafRetries.clear()
            _uiState.update { it.copy(wafChallengeUrl = null, error = null) }
            retries.forEach { it() }
        }
    }

    fun onWafBootstrapError(message: String) {
        failWafBootstrap(message)
    }

    private fun fetchNotices() {
        if (noticesJob?.isActive == true) return
        noticesJob = viewModelScope.launch {
            repository.getNotices(PREVIEW_LIMIT)
                .onSuccess { notices ->
                    cache.saveJwcNoticeJson(GsonProvider.instance.toJson(notices))
                    _uiState.update {
                        it.copy(notices = notices, isLoading = false, error = null)
                    }
                }
                .onFailure { error ->
                    handleFailure(
                        key = "notices",
                        error = error,
                        retry = ::fetchNotices,
                    ) {
                        _uiState.update {
                            it.copy(isLoading = false, error = error.message ?: "教务通告加载失败")
                        }
                    }
                }
        }
    }

    private fun loadDetail(notice: JwcNotice) {
        _uiState.update {
            it.copy(details = it.details + (notice.url to NoticeDetailState.Loading))
        }
        fetchDetail(notice)
    }

    private fun fetchDetail(notice: JwcNotice) {
        viewModelScope.launch {
            repository.getNoticeDetail(notice)
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(details = it.details + (notice.url to NoticeDetailState.Success(detail)))
                    }
                }
                .onFailure { error ->
                    handleFailure(
                        key = "detail:${notice.url}",
                        error = error,
                        retry = { fetchDetail(notice) },
                    ) {
                        _uiState.update {
                            it.copy(
                                details = it.details + (
                                    notice.url to NoticeDetailState.Error(
                                        error.message ?: "通知详情加载失败",
                                    )
                                ),
                            )
                        }
                    }
                }
        }
    }

    private fun handleFailure(
        key: String,
        error: Throwable,
        retry: () -> Unit,
        onRegularFailure: () -> Unit,
    ) {
        if (error is JwcWafChallengeRequiredException) {
            pendingWafRetries[key] = retry
            _uiState.update {
                it.copy(
                    wafChallengeUrl = repository.challengeUrl(),
                    wafBootstrapKey = it.wafBootstrapKey + 1,
                )
            }
        } else {
            onRegularFailure()
        }
    }

    private fun failWafBootstrap(message: String) {
        pendingWafRetries.clear()
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                error = message,
                wafChallengeUrl = null,
                details = state.details.mapValues { (_, detailState) ->
                    if (detailState is NoticeDetailState.Loading) NoticeDetailState.Error(message) else detailState
                },
            )
        }
    }

    companion object {
        private const val PREVIEW_LIMIT = 6
    }
}

data class JwcNoticeUiState(
    val notices: List<JwcNotice> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val expandedUrl: String? = null,
    val details: Map<String, NoticeDetailState> = emptyMap(),
    val wafChallengeUrl: String? = null,
    val wafBootstrapKey: Int = 0,
)

sealed interface NoticeDetailState {
    data object Loading : NoticeDetailState
    data class Success(val detail: JwcNoticeDetail) : NoticeDetailState
    data class Error(val message: String) : NoticeDetailState
}
