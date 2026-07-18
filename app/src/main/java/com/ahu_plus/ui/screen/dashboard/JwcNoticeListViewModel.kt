package com.ahu_plus.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.local.JwcNoticeCache
import com.ahu_plus.data.model.JwcNotice
import com.ahu_plus.data.model.JwcNoticeAttachment
import com.ahu_plus.data.model.JwcNoticeDetail
import com.ahu_plus.data.repository.JwcNoticeRepository
import com.ahu_plus.data.repository.JwcWafChallengeRequiredException
import com.google.gson.reflect.TypeToken
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 教务通告列表与详情，WebView 只在 Repository 报告 412 时完成一次校验。 */
class JwcNoticeListViewModel(
    private val repository: JwcNoticeRepository,
    private val cache: JwcNoticeCache,
) : ViewModel() {
    private val _uiState = MutableStateFlow(JwcNoticeListUiState())
    val uiState: StateFlow<JwcNoticeListUiState> = _uiState.asStateFlow()
    private val cachedDetails = mutableMapOf<String, CachedNoticeDetail>()
    private val pendingWafRetries = linkedMapOf<String, () -> Unit>()

    init {
        cache.getJwcNoticeJson()?.let { raw ->
            runCatching {
                GsonProvider.instance.fromJson(raw, Array<JwcNotice>::class.java).toList()
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { notices ->
                _uiState.update { it.copy(notices = notices) }
            }
        }
        cache.getJwcNoticeDetailsJson()?.let { raw ->
            runCatching {
                val type = object : TypeToken<Map<String, CachedNoticeDetail>>() {}.type
                val parsed: Map<String, CachedNoticeDetail> = GsonProvider.instance.fromJson(raw, type)
                val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
                cachedDetails.putAll(parsed.filterValues { it.updatedAt >= cutoff })
                _uiState.update { state ->
                    state.copy(details = cachedDetails.mapValues { NoticeDetailState.Success(it.value.detail) })
                }
            }
        }
    }

    fun activate() {
        if (_uiState.value.currentPage == 0 && !_uiState.value.isLoading) loadFirstPage()
    }

    fun loadFirstPage() {
        _uiState.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                error = null,
                currentPage = 0,
                hasMore = true,
            )
        }
        fetchPage(page = 1, replace = true)
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoading || state.isLoadingMore) return
        val nextPage = state.currentPage + 1
        _uiState.update { it.copy(isLoadingMore = true, error = null) }
        fetchPage(page = nextPage, replace = false)
    }

    fun openNotice(notice: JwcNotice) {
        val cached = _uiState.value.details[notice.url]
        _uiState.update {
            it.copy(
                selectedNotice = notice,
                details = if (cached is NoticeDetailState.Success) {
                    it.details
                } else {
                    it.details + (notice.url to NoticeDetailState.Loading)
                },
            )
        }
        if (cached !is NoticeDetailState.Success) fetchDetail(notice)
    }

    fun closeNotice() {
        _uiState.update { it.copy(selectedNotice = null) }
    }

    fun retryDetail() {
        val notice = _uiState.value.selectedNotice ?: return
        _uiState.update {
            it.copy(details = it.details + (notice.url to NoticeDetailState.Loading))
        }
        fetchDetail(notice)
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

    suspend fun downloadAttachment(
        attachment: JwcNoticeAttachment,
        output: OutputStream,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<Long> = repository.downloadAttachment(attachment, output, onProgress)

    private fun fetchPage(page: Int, replace: Boolean) {
        viewModelScope.launch {
            repository.getNoticePage(page)
                .onSuccess { result ->
                    _uiState.update { previous ->
                        val merged = if (replace) {
                            result.notices
                        } else {
                            val existing = previous.notices.mapTo(hashSetOf()) { it.url }
                            previous.notices + result.notices.filterNot { it.url in existing }
                        }
                        previous.copy(
                            notices = merged,
                            isLoading = false,
                            isLoadingMore = false,
                            error = null,
                            currentPage = result.page,
                            hasMore = result.hasMore,
                        )
                    }
                }
                .onFailure { error ->
                    handleFailure(
                        key = "page:$page",
                        error = error,
                        retry = { fetchPage(page, replace) },
                    ) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                error = error.message ?: "教务通告加载失败",
                            )
                        }
                    }
                }
        }
    }

    private fun fetchDetail(notice: JwcNotice) {
        viewModelScope.launch {
            repository.getNoticeDetail(notice)
                .onSuccess { detail -> saveDetail(notice.url, detail) }
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

    private suspend fun saveDetail(url: String, detail: JwcNoticeDetail) {
        _uiState.update {
            it.copy(details = it.details + (url to NoticeDetailState.Success(detail)))
        }
        cachedDetails[url] = CachedNoticeDetail(detail, System.currentTimeMillis())
        cache.saveJwcNoticeDetailsJson(GsonProvider.instance.toJson(cachedDetails))
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
                isLoadingMore = false,
                error = message,
                wafChallengeUrl = null,
                details = state.details.mapValues { (_, detailState) ->
                    if (detailState is NoticeDetailState.Loading) NoticeDetailState.Error(message) else detailState
                },
            )
        }
    }
}

private data class CachedNoticeDetail(
    val detail: JwcNoticeDetail,
    val updatedAt: Long,
)

data class JwcNoticeListUiState(
    val notices: List<JwcNotice> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0,
    val hasMore: Boolean = true,
    val selectedNotice: JwcNotice? = null,
    val details: Map<String, NoticeDetailState> = emptyMap(),
    val wafChallengeUrl: String? = null,
    val wafBootstrapKey: Int = 0,
)
