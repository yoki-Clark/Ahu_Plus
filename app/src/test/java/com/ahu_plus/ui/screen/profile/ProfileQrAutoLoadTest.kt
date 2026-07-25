package com.ahu_plus.ui.screen.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileQrAutoLoadTest {
    @Test
    fun `opening an empty payment code panel starts loading`() {
        assertTrue(shouldEnsureProfileQr(isPanelVisible = true))
    }

    @Test
    fun `hidden payment code panel does not load`() {
        assertFalse(shouldEnsureProfileQr(isPanelVisible = false))
    }
}
