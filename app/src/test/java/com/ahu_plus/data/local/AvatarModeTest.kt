package com.ahu_plus.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarModeTest {

    @Test
    fun `fromStored returns DEFAULT for null, blank or unknown values`() {
        assertEquals(AvatarMode.DEFAULT, AvatarMode.fromStored(null))
        assertEquals(AvatarMode.DEFAULT, AvatarMode.fromStored(""))
        assertEquals(AvatarMode.DEFAULT, AvatarMode.fromStored("garbage"))
    }

    @Test
    fun `fromStored roundtrips each mode name`() {
        AvatarMode.entries.forEach { mode ->
            assertEquals(mode, AvatarMode.fromStored(mode.name))
        }
    }
}
