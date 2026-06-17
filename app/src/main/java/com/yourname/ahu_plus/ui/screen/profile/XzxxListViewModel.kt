package com.yourname.ahu_plus.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.model.XzxxLetter
import com.yourname.ahu_plus.data.repository.XzxxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class XzxxListViewModel(
    private val repository: XzxxRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(XzxxListUiState())
    val uiState: StateFlow<XzxxListUiState> = _uiState.asStateFlow()

    init { loadFirstPage() }

    fun loadFirstPage() {
        _uiState.update {
            it.copy(
                letters = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                error = null,
                currentPage = 0,
                hasMore = true,
                letterFetchUrl = repository.listUrl(1),
                letterFetchKey = it.letterFetchKey + 1
            )
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoading || state.isLoadingMore) return
        val nextPage = state.currentPage + 1
        _uiState.update {
            it.copy(
                isLoadingMore = true,
                error = null,
                letterFetchUrl = repository.listUrl(nextPage),
                letterFetchKey = it.letterFetchKey + 1
            )
        }
    }

    /** 列表 HTML 加载回调。非首页自动追加到已有列表尾部。 */
    fun onLetterHtmlLoaded(fetchUrl: String, html: String) {
        if (fetchUrl != _uiState.value.letterFetchUrl) return
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.Default) {
                XzxxRepository.parseLetterList(html, "https://www6.ahu.edu.cn")
            }
            val nextPageUrl = withContext(Dispatchers.Default) {
                XzxxRepository.parseNextPageUrl(html, _uiState.value.currentPage + 1)
            }
            val incomingPage = pageFromUrl(fetchUrl)

            if (parsed.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false, isLoadingMore = false,
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
                    val existing = prev.letters.map { it.url }.toHashSet()
                    prev.letters + parsed.filterNot { it.url in existing }
                }
                prev.copy(
                    letters = merged,
                    isLoading = false, isLoadingMore = false,
                    currentPage = maxOf(prev.currentPage, incomingPage),
                    hasMore = nextPageUrl != null
                )
            }
        }
    }

    fun onLetterHtmlError(fetchUrl: String, message: String) {
        if (fetchUrl != _uiState.value.letterFetchUrl) return
        val isFirstLoad = _uiState.value.currentPage == 0
        _uiState.update {
            if (isFirstLoad) it.copy(isLoading = false, error = message)
            else it.copy(isLoadingMore = false, error = message)
        }
    }

    /** 展开/折叠一条信件的详情。 */
    fun toggleDetail(letter: XzxxLetter) {
        val idx = _uiState.value.letters.indexOfFirst { it.contentId == letter.contentId }
        if (idx < 0) return
        val cur = _uiState.value.letters[idx]
        if (cur.isExpanded) {
            _uiState.update {
                it.copy(letters = it.letters.toMutableList().also { l ->
                    l[idx] = cur.copy(detail = null, detailError = null)
                })
            }
        } else {
            _uiState.update {
                it.copy(detailFetchUrl = repository.detailUrl(letter.contentId),
                    detailFetchKey = it.detailFetchKey + 1)
            }
        }
    }

    fun onDetailHtmlLoaded(fetchUrl: String, html: String) {
        if (fetchUrl != _uiState.value.detailFetchUrl) return
        viewModelScope.launch {
            val detail = withContext(Dispatchers.Default) {
                XzxxRepository.parseLetterDetail(html)
            }
            val cid = Regex("""contentid=(\d+)""").find(fetchUrl)?.groupValues?.get(1).orEmpty()
            val idx = _uiState.value.letters.indexOfFirst { it.contentId == cid }
            if (idx < 0) return@launch
            _uiState.update {
                it.copy(
                    letters = it.letters.toMutableList().also { l ->
                        l[idx] = if (detail != null) l[idx].copy(detail = detail)
                        else l[idx].copy(detailError = "暂未获取到内容")
                    },
                    detailFetchUrl = null
                )
            }
        }
    }

    fun onDetailHtmlError(fetchUrl: String, message: String) {
        val cid = Regex("""contentid=(\d+)""").find(fetchUrl)?.groupValues?.get(1).orEmpty()
        val idx = _uiState.value.letters.indexOfFirst { it.contentId == cid }
        if (idx < 0) return
        _uiState.update {
            it.copy(
                letters = it.letters.toMutableList().also { l ->
                    l[idx] = l[idx].copy(detailError = message)
                },
                detailFetchUrl = null
            )
        }
    }

    private fun pageFromUrl(url: String): Int =
        Regex("""page=(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""list(\d+)\.htm""", RegexOption.IGNORE_CASE).find(url)
                ?.groupValues?.get(1)?.toIntOrNull()
            ?: 1
}

data class XzxxListUiState(
    val letters: List<XzxxLetter> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0,
    val hasMore: Boolean = true,
    val letterFetchUrl: String? = null,
    val letterFetchKey: Int = 0,
    val detailFetchUrl: String? = null,
    val detailFetchKey: Int = 0
)
