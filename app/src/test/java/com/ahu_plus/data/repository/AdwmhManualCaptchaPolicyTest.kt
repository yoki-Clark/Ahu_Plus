package com.ahu_plus.data.repository

import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdwmhManualCaptchaPolicyTest {
    @Test
    fun `protocol authentication failure requests manual captcha`() {
        assertTrue(shouldRequestManualAdwmhCaptcha(AdwmhAuthException("验证码错误")))
        assertTrue(shouldRequestManualAdwmhCaptcha(AdwmhAuthException("验证码已过期")))
        assertTrue(
            shouldRequestManualAdwmhCaptcha(
                AdwmhLoginHttpException(statusCode = 401, captchaProvided = false),
            ),
        )
    }

    @Test
    fun `non captcha failures do not open captcha flow`() {
        assertFalse(shouldRequestManualAdwmhCaptcha(SocketTimeoutException("timeout")))
        assertFalse(shouldRequestManualAdwmhCaptcha(AdwmhAuthException("登录失败(503)")))
        assertFalse(shouldRequestManualAdwmhCaptcha(AdwmhAuthException("用户名或密码错误")))
        assertFalse(shouldRequestManualAdwmhCaptcha(AdwmhAuthException("系统繁忙，请稍后重试")))
        assertFalse(
            shouldRequestManualAdwmhCaptcha(
                AdwmhLoginHttpException(statusCode = 503, captchaProvided = false),
            ),
        )
        assertFalse(
            shouldRequestManualAdwmhCaptcha(
                AdwmhLoginHttpException(statusCode = 401, captchaProvided = true),
            ),
        )
    }

    @Test
    fun `login failures retain their specific category`() {
        assertEquals(
            AdwmhLoginFailureKind.INVALID_CREDENTIALS,
            classifyAdwmhLoginFailure(AdwmhAuthException("用户名或密码错误")),
        )
        assertEquals(
            AdwmhLoginFailureKind.RATE_LIMITED,
            classifyAdwmhLoginFailure(AdwmhAuthException("操作频繁，请稍后重试")),
        )
        assertEquals(
            AdwmhLoginFailureKind.OTHER,
            classifyAdwmhLoginFailure(AdwmhAuthException("系统繁忙，请稍后重试")),
        )
    }
}
