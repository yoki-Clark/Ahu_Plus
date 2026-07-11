package com.ahu_plus.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.model.JwcNotice
import com.ahu_plus.data.model.JwcNoticeDetail
import com.ahu_plus.data.repository.JwcNoticeRepository
import com.ahu_plus.data.local.DataRefreshPolicy
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.GsonProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页 "通知公告" preview 的 ViewModel:
 * - 拉通知公告频道第 1 页,只取前 6 条
 * - 点击条目仍然在首页内嵌展开详情
 * - "更多" 按钮跳转独立二级页 [JwcNoticeListViewModel] 处理
 */
class JwcNoticeViewModel(
    private val repository: JwcNoticeRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        JwcNoticeUiState(
            noticeFetchUrl = repository.listUrl(1)
        )
    )
    val uiState: StateFlow<JwcNoticeUiState> = _uiState.asStateFlow()

    init {
        val cached = sessionManager.getJwcNoticeJson()?.let { raw ->
            runCatching { GsonProvider.instance.fromJson(raw, Array<JwcNotice>::class.java).toList() }
                .getOrNull()
        }.orEmpty()
        if (cached.isNotEmpty()) _uiState.update { it.copy(notices = cached) }
        if (DataRefreshPolicy.isStale(
                sessionManager.getJwcNoticeUpdatedAt(), 30L * 60 * 1000
            )) loadNotices()
    }

    fun loadNotices() {
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                noticeFetchUrl = repository.listUrl(1),
                noticeFetchKey = it.noticeFetchKey + 1
            )
        }
    }

    fun toggleNotice(notice: JwcNotice) {
        val expandedUrl = _uiState.value.expandedUrl
        if (expandedUrl == notice.url) {
            _uiState.update { it.copy(expandedUrl = null) }
            return
        }

        _uiState.update { it.copy(expandedUrl = notice.url) }
        if (_uiState.value.details[notice.url] !is NoticeDetailState.Success) {
            loadDetail(notice)
        }
    }

    fun onNoticeHtmlLoaded(fetchUrl: String, html: String) {
        if (fetchUrl != _uiState.value.noticeFetchUrl) return
        viewModelScope.launch {
            val notices = withContext(Dispatchers.Default) {
                JwcNoticeRepository.parseNoticeList(html).take(PREVIEW_LIMIT)
            }
            if (notices.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "未读取到通知公告"
                    )
                }
            } else {
                sessionManager.saveJwcNoticeJson(GsonProvider.instance.toJson(notices))
                _uiState.update {
                    it.copy(
                        notices = notices,
                        isLoading = false,
                        error = null
                    )
                }
            }
        }
    }

    fun onNoticeHtmlError(fetchUrl: String, message: String) {
        if (fetchUrl != _uiState.value.noticeFetchUrl) return
        _uiState.update {
            it.copy(
                isLoading = false,
                error = message
            )
        }
    }

    fun onDetailHtmlLoaded(url: String, html: String) {
        viewModelScope.launch {
            val notice = _uiState.value.notices.firstOrNull { it.url == url }
                ?: return@launch
            val detail = withContext(Dispatchers.Default) {
                JwcNoticeRepository.parseNoticeDetail(html, notice)
            }
            _uiState.update {
                it.copy(
                    detailFetchUrl = null,
                    details = it.details + (url to NoticeDetailState.Success(detail))
                )
            }
        }
    }

    fun onDetailHtmlError(url: String, message: String) {
        _uiState.update {
            it.copy(
                detailFetchUrl = null,
                details = it.details + (url to NoticeDetailState.Error(message))
            )
        }
    }

    private fun loadDetail(notice: JwcNotice) {
        _uiState.update {
            it.copy(
                detailFetchUrl = notice.url,
                details = it.details + (notice.url to NoticeDetailState.Loading)
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
    val noticeFetchUrl: String,
    val noticeFetchKey: Int = 0,
    val detailFetchUrl: String? = null
)

sealed interface NoticeDetailState {
    data object Loading : NoticeDetailState
    data class Success(val detail: JwcNoticeDetail) : NoticeDetailState
    data class Error(val message: String) : NoticeDetailState
}
