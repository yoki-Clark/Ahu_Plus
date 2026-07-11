package com.ahu_plus.ui.screen.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.model.weather.WeatherFeed
import com.ahu_plus.data.weather.WeatherManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 天气独立屏 ViewModel。
 *
 * 设计参考 [com.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomViewModel]:
 *  - 订阅 [WeatherManager.feed] StateFlow 直接显示
 *  - init 时主动 refresh 一次
 *  - onRefresh() 由 UI 调用
 */
class WeatherViewModel(
    private val weatherManager: WeatherManager,
) : ViewModel() {

    /** 内部 isLoading + error + ageMillis, 派生给 UI。 */
    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    /** 共享 WeatherManager 的最新 feed; UI 直接 collect。 */
    val feed: StateFlow<WeatherFeed?> = weatherManager.feed

    fun activate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val ok = weatherManager.refreshIfStale()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    ageMillis = weatherManager.getCachedAgeMillis(),
                    error = if (!ok && it.ageMillis == Long.MAX_VALUE) "获取天气失败" else null,
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val ok = weatherManager.refresh()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    ageMillis = weatherManager.getCachedAgeMillis(),
                    error = if (!ok) "刷新失败" else null,
                )
            }
        }
    }
}

data class WeatherUiState(
    val isLoading: Boolean = false,
    val ageMillis: Long = Long.MAX_VALUE,
    val error: String? = null,
) {
    val isStale: Boolean get() = ageMillis > 60L * 60L * 1000L
}

/** MutableStateFlow.update 没有自带, 这里给 WeatherUiState 一个小 helper。 */
private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    while (true) {
        val prev = value
        val next = block(prev)
        if (compareAndSet(prev, next)) return
    }
}
