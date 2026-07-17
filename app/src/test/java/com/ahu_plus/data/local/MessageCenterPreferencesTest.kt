package com.ahu_plus.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageCenterPreferencesTest {
    @Test
    fun previewCountOnlyAcceptsSupportedOptions() {
        assertEquals(0, normalizeMessagePreviewCount(0))
        assertEquals(3, normalizeMessagePreviewCount(3))
        assertEquals(10, normalizeMessagePreviewCount(10))
        assertEquals(3, normalizeMessagePreviewCount(7))
    }
}
