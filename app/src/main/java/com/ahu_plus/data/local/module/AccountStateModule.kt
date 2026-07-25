package com.ahu_plus.data.local.module

import com.ahu_plus.data.local.EncryptedCredentialStore
import kotlinx.coroutines.flow.Flow

/**
 * 账号状态模块：管理账号 generation、凭据、会话。
 *
 * ponytail: 只包含账号生命周期相关状态，不包含业务缓存。
 */
interface AccountStateModule {
    // ── Generation ──────────────────────────────────────
    suspend fun currentGeneration(): Long
    suspend fun incrementGeneration(): Long
    fun isCurrentGeneration(generation: Long?): Boolean

    // ── 基础账号信息 ──────────────────────────────────────
    suspend fun getUsername(): String?
    suspend fun saveUsername(username: String, generation: Long? = null)
    suspend fun clearUsername()

    suspend fun getPassword(): String?
    suspend fun savePassword(password: String, generation: Long? = null)
    suspend fun clearPassword()

    // ── CAS 会话 ──────────────────────────────────────────
    suspend fun getPortalSession(): String?
    suspend fun savePortalSession(session: String, generation: Long? = null)

    suspend fun getJwSession(): String?
    suspend fun saveJwSession(session: String, generation: Long? = null)

    suspend fun getPstsid(): String?
    suspend fun savePstsid(pstsid: String, generation: Long? = null)

    // ── 其他系统会话 ──────────────────────────────────────
    suspend fun getAdwmhSession(): String?
    suspend fun saveAdwmhSession(session: String, generation: Long? = null)

    suspend fun getKqSession(): String?
    suspend fun saveKqSession(session: String, generation: Long? = null)

    // ── 第三方凭据 ──────────────────────────────────────
    suspend fun getJwAppToken(): String?
    suspend fun saveJwAppToken(token: String, generation: Long? = null)

    suspend fun getMarketToken(): String?
    suspend fun saveMarketToken(token: String, generation: Long? = null)

    suspend fun getChaoxingCredentials(): Pair<String?, String?>
    suspend fun saveChaoxingCredentials(username: String?, password: String?, generation: Long? = null)

    suspend fun getWeLearnCredentials(): Pair<String?, String?>
    suspend fun saveWeLearnCredentials(username: String?, password: String?, generation: Long? = null)

    suspend fun getCProgCredentials(): Triple<String?, String?, String?>
    suspend fun saveCProgCredentials(username: String?, password: String?, idno: String?, generation: Long? = null)

    // ── 清理 ──────────────────────────────────────────
    suspend fun clearAllCredentials()
    suspend fun clearThirdPartyCredentials()
}
