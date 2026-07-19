package com.ahu_plus.data.repository

import java.net.SocketTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdwmhManualCaptchaPolicyTest {
    @Test
    fun `protocol authentication failure requests manual captcha`() {
        assertTrue(shouldRequestManualAdwmhCaptcha(AdwmhAuthException("验证码错误")))
    }

    @Test
    fun `network and HTTP failures do not open captcha flow`() {
        assertFalse(shouldRequestManualAdwmhCaptcha(SocketTimeoutException("timeout")))
        assertFalse(shouldRequestManualAdwmhCaptcha(AdwmhAuthException("登录失败(503)")))
    }
}
