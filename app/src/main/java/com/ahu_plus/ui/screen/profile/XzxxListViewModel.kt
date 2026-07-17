package com.ahu_plus.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.model.XzxxLetter
import com.ahu_plus.data.model.XzxxLetterDetail
import com.ahu_plus.data.model.XzxxSubmitRequest
import com.ahu_plus.data.model.XzxxSubmitResult
import com.ahu_plus.data.repository.XzxxRepository
import com.ahu_plus.data.repository.XzxxWafChallengeRequiredException
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class XzxxViewModel(
    private val repository: XzxxRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(XzxxUiState())
    val uiState: StateFlow<XzxxUiState> = _uiState.asStateFlow()

    private var pendingAfterWaf: (() -> Unit)? = null

    init {
        loadFirstPage()
    }

    fun loadFirstPage() {
        _uiState.update {
            it.copy(
                letters = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                loadMoreError = null,
                error = null,
                currentPage = 0,
                hasMore = true,
            )
        }
        viewModelScope.launch {
            runCatching { repository.getLetters(1) }
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            letters = page.letters,
                            isLoading = false,
                            currentPage = 1,
                            hasMore = page.hasMore,
                            expandedIds = it.expandedIds.intersect(page.letters.mapTo(hashSetOf()) { letter -> letter.contentId }),
                        )
                    }
                }
                .onFailure { throwable ->
                    handleFailure(throwable, retry = ::loadFirstPage) { message ->
                        _uiState.update { it.copy(isLoading = false, error = message) }
                    }
                }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoading || state.isLoadingMore) return
        val nextPage = state.currentPage + 1
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            runCatching { repository.getLetters(nextPage) }
                .onSuccess { page ->
                    _uiState.update { previous ->
                        val knownIds = previous.letters.mapTo(hashSetOf()) { it.contentId }
                        previous.copy(
                            letters = previous.letters + page.letters.filterNot { it.contentId in knownIds },
                            isLoadingMore = false,
                            currentPage = nextPage,
                            hasMore = page.hasMore,
                        )
                    }
                }
                .onFailure { throwable ->
                    handleFailure(throwable, retry = {
                        _uiState.update { it.copy(isLoadingMore = false) }
                        loadNextPage()
                    }) { message ->
                        _uiState.update { it.copy(isLoadingMore = false, loadMoreError = message) }
                    }
                }
        }
    }

    fun toggleDetail(letter: XzxxLetter) {
        val contentId = letter.contentId
        val state = _uiState.value
        if (contentId in state.expandedIds) {
            _uiState.update { it.copy(expandedIds = it.expandedIds - contentId) }
            return
        }
        _uiState.update { it.copy(expandedIds = it.expandedIds + contentId) }
        if (state.details[contentId] == null && contentId !in state.detailLoadingIds) {
            loadDetail(contentId)
        }
    }

    fun retryDetail(contentId: String) {
        _uiState.update { it.copy(detailErrors = it.detailErrors - contentId) }
        loadDetail(contentId)
    }

    private fun loadDetail(contentId: String) {
        _uiState.update {
            it.copy(
                detailLoadingIds = it.detailLoadingIds + contentId,
                detailErrors = it.detailErrors - contentId,
            )
        }
        viewModelScope.launch {
            runCatching { repository.getLetterDetail(contentId) }
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(
                            details = it.details + (contentId to detail),
                            detailLoadingIds = it.detailLoadingIds - contentId,
                        )
                    }
                }
                .onFailure { throwable ->
                    handleFailure(throwable, retry = { loadDetail(contentId) }) { message ->
                        _uiState.update {
                            it.copy(
                                detailLoadingIds = it.detailLoadingIds - contentId,
                                detailErrors = it.detailErrors + (contentId to message),
                            )
                        }
                    }
                }
        }
    }

    fun openCompose() {
        _uiState.update {
            it.copy(
                isComposeVisible = true,
                captchaBytes = null,
                captchaError = null,
                submitResult = null,
            )
        }
        prepareCompose()
    }

    fun closeCompose() {
        if (_uiState.value.isSubmitting) return
        _uiState.update {
            it.copy(
                isComposeVisible = false,
                captchaBytes = null,
                captchaLoading = false,
                captchaError = null,
                submitResult = null,
            )
        }
    }

    private fun prepareCompose() {
        _uiState.update { it.copy(captchaLoading = true, captchaError = null) }
        viewModelScope.launch {
            runCatching { repository.prepareCompose() }
                .onSuccess { captcha -> applyCaptcha(captcha.bytes) }
                .onFailure { throwable ->
                    handleFailure(throwable, retry = ::prepareCompose) { message ->
                        _uiState.update { it.copy(captchaLoading = false, captchaError = message) }
                    }
                }
        }
    }

    fun refreshCaptcha() {
        _uiState.update { it.copy(captchaLoading = true, captchaError = null) }
        viewModelScope.launch {
            runCatching { repository.refreshCaptcha() }
                .onSuccess { captcha -> applyCaptcha(captcha.bytes) }
                .onFailure { throwable ->
                    handleFailure(throwable, retry = ::refreshCaptcha) { message ->
                        _uiState.update { it.copy(captchaLoading = false, captchaError = message) }
                    }
                }
        }
    }

    fun submitLetter(request: XzxxSubmitRequest) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, submitResult = null) }
        viewModelScope.launch {
            runCatching { repository.submitLetter(request) }
                .onSuccess { result ->
                    _uiState.update { it.copy(isSubmitting = false, submitResult = result) }
                    if (!result.success) refreshCaptcha()
                }
                .onFailure { throwable ->
                    handleFailure(throwable, retry = {
                        _uiState.update { it.copy(isSubmitting = false) }
                        submitLetter(request)
                    }) { message ->
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                submitResult = XzxxSubmitResult(false, message),
                            )
                        }
                    }
                }
        }
    }

    fun dismissSubmitResult() {
        val success = _uiState.value.submitResult?.success == true
        _uiState.update { it.copy(submitResult = null) }
        if (success) {
            closeCompose()
            loadFirstPage()
        }
    }

    fun onWafCookieCaptured(cookieHeader: String) {
        if (_uiState.value.wafValidating) return
        _uiState.update { it.copy(wafValidating = true, wafError = null) }
        viewModelScope.launch {
            val accepted = runCatching { repository.acceptWafCookies(cookieHeader) }.getOrDefault(false)
            if (accepted) {
                val pending = pendingAfterWaf
                pendingAfterWaf = null
                _uiState.update { it.copy(needsWaf = false, wafValidating = false, wafError = null) }
                pending?.invoke()
            } else {
                _uiState.update {
                    it.copy(
                        wafValidating = false,
                        wafError = "安全校验未通过，请重试",
                    )
                }
            }
        }
    }

    fun onWafBootstrapError(message: String) {
        if (!_uiState.value.needsWaf) return
        _uiState.update { it.copy(wafValidating = false, wafError = message) }
    }

    fun retryWafBootstrap() {
        _uiState.update {
            it.copy(
                wafError = null,
                wafValidating = false,
                wafReloadKey = it.wafReloadKey + 1,
            )
        }
    }

    fun challengeUrl(): String = repository.challengeUrl()

    private fun applyCaptcha(bytes: ByteArray) {
        _uiState.update {
            it.copy(
                captchaBytes = bytes,
                captchaLoading = false,
                captchaError = null,
                captchaRevision = it.captchaRevision + 1,
            )
        }
    }

    private fun handleFailure(
        throwable: Throwable,
        retry: () -> Unit,
        onRegularError: (String) -> Unit,
    ) {
        if (throwable is XzxxWafChallengeRequiredException) {
            pendingAfterWaf = retry
            _uiState.update {
                it.copy(
                    needsWaf = true,
                    wafValidating = false,
                    wafError = null,
                    wafReloadKey = it.wafReloadKey + 1,
                )
            }
        } else {
            onRegularError(throwable.userMessage())
        }
    }

    private fun Throwable.userMessage(): String = when (this) {
        is IOException -> message ?: "网络请求失败"
        else -> message ?: "加载失败，请稍后重试"
    }
}

data class XzxxUiState(
    val letters: List<XzxxLetter> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val currentPage: Int = 0,
    val hasMore: Boolean = true,
    val expandedIds: Set<String> = emptySet(),
    val detailLoadingIds: Set<String> = emptySet(),
    val details: Map<String, XzxxLetterDetail> = emptyMap(),
    val detailErrors: Map<String, String> = emptyMap(),
    val isComposeVisible: Boolean = false,
    val captchaBytes: ByteArray? = null,
    val captchaLoading: Boolean = false,
    val captchaError: String? = null,
    val captchaRevision: Int = 0,
    val isSubmitting: Boolean = false,
    val submitResult: XzxxSubmitResult? = null,
    val needsWaf: Boolean = false,
    val wafValidating: Boolean = false,
    val wafError: String? = null,
    val wafReloadKey: Int = 0,
)
