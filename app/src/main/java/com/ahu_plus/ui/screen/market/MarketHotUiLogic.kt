package com.ahu_plus.ui.screen.market

import com.ahu_plus.data.model.MarketTopic

internal data class MarketHotDisplay(
    val title: String,
    val topics: List<MarketTopic>,
    val loading: Boolean,
    val error: String?,
    val readOnly: Boolean,
)

internal fun marketHotDisplay(uiState: MarketUiState): MarketHotDisplay {
    val readOnly = !uiState.hasSavedIdentity
    return if (readOnly) {
        MarketHotDisplay(
            title = "安大热榜",
            topics = uiState.readOnlyHotTopics,
            loading = uiState.readOnlyHotLoading,
            error = uiState.readOnlyHotError,
            readOnly = true,
        )
    } else {
        MarketHotDisplay(
            title = "集市热榜",
            topics = uiState.hotTopics,
            loading = uiState.hotLoading,
            error = uiState.hotError,
            readOnly = false,
        )
    }
}
