package com.ahu_plus.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed storage for credentials and bearer/session tokens.
 *
 * Business caches and ordinary preferences remain in DataStore. If the device Keystore is
 * unavailable, secrets stay in memory for the current process and are not written as plaintext.
 */
internal class EncryptedCredentialStore(context: Context) {
    private val preferences: SharedPreferences? = runCatching {
        val appContext = context.applicationContext
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.onFailure {
        Log.e(TAG, "Encrypted credential storage is unavailable; secrets will not be persisted", it)
    }.getOrNull()

    fun getString(key: String): String? = runCatching {
        preferences?.getString(key, null)
    }.onFailure {
        Log.e(TAG, "Failed to read encrypted credential: $key", it)
    }.getOrNull()

    /** Returns true only after the encrypted value has been committed and read back. */
    fun putString(key: String, value: String): Boolean {
        val prefs = preferences ?: return false
        return runCatching {
            prefs.edit().putString(key, value).commit() && prefs.getString(key, null) == value
        }.onFailure {
            Log.e(TAG, "Failed to persist encrypted credential: $key", it)
        }.getOrDefault(false)
    }

    fun remove(key: String) {
        runCatching { preferences?.edit()?.remove(key)?.commit() }
            .onFailure { Log.e(TAG, "Failed to remove encrypted credential: $key", it) }
    }

    fun remove(keys: Iterable<String>) {
        val prefs = preferences ?: return
        runCatching {
            prefs.edit().apply { keys.forEach(::remove) }.commit()
        }.onFailure {
            Log.e(TAG, "Failed to remove encrypted credentials", it)
        }
    }

    companion object {
        private const val TAG = "CredentialStore"
        private const val FILE_NAME = "ahu_plus_credentials"

        const val CAS_USERNAME = "cas_username"
        const val CAS_PASSWORD = "cas_password"
        const val PORTAL_SESSION = "portal_session"
        const val JW_SESSION = "jw_session"
        const val JW_PST_SID = "jw_pst_sid"
        const val JWAPP_USERNAME = "jwapp_username"
        const val JWAPP_PASSWORD = "jwapp_password"
        const val JWAPP_TOKEN = "jwapp_token"
        const val ADWMH_SESSION = "adwmh_session"
        const val EVALUATION_JWT = "evaluation_jwt"
        const val MARKET_LEGACY_IDENTITY = "market_legacy_identity"
        const val MARKET_IDENTITIES = "market_identities"
        const val CHAOXING_COOKIES = "chaoxing_cookies"
        const val CHAOXING_PHONE = "chaoxing_phone"
        const val CHAOXING_PASSWORD = "chaoxing_password"
        const val CHAOXING_YANXI_TOKENS = "chaoxing_yanxi_tokens"
        const val CHAOXING_SILICONFLOW_KEY = "chaoxing_siliconflow_key"
        const val CHAOXING_GO_AUTHORIZATION = "chaoxing_go_authorization"
        const val CHAOXING_TIKU_TOKEN = "chaoxing_tiku_token"
        const val CHAOXING_AI_KEY = "chaoxing_ai_key"
        const val CHAOXING_NOTIFY_URL = "chaoxing_notify_url"
        const val WELEARN_COOKIES = "welearn_cookies"
        const val WELEARN_USERNAME = "welearn_username"
        const val WELEARN_PASSWORD = "welearn_password"
        const val CPROG_JWT = "cprog_jwt"
        const val CPROG_JSESSIONID = "cprog_jsessionid"
        const val CPROG_USER_ID = "cprog_user_id"
        const val CPROG_USERNAME = "cprog_username"
        const val CPROG_IDNO = "cprog_idno"

        val ACCOUNT_KEYS = setOf(
            CAS_USERNAME,
            CAS_PASSWORD,
            PORTAL_SESSION,
            JW_SESSION,
            JW_PST_SID,
            ADWMH_SESSION,
            EVALUATION_JWT,
        )
        val THIRD_PARTY_ACCOUNT_KEYS = setOf(
            JWAPP_USERNAME,
            JWAPP_PASSWORD,
            JWAPP_TOKEN,
            MARKET_LEGACY_IDENTITY,
            MARKET_IDENTITIES,
            CHAOXING_COOKIES,
            CHAOXING_PHONE,
            CHAOXING_PASSWORD,
            CHAOXING_YANXI_TOKENS,
            CHAOXING_SILICONFLOW_KEY,
            CHAOXING_GO_AUTHORIZATION,
            CHAOXING_TIKU_TOKEN,
            CHAOXING_AI_KEY,
            CHAOXING_NOTIFY_URL,
            WELEARN_COOKIES,
            WELEARN_USERNAME,
            WELEARN_PASSWORD,
            CPROG_JWT,
            CPROG_JSESSIONID,
            CPROG_USER_ID,
            CPROG_USERNAME,
            CPROG_IDNO,
        )
    }
}
