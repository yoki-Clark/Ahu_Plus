package com.ahu_plus.data.developer

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.ahu_plus.data.local.AppDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Developer-only access to the shared Preferences DataStore.
 *
 * Deletion always resolves an exact key name from the current snapshot. There is intentionally no
 * wildcard or prefix deletion API here.
 */
class DeveloperCacheRepository(
    private val appDataStore: AppDataStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeReport(): Flow<DeveloperCacheReport> = appDataStore.dataStore.data
        .map(::inspectPreferences)
        .flowOn(ioDispatcher)

    suspend fun inspect(): DeveloperCacheReport = withContext(ioDispatcher) {
        inspectPreferences(appDataStore.dataStore.data.first())
    }

    /** Returns true only when an entry with the exact name existed and was removed. */
    suspend fun clearKey(keyName: String): Boolean = withContext(ioDispatcher) {
        if (keyName.isBlank() || isProtectedKeyName(keyName)) return@withContext false

        var removed = false
        appDataStore.dataStore.edit { mutablePreferences ->
            val key = mutablePreferences.asMap().keys.firstOrNull { it.name == keyName }
            if (key != null) {
                mutablePreferences.removeUntyped(key)
                removed = true
            }
        }
        removed
    }

    /** Returns the number of exact key names that existed and were removed atomically. */
    suspend fun clearKeys(keyNames: Collection<String>): Int = withContext(ioDispatcher) {
        val requested = keyNames.filterTo(mutableSetOf()) {
            it.isNotBlank() && !isProtectedKeyName(it)
        }
        if (requested.isEmpty()) return@withContext 0

        var removedCount = 0
        appDataStore.dataStore.edit { mutablePreferences ->
            mutablePreferences.asMap().keys
                .filter { it.name in requested }
                .forEach { key ->
                    mutablePreferences.removeUntyped(key)
                    removedCount++
                }
        }
        removedCount
    }

    suspend fun exportRedactedText(): String =
        DeveloperCacheInspector.exportRedactedText(inspect())

    suspend fun exportRedactedJson(): String =
        DeveloperCacheInspector.exportRedactedJson(inspect())

    private fun inspectPreferences(preferences: Preferences): DeveloperCacheReport {
        val values = preferences.asMap().entries.associate { (key, value) -> key.name to value }
        return DeveloperCacheInspector.inspect(values)
    }

    companion object {
        /** The active gate must only be changed through the About-screen switch. */
        fun isProtectedKeyName(keyName: String): Boolean = keyName == "developer_enabled"
    }

    @Suppress("UNCHECKED_CAST")
    private fun androidx.datastore.preferences.core.MutablePreferences.removeUntyped(
        key: Preferences.Key<*>,
    ) {
        remove(key as Preferences.Key<Any>)
    }
}
