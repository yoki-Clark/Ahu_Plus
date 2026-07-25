package com.ahu_plus.data.repository

import com.ahu_plus.data.network.ChaoxingAuthExpiredException
import com.ahu_plus.data.network.ChaoxingForbiddenException
import com.ahu_plus.data.network.ChaoxingRateLimitedException
import com.ahu_plus.data.network.ChaoxingRiskChallengeException
import com.ahu_plus.data.network.ChaoxingTrafficBusyException
import com.ahu_plus.data.network.ChaoxingTrafficCooldownException
import com.ahu_plus.data.network.ChaoxingTrafficException
import com.ahu_plus.data.network.ChaoxingTrafficKey
import com.ahu_plus.data.network.SessionExpiredException as NetworkSessionExpiredException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class ErrorClassifierTest {

    // ── classify(Throwable) ──

    @Test
    fun `repository exception uses its kind directly`() {
        assertEquals(
            ErrorKind.PERMISSION_DENIED,
            ErrorClassifier.classify(RepositoryException(ErrorKind.PERMISSION_DENIED)),
        )
    }

    @Test
    fun `null throwable is unknown`() {
        assertEquals(ErrorKind.UNKNOWN, ErrorClassifier.classify(null))
    }

    @Test
    fun `cancellation exception is cancelled`() {
        assertEquals(ErrorKind.CANCELLED, ErrorClassifier.classify(CancellationException()))
    }

    @Test
    fun `domain auth exceptions map to auth expired`() {
        val key = ChaoxingTrafficKey.of("test", "chaoxing.com")
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classify(YcardAuthExpiredException("401")))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classify(ChaoxingAuthExpiredException(key, 0L, null)))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classify(NetworkSessionExpiredException("expired")))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classify(SessionExpiredException()))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classify(EvaluationAuthException("auth")))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classify(JwAppAuthRequiredException()))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classify(PortalHtmlResponseException()))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classify(CasAuthException("CAS auth failed")))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classify(JwAuthException("JW auth failed")))
    }

    @Test
    fun `chaoxing traffic cooldown and base traffic exception map to rate limited`() {
        val key = ChaoxingTrafficKey.of("test", "chaoxing.com")
        // 裸 Cooldown(非 RateLimited/RiskChallenge/AuthExpired/Forbidden 子类)
        assertEquals(
            ErrorKind.RATE_LIMITED,
            ErrorClassifier.classify(ChaoxingTrafficCooldownException(key, 30000L, null)),
        )
        // 裸 Traffic(非 Busy 子类)
        assertEquals(
            ErrorKind.RATE_LIMITED,
            ErrorClassifier.classify(ChaoxingTrafficException("traffic issue", key)),
        )
    }

    @Test
    fun `chaoxing study restriction maps to rate limited`() {
        assertEquals(
            ErrorKind.RATE_LIMITED,
            ErrorClassifier.classify(ChaoxingStudyRestrictionException(429, null)),
        )
    }

    @Test
    fun `cprog session expired io exception maps to auth expired`() {
        // CProg 用 IOException("CPROG_SESSION_EXPIRED") 标记会话过期
        assertEquals(
            ErrorKind.AUTH_EXPIRED,
            ErrorClassifier.classify(IOException("CPROG_SESSION_EXPIRED")),
        )
    }

    @Test
    fun `generic io exception with no semantic message maps to network unreachable`() {
        assertEquals(
            ErrorKind.NETWORK_UNREACHABLE,
            ErrorClassifier.classify(IOException("connection reset")),
        )
    }

    @Test
    fun `repository exception with unknown kind inspects cause for classification`() {
        // UNKNOWN kind 但 cause 携带领域异常 → 从 cause 推断
        val wrapped = RepositoryException(
            ErrorKind.UNKNOWN,
            cause = YcardAuthExpiredException("401"),
        )
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classify(wrapped))
    }

    @Test
    fun `generic exception wrapping domain exception classifies from cause`() {
        val wrapper = RuntimeException("wrapper", YcardAuthExpiredException("401"))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classify(wrapper))
    }

    @Test
    fun `exception with self referential cause does not loop infinitely`() {
        val selfRef = RuntimeException("self")
        // 构造自引用 cause(虽然不常见,但分类器不应崩溃)
        assertEquals(ErrorKind.UNKNOWN, ErrorClassifier.classify(selfRef))
    }

    @Test
    fun `adwmh captcha required maps to invalid input`() {
        assertEquals(
            ErrorKind.INVALID_INPUT,
            ErrorClassifier.classify(AdwmhCaptchaRequiredException("captcha")),
        )
    }

    @Test
    fun `adwmh auth exception with session message maps to auth expired`() {
        assertEquals(
            ErrorKind.AUTH_EXPIRED,
            ErrorClassifier.classify(AdwmhAuthException("智慧安大会话已过期，请重新登录")),
        )
    }

    @Test
    fun `rate limit exceptions map to rate limited`() {
        val key = ChaoxingTrafficKey.of("test", "chaoxing.com")
        assertEquals(
            ErrorKind.RATE_LIMITED,
            ErrorClassifier.classify(ChaoxingRateLimitedException(key, 60000L, null)),
        )
        assertEquals(
            ErrorKind.RATE_LIMITED,
            ErrorClassifier.classify(ChaoxingTrafficBusyException(key)),
        )
    }

    @Test
    fun `forbidden exception maps to permission denied`() {
        val key = ChaoxingTrafficKey.of("test", "chaoxing.com")
        assertEquals(
            ErrorKind.PERMISSION_DENIED,
            ErrorClassifier.classify(ChaoxingForbiddenException(key, 0L, null)),
        )
    }

    @Test
    fun `waf and risk challenge exceptions map to protocol changed`() {
        val key = ChaoxingTrafficKey.of("test", "chaoxing.com")
        assertEquals(
            ErrorKind.PROTOCOL_CHANGED,
            ErrorClassifier.classify(XzxxWafChallengeRequiredException()),
        )
        assertEquals(
            ErrorKind.PROTOCOL_CHANGED,
            ErrorClassifier.classify(JwcWafChallengeRequiredException()),
        )
        assertEquals(
            ErrorKind.PROTOCOL_CHANGED,
            ErrorClassifier.classify(ChaoxingRiskChallengeException(key, 0L, null)),
        )
    }

    @Test
    fun `network io exceptions map to network unreachable`() {
        assertEquals(ErrorKind.NETWORK_UNREACHABLE, ErrorClassifier.classify(UnknownHostException()))
        assertEquals(ErrorKind.NETWORK_UNREACHABLE, ErrorClassifier.classify(SocketTimeoutException()))
        assertEquals(ErrorKind.NETWORK_UNREACHABLE, ErrorClassifier.classify(SSLException("handshake failed")))
        assertEquals(ErrorKind.NETWORK_UNREACHABLE, ErrorClassifier.classify(IOException("broken pipe")))
    }

    @Test
    fun `unknown exception falls back to message classification`() {
        assertEquals(
            ErrorKind.AUTH_EXPIRED,
            ErrorClassifier.classify(Exception("会话已过期")),
        )
        assertEquals(
            ErrorKind.UNKNOWN,
            ErrorClassifier.classify(Exception("some random error")),
        )
    }

    // ── classifyMessage(String) ──

    @Test
    fun `classifyMessage handles auth patterns`() {
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classifyMessage("会话已过期"))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classifyMessage("请重新登录"))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classifyMessage("请先登录智慧安大"))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classifyMessage("返回 HTML"))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classifyMessage("Session expired"))
        assertEquals(ErrorKind.AUTH_EXPIRED, ErrorClassifier.classifyMessage("Unauthorized access"))
    }

    @Test
    fun `classifyMessage handles network patterns`() {
        assertEquals(ErrorKind.NETWORK_UNREACHABLE, ErrorClassifier.classifyMessage("连接超时"))
        assertEquals(ErrorKind.NETWORK_UNREACHABLE, ErrorClassifier.classifyMessage("Connection timed out"))
        assertEquals(ErrorKind.NETWORK_UNREACHABLE, ErrorClassifier.classifyMessage("unable to resolve host"))
    }

    @Test
    fun `classifyMessage handles rate limit patterns`() {
        assertEquals(ErrorKind.RATE_LIMITED, ErrorClassifier.classifyMessage("请求被限流"))
        assertEquals(ErrorKind.RATE_LIMITED, ErrorClassifier.classifyMessage("Too Many Requests"))
        assertEquals(ErrorKind.RATE_LIMITED, ErrorClassifier.classifyMessage("HTTP 429"))
    }

    @Test
    fun `classifyMessage handles permission patterns`() {
        assertEquals(ErrorKind.PERMISSION_DENIED, ErrorClassifier.classifyMessage("没有权限"))
        assertEquals(ErrorKind.PERMISSION_DENIED, ErrorClassifier.classifyMessage("Forbidden"))
        assertEquals(ErrorKind.PERMISSION_DENIED, ErrorClassifier.classifyMessage("HTTP 403"))
    }

    @Test
    fun `classifyMessage handles platform failure patterns`() {
        assertEquals(ErrorKind.PLATFORM_FAILURE, ErrorClassifier.classifyMessage("502 Bad Gateway"))
        assertEquals(ErrorKind.PLATFORM_FAILURE, ErrorClassifier.classifyMessage("503 Service Unavailable"))
    }

    @Test
    fun `classifyMessage returns unknown for null and blank`() {
        assertEquals(ErrorKind.UNKNOWN, ErrorClassifier.classifyMessage(null))
        assertEquals(ErrorKind.UNKNOWN, ErrorClassifier.classifyMessage(""))
        assertEquals(ErrorKind.UNKNOWN, ErrorClassifier.classifyMessage("   "))
    }

    // ── userMessage ──

    @Test
    fun `userMessage returns non-blank for all kinds except cancelled`() {
        for (kind in ErrorKind.entries) {
            val msg = ErrorClassifier.userMessage(kind, "original")
            if (kind == ErrorKind.CANCELLED) {
                assertEquals("", msg)
            } else {
                assertTrue("userMessage for $kind should not be blank", msg.isNotBlank())
            }
        }
    }

    @Test
    fun `userMessage preserves original for unknown and invalid input`() {
        assertEquals("custom error", ErrorClassifier.userMessage(ErrorKind.UNKNOWN, "custom error"))
        assertEquals("密码不能为空", ErrorClassifier.userMessage(ErrorKind.INVALID_INPUT, "密码不能为空"))
    }

    @Test
    fun `userMessage uses default for unknown when original is null`() {
        assertEquals("操作失败,请重试", ErrorClassifier.userMessage(ErrorKind.UNKNOWN, null))
    }

    // ── shouldReauth / shouldRetry ──

    @Test
    fun `shouldReauth is true only for auth expired`() {
        assertTrue(ErrorClassifier.shouldReauth(ErrorKind.AUTH_EXPIRED))
        for (kind in ErrorKind.entries) {
            if (kind != ErrorKind.AUTH_EXPIRED) {
                assertFalse("shouldReauth should be false for $kind", ErrorClassifier.shouldReauth(kind))
            }
        }
    }

    @Test
    fun `shouldRetry is true for transient failures`() {
        assertTrue(ErrorClassifier.shouldRetry(ErrorKind.NETWORK_UNREACHABLE))
        assertTrue(ErrorClassifier.shouldRetry(ErrorKind.RATE_LIMITED))
        assertTrue(ErrorClassifier.shouldRetry(ErrorKind.PLATFORM_FAILURE))
    }

    @Test
    fun `shouldRetry is false for auth permission and cancel`() {
        assertFalse(ErrorClassifier.shouldRetry(ErrorKind.AUTH_EXPIRED))
        assertFalse(ErrorClassifier.shouldRetry(ErrorKind.PERMISSION_DENIED))
        assertFalse(ErrorClassifier.shouldRetry(ErrorKind.CANCELLED))
        assertFalse(ErrorClassifier.shouldRetry(ErrorKind.INVALID_INPUT))
    }
}
