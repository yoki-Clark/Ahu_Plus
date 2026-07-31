package com.ahu_plus.ui.screen.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.data.diagnostic.SafeLog as Log
import com.ahu_plus.data.model.mail.MailAccountInfo
import com.ahu_plus.data.model.mail.MailMessageDetail
import com.ahu_plus.data.model.mail.MailMessageSummary
import com.ahu_plus.data.repository.ErrorClassifier
import com.ahu_plus.data.repository.mail.MailAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 教育邮箱 ViewModel。
 *
 * 状态管理参考 [com.ahu_plus.ui.screen.home.HomeViewModel] 的模式:
 * - 本地优先(先展示缓存,后台刷新)
 * - 加载/空/错误/刷新状态全覆盖
 * - autoLogin 失败静默,不阻塞 UI
 */
class MailViewModel(
    private val application: AhuPlusApplication,
) : ViewModel() {

    companion object {
        private const val TAG = "MailViewModel"
        private const val DEFAULT_FOLDER_ID = 1  // 收件箱
        private const val PAGE_SIZE = 30
    }

    private val _uiState = MutableStateFlow(MailUiState())
    val uiState: StateFlow<MailUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow(MailDetailUiState())
    val detailState: StateFlow<MailDetailUiState> = _detailState.asStateFlow()

    /** 首次加载(从应用聚合页进入时调用)。 */
    fun loadInbox() {
        if (_uiState.value.messages != null) return  // 已有缓存,不重复加载
        refreshInbox()
    }

    /** 下拉刷新/手动刷新。 */
    fun refreshInbox() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                // 1. 确保 session 有效(可能触发握手)
                application.ahuMailRepository.ensureSession().getOrThrow()
                // 2. 拉取邮件列表
                val result = application.ahuMailRepository.listMessages(
                    fid = DEFAULT_FOLDER_ID,
                    limit = PAGE_SIZE,
                )
                result.fold(
                    onSuccess = { messages ->
                        _uiState.update {
                            it.copy(
                                messages = messages,
                                isRefreshing = false,
                                error = null,
                                isEmpty = messages.isEmpty(),
                            )
                        }
                        // 后台拉取账户信息(失败不阻塞)
                        launch { loadAccountInfo() }
                    },
                    onFailure = { e ->
                        Log.w(TAG, "refreshInbox 失败: ${e.message}")
                        val kind = ErrorClassifier.classify(e)
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                error = ErrorClassifier.userMessage(kind, e.message),
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                Log.e(TAG, "refreshInbox 异常", e)
                val kind = ErrorClassifier.classify(e)
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = ErrorClassifier.userMessage(kind, e.message),
                    )
                }
            }
        }
    }

    /** 后台加载账户信息(用于 TopAppBar 显示昵称)。 */
    private suspend fun loadAccountInfo() {
        withContext(Dispatchers.IO) {
            application.ahuMailRepository.getAccountInfo().onSuccess { info ->
                _uiState.update { it.copy(accountInfo = info) }
            }.onFailure {
                Log.w(TAG, "loadAccountInfo 失败(不阻塞): ${it.message}")
            }
        }
    }

    /** 进入邮件详情。 */
    fun openMessage(messageId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoading = true, error = null, detail = null) }
            application.ahuMailRepository.readMessage(messageId).fold(
                onSuccess = { detail ->
                    _detailState.update {
                        it.copy(isLoading = false, detail = detail)
                    }
                },
                onFailure = { e ->
                    val kind = ErrorClassifier.classify(e)
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            error = ErrorClassifier.userMessage(kind, e.message),
                        )
                    }
                },
            )
        }
    }

    /** 标记邮件已读/未读。 */
    fun toggleRead(message: MailMessageSummary) {
        viewModelScope.launch {
            val newRead = !message.isRead
            application.ahuMailRepository.markRead(listOf(message.id), newRead).onSuccess {
                // 本地更新列表
                _uiState.update { state ->
                    val updated = state.messages?.map {
                        if (it.id == message.id) it.copy(isRead = newRead) else it
                    }
                    state.copy(messages = updated)
                }
            }
        }
    }

    /** 清空详情状态(返回列表时调用)。 */
    fun clearDetail() {
        _detailState.update { MailDetailUiState() }
    }
}

/** 邮件列表 UI 状态。 */
data class MailUiState(
    val messages: List<MailMessageSummary>? = null,
    val accountInfo: MailAccountInfo? = null,
    val isRefreshing: Boolean = false,
    val isEmpty: Boolean = false,
    val error: String? = null,
) {
    val isLoading: Boolean get() = messages == null && isRefreshing
}

/** 邮件详情 UI 状态。 */
data class MailDetailUiState(
    val detail: MailMessageDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)
