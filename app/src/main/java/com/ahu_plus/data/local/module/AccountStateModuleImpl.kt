package com.ahu_plus.data.local.module

import com.ahu_plus.data.diagnostic.SafeLog as Log
import com.ahu_plus.data.local.AppDataStore
import com.ahu_plus.data.local.EncryptedCredentialStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AccountStateModule 实现。
 *
 * ponytail: 新写入只用新 key（加密存储），读取兼容旧 key（DataStore）。
 */
class AccountStateModuleImpl(
    private val credentialStore: EncryptedCredentialStore,
    private val appDataStore: AppDataStore
) : AccountStateModule {

    private val mutex = Mutex()

    @Volatile
    private var cachedGeneration: Long = 0L
    @Volatile
    private var generationLoaded = false

    // ── DataStore keys（兼容读取旧位置）──────────────────
    companion object {
        private const val TAG = "AccountStateModule"

        // Generation
        private val GENERATION_KEY = longPreferencesKey("account_generation")

        // 校园账号（兼容读取）
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val PASSWORD_KEY = stringPreferencesKey("password")
        private val PORTAL_SESSION_KEY = stringPreferencesKey("JSESSIONID")
        private val JW_SESSION_KEY = stringPreferencesKey("JSESSIONID_jw")
        private val PSTSID_KEY = stringPreferencesKey("PSTSID")

        // 其他系统
        private val ADWMH_SESSION_KEY = stringPreferencesKey("adwmh_jsessionid")
        private val KQ_SESSION_KEY = stringPreferencesKey("kq_jsessionid")

        // 第三方
        private val JWAPP_TOKEN_KEY = stringPreferencesKey("jw_app_token")
        private val MARKET_API_IDENTITY_KEY = stringPreferencesKey("market_api_identity")
        private val CHAOXING_USERNAME_KEY = stringPreferencesKey("chaoxing_username")
        private val CHAOXING_PASSWORD_KEY = stringPreferencesKey("chaoxing_password")
        private val WELEARN_USERNAME_KEY = stringPreferencesKey("welearn_username")
        private val WELEARN_PASSWORD_KEY = stringPreferencesKey("welearn_password")
        private val CPROG_USERNAME_KEY = stringPreferencesKey("cprog_username")
        private val CPROG_PASSWORD_KEY = stringPreferencesKey("cprog_password")
        private val CPROG_IDNO_KEY = stringPreferencesKey("cprog_idno")
    }

    // ── Generation ──────────────────────────────────────
    override suspend fun currentGeneration(): Long = mutex.withLock {
        if (!generationLoaded) {
            cachedGeneration = appDataStore.dataStore.data.first()[GENERATION_KEY] ?: 0L
            generationLoaded = true
        }
        cachedGeneration
    }

    override suspend fun incrementGeneration(): Long = mutex.withLock {
        if (!generationLoaded) {
            cachedGeneration = appDataStore.dataStore.data.first()[GENERATION_KEY] ?: 0L
            generationLoaded = true
        }
        val newGen = cachedGeneration + 1
        cachedGeneration = newGen
        appDataStore.dataStore.edit { it[GENERATION_KEY] = newGen }
        Log.i(TAG, "Generation incremented to $newGen")
        newGen
    }

    override fun isCurrentGeneration(generation: Long?): Boolean {
        if (generation == null) return true
        return generation == cachedGeneration
    }

    // ── 基础账号信息 ──────────────────────────────────────
    override suspend fun getUsername(): String? {
        // 优先读加密存储（新位置）
        credentialStore.getString(EncryptedCredentialStore.USERNAME)?.let { return it }
        // 兼容读 DataStore 旧 key
        return appDataStore.dataStore.data.first()[USERNAME_KEY]
    }

    override suspend fun saveUsername(username: String, generation: Long?) {
        if (!isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale username write")
            return
        }
        // 只写加密存储（新位置）
        credentialStore.putString(EncryptedCredentialStore.USERNAME, username)
    }

    override suspend fun clearUsername() {
        credentialStore.remove(EncryptedCredentialStore.USERNAME)
    }

    override suspend fun getPassword(): String? {
        credentialStore.getString(EncryptedCredentialStore.PASSWORD)?.let { return it }
        return appDataStore.dataStore.data.first()[PASSWORD_KEY]
    }

    override suspend fun savePassword(password: String, generation: Long?) {
        if (!isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale password write")
            return
        }
        credentialStore.putString(EncryptedCredentialStore.PASSWORD, password)
    }

    override suspend fun clearPassword() {
        credentialStore.remove(EncryptedCredentialStore.PASSWORD)
    }

    // ── CAS 会话 ──────────────────────────────────────────
    override suspend fun getPortalSession(): String? {
        credentialStore.getString(EncryptedCredentialStore.PORTAL_SESSION)?.let { return it }
        return appDataStore.dataStore.data.first()[PORTAL_SESSION_KEY]
    }

    override suspend fun savePortalSession(session: String, generation: Long?) {
        if (!isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale portal session write")
            return
        }
        credentialStore.putString(EncryptedCredentialStore.PORTAL_SESSION, session)
    }

    override suspend fun getJwSession(): String? {
        credentialStore.getString(EncryptedCredentialStore.JW_SESSION)?.let { return it }
        return appDataStore.dataStore.data.first()[JW_SESSION_KEY]
    }

    override suspend fun saveJwSession(session: String, generation: Long?) {
        if (!isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale JW session write")
            return
        }
        credentialStore.putString(EncryptedCredentialStore.JW_SESSION, session)
    }

    override suspend fun getPstsid(): String? {
        credentialStore.getString(EncryptedCredentialStore.PSTSID)?.let { return it }
        return appDataStore.dataStore.data.first()[PSTSID_KEY]
    }

    override suspend fun savePstsid(pstsid: String, generation: Long?) {
        if (!isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale PSTSID write")
            return
        }
        credentialStore.putString(EncryptedCredentialStore.PSTSID, pstsid)
    }

    // ── 其他系统会话 ──────────────────────────────────────
    override suspend fun getAdwmhSession(): String? {
        credentialStore.getString(EncryptedCredentialStore.ADWMH_SESSION)?.let { return it }
        return appDataStore.dataStore.data.first()[ADWMH_SESSION_KEY]
    }

    override suspend fun saveAdwmhSession(session: String, generation: Long?) {
        if (!isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale adwmh session write")
            return
        }
        credentialStore.putString(EncryptedCredentialStore.ADWMH_SESSION, session)
    }

    override suspend fun getKqSession(): String? {
        credentialStore.getString(EncryptedCredentialStore.KQ_SESSION)?.let { return it }
        return appDataStore.dataStore.data.first()[KQ_SESSION_KEY]
    }

    override suspend fun saveKqSession(session: String, generation: Long?) {
        if (!isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale KQ session write")
            return
        }
        credentialStore.putString(EncryptedCredentialStore.KQ_SESSION, session)
    }

    // ── 第三方凭据 ──────────────────────────────────────
    override suspend fun getJwAppToken(): String? {
        credentialStore.getString(EncryptedCredentialStore.JWAPP_TOKEN)?.let { return it }
        return appDataStore.dataStore.data.first()[JWAPP_TOKEN_KEY]
    }

    override suspend fun saveJwAppToken(token: String, generation: Long?) {
        if (!isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale jwapp token write")
            return
        }
        credentialStore.putString(EncryptedCredentialStore.JWAPP_TOKEN, token)
    }

    override suspend fun getMarketToken(): String? {
        credentialStore.getString(EncryptedCredentialStore.MARKET_TOKEN)?.let { return it }
        return appDataStore.dataStore.data.first()[MARKET_API_IDENTITY_KEY]
    }

    override suspend fun saveMarketToken(token: String, generation: Long?) {
        if (!isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale market token write")
            return
        }
        credentialStore.putString(EncryptedCredentialStore.MARKET_TOKEN, token)
    }

    override suspend fun getChaoxingCredentials(): Pair<String?, String?> {
        val username = credentialStore.getString(EncryptedCredentialStore.CHAOXING_USERNAME)
            ?: appDataStore.dataStore.data.first()[CHAOXING_USERNAME_KEY]
        val password = credentialStore.getString(EncryptedCredentialStore.CHAOXING_PASSWORD)
            ?: appDataStore.dataStore.data.first()[CHAOXING_PASSWORD_KEY]
        return Pair(username, password)
    }

    override suspend fun saveChaoxingCredentials(username: String?, password: String?, generation: Long?) {
        if (!isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale chaoxing credentials write")
            return
        }
        username?.let { credentialStore.putString(EncryptedCredentialStore.CHAOXING_USERNAME, it) }
        password?.let { credentialStore.putString(EncryptedCredentialStore.CHAOXING_PASSWORD, it) }
    }

    override suspend fun getWeLearnCredentials(): Pair<String?, String?> {
        val username = credentialStore.getString(EncryptedCredentialStore.WELEARN_USERNAME)
            ?: appDataStore.dataStore.data.first()[WELEARN_USERNAME_KEY]
        val password = credentialStore.getString(EncryptedCredentialStore.WELEARN_PASSWORD)
            ?: appDataStore.dataStore.data.first()[WELEARN_PASSWORD_KEY]
        return Pair(username, password)
    }

    override suspend fun saveWeLearnCredentials(username: String?, password: String?, generation: Long?) {
        if (!isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale welearn credentials write")
            return
        }
        username?.let { credentialStore.putString(EncryptedCredentialStore.WELEARN_USERNAME, it) }
        password?.let { credentialStore.putString(EncryptedCredentialStore.WELEARN_PASSWORD, it) }
    }

    override suspend fun getCProgCredentials(): Triple<String?, String?, String?> {
        val username = credentialStore.getString(EncryptedCredentialStore.CPROG_USERNAME)
            ?: appDataStore.dataStore.data.first()[CPROG_USERNAME_KEY]
        val password = credentialStore.getString(EncryptedCredentialStore.CPROG_PASSWORD)
            ?: appDataStore.dataStore.data.first()[CPROG_PASSWORD_KEY]
        val idno = credentialStore.getString(EncryptedCredentialStore.CPROG_IDNO)
            ?: appDataStore.dataStore.data.first()[CPROG_IDNO_KEY]
        return Triple(username, password, idno)
    }

    override suspend fun saveCProgCredentials(username: String?, password: String?, idno: String?, generation: Long?) {
        if (!isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale cprog credentials write")
            return
        }
        username?.let { credentialStore.putString(EncryptedCredentialStore.CPROG_USERNAME, it) }
        password?.let { credentialStore.putString(EncryptedCredentialStore.CPROG_PASSWORD, it) }
        idno?.let { credentialStore.putString(EncryptedCredentialStore.CPROG_IDNO, it) }
    }

    // ── 清理 ──────────────────────────────────────────
    override suspend fun clearAllCredentials() {
        EncryptedCredentialStore.ACCOUNT_KEYS.forEach { key ->
            credentialStore.remove(key)
        }
        incrementGeneration()
        Log.i(TAG, "Cleared all credentials, generation incremented")
    }

    override suspend fun clearThirdPartyCredentials() {
        EncryptedCredentialStore.THIRD_PARTY_KEYS.forEach { key ->
            credentialStore.remove(key)
        }
        Log.i(TAG, "Cleared third-party credentials")
    }
}
