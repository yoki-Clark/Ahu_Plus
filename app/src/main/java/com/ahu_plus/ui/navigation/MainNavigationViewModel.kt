package com.ahu_plus.ui.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainNavigationViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        MainNavigationSnapshotCodec.decode(savedStateHandle[SNAPSHOT_KEY])
            ?: MainNavigationState.initial()
    )
    val state: StateFlow<MainNavigationState> = _state.asStateFlow()

    fun selectTopLevel(destination: TopLevelDestination) = update {
        it.selectTopLevel(destination)
    }

    fun navigate(request: NavigationRequest) = update { it.navigate(request) }

    fun back(): Boolean {
        val result = _state.value.back()
        if (result is BackResult.AtRoot) return false
        persist(result.state)
        return true
    }

    fun disable(destination: TopLevelDestination) = update { it.disable(destination) }

    fun reset() = persist(MainNavigationState.initial())

    private inline fun update(transform: (MainNavigationState) -> MainNavigationState) {
        persist(transform(_state.value))
    }

    private fun persist(state: MainNavigationState) {
        _state.value = state
        savedStateHandle[SNAPSHOT_KEY] = MainNavigationSnapshotCodec.encode(state)
    }

    private companion object {
        const val SNAPSHOT_KEY = "main_navigation_snapshot_v1"
    }
}

