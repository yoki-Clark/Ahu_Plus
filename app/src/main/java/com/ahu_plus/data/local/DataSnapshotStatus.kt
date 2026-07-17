package com.ahu_plus.data.local

enum class DataSnapshotOrigin {
    CACHE,
    NETWORK,
}

data class DataSnapshotStatus(
    val origin: DataSnapshotOrigin,
    val updatedAt: Long,
    val lastFailedRefreshAt: Long? = null,
) {
    fun withFailedRefresh(at: Long = System.currentTimeMillis()): DataSnapshotStatus =
        copy(lastFailedRefreshAt = at)

    companion object {
        fun cache(updatedAt: Long): DataSnapshotStatus =
            DataSnapshotStatus(DataSnapshotOrigin.CACHE, updatedAt)

        fun network(updatedAt: Long = System.currentTimeMillis()): DataSnapshotStatus =
            DataSnapshotStatus(DataSnapshotOrigin.NETWORK, updatedAt)
    }
}
