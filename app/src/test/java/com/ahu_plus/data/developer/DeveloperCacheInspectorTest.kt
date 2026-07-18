package com.ahu_plus.data.developer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperCacheInspectorTest {
    @Test
    fun `all credential key families are marked sensitive`() {
        val keys = listOf(
            "password",
            "cx_tiku_token",
            "cx_cookies",
            "jw_session_id",
            "market_api_identity",
            "cprog_jwt",
            "adwmh_qr_payload",
            "custom_api_key",
            "custom-api-key",
            "qrcode_payload",
            "auth_header",
            "cx_ai_key",
            "cx_go_authorization",
        )

        keys.forEach { key ->
            assertTrue("Expected $key to be sensitive", DeveloperCacheInspector.isSensitiveKey(key))
        }
        assertFalse(DeveloperCacheInspector.isSensitiveKey("theme_mode"))
        assertFalse(DeveloperCacheInspector.isSensitiveKey("schedule_row_height"))
    }

    @Test
    fun `sensitive raw values never enter report or exports`() {
        val secret = "do-not-export-this-value"
        val report = DeveloperCacheInspector.inspect(
            mapOf(
                "api_token" to secret,
                "theme_mode" to "dark",
            ),
        )

        val sensitive = report.entries.first { it.keyName == "api_token" }
        assertEquals(DeveloperCacheInspector.REDACTED_SUMMARY, sensitive.summary)
        assertFalse(DeveloperCacheInspector.exportRedactedText(report).contains(secret))
        assertFalse(DeveloperCacheInspector.exportRedactedJson(report).contains(secret))
        assertTrue(DeveloperCacheInspector.exportRedactedJson(report).contains("<redacted>"))
        assertFalse(DeveloperCacheInspector.exportRedactedText(report).contains("dark"))
    }

    @Test
    fun `developer enabled key is protected from raw deletion`() {
        assertTrue(DeveloperCacheRepository.isProtectedKeyName("developer_enabled"))
        assertFalse(DeveloperCacheRepository.isProtectedKeyName("schedule_json"))
    }

    @Test
    fun `json arrays and nested record arrays expose counts without contents`() {
        val report = DeveloperCacheInspector.inspect(
            mapOf(
                "public_items_json" to """[{"name":"one"},{"name":"two"}]""",
                "weather_json" to """{"data":{"items":[1,2,3]},"city":"Hefei"}""",
            ),
        )

        val array = report.entries.first { it.keyName == "public_items_json" }
        assertEquals(DeveloperJsonState.VALID, array.jsonState)
        assertEquals(2, array.jsonRecordCount)
        assertFalse(array.summary.contains("one"))

        val nested = report.entries.first { it.keyName == "weather_json" }
        assertEquals(3, nested.jsonRecordCount)
        assertFalse(nested.summary.contains("Hefei"))
    }

    @Test
    fun `malformed declared json is reported as invalid`() {
        val entry = DeveloperCacheInspector.inspectEntry("schedule_json", "not-json")

        assertEquals(DeveloperJsonState.INVALID, entry.jsonState)
        assertEquals("Invalid JSON", entry.summary)
        assertNull(entry.jsonRecordCount)
    }

    @Test
    fun `free form notes beginning with braces are not treated as json`() {
        val courseNote = DeveloperCacheInspector.inspectEntry("course_note_MATH1001", "{待补充")
        val slotNote = DeveloperCacheInspector.inspectEntry("slot_note_12345_3", "[重点内容")

        assertEquals(DeveloperJsonState.NOT_APPLICABLE, courseNote.jsonState)
        assertEquals(DeveloperJsonState.NOT_APPLICABLE, slotNote.jsonState)
    }

    @Test
    fun `declared json keys without json suffix are still validated`() {
        val valid = DeveloperCacheInspector.inspectEntry("market_block_keywords", "[\"spam\"]")
        val invalid = DeveloperCacheInspector.inspectEntry("empty_classroom_presets", "not-json")

        assertEquals(DeveloperJsonState.VALID, valid.jsonState)
        assertEquals(DeveloperJsonState.INVALID, invalid.jsonState)
    }

    @Test
    fun `dynamic course identifiers are removed from exports`() {
        val report = DeveloperCacheInspector.inspect(
            mapOf("assessment_lesson-secret-2023123456" to "{}"),
        )

        val text = DeveloperCacheInspector.exportRedactedText(report)
        val json = DeveloperCacheInspector.exportRedactedJson(report)

        assertFalse(text.contains("lesson-secret-2023123456"))
        assertFalse(json.contains("lesson-secret-2023123456"))
        assertTrue(text.contains("assessment_<redacted:"))
    }

    @Test
    fun `report totals and categories are deterministic`() {
        val report = DeveloperCacheInspector.inspect(
            linkedMapOf(
                "theme_mode" to "dark",
                "schedule_json" to "[]",
                "cx_cookies" to "secret",
                "beta_enabled" to true,
            ),
        )

        assertEquals(
            listOf("beta_enabled", "cx_cookies", "schedule_json", "theme_mode"),
            report.entries.map { it.keyName },
        )
        assertEquals(4, report.totalEntryCount)
        assertEquals(1, report.sensitiveEntryCount)
        assertEquals(1, report.validJsonCount)
        assertEquals(report.entries.sumOf { it.estimatedBytes }, report.totalEstimatedBytes)
        assertEquals(
            report.totalEntryCount,
            report.categories.sumOf { it.entryCount },
        )
        assertEquals(
            report.totalEstimatedBytes,
            report.categories.sumOf { it.estimatedBytes },
        )
    }

    @Test
    fun `preference types and byte estimates cover all supported value types`() {
        val values = linkedMapOf<String, Any>(
            "s" to "abc",
            "b" to true,
            "i" to 1,
            "l" to 1L,
            "f" to 1f,
            "d" to 1.0,
            "set" to setOf("a", "bb"),
        )
        val report = DeveloperCacheInspector.inspect(values)

        assertEquals(DeveloperPreferenceType.STRING, report.entries.byName("s").type)
        assertEquals(DeveloperPreferenceType.BOOLEAN, report.entries.byName("b").type)
        assertEquals(DeveloperPreferenceType.INT, report.entries.byName("i").type)
        assertEquals(DeveloperPreferenceType.LONG, report.entries.byName("l").type)
        assertEquals(DeveloperPreferenceType.FLOAT, report.entries.byName("f").type)
        assertEquals(DeveloperPreferenceType.DOUBLE, report.entries.byName("d").type)
        assertEquals(DeveloperPreferenceType.STRING_SET, report.entries.byName("set").type)
        assertEquals(4L, report.entries.byName("s").estimatedBytes)
        assertEquals(7L, report.entries.byName("set").estimatedBytes)
    }

    private fun List<DeveloperPreferenceEntry>.byName(name: String): DeveloperPreferenceEntry =
        first { it.keyName == name }
}
