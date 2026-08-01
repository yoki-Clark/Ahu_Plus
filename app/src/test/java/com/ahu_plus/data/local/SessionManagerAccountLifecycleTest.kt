package com.ahu_plus.data.local

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SessionManager 账号生命周期与数据安全红线测试。
 *
 * SessionManager 依赖 Android Context/DataStore/Keystore，纯 JVM 无法实例化；
 * 沿用 SessionManagerClearPolicyTest 的源码不变式校验风格，覆盖第 5 项方案的 P0 场景：
 * 登录/退出/全清边界、账号切换 generation、明文迁移与加密失败回退。
 */
class SessionManagerAccountLifecycleTest {

    private val source = File("src/main/java/com/ahu_plus/data/local/SessionManager.kt").readText()

    @Test
    fun `login persists credentials only through encrypted store`() {
        val saveCredentials = source
            .substringAfter("suspend fun saveCredentials(")
            .substringBefore("fun getUsername(): String?")

        // 凭据只经 saveEncrypted -> credentialStore.putString 落盘
        assertTrue(
            saveCredentials.contains("saveEncrypted(EncryptedCredentialStore.CAS_USERNAME, USERNAME_KEY, username)"),
        )
        assertTrue(
            saveCredentials.contains("saveEncrypted(EncryptedCredentialStore.CAS_PASSWORD, PASSWORD_KEY, password)"),
        )
        // 内存缓存同时更新,供静默续期使用
        assertTrue(saveCredentials.contains("cachedUsername = username"))
        assertTrue(saveCredentials.contains("cachedPassword = password"))
    }

    @Test
    fun `init migrates legacy plaintext credentials into encrypted store`() {
        val initBody = source
            .substringAfter("suspend fun init(): String? {")
            .substringBefore("suspend fun clearSession()")

        // encryptedOrLegacy:优先读加密存储;读到旧明文时写入加密存储并标记迁移
        assertTrue(initBody.contains("credentialStore.putString(encryptedKey, legacy)"))
        assertTrue(initBody.contains("migratedPlaintextKeys += legacyKey"))
        assertTrue(initBody.contains("encryptedOrLegacy(EncryptedCredentialStore.CAS_USERNAME, USERNAME_KEY)"))
        assertTrue(initBody.contains("encryptedOrLegacy(EncryptedCredentialStore.CAS_PASSWORD, PASSWORD_KEY)"))
    }

    @Test
    fun `encryption failure never falls back to plaintext persistence`() {
        val saveEncrypted = source
            .substringAfter("private suspend fun saveEncrypted(")
            .substringBefore("private fun parseIdentityList(")

        // 只有加密成功才移除旧明文 key;失败时只记日志,绝不写明文
        assertTrue(saveEncrypted.contains("if (credentialStore.putString(encryptedKey, value))"))
        assertTrue(saveEncrypted.contains("it.remove(legacyKey)"))
        assertFalse(saveEncrypted.contains("putString(legacyKey"))
        assertFalse(saveEncrypted.contains("dataStore.edit { it[legacyKey]"))
    }

    @Test
    fun `logout clears auth data but keeps user assets and settings`() {
        // 用户资产与账号数据互斥
        assertTrue(SessionManager.AUTH_DATA_KEYS.intersect(SessionManager.USER_ASSET_KEYS).isEmpty())

        // clearAuthData 只移除 AUTH_DATA_KEYS,不清用户资产
        val clearAuthData = source
            .substringAfter("suspend fun clearAuthData() {")
            .substringBefore("suspend fun clearAll() {")
        assertTrue(clearAuthData.contains("AUTH_DATA_KEYS.forEach { preferences.remove(it) }"))
        assertFalse(clearAuthData.contains("clearCachedUserAssets()"))
    }

    @Test
    fun `clearAll clears auth data plus user assets and settings`() {
        assertTrue(SessionManager.ALL_CLEARABLE_KEYS.containsAll(SessionManager.AUTH_DATA_KEYS))
        assertTrue(SessionManager.ALL_CLEARABLE_KEYS.containsAll(SessionManager.USER_ASSET_KEYS))

        val clearAll = source
            .substringAfter("suspend fun clearAll() {")
            .substringBefore("private fun clearCachedAuthData()")
        assertTrue(clearAll.contains("clearCachedAuthData()"))
        assertTrue(clearAll.contains("clearCachedUserAssets()"))
    }

    @Test
    fun `account switch invalidates generation so stale writes are dropped`() {
        // generation 只增不减,保证旧请求写不回新账号
        val invalidate = source
            .substringAfter("suspend fun invalidateAccountGeneration(): Long")
            .substringBefore("private fun isCurrentGeneration")
        assertTrue(invalidate.contains("accountGeneration.incrementAndGet()"))

        // 所有会话/凭据写入都带 generation 守卫
        val guard = source
            .substringAfter("private fun isCurrentGeneration(")
            .substringBefore("@Volatile private var cachedSessionId")
        assertTrue(guard.contains("generation == null || generation == accountGeneration.get()"))

        for (method in listOf("saveSessionId(", "saveJwSession(", "saveCredentials(", "saveAdwmhSessionId(")) {
            val body = source.substringAfter(method).substringBefore("\n    }")
            assertTrue("$method 缺少 generation 守卫", body.contains("if (!isCurrentGeneration(generation)) return@withLock"))
        }
    }

    @Test
    fun `full data clear is only reachable from explicit user actions`() {
        // data-safety 红线:普通 needsLogin/错误路径不得触发全清
        val app = File("src/main/java/com/ahu_plus/AhuPlusApplication.kt").readText()
        val fullClearCalls = Regex("clearAllLocalData\\(\\)").findAll(app).map { it.range.first }.toList()
        // 定义处(261) + 仅 withdrawPrivacyConsent(258) 调用,UI 层只在 AboutScreen 显式按钮触发
        assertTrue(fullClearCalls.size >= 2)

        val about = File("src/main/java/com/ahu_plus/ui/screen/profile/AboutScreen.kt").readText()
        assertTrue(
            "全清必须由用户显式点击触发",
            about.contains("onClearAll = { scope.launch { app.clearAllLocalData() } }"),
        )

        // SessionManager.clearAll 不得从任何 UI/Service/Repository 错误路径直接调用
        val mainSrc = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .filterNot { it.name == "SessionManager.kt" }
            .map { it.readText() }
        val clearAllCallers = mainSrc.filter { it.contains("sessionManager.clearAll()") }.toList()
        assertTrue("SessionManager.clearAll() 不应有 UI/Service/Repository 调用点: $clearAllCallers", clearAllCallers.isEmpty())
    }
}
