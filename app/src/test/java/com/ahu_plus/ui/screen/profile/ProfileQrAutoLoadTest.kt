package com.ahu_plus.ui.screen.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileQrAutoLoadTest {
    @Test
    fun `opening an empty payment code panel starts loading`() {
        assertTrue(
            shouldAutoLoadProfileQr(
                isPanelVisible = true,
                hasQrCode = false,
                isLoading = false,
                hasError = false,
            )
        )
    }

    @Test
    fun `payment code panel does not duplicate requests`() {
        assertFalse(shouldAutoLoadProfileQr(false, false, false, false))
        assertFalse(shouldAutoLoadProfileQr(true, true, false, false))
        assertFalse(shouldAutoLoadProfileQr(true, false, true, false))
        assertFalse(shouldAutoLoadProfileQr(true, false, false, true))
    }
}
