// 本测试的目的就是断言这些已废弃 API 的格式参数不变，deprecation 警告在此无信息量。
// 背景见 BUG_REVIEW.md「security-crypto 已停止维护」。
@file:Suppress("DEPRECATION")

package com.ahu_plus.data.local

import android.app.Application
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * security-crypto 升级后的老用户凭据兼容护栏（配套 alpha06 -> 1.1.0 升级）。
 *
 * 能力边界：Robolectric 不提供 AndroidKeyStore provider，`EncryptedSharedPreferences.create`
 * 必然失败，因此**无法在 JVM 内做跨版本真实读写**。旧密文能否被新版本解开，取决于四个
 * 格式决定参数是否不变：prefs 文件名、MasterKey 别名、key/value 加密方案；密钥材料本身
 * 存于系统 Keystore，不随库版本变化，且 alpha06 与 1.1.0 都依赖 tink-android 1.8.0
 * （keyset 格式一致）。本测试锁定这四个参数，任何一处漂移都会让老用户凭据变成不可读。
 *
 * 仍需真机验证：升级安装后旧登录态可自动恢复（本测试不能替代）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class EncryptedCredentialStoreCompatTest {

    private val source =
        File("src/main/java/com/ahu_plus/data/local/EncryptedCredentialStore.kt").readText()

    @Test
    fun `format determining parameters stay pinned to the alpha06 layout`() {
        // 1. prefs 文件名：换名等于旧密文文件被弃用
        assertTrue(
            "凭据 prefs 文件名必须保持 ahu_plus_credentials",
            source.contains("""private const val FILE_NAME = "ahu_plus_credentials""""),
        )

        // 2. MasterKey 别名：库侧默认别名变了会导致用新主密钥解旧密文
        assertEquals(
            "MasterKey 默认别名发生变化，旧密文将无法解密",
            "_androidx_security_master_key_",
            MasterKey.DEFAULT_MASTER_KEY_ALIAS,
        )
        assertTrue(
            "必须使用默认别名（不得自定义 setKeyAlias）",
            !source.contains("setKeyAlias("),
        )

        // 3. 主密钥方案与 key/value 加密方案：三者共同决定密文格式
        assertTrue(source.contains("setKeyScheme(MasterKey.KeyScheme.AES256_GCM)"))
        assertTrue(
            source.contains("EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV"),
        )
        assertTrue(
            source.contains("EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM"),
        )

        // 4. 库侧枚举仍存在同名常量（改名/移除会让上面的源码断言失去意义）
        assertEquals("AES256_SIV", EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV.name)
        assertEquals(
            "AES256_GCM",
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM.name,
        )
        assertEquals("AES256_GCM", MasterKey.KeyScheme.AES256_GCM.name)
    }

    @Test
    fun `keystore unavailable degrades to memory without plaintext fallback`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        // 仅作明文落盘扫描的标记串（刻意短且无 credential 关键字，避免触发 check_secrets.py）
        val probeMarker = "leak-probe-4f2a"

        // Keystore 不可用时构造不得抛异常（否则 SessionManager.init 会整体失败）
        val store = EncryptedCredentialStore(context)

        // 写入失败必须如实返回 false，不得假成功
        assertFalse(
            "Keystore 不可用时 putString 必须返回 false",
            store.putString(EncryptedCredentialStore.CAS_PASSWORD, probeMarker),
        )
        assertNull(store.getString(EncryptedCredentialStore.CAS_PASSWORD))

        // 关键红线：任何落盘文件都不得包含明文标记串
        val leaked = context.dataDir.walkTopDown()
            .filter { it.isFile }
            .filter { it.length() < 1_000_000 }
            .filter { file ->
                runCatching { file.readText().contains(probeMarker) }.getOrDefault(false)
            }
            .map { it.path }
            .toList()
        assertTrue("加密失败后出现明文落盘: $leaked", leaked.isEmpty())

        // remove/clearAll 在降级状态下也必须静默可用
        store.remove(EncryptedCredentialStore.CAS_PASSWORD)
        store.remove(listOf(EncryptedCredentialStore.CAS_USERNAME))
        store.clearAll()
    }

    @Test
    fun `credential key names stay stable across the library upgrade`() {
        // key 名是密文的查找键；改名等于旧数据不可达。别名常量必须指向同一字符串。
        assertEquals(EncryptedCredentialStore.CAS_USERNAME, EncryptedCredentialStore.USERNAME)
        assertEquals(EncryptedCredentialStore.CAS_PASSWORD, EncryptedCredentialStore.PASSWORD)
        assertEquals(EncryptedCredentialStore.JW_PST_SID, EncryptedCredentialStore.PSTSID)
        assertEquals(
            EncryptedCredentialStore.MARKET_LEGACY_IDENTITY,
            EncryptedCredentialStore.MARKET_TOKEN,
        )
        assertEquals(EncryptedCredentialStore.CHAOXING_PHONE, EncryptedCredentialStore.CHAOXING_USERNAME)

        // 账号维度的 key 快照：值必须与升级前一致
        assertEquals("cas_username", EncryptedCredentialStore.CAS_USERNAME)
        assertEquals("cas_password", EncryptedCredentialStore.CAS_PASSWORD)
        assertEquals("portal_session", EncryptedCredentialStore.PORTAL_SESSION)
        assertEquals("jw_session", EncryptedCredentialStore.JW_SESSION)
        assertEquals("jw_pst_sid", EncryptedCredentialStore.JW_PST_SID)
        assertEquals("adwmh_session", EncryptedCredentialStore.ADWMH_SESSION)
        assertEquals("kq_session", EncryptedCredentialStore.KQ_SESSION)

        // 清理集合必须覆盖账号凭据，退登才不会残留旧账号密文
        assertTrue(
            EncryptedCredentialStore.ACCOUNT_KEYS.containsAll(
                setOf(
                    EncryptedCredentialStore.CAS_USERNAME,
                    EncryptedCredentialStore.CAS_PASSWORD,
                    EncryptedCredentialStore.PORTAL_SESSION,
                    EncryptedCredentialStore.JW_SESSION,
                ),
            ),
        )
        assertTrue(
            EncryptedCredentialStore.ACCOUNT_KEYS
                .intersect(EncryptedCredentialStore.THIRD_PARTY_KEYS)
                .isEmpty(),
        )
    }
}
