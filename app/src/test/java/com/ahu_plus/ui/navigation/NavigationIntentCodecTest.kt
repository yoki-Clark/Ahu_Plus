package com.ahu_plus.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `NavigationIntentCodec` 纯 JVM 测试:覆盖编解码 round trip、legacy 深链回退和异常输入。
 *
 * 不依赖 Robolectric:仅通过 [NavigationIntentCodec.encode] / [NavigationIntentCodec.decodeEncoded]
 * 与 [NavigationIntentCodec.legacyTarget] 三个纯函数入口验证序列化稳定性。
 */
class NavigationIntentCodecTest {

    // ── legacy 深链回退 ─────────────────────────────────────────

    @Test
    fun `legacy deep links map through the same target model`() {
        assertEquals(HomeTarget(HomeRoute.SCHEDULE), NavigationIntentCodec.legacyTarget("schedule"))
        assertEquals(HomeTarget(HomeRoute.AGENDA), NavigationIntentCodec.legacyTarget("agenda"))
        assertEquals(HomeTarget(HomeRoute.GRADE), NavigationIntentCodec.legacyTarget("grade"))
        assertEquals(ChaoxingTarget(), NavigationIntentCodec.legacyTarget("chaoxing"))
        assertEquals(WeLearnTarget(), NavigationIntentCodec.legacyTarget("welearn"))
        assertNull(NavigationIntentCodec.legacyTarget("unknown"))
        assertNull(NavigationIntentCodec.legacyTarget(null))
        assertNull(NavigationIntentCodec.legacyTarget(""))
    }

    // ── Home target round trip ──────────────────────────────────

    @Test
    fun `encode decode round trip preserves home target and source`() {
        val request = NavigationRequest(HomeTarget(HomeRoute.SCHEDULE), NavigationSource.NOTIFICATION)
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves home dashboard with internal source`() {
        val request = NavigationRequest(HomeTarget(HomeRoute.DASHBOARD), NavigationSource.INTERNAL)
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    // ── Apps target round trip ──────────────────────────────────

    @Test
    fun `encode decode round trip preserves apps target with app key`() {
        val request = NavigationRequest(
            AppsTarget(AppsRoute.APP, appKey = "weather"),
            NavigationSource.RECENT_APP,
        )
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves apps evaluation detail with entity id`() {
        val request = NavigationRequest(
            AppsTarget(AppsRoute.EVALUATION_DETAIL, entityId = "2026-001"),
            NavigationSource.INTERNAL,
        )
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves apps root without optional args`() {
        val request = NavigationRequest(AppsTarget(), NavigationSource.TOP_LEVEL)
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    // ── Profile target round trip ───────────────────────────────

    @Test
    fun `encode decode round trip preserves profile target with utility`() {
        val request = NavigationRequest(
            ProfileTarget(ProfileRoute.UTILITY, utility = "bathroom"),
            NavigationSource.INTERNAL,
        )
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves profile root`() {
        val request = NavigationRequest(ProfileTarget(), NavigationSource.TOP_LEVEL)
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves profile bills route`() {
        val request = NavigationRequest(ProfileTarget(ProfileRoute.BILLS), NavigationSource.WIDGET)
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    // ── Market target round trip ────────────────────────────────

    @Test
    fun `encode decode round trip preserves market topic with topic id`() {
        val request = NavigationRequest(
            MarketTarget(MarketRoute.TOPIC, topicId = "123456"),
            NavigationSource.DEEP_LINK,
        )
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves market root`() {
        val request = NavigationRequest(MarketTarget(), NavigationSource.TOP_LEVEL)
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves market compose route`() {
        val request = NavigationRequest(
            MarketTarget(MarketRoute.COMPOSE, topicId = "999"),
            NavigationSource.INTERNAL,
        )
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    // ── Chaoxing target round trip ──────────────────────────────

    @Test
    fun `encode decode round trip preserves chaoxing root`() {
        val request = NavigationRequest(ChaoxingTarget(), NavigationSource.TOP_LEVEL)
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves chaoxing course with sub tab and entity id`() {
        val request = NavigationRequest(
            ChaoxingTarget(ChaoxingRoute.COURSE, subTab = "homework", entityId = "course-123"),
            NavigationSource.NOTIFICATION,
        )
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves chaoxing study with sub tab only`() {
        val request = NavigationRequest(
            ChaoxingTarget(ChaoxingRoute.STUDY, subTab = "video"),
            NavigationSource.SERVICE,
        )
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    // ── WeLearn target round trip ───────────────────────────────

    @Test
    fun `encode decode round trip preserves welearn root`() {
        val request = NavigationRequest(WeLearnTarget(), NavigationSource.TOP_LEVEL)
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves welearn course with course id`() {
        val request = NavigationRequest(
            WeLearnTarget(WeLearnRoute.COURSE, courseId = "wl-789"),
            NavigationSource.RECENT_APP,
        )
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves welearn study with course id and unit ids`() {
        val request = NavigationRequest(
            WeLearnTarget(WeLearnRoute.STUDY, courseId = "wl-1", unitIds = listOf(1, 2, 3)),
            NavigationSource.SERVICE,
        )
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves welearn study with single unit id`() {
        val request = NavigationRequest(
            WeLearnTarget(WeLearnRoute.STUDY, courseId = "wl-2", unitIds = listOf(42)),
            NavigationSource.INTERNAL,
        )
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
    }

    @Test
    fun `encode decode round trip preserves welearn with empty unit ids list`() {
        // unitIds 默认空列表,序列化时省略,反序列化时应回填为空列表
        val request = NavigationRequest(
            WeLearnTarget(WeLearnRoute.COURSE, courseId = "wl-3"),
            NavigationSource.INTERNAL,
        )
        val restored = NavigationIntentCodec.decodeEncoded(NavigationIntentCodec.encode(request))

        assertEquals(request, restored)
        assertEquals(emptyList<Int>(), restored?.target?.let { (it as WeLearnTarget).unitIds })
    }

    // ── 异常输入与回退 ─────────────────────────────────────────

    @Test
    fun `decode encoded returns null for corrupt json`() {
        assertNull(NavigationIntentCodec.decodeEncoded("not-json"))
        assertNull(NavigationIntentCodec.decodeEncoded(""))
    }

    @Test
    fun `decode encoded returns null for unknown top level`() {
        val encoded = """{"target":{"topLevel":"UNKNOWN","route":"SCHEDULE","args":{}},"source":"INTERNAL"}"""
        assertNull(NavigationIntentCodec.decodeEncoded(encoded))
    }

    @Test
    fun `decode encoded returns null for unknown route`() {
        val encoded = """{"target":{"topLevel":"HOME","route":"UNKNOWN","args":{}},"source":"INTERNAL"}"""
        assertNull(NavigationIntentCodec.decodeEncoded(encoded))
    }

    @Test
    fun `decode encoded falls back to deep link source for unknown source name`() {
        val encoded = """{"target":{"topLevel":"HOME","route":"SCHEDULE","args":{}},"source":"UNKNOWN_SOURCE"}"""
        val restored = NavigationIntentCodec.decodeEncoded(encoded)

        assertEquals(NavigationSource.DEEP_LINK, restored?.source)
        assertEquals(HomeTarget(HomeRoute.SCHEDULE), restored?.target)
    }

    @Test
    fun `decode encoded handles unit ids with non integer entries by dropping them`() {
        // 单元列表含非数字时应被静默丢弃,不抛异常
        val encoded = """{"target":{"topLevel":"WELEARN","route":"STUDY","args":{"courseId":"wl-x","unitIds":"1,abc,3"}},"source":"INTERNAL"}"""
        val restored = NavigationIntentCodec.decodeEncoded(encoded)

        assertEquals(
            WeLearnTarget(WeLearnRoute.STUDY, courseId = "wl-x", unitIds = listOf(1, 3)),
            restored?.target,
        )
    }

    @Test
    fun `decode encoded handles missing args map`() {
        // args 缺失时所有可选字段应为 null/empty
        val encoded = """{"target":{"topLevel":"APPS","route":"APP"},"source":"INTERNAL"}"""
        val restored = NavigationIntentCodec.decodeEncoded(encoded)

        assertEquals(AppsTarget(AppsRoute.APP), restored?.target)
    }
}
