package com.ahu_plus.data.developer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperPayloadAnalyzerTest {
    @Test
    fun `analyzes and formats json`() {
        val result = DeveloperPayloadAnalyzer.analyze("{\"rows\":[1,2],\"total\":2}")

        assertEquals(DeveloperPayloadType.JSON, result.type)
        assertTrue(result.details.any { it.contains("字段数量：2") })
        assertTrue(result.formatted.contains("\n"))
    }

    @Test
    fun `reports malformed json without throwing`() {
        val result = DeveloperPayloadAnalyzer.analyze("{\"rows\":")

        assertEquals(DeveloperPayloadType.INVALID_JSON, result.type)
        assertEquals("JSON 解析失败", result.summary)
    }

    @Test
    fun `detects cas login html`() {
        val result = DeveloperPayloadAnalyzer.analyze(
            "<html><head><title>CAS</title></head><body><form><input name='lt'></form></body></html>",
        )

        assertEquals(DeveloperPayloadType.HTML, result.type)
        assertTrue(result.details.any { it.contains("CAS 登录表单") })
    }
}
