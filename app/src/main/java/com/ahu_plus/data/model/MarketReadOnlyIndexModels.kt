package com.ahu_plus.data.model

enum class MarketReadOnlyIndexStatus {
    IDLE,
    READY,
    INITIALIZING,
    STALE,
    PAUSED,
}

enum class MarketReadOnlyTab {
    INDEX,
    HOT,
}

data class MarketReadOnlyIndexPage(
    val ids: List<Long>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val sourceStatus: MarketReadOnlyIndexStatus,
    val generatedAt: String,
)

data class MarketReadOnlyLoadedPage(
    val topics: List<MarketTopic>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val failedIds: List<Long>,
    val requestedIds: Int,
    val sourceStatus: MarketReadOnlyIndexStatus,
)
