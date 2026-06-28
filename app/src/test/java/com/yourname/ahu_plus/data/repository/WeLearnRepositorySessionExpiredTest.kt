package com.yourname.ahu_plus.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 验证 SFLEP 未登录返回 HTML 登录页时,WeLearnRepository 把 body 识别为 session expired,
 * VM 端据此触发 needsLogin + 自动重登。
 *
 * ponytail: 直接调 companion 里的 helper,跳过 Repository 完整构造(SessionManager + AuthRepo 链路太重)。
 */
class WeLearnRepositorySessionExpiredTest {

    @Test
    fun `html body throws IOException with session expired prefix`() {
        val ex = assertThrows(IOException::class.java) {
            WeLearnRepository.parseJsonOrSessionExpired("<html><body>login</body></html>", "authCourse gmc")
        }
        assertTrue(ex.message!!.startsWith(WeLearnRepository.SESSION_EXPIRED_PREFIX))
        assertTrue(ex.message!!.contains("authCourse gmc"))
    }

    @Test
    fun `malformed json body throws IOException with session expired prefix`() {
        val ex = assertThrows(IOException::class.java) {
            WeLearnRepository.parseJsonOrSessionExpired("{not valid json", "courseunits")
        }
        assertTrue(ex.message!!.startsWith(WeLearnRepository.SESSION_EXPIRED_PREFIX))
        assertTrue(ex.message!!.contains("(parse)"))
    }

    @Test
    fun `valid json body returns JsonObject`() {
        val obj = WeLearnRepository.parseJsonOrSessionExpired("""{"clist":[]}""", "authCourse gmc")
        assertEquals(0, obj.getAsJsonArray("clist").size())
    }
}