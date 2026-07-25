package com.ahu_plus.data.job

import com.ahu_plus.data.local.AppDataStore
import com.ahu_plus.data.network.ChaoxingAuthExpiredException
import com.ahu_plus.data.network.ChaoxingForbiddenException
import com.ahu_plus.data.network.ChaoxingRateLimitedException
import com.ahu_plus.data.network.ChaoxingRiskChallengeException
import com.ahu_plus.data.network.ChaoxingTrafficBusyException
import com.ahu_plus.data.network.ChaoxingTrafficCooldownException
import com.ahu_plus.data.network.SessionExpiredException
import com.google.gson.JsonParseException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BackgroundJobController private constructor(
    private val store: BackgroundJobPersistence,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    constructor(
        appDataStore: AppDataStore,
        nowMillis: () -> Long = System::currentTimeMillis,
    ) : this(BackgroundJobStore(appDataStore, nowMillis), nowMillis)

    internal constructor(
        persistence: BackgroundJobPersistence,
        nowMillis: () -> Long,
        @Suppress("UNUSED_PARAMETER") testMarker: Unit = Unit,
    ) : this(persistence, nowMillis)
    private val mutex = Mutex()
    private val cancellers = ConcurrentHashMap<String, () -> Unit>()
    private val _records = MutableStateFlow<List<BackgroundJobRecord>>(emptyList())
    val records: StateFlow<List<BackgroundJobRecord>> = _records.asStateFlow()
    private var loaded = false
    private var initialized = false

    suspend fun initialize() = mutex.withLock {
        ensureInitializedLocked()
    }

    private suspend fun ensureInitializedLocked() {
        if (initialized) return
        ensureLoadedLocked()
        val now = nowMillis()
        val reconciled = _records.value.map { record ->
            if (record.phase.isActive) {
                record.copy(
                    phase = BackgroundJobPhase.INTERRUPTED,
                    interruption = BackgroundJobInterruption.PROCESS_TERMINATED,
                    updatedAtMillis = now,
                    finishedAtMillis = now,
                )
            } else record
        }
        replaceLocked(reconciled)
        initialized = true
    }

    suspend fun start(command: BackgroundJobCommand): BackgroundJobStartResult = mutex.withLock {
        ensureInitializedLocked()
        _records.value.firstOrNull { it.platform == command.platform && it.phase.isActive }?.let {
            return@withLock BackgroundJobStartResult.Rejected(it)
        }
        val now = nowMillis()
        val record = BackgroundJobRecord(
            platform = command.platform,
            payload = command.payload,
            createdAtMillis = now,
        )
        replaceLocked(listOf(record) + _records.value)
        BackgroundJobStartResult.Accepted(record)
    }

    suspend fun resume(id: String): BackgroundJobStartResult = mutex.withLock {
        ensureInitializedLocked()
        val target = _records.value.firstOrNull { it.id == id } ?: return@withLock BackgroundJobStartResult.Missing
        _records.value.firstOrNull {
            it.platform == target.platform && it.id != id && it.phase.isActive
        }?.let { return@withLock BackgroundJobStartResult.Rejected(it) }
        if (target.phase != BackgroundJobPhase.INTERRUPTED && target.phase != BackgroundJobPhase.FAILED) {
            return@withLock BackgroundJobStartResult.Rejected(target)
        }
        val resumed = target.copy(
            phase = BackgroundJobPhase.RESUMING,
            failure = null,
            interruption = null,
            updatedAtMillis = nowMillis(),
            finishedAtMillis = null,
        )
        updateLocked(resumed)
        BackgroundJobStartResult.Accepted(resumed)
    }

    suspend fun get(id: String): BackgroundJobRecord? = mutex.withLock {
        ensureInitializedLocked()
        _records.value.firstOrNull { it.id == id }
    }

    fun active(platform: BackgroundJobPlatform): BackgroundJobRecord? =
        _records.value.firstOrNull { it.platform == platform && it.phase.isActive }

    suspend fun markRunning(id: String) = transition(id) {
        it.copy(phase = BackgroundJobPhase.RUNNING, updatedAtMillis = nowMillis())
    }

    suspend fun updateProgress(id: String, completed: Int, total: Int) = transition(id) {
        it.copy(
            progress = BackgroundJobProgress(completed.coerceAtLeast(0), total.coerceAtLeast(0)),
            updatedAtMillis = nowMillis(),
        )
    }

    suspend fun markSucceeded(id: String) = finish(id, BackgroundJobPhase.SUCCEEDED)

    suspend fun markFailed(id: String, failure: BackgroundJobFailure) = transition(id) {
        val now = nowMillis()
        it.copy(
            phase = BackgroundJobPhase.FAILED,
            failure = failure,
            interruption = null,
            updatedAtMillis = now,
            finishedAtMillis = now,
        )
    }

    suspend fun markInterrupted(id: String, reason: BackgroundJobInterruption) = transition(id) {
        val now = nowMillis()
        it.copy(
            phase = BackgroundJobPhase.INTERRUPTED,
            failure = null,
            interruption = reason,
            updatedAtMillis = now,
            finishedAtMillis = now,
        )
    }

    suspend fun cancel(id: String): Boolean {
        val canceller = mutex.withLock {
            ensureInitializedLocked()
            val record = _records.value.firstOrNull { it.id == id } ?: return@withLock null
            if (!record.phase.isActive) return@withLock null
            cancellers[id]
        }
        canceller?.invoke()
        finish(id, BackgroundJobPhase.CANCELLED)
        return canceller != null || _records.value.any { it.id == id && it.phase == BackgroundJobPhase.CANCELLED }
    }

    suspend fun clearHistory(platform: BackgroundJobPlatform? = null) = mutex.withLock {
        ensureInitializedLocked()
        replaceLocked(_records.value.filter { record ->
            record.phase.isActive || (platform != null && record.platform != platform)
        })
    }

    fun attachCanceller(id: String, canceller: () -> Unit) {
        cancellers[id] = canceller
    }

    fun detachCanceller(id: String) {
        cancellers.remove(id)
    }

    fun classifyFailure(error: Throwable): BackgroundJobFailure = when (error) {
        is SessionExpiredException, is ChaoxingAuthExpiredException ->
            BackgroundJobFailure.AUTHENTICATION_REQUIRED
        is ChaoxingRateLimitedException,
        is ChaoxingForbiddenException,
        is ChaoxingRiskChallengeException,
        is ChaoxingTrafficBusyException,
        is ChaoxingTrafficCooldownException -> BackgroundJobFailure.REMOTE_REJECTED
        is JsonParseException -> BackgroundJobFailure.PROTOCOL_CHANGED
        is UnknownHostException, is SocketTimeoutException -> BackgroundJobFailure.NETWORK_UNAVAILABLE
        is IOException -> if (error.message?.startsWith("session expired:", ignoreCase = true) == true) {
            BackgroundJobFailure.AUTHENTICATION_REQUIRED
        } else {
            BackgroundJobFailure.NETWORK_UNAVAILABLE
        }
        is IllegalArgumentException -> BackgroundJobFailure.INVALID_REQUEST
        else -> BackgroundJobFailure.UNKNOWN
    }

    private suspend fun finish(id: String, phase: BackgroundJobPhase) = transition(id) {
        val now = nowMillis()
        it.copy(phase = phase, updatedAtMillis = now, finishedAtMillis = now)
    }

    private suspend fun transition(
        id: String,
        transform: (BackgroundJobRecord) -> BackgroundJobRecord,
    ) = mutex.withLock {
        ensureInitializedLocked()
        val current = _records.value.firstOrNull { it.id == id } ?: return@withLock
        updateLocked(transform(current))
    }

    private suspend fun ensureLoadedLocked() {
        if (loaded) return
        _records.value = store.load()
        loaded = true
    }

    private suspend fun updateLocked(record: BackgroundJobRecord) {
        replaceLocked(_records.value.map { if (it.id == record.id) record else it })
    }

    private suspend fun replaceLocked(records: List<BackgroundJobRecord>) {
        store.save(records)
        _records.value = store.load()
    }
}
