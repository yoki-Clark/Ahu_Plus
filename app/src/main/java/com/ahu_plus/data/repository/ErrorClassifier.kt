package com.ahu_plus.data.repository

import com.ahu_plus.data.network.ChaoxingAuthExpiredException
import com.ahu_plus.data.network.ChaoxingForbiddenException
import com.ahu_plus.data.network.ChaoxingRateLimitedException
import com.ahu_plus.data.network.ChaoxingRiskChallengeException
import com.ahu_plus.data.network.ChaoxingTrafficBusyException
import com.ahu_plus.data.network.ChaoxingTrafficCooldownException
import com.ahu_plus.data.network.ChaoxingTrafficException
import com.ahu_plus.data.network.SessionExpiredException as NetworkSessionExpiredException
import com.ahu_plus.data.repository.mail.MailApiException
import com.ahu_plus.data.repository.mail.MailAuthException
import com.ahu_plus.data.repository.mail.MailCaptchaRequiredException
import com.ahu_plus.data.repository.mail.MailHandshakeFailedException
import com.ahu_plus.data.repository.mail.MailRateLimitedException
import com.ahu_plus.data.repository.mail.MailSessionExpiredException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 将 [Throwable] 或错误消息字符串映射为 [ErrorKind],并提供用户可见文案和行为决策。
 *
 * 分类优先级:
 * 1. 已结构化的 [RepositoryException] → 直接使用其 [RepositoryException.kind]
 *    (kind 为 UNKNOWN 且有 cause 时,尝试从 cause 推断)
 * 2. 已知领域异常类型 → 映射到对应 [ErrorKind]
 * 3. 通用网络异常(IOException 子类)→ [ErrorKind.NETWORK_UNREACHABLE]
 * 4. 异常消息文本 → [classifyMessage] 兜底(迁移期兼容未抛出结构化异常的 Repository)
 * 5. 嵌套 cause → 递归 [classify] 兜底(仅一层,避免循环)
 */
object ErrorClassifier {

    fun classify(throwable: Throwable?): ErrorKind {
        if (throwable == null) return ErrorKind.UNKNOWN

        val direct = classifyDirect(throwable)
        if (direct != ErrorKind.UNKNOWN) return direct

        // 外层无法分类时,尝试从 cause 推断(仅一层,避免递归风险)
        val cause = throwable.cause
        if (cause != null && cause !== throwable) {
            val causeKind = classifyDirect(cause)
            if (causeKind != ErrorKind.UNKNOWN) return causeKind
        }

        return ErrorKind.UNKNOWN
    }

    /**
     * 直接分类逻辑,不递归 cause。供 [classify] 调用。
     */
    private fun classifyDirect(throwable: Throwable): ErrorKind {
        // 已结构化
        if (throwable is RepositoryException) return throwable.kind

        // 协程取消
        if (throwable is CancellationException) return ErrorKind.CANCELLED

        // 领域异常:认证过期
        when (throwable) {
            is YcardAuthExpiredException,
            is ChaoxingAuthExpiredException,
            is NetworkSessionExpiredException,
            is SessionExpiredException,
            is EvaluationAuthException,
            is JwAppAuthRequiredException,
            is PortalHtmlResponseException,
            is CasAuthException,
            is JwAuthException -> return ErrorKind.AUTH_EXPIRED
        }

        // Adwmh: 验证码需要用户输入,其余 auth 错误按消息分类
        if (throwable is AdwmhCaptchaRequiredException) return ErrorKind.INVALID_INPUT
        if (throwable is AdwmhAuthException) return classifyMessage(throwable.message)

        // Mail(教育邮箱):具体子类必须在基类 MailAuthException 之前
        // 模式参考 ChaoxingTrafficCooldownException(具体子类优先于基类)
        when (throwable) {
            // 验证码 → 需要用户输入
            is MailCaptchaRequiredException -> return ErrorKind.INVALID_INPUT
            // 限流
            is MailRateLimitedException -> return ErrorKind.RATE_LIMITED
            // 会话过期 → 触发后台静默重新登录
            is MailSessionExpiredException -> return ErrorKind.AUTH_EXPIRED
        }
        // 握手失败:按消息细分(验证码/限流/网络)
        if (throwable is MailHandshakeFailedException) {
            val msg = throwable.message.orEmpty().lowercase()
            return when {
                throwable.message?.contains("验证码") == true -> ErrorKind.INVALID_INPUT
                msg.contains("限流") || msg.contains("rate limit") || throwable.httpStatus == 429 ->
                    ErrorKind.RATE_LIMITED
                throwable.httpStatus in setOf(401, 403) -> ErrorKind.AUTH_EXPIRED
                throwable.httpStatus in setOf(500, 502, 503, 504) -> ErrorKind.PLATFORM_FAILURE
                else -> ErrorKind.NETWORK_UNREACHABLE
            }
        }
        // Sirius 业务 API 错误:按 code 分流
        if (throwable is MailApiException) return when (throwable.code) {
            401, 403, 440 -> ErrorKind.AUTH_EXPIRED
            429 -> ErrorKind.RATE_LIMITED
            500, 502, 503, 504 -> ErrorKind.PLATFORM_FAILURE
            else -> ErrorKind.UNKNOWN
        }
        // Mail 基类:按消息兜底
        if (throwable is MailAuthException) return classifyMessage(throwable.message)

        // 领域异常:限流(具体子类优先于基类,因 Forbidden/RiskChallenge/AuthExpired 继承 Cooldown)
        when (throwable) {
            is ChaoxingRateLimitedException,
            is ChaoxingTrafficBusyException -> return ErrorKind.RATE_LIMITED
        }

        // 领域异常:权限拒绝(须在 Cooldown 基类之前,因 Forbidden 继承 Cooldown)
        if (throwable is ChaoxingForbiddenException) return ErrorKind.PERMISSION_DENIED

        // 领域异常:WAF/风控挑战 = 协议变化(须在 Cooldown 基类之前,因 RiskChallenge 继承 Cooldown)
        when (throwable) {
            is XzxxWafChallengeRequiredException,
            is JwcWafChallengeRequiredException,
            is ChaoxingRiskChallengeException -> return ErrorKind.PROTOCOL_CHANGED
        }

        // Chaoxing 流量基类:具体子类已被上方分支处理,此处仅捕获裸 Cooldown/Traffic
        when (throwable) {
            is ChaoxingTrafficCooldownException,
            is ChaoxingTrafficException -> return ErrorKind.RATE_LIMITED
        }

        // 领域异常:学习通学习被限流阻止
        if (throwable is ChaoxingStudyRestrictionException) return ErrorKind.RATE_LIMITED

        // 通用网络异常
        when (throwable) {
            is UnknownHostException,
            is SocketTimeoutException,
            is SSLException -> return ErrorKind.NETWORK_UNREACHABLE
            is IOException -> {
                // IOException 可能携带语义消息(如 CProg 会话过期),先尝试消息分类
                val messageKind = classifyMessage(throwable.message)
                return if (messageKind != ErrorKind.UNKNOWN) messageKind else ErrorKind.NETWORK_UNREACHABLE
            }
        }

        // 兜底:消息文本分类
        return classifyMessage(throwable.message)
    }

    /**
     * 从错误消息文本推断 [ErrorKind],用于尚未抛出 [RepositoryException] 的 Repository 兜底。
     *
     * 保留与现有字符串匹配逻辑等价的行为,后续 Repository 迁移后可逐步移除。
     */
    fun classifyMessage(message: String?): ErrorKind {
        if (message.isNullOrBlank()) return ErrorKind.UNKNOWN
        val lower = message.lowercase()
        return when {
            // 认证/会话过期
            message.contains("会话已过期") ||
            message.contains("重新登录") ||
            message.contains("请先登录") ||
            message.contains("返回 HTML") ||
            message.contains("返回html", ignoreCase = true) ||
            lower.contains("session expired") ||
            lower.contains("unauthorized") ||
            message.contains("请登录") ||
            lower.contains("log in") ||
            lower.contains("login required") ||
            // CProg 会话过期用 IOException 携带此标记抛出(过渡期,后续应改为类型化异常)
            message.contains("CPROG_SESSION_EXPIRED") -> ErrorKind.AUTH_EXPIRED

            // 网络/超时
            message.contains("超时") ||
            lower.contains("timeout") ||
            lower.contains("timed out") ||
            lower.contains("unreachable") ||
            lower.contains("unable to resolve host") -> ErrorKind.NETWORK_UNREACHABLE

            // 限流
            message.contains("限流") ||
            lower.contains("rate limit") ||
            lower.contains("too many requests") ||
            lower.contains("429") -> ErrorKind.RATE_LIMITED

            // 权限
            message.contains("权限") ||
            lower.contains("forbidden") ||
            lower.contains("permission denied") ||
            lower.contains("403") -> ErrorKind.PERMISSION_DENIED

            // 协议变化
            lower.contains("protocol") ||
            message.contains("格式错误") ||
            message.contains("解析失败") -> ErrorKind.PROTOCOL_CHANGED

            // 平台故障
            lower.contains("server error") ||
            lower.contains("internal error") ||
            lower.contains("502") ||
            lower.contains("503") ||
            lower.contains("504") ||
            lower.contains("service unavailable") -> ErrorKind.PLATFORM_FAILURE

            else -> ErrorKind.UNKNOWN
        }
    }

    /** 用户可见的错误描述,不暴露技术细节。[original] 用于 [ErrorKind.UNKNOWN] 和 [ErrorKind.INVALID_INPUT]。 */
    fun userMessage(kind: ErrorKind, original: String? = null): String = when (kind) {
        ErrorKind.AUTH_EXPIRED -> "登录已过期,请重新登录"
        ErrorKind.PERMISSION_DENIED -> "没有权限执行此操作"
        ErrorKind.NETWORK_UNREACHABLE -> "网络不可用,请检查连接"
        ErrorKind.RATE_LIMITED -> "请求过于频繁,请稍后再试"
        ErrorKind.PROTOCOL_CHANGED -> "服务格式可能已更新,请反馈开发者"
        ErrorKind.PLATFORM_FAILURE -> "服务暂时不可用,请稍后再试"
        ErrorKind.INVALID_INPUT -> original ?: "输入有误"
        ErrorKind.CANCELLED -> ""
        ErrorKind.UNKNOWN -> original ?: "操作失败,请重试"
    }

    /** 是否应触发后台静默重新登录。 */
    fun shouldReauth(kind: ErrorKind): Boolean = kind == ErrorKind.AUTH_EXPIRED

    /** 是否适合自动重试(非认证、非权限、非取消类错误)。 */
    fun shouldRetry(kind: ErrorKind): Boolean = when (kind) {
        ErrorKind.NETWORK_UNREACHABLE,
        ErrorKind.RATE_LIMITED,
        ErrorKind.PLATFORM_FAILURE -> true
        else -> false
    }
}
