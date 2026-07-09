package com.ahu_plus.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.model.JwcNotice
import com.ahu_plus.data.model.JwcNoticeDetail
import com.ahu_plus.data.repository.JwcNoticeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "通知公告" 二级页 ViewModel:
 * - 进入即拉第 1 页
 * - 滚到底自动加载下一页
 * - 复用首页已有的 WebView 反爬加载器(由 Screen 提供 `JwcHtmlLoader`,回调灌进来)
 * - 点击条目由 Screen 负责调用浏览器打开
 */
class JwcNoticeListViewModel(
    private val repository: JwcNoticeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(JwcNoticeListUiState())
    val uiState: StateFlow<JwcNoticeListUiState> = _uiState.asStateFlow()

    init {
        loadFirstPage()
    }

    /** 首次进入或下拉刷新:清空已有数据,重新加载第 1 页。 */
    fun loadFirstPage() {
        _uiState.update {
            it.copy(
                notices = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                error = null,
                currentPage = 0,
                hasMore = true,
                noticeFetchUrl = repository.listUrl(1),
                noticeFetchKey = it.noticeFetchKey + 1
            )
        }
    }

    /** 列表滚到底时调用:加载下一页。 */
    fun loadNextPage() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoading || state.isLoadingMore) return
        val nextPage = state.currentPage + 1
        _uiState.update {
            it.copy(
                isLoadingMore = true,
                error = null,
                noticeFetchUrl = repository.listUrl(nextPage),
                noticeFetchKey = it.noticeFetchKey + 1
            )
        }
    }

    /** WebView 加载完成回调:解析 HTML 并累加条目。 */
    fun onNoticeHtmlLoaded(fetchUrl: String, html: String) {
        if (fetchUrl != _uiState.value.noticeFetchUrl) return
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.Default) {
                JwcNoticeRepository.parseNoticeList(html)
            }
            val nextPagePath = withContext(Dispatchers.Default) {
                JwcNoticeRepository.parseNextPagePath(html)
            }
            val incomingPage = pageFromUrl(fetchUrl)

            if (parsed.isEmpty()) {
                // 当前页没解析到东西:作为分页终点处理,保留已有列表
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = false,
                        currentPage = maxOf(it.currentPage, incomingPage)
                    )
                }
                return@launch
            }

            val isFirstLoad = _uiState.value.currentPage == 0
            _uiState.update { prev ->
                val merged = if (isFirstLoad) {
                    parsed
                } else {
                    // 拼接前按 url 去重,避免同一篇被重复展示
                    val existing = prev.notices.map { it.url }.toHashSet()
                    prev.notices + parsed.filterNot { it.url in existing }
                }
                prev.copy(
                    notices = merged,
                    isLoading = false,
                    isLoadingMore = false,
                    currentPage = maxOf(prev.currentPage, incomingPage),
                    hasMore = nextPagePath != null
                )
            }
        }
    }

    /** WebView 加载失败回调:仅对"首次加载"置为错误态;翻页失败保留旧数据。 */
    fun onNoticeHtmlError(fetchUrl: String, message: String) {
        if (fetchUrl != _uiState.value.noticeFetchUrl) return
        val isFirstLoad = _uiState.value.currentPage == 0
        _uiState.update {
            if (isFirstLoad) {
                it.copy(isLoading = false, error = message)
            } else {
                it.copy(isLoadingMore = false, error = message)
            }
        }
    }

    /** 列表项点击后在模块内打开详情,由隐藏 WebView 拉取正文 HTML。 */
    fun openNotice(notice: JwcNotice) {
        val detailState = _uiState.value.details[notice.url]
        _uiState.update {
            it.copy(
                selectedNotice = notice,
                detailFetchUrl = if (detailState is NoticeDetailState.Success) null else notice.url,
                detailFetchKey = if (detailState is NoticeDetailState.Success) {
                    it.detailFetchKey
                } else {
                    it.detailFetchKey + 1
                },
                details = if (detailState is NoticeDetailState.Success) {
                    it.details
                } else {
                    it.details + (notice.url to NoticeDetailState.Loading)
                }
            )
        }
    }

    fun closeNotice() {
        _uiState.update { it.copy(selectedNotice = null, detailFetchUrl = null) }
    }

    fun retryDetail() {
        val notice = _uiState.value.selectedNotice ?: return
        _uiState.update {
            it.copy(
                detailFetchUrl = notice.url,
                detailFetchKey = it.detailFetchKey + 1,
                details = it.details + (notice.url to NoticeDetailState.Loading)
            )
        }
    }

    fun onDetailHtmlLoaded(url: String, html: String) {
        if (url != _uiState.value.detailFetchUrl) return
        viewModelScope.launch {
            val notice = _uiState.value.selectedNotice?.takeIf { it.url == url }
                ?: _uiState.value.notices.firstOrNull { it.url == url }
                ?: return@launch
            val detail: JwcNoticeDetail = withContext(Dispatchers.Default) {
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
        if (url != _uiState.value.detailFetchUrl) return
        _uiState.update {
            it.copy(
                detailFetchUrl = null,
                details = it.details + (url to NoticeDetailState.Error(message))
            )
        }
    }

    private fun pageFromUrl(url: String): Int {
        val match = Regex("""list(\d+)\.htm""", RegexOption.IGNORE_CASE).find(url)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }
}

data class JwcNoticeListUiState(
    val notices: List<JwcNotice> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0,
    val hasMore: Boolean = true,
    val noticeFetchUrl: String? = null,
    val noticeFetchKey: Int = 0,
    val selectedNotice: JwcNotice? = null,
    val details: Map<String, NoticeDetailState> = emptyMap(),
    val detailFetchUrl: String? = null,
    val detailFetchKey: Int = 0
)
