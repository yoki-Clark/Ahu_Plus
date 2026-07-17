package com.ahu_plus.data.repository

import okhttp3.Cookie
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CasCookieSlotTest {

    @Test
    fun `cookies with the same name but different paths occupy different slots`() {
        val portal = cookie("/tp_up")
        val studentTable = cookie("/tp_ep_stu")

        assertFalse(isSameCookieSlot(portal, studentTable))
        assertTrue(isSameCookieSlot(portal, cookie("/tp_up")))
    }

    private fun cookie(path: String) = Cookie.Builder()
        .name("JSESSIONID")
        .value("value")
        .domain("one.ahu.edu.cn")
        .path(path)
        .build()
}
