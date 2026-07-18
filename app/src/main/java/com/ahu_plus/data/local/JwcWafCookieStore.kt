package com.ahu_plus.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

data class JwcWafCookie(
    val value: String,
    val expiresAtMillis: Long,
)

interface JwcWafCookieStorage {
    suspend fun read(): JwcWafCookie?
    suspend fun save(cookie: JwcWafCookie)
    suspend fun clear()
}

/** The public-site WAF pass is device-scoped and intentionally survives account logout. */
class JwcWafCookieStore(
    private val appDataStore: AppDataStore,
) : JwcWafCookieStorage {

    override suspend fun read(): JwcWafCookie? {
        val preferences = appDataStore.dataStore.data.first()
        val value = preferences[VALUE_KEY].orEmpty()
        val expiresAt = preferences[EXPIRES_AT_KEY] ?: 0L
        return value.takeIf { it.isNotBlank() }?.let { JwcWafCookie(it, expiresAt) }
    }

    override suspend fun save(cookie: JwcWafCookie) {
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
        val VALUE_KEY = stringPreferencesKey("jwc_waf_cookie")
        val EXPIRES_AT_KEY = longPreferencesKey("jwc_waf_cookie_expires_at")
    }
}
