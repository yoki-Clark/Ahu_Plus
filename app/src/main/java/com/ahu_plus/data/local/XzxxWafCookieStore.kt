package com.ahu_plus.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

data class XzxxWafCookie(
    val value: String,
    val expiresAtMillis: Long,
)

interface XzxxWafCookieStorage {
    suspend fun read(): XzxxWafCookie?
    suspend fun save(cookie: XzxxWafCookie)
    suspend fun clear()
}

/** The WAF pass is device-scoped and intentionally survives account logout. */
class XzxxWafCookieStore(
    private val appDataStore: AppDataStore,
) : XzxxWafCookieStorage {

    override suspend fun read(): XzxxWafCookie? {
        val preferences = appDataStore.dataStore.data.first()
        val value = preferences[VALUE_KEY].orEmpty()
        val expiresAt = preferences[EXPIRES_AT_KEY] ?: 0L
        return value.takeIf { it.isNotBlank() }?.let { XzxxWafCookie(it, expiresAt) }
    }

    override suspend fun save(cookie: XzxxWafCookie) {
        appDataStore.dataStore.edit { preferences ->
            preferences[VALUE_KEY] = cookie.value
            preferences[EXPIRES_AT_KEY] = cookie.expiresAtMillis
        }
    }

    override suspend fun clear() {
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(VALUE_KEY)
            preferences.remove(EXPIRES_AT_KEY)
        }
    }

    private companion object {
        val VALUE_KEY = stringPreferencesKey("xzxx_waf_cookie")
        val EXPIRES_AT_KEY = longPreferencesKey("xzxx_waf_cookie_expires_at")
    }
}
