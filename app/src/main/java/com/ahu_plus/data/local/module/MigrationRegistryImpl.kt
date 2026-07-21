package com.ahu_plus.data.local.module

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.ahu_plus.data.diagnostic.SafeLog as Log
import kotlinx.coroutines.flow.first

/**
 * MigrationRegistry 实现。
 *
 * ponytail: 顺序执行待迁移列表，幂等，记录版本。
 */
class MigrationRegistryImpl(
    private val dataStore: DataStore<Preferences>,
    private val migrations: List<MigrationRegistry.Migration>
) : MigrationRegistry {

    companion object {
        private const val TAG = "MigrationRegistry"
        private val MIGRATED_VERSION_KEY = intPreferencesKey("migrated_version")
    }

    override suspend fun runPendingMigrations(): Result<Int> = runCatching {
        val currentVersion = getCurrentMigratedVersion()
        Log.i(TAG, "Current migrated version: $currentVersion, target: ${MigrationRegistry.CURRENT_VERSION}")

        var executedCount = 0
        migrations.filter { it.fromVersion >= currentVersion && it.toVersion <= MigrationRegistry.CURRENT_VERSION }
            .sortedBy { it.fromVersion }
            .forEach { migration ->
                Log.i(TAG, "Running migration: ${migration.fromVersion} -> ${migration.toVersion}: ${migration.description}")

                migration.migrate().onSuccess {
                    markMigrationComplete(migration.toVersion)
                    executedCount++
                    Log.i(TAG, "Migration ${migration.toVersion} completed")
                }.onFailure { e ->
                    Log.e(TAG, "Migration ${migration.toVersion} failed", e)
                    throw e
                }
            }

        Log.i(TAG, "All migrations completed, executed $executedCount migrations")
        executedCount
    }

    override suspend fun getCurrentMigratedVersion(): Int {
        return dataStore.data.first()[MIGRATED_VERSION_KEY] ?: 0
    }

    override suspend fun markMigrationComplete(version: Int) {
        dataStore.edit { prefs ->
            prefs[MIGRATED_VERSION_KEY] = version
        }
        Log.i(TAG, "Marked migration complete: version $version")
    }

    override fun getMigrations(): List<MigrationRegistry.Migration> = migrations
}
