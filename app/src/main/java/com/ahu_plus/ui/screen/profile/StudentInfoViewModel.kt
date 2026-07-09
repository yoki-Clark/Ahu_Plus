package com.ahu_plus.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.StudentInfo
import com.ahu_plus.data.repository.SessionExpiredException
import com.ahu_plus.data.repository.StudentInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudentInfoViewModel(
    private val repository: StudentInfoRepository,
    private val sessionManager: SessionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        StudentInfoUiState(
            isLoading = false,
            lastUpdatedAt = sessionManager.getStudentInfoUpdatedAt(),
            info = null
        )
    )
    val uiState: StateFlow<StudentInfoUiState> = _uiState.asStateFlow()

    init {
        // 启动只读本地缓存,不发网络请求
        val cached = repository.readCachedStudentInfo()
        if (cached != null) {
            _uiState.update { it.copy(info = cached) }
        }
    }

    /**
     * 用户主动点击「更新数据」时调用。
     * 成功后会写回本地缓存,失败时只在 UI 上显示错误,不会清掉已有缓存。
     */
    fun refreshStudentInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                repository.getStudentInfo()
            }
            result.fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            info = info,
                            lastUpdatedAt = sessionManager.getStudentInfoUpdatedAt()
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "个人信息更新失败",
                            needsLogin = e is SessionExpiredException
                        )
                    }
                }
            )
        }
    }
}

data class StudentInfoUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false,
    val info: StudentInfo? = null,
    val lastUpdatedAt: Long = 0L
)
