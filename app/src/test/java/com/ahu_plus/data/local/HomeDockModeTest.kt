package com.ahu_plus.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDockModeTest {
    @Test
    fun defaultValueIsFavorite() {
        assertEquals(HomeDockMode.FAVORITE, HomeDockMode.DEFAULT)
    }

    @Test
    fun fromStorageValueRoundTripsKnownValues() {
        assertEquals(HomeDockMode.RECENT, HomeDockMode.fromStorageValue("recent"))
        assertEquals(HomeDockMode.FAVORITE, HomeDockMode.fromStorageValue("favorite"))
    }

    @Test
    fun fromStorageValueFallsBackToDefaultForNullOrUnknown() {
        assertEquals(HomeDockMode.DEFAULT, HomeDockMode.fromStorageValue(null))
        assertEquals(HomeDockMode.DEFAULT, HomeDockMode.fromStorageValue(""))
        assertEquals(HomeDockMode.DEFAULT, HomeDockMode.fromStorageValue("bogus"))
    }

    @Test
    fun storageValueIsStable() {
        assertEquals("recent", HomeDockMode.RECENT.storageValue)
        assertEquals("favorite", HomeDockMode.FAVORITE.storageValue)
    }
}
