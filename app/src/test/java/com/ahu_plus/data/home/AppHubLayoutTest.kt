package com.ahu_plus.data.home

import com.ahu_plus.data.GsonProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 应用页排版配置的纯逻辑测试:normalize 清洗、arrange 排序/分组、JSON 往返。
 * 用 [AppRegistry.allKeys] 动态取真实 key,避免硬编码注册表。
 */
class AppHubLayoutTest {

    private val allKeys = AppRegistry.allKeys().toList()
    private val gson = GsonProvider.instance

    // ── arrange:分组 ────────────────────────────────────────────────

    @Test
    fun arrangeFlatReturnsSingleNullTitledSectionWithAllApps() {
        val sections = AppRegistry.arrange(AppHubLayoutConfig(groupMode = AppHubGroupMode.FLAT))
        assertEquals(1, sections.size)
        assertNull(sections.first().title)
        assertEquals(allKeys.toSet(), sections.first().apps.map { it.key }.toSet())
    }

    @Test
    fun arrangeByCategoryUnionCoversAllVisibleApps() {
        val sections = AppRegistry.arrange(AppHubLayoutConfig(groupMode = AppHubGroupMode.BY_CATEGORY))
        // 每个分区都有标题
        assertTrue(sections.all { it.title != null })
        // 所有分区拼起来 == 全部 app,无遗漏无重复
        val union = sections.flatMap { it.apps.map { spec -> spec.key } }
        assertEquals(allKeys.size, union.size)
        assertEquals(allKeys.toSet(), union.toSet())
    }

    // ── arrange:隐藏 ────────────────────────────────────────────────

    @Test
    fun arrangeExcludesHiddenKeys() {
        val hidden = allKeys.first()
        val sections = AppRegistry.arrange(
            AppHubLayoutConfig(hiddenKeys = setOf(hidden), groupMode = AppHubGroupMode.FLAT),
        )
        val keys = sections.flatMap { it.apps.map { spec -> spec.key } }
        assertTrue(hidden !in keys)
        assertEquals(allKeys.size - 1, keys.size)
    }

    // ── arrange:排序 ────────────────────────────────────────────────

    @Test
    fun arrangeNameSortOrdersByChineseCollator() {
        val apps = AppRegistry.arrange(
            AppHubLayoutConfig(sortMode = AppHubSortMode.NAME, groupMode = AppHubGroupMode.FLAT),
        ).first().apps
        val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
        val expected = apps.map { it.title }.sortedWith(collator)
        assertEquals(expected, apps.map { it.title })
    }

    @Test
    fun arrangeCustomSortHonorsExplicitOrderPrefix() {
        // 把最后两个 key 提到最前
        val custom = listOf(allKeys.last(), allKeys[allKeys.size - 2])
        val apps = AppRegistry.arrange(
            AppHubLayoutConfig(
                sortMode = AppHubSortMode.CUSTOM,
                customOrder = custom,
                groupMode = AppHubGroupMode.FLAT,
            ),
        ).first().apps
        assertEquals(custom, apps.take(2).map { it.key })
    }

    @Test
    fun arrangeFrequencySortPutsMostUsedFirst() {
        val target = allKeys[allKeys.size / 2]
        val apps = AppRegistry.arrange(
            AppHubLayoutConfig(sortMode = AppHubSortMode.FREQUENCY, groupMode = AppHubGroupMode.FLAT),
            usageCounts = mapOf(target to 999),
        ).first().apps
        assertEquals(target, apps.first().key)
    }

    @Test
    fun arrangeRecentSortPutsRecentKeysFirst() {
        val recent = listOf(allKeys.last(), allKeys.first())
        val apps = AppRegistry.arrange(
            AppHubLayoutConfig(sortMode = AppHubSortMode.RECENT, groupMode = AppHubGroupMode.FLAT),
            recentKeys = recent,
        ).first().apps
        assertEquals(recent, apps.take(2).map { it.key })
    }

    // ── normalize ───────────────────────────────────────────────────

    @Test
    fun normalizeForcesSingleColumnForCompactStyle() {
        val normalized = AppHubLayoutConfig(
            columns = AppHubColumns.THREE,
            cardStyle = AppHubCardStyle.COMPACT,
        ).normalize(allKeys.toSet())
        assertEquals(AppHubColumns.ONE, normalized.columns)
    }

    @Test
    fun normalizeStripsInvalidHiddenAndCustomKeys() {
        val valid = allKeys.first()
        val normalized = AppHubLayoutConfig(
            hiddenKeys = setOf(valid, "bogus-hidden"),
            customOrder = listOf("bogus-a", valid, valid, "bogus-b"),
        ).normalize(allKeys.toSet())
        assertEquals(setOf(valid), normalized.hiddenKeys)
        // 无效 key 剔除 + 去重
        assertEquals(listOf(valid), normalized.customOrder)
    }

    @Test
    fun normalizeKeepsNonCompactColumns() {
        val normalized = AppHubLayoutConfig(
            columns = AppHubColumns.THREE,
            cardStyle = AppHubCardStyle.VERTICAL,
        ).normalize(allKeys.toSet())
        assertEquals(AppHubColumns.THREE, normalized.columns)
    }

    // ── JSON 往返 ───────────────────────────────────────────────────

    @Test
    fun jsonRoundTripPreservesAllFields() {
        val original = AppHubLayoutConfig(
            columns = AppHubColumns.THREE,
            cardStyle = AppHubCardStyle.VERTICAL,
            density = AppHubDensity.COMPACT,
            groupMode = AppHubGroupMode.FLAT,
            sortMode = AppHubSortMode.CUSTOM,
            showSectionHeaders = false,
            showSearchBar = false,
            showThirdPartyServices = false,
            showIcons = false,
            customOrder = listOf(allKeys.first(), allKeys.last()),
            hiddenKeys = setOf(allKeys[1]),
        )
        val json = gson.toJson(original)
        val restored = gson.fromJson(json, AppHubLayoutConfig::class.java)
        assertEquals(original, restored)
    }

    // ── fromStoredJson:后加字段向后兼容 ──────────────────────────────

    @Test
    fun fromStoredJsonDefaultsMissingShowIconsToTrue() {
        // 模拟「加 showIcons 之前」存下的旧 JSON:有别的键,唯独没有 showIcons。
        // Gson 直接 fromJson 会把缺失 Boolean 填成 false,fromStoredJson 必须回退到默认 true。
        val legacyJson = """
            {"columns":"THREE","cardStyle":"VERTICAL","density":"COMPACT",
             "groupMode":"FLAT","sortMode":"DEFAULT",
             "showSectionHeaders":false,"showSearchBar":false,"showThirdPartyServices":false,
             "customOrder":[],"hiddenKeys":[]}
        """.trimIndent()
        val restored = AppHubLayoutConfig.fromStoredJson(legacyJson)
        assertTrue("缺失的 showIcons 应回退默认 true", restored.showIcons)
        // 旧 JSON 里显式写了的键要照旧保留
        assertEquals(AppHubColumns.THREE, restored.columns)
        assertEquals(AppHubCardStyle.VERTICAL, restored.cardStyle)
        assertEquals(false, restored.showSearchBar)
    }

    @Test
    fun fromStoredJsonHonorsExplicitShowIconsFalse() {
        val json = gson.toJson(AppHubLayoutConfig.Default.copy(showIcons = false))
        val restored = AppHubLayoutConfig.fromStoredJson(json)
        assertEquals(false, restored.showIcons)
    }

    @Test
    fun fromStoredJsonFallsBackToDefaultOnNullOrGarbage() {
        assertEquals(AppHubLayoutConfig.Default, AppHubLayoutConfig.fromStoredJson(null))
        assertEquals(AppHubLayoutConfig.Default, AppHubLayoutConfig.fromStoredJson(""))
        assertEquals(AppHubLayoutConfig.Default, AppHubLayoutConfig.fromStoredJson("not json {["))
    }

    @Test
    fun defaultConfigIsStableAcrossRoundTrip() {
        val json = gson.toJson(AppHubLayoutConfig.Default)
        val restored = gson.fromJson(json, AppHubLayoutConfig::class.java)
        assertEquals(AppHubLayoutConfig.Default, restored)
    }

    @Test
    fun defaultDiffersFromCustomized() {
        assertNotEquals(
            AppHubLayoutConfig.Default,
            AppHubLayoutConfig.Default.copy(columns = AppHubColumns.THREE),
        )
    }
}
