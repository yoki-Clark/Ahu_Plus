package com.ahu_plus.data.job

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.local.AppDataStore
import kotlinx.coroutines.flow.first

internal interface BackgroundJobPersistence {
    suspend fun load(): List<BackgroundJobRecord>
    suspend fun save(records: List<BackgroundJobRecord>)
}

internal class BackgroundJobStore(
    private val appDataStore: AppDataStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : BackgroundJobPersistence {
    private data class Envelope(
        val schemaVersion: Int = SCHEMA_VERSION,
        val records: List<BackgroundJobRecord> = emptyList(),
    )

    private val gson = GsonProvider.instance

    override suspend fun load(): List<BackgroundJobRecord> {
        val json = appDataStore.dataStore.data.first()[KEY] ?: return emptyList()
        val envelope = runCatching { gson.fromJson(json, Envelope::class.java) }.getOrNull()
            ?: return emptyList()
        if (envelope.schemaVersion != SCHEMA_VERSION) return emptyList()
        return pruneBackgroundJobRecords(envelope.records, nowMillis())
    }

    override suspend fun save(records: List<BackgroundJobRecord>) {
        val json = gson.toJson(Envelope(records = pruneBackgroundJobRecords(records, nowMillis())))
        appDataStore.dataStore.edit { it[KEY] = json }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        val KEY = stringPreferencesKey("background_jobs_v1")
    }
}

internal const val MAX_JOB_HISTORY_PER_PLATFORM = 20
internal const val JOB_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1_000

internal fun pruneBackgroundJobRecords(
    records: List<BackgroundJobRecord>,
    nowMillis: Long,
): List<BackgroundJobRecord> {
    val cutoff = nowMillis - JOB_RETENTION_MILLIS
    return records
        .filter { it.phase.isActive || it.updatedAtMillis >= cutoff }
        .groupBy { it.platform }
        .values
        .flatMap { platformRecords ->
            val active = platformRecords.filter { it.phase.isActive }
            val history = platformRecords.filterNot { it.phase.isActive }
                .sortedByDescending { it.updatedAtMillis }
                .take(MAX_JOB_HISTORY_PER_PLATFORM)
            active + history
        }
        .sortedByDescending { it.updatedAtMillis }
}
