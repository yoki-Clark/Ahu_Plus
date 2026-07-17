package com.ahu_plus.data.developer

internal enum class NetworkDiagnosticCancelTarget {
    NONE,
    SINGLE,
    BATCH,
}

/**
 * Tracks ownership of developer network diagnostics without depending on coroutine Jobs.
 *
 * Individual probes may run together. A batch is exclusive so the same host cannot be probed by
 * both paths and the UI can route a row-level cancel action to the job that actually owns it.
 */
internal class NetworkDiagnosticRunCoordinator {
    private val singleIds = linkedSetOf<String>()
    private var batchActive = false
    private var batchCurrentId: String? = null

    val isBatchActive: Boolean
        get() = batchActive

    val runningIds: Set<String>
        get() = buildSet {
            addAll(singleIds)
            batchCurrentId?.let(::add)
        }

    fun tryStartSingle(id: String): Boolean {
        if (batchActive || id in singleIds) return false
        singleIds += id
        return true
    }

    fun finishSingle(id: String) {
        singleIds -= id
    }

    fun tryStartBatch(): Boolean {
        if (batchActive || singleIds.isNotEmpty()) return false
        batchActive = true
        return true
    }

    fun startBatchHost(id: String) {
        check(batchActive) { "Cannot start a batch host without an active batch" }
        batchCurrentId = id
    }

    fun finishBatchHost(id: String) {
        if (batchCurrentId == id) batchCurrentId = null
    }

    fun finishBatch() {
        batchCurrentId = null
        batchActive = false
    }

    fun cancelTarget(id: String): NetworkDiagnosticCancelTarget = when {
        id in singleIds -> NetworkDiagnosticCancelTarget.SINGLE
        batchActive && batchCurrentId == id -> NetworkDiagnosticCancelTarget.BATCH
        else -> NetworkDiagnosticCancelTarget.NONE
    }
}
