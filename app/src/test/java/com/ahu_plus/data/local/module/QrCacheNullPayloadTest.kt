package com.ahu_plus.data.local.module

import com.ahu_plus.data.GsonProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 回归:Release 闪退 — Gson 绕过 Kotlin 构造器(Unsafe)反序列化旧缓存 JSON 时,
 * `payload: String` 字段运行时可能为 null;R8 按非空类型删掉空检查后
 * `isBlank(null)` 抛 NPE(仅 release 崩溃,debug 正常)。
 *
 * 修复:QrCodeCache 的 payload/serverText 标可空;读取处走显式可空的
 * `orNullIfBlank()`(参数可空,R8 无法删空检查)。本测试复现前提 + 验证读取语义。
 */
class QrCacheNullPayloadTest {

    /** 复现崩溃前提:Gson 可从缺字段/显式 null 的 JSON 产出 payload==null 的实例。 */
    @Test
    fun `gson can produce QrCodeCache with null payload field`() {
        val cache = GsonProvider.instance.fromJson(
            """{"serverText":"ok","fetchedAt":1,"generation":2}""",
            CacheModule.QrCodeCache::class.java,
        )
        assertNull(cache.payload)
    }

    /** 与 CacheModuleImpl.orNullIfBlank 同款语义:null/空白 → null,否则原值。 */
    private fun String?.orNullIfBlank(): String? = if (isNullOrBlank()) null else this

    @Test
    fun `null payload falls through legacy source without crash`() {
        val cache = GsonProvider.instance.fromJson(
            """{"payload":null,"serverText":"ok","fetchedAt":1,"generation":2}""",
            CacheModule.QrCodeCache::class.java,
        )
        // 与 getQrCodeCache 读取处同款调用链,回归期防止语义漂移(改回 isNotBlank 等)
        val legacyPlaintextPayload = cache.payload.orNullIfBlank()
        assertNull(legacyPlaintextPayload)
    }

    @Test
    fun `blank payload also falls through, non-blank kept`() {
        val cache = GsonProvider.instance.fromJson(
            """{"payload":"  ","fetchedAt":1,"generation":2}""",
            CacheModule.QrCodeCache::class.java,
        )
        assertNull(cache.payload.orNullIfBlank())

        val valid = GsonProvider.instance.fromJson(
            """{"payload":"abc","fetchedAt":1,"generation":2}""",
            CacheModule.QrCodeCache::class.java,
        )
        assertEquals("abc", valid.payload.orNullIfBlank())
    }
}
