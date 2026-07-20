package com.ahu_plus.notification

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestSecurityTest {
    @Test
    fun `manifest keeps exact alarms only for user reminders`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertFalse(manifest.contains("android.permission.USE_EXACT_ALARM"))
        assertFalse(manifest.contains(".notification.WidgetUpdateScheduler"))
    }
}
