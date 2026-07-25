package com.ahu_plus.ui.screen.profile

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareSheetSecurityTest {
    @Test
    fun `apk sharing delegates download and verification to update manager`() {
        val source = File("src/main/java/com/ahu_plus/ui/screen/profile/ShareSheet.kt").readText()

        assertTrue(source.contains("updateManager.downloadVerifiedApkForSharing"))
        assertFalse(source.contains("OkHttpClient.Builder"))
        assertFalse(source.contains(".execute()"))
    }

    @Test
    fun `update downloads use the secure client factory`() {
        val source = File("src/main/java/com/ahu_plus/data/update/UpdateManager.kt").readText()

        assertTrue(source.contains("SecureHttpClientFactory.create"))
        assertFalse(source.contains("OkHttpClient.Builder"))
    }
}
