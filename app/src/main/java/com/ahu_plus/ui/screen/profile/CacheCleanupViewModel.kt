package com.ahu_plus.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.local.CacheSizeInfo
import com.ahu_plus.data.repository.CacheCleanupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 缓存清理页状态:
 * - 进入即异步计算,完成前显示"计算中..."(避免阻塞 UI)
 * - 用户点击确认清理后,清选中的 group,然后重算一次刷新 UI
 */
class CacheCleanupViewModel(
    private val repository: CacheCleanupRepository
) : ViewModel() {

    data class UiState(
        val sizeInfo: CacheSizeInfo? = null,
        val downloadSize: Long = 0L,
        val downloadCount: Int = 0,
        val isCalculating: Boolean = false,
        val isClearing: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState(isCalculating = true))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        recalculate()
    }

    /** 重新计算所有分组的字节大小(以及 download APK 文件)。 */
    fun recalculate() {
        _uiState.update { it.copy(isCalculating = true) }
        viewModelScope.launch {
            val info = repository.calculate()
            val (dlSize, dlCount) = repository.downloadApkSize()
            _uiState.update {
                it.copy(
                    sizeInfo = info,
                    downloadSize = dlSize,
                    downloadCount = dlCount,
                    isCalculating = false
                )
            }
        }
    }

    /** 清理选中的 group,完成后重新计算。 */
    fun clear(selectedGroups: List<String>) {
        if (selectedGroups.isEmpty()) return
        _uiState.update { it.copy(isClearing = true) }
        viewModelScope.launch {
            repository.clearGroups(selectedGroups)
            // 不在原 UiState 上叠 isClearing,以便 UI 立即消失
            // 紧接着触发一次 recalculate 重新拉取最新体积
            recalculate()
            _uiState.update { it.copy(isClearing = false) }
        }
    }

    class Factory(private val repository: CacheCleanupRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CacheCleanupViewModel(repository) as T
        }
    }
}