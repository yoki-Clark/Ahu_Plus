package com.ahu_plus.data.repository

import com.ahu_plus.data.repository.mail.MailApiException
import com.ahu_plus.data.repository.mail.MailAuthException
import com.ahu_plus.data.repository.mail.MailCaptchaRequiredException
import com.ahu_plus.data.repository.mail.MailHandshakeFailedException
import com.ahu_plus.data.repository.mail.MailRateLimitedException
import com.ahu_plus.data.repository.mail.MailSessionExpiredException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ErrorClassifier] 对教育邮箱异常分类的单元测试。
 *
 * AGENTS.md 要求:新增 ErrorKind 分支时必须同步补对应 classify 测试。
 * 验证"具体子类在基类之前"的顺序正确(参考 ChaoxingTrafficCooldownException 模式)。
 */
class ErrorClassifierMailTest {

    @Test
    fun `MailSessionExpiredException maps to AUTH_EXPIRED`() {
        val kind = ErrorClassifier.classify(MailSessionExpiredException())
        assertEquals(ErrorKind.AUTH_EXPIRED, kind)
        // 应触发后台静默重新登录
        assert(ErrorClassifier.shouldReauth(kind))
    }

    @Test
    fun `MailCaptchaRequiredException maps to INVALID_INPUT`() {
        val kind = ErrorClassifier.classify(MailCaptchaRequiredException("需要验证码"))
        assertEquals(ErrorKind.INVALID_INPUT, kind)
    }

    @Test
    fun `MailRateLimitedException maps to RATE_LIMITED`() {
        val kind = ErrorClassifier.classify(MailRateLimitedException("被限流"))
        assertEquals(ErrorKind.RATE_LIMITED, kind)
        assert(ErrorClassifier.shouldRetry(kind))
    }

    @Test
    fun `MailHandshakeFailedException with 429 maps to RATE_LIMITED`() {
        val ex = MailHandshakeFailedException(step = 4, httpStatus = 429, message = "被限流")
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.RATE_LIMITED, kind)
    }

    @Test
    fun `MailHandshakeFailedException with 401 maps to AUTH_EXPIRED`() {
        val ex = MailHandshakeFailedException(step = 3, httpStatus = 401, message = "认证失败")
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.AUTH_EXPIRED, kind)
    }

    @Test
    fun `MailHandshakeFailedException with 503 maps to PLATFORM_FAILURE`() {
        val ex = MailHandshakeFailedException(step = 5, httpStatus = 503, message = "服务不可用")
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.PLATFORM_FAILURE, kind)
    }

    @Test
    fun `MailHandshakeFailedException with captcha message maps to INVALID_INPUT`() {
        val ex = MailHandshakeFailedException(step = 1, httpStatus = 200, message = "需要输入验证码")
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.INVALID_INPUT, kind)
    }

    @Test
    fun `MailHandshakeFailedException with 500 maps to PLATFORM_FAILURE`() {
        val ex = MailHandshakeFailedException(step = 2, httpStatus = 500, message = "服务器错误")
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.PLATFORM_FAILURE, kind)
    }

    @Test
    fun `MailApiException with 401 maps to AUTH_EXPIRED`() {
        val ex = MailApiException(code = 401, apiMessage = "session expired")
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.AUTH_EXPIRED, kind)
    }

    @Test
    fun `MailApiException with 429 maps to RATE_LIMITED`() {
        val ex = MailApiException(code = 429, apiMessage = "too many requests")
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.RATE_LIMITED, kind)
    }

    @Test
    fun `MailApiException with 502 maps to PLATFORM_FAILURE`() {
        val ex = MailApiException(code = 502, apiMessage = "bad gateway")
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.PLATFORM_FAILURE, kind)
    }

    @Test
    fun `MailApiException with unknown code maps to UNKNOWN`() {
        val ex = MailApiException(code = 999, apiMessage = "unknown")
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.UNKNOWN, kind)
    }

    @Test
    fun `MailAuthException base class falls back to classifyMessage`() {
        // 基类消息含"会话已过期"应映射到 AUTH_EXPIRED
        val ex = MailAuthException("邮箱会话已过期")
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.AUTH_EXPIRED, kind)
    }

    @Test
    fun `MailAuthException with limit message maps to RATE_LIMITED`() {
        val ex = MailAuthException("邮箱限流")
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.RATE_LIMITED, kind)
    }

    @Test
    fun `具体子类优先于基类 MailAuthException`() {
        // MailSessionExpiredException 继承 MailAuthException
        // 应映射到 AUTH_EXPIRED,而非走基类的 classifyMessage 兜底
        val ex: MailAuthException = MailSessionExpiredException()
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.AUTH_EXPIRED, kind)
    }

    @Test
    fun `MailHandshakeFailedException 优先于 MailAuthException 基类`() {
        // MailHandshakeFailedException 继承 MailAuthException
        // 应映射到具体分类,而非走基类兜底
        val ex: MailAuthException = MailHandshakeFailedException(1, 403, "认证失败")
        val kind = ErrorClassifier.classify(ex)
        assertEquals(ErrorKind.AUTH_EXPIRED, kind)
    }

    @Test
    fun `userMessage for AUTH_EXPIRED is not empty`() {
        val msg = ErrorClassifier.userMessage(ErrorKind.AUTH_EXPIRED)
        assert(msg.isNotBlank())
    }

    @Test
    fun `shouldReauth returns true for AUTH_EXPIRED from mail exceptions`() {
        val ex = MailSessionExpiredException()
        val kind = ErrorClassifier.classify(ex)
        assert(ErrorClassifier.shouldReauth(kind))
    }
}
