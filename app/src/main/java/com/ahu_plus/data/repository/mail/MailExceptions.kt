package com.ahu_plus.data.repository.mail

/**
 * 教育邮箱相关异常层级,参考 [com.ahu_plus.data.repository.AdwmhAuthException] 模式。
 *
 * 在 [com.ahu_plus.data.repository.ErrorClassifier.classifyDirect] 中按"具体子类在基类之前"
 * 的顺序注册(与 ChaoxingTrafficCooldownException 模式一致)。
 */
open class MailAuthException(message: String) : Exception(message)

/** 邮箱会话已过期,需要重新握手。 */
class MailSessionExpiredException(message: String = "教育邮箱会话已过期") :
    MailAuthException(message)

/**
 * 邮箱握手链失败(7 步跳转中某一步)。
 *
 * @param step 失败的步骤编号(1-7)
 * @param httpStatus HTTP 状态码(0 表示未拿到响应)
 * @param rawMessage 原始错误消息
 */
class MailHandshakeFailedException(
    val step: Int,
    val httpStatus: Int,
    message: String,
) : MailAuthException("教育邮箱握手第 $step 步失败(HTTP $httpStatus): $message")

/** WebVPN 引入验证码(预留,目前抓包未见但 Sirius 升级可能引入)。 */
class MailCaptchaRequiredException(message: String) :
    MailAuthException(message)

/** 邮箱访问被限流。 */
class MailRateLimitedException(message: String) :
    MailAuthException(message)

/**
 * Sirius 业务 API 返回非 0 code(格式 A)或非 200 code(格式 B)。
 *
 * @param code Sirius 业务 code
 * @param apiMessage Sirius 返回的 message/desc
 */
class MailApiException(
    val code: Int,
    val apiMessage: String,
) : MailAuthException("教育邮箱 API 错误($code): $apiMessage")

/** 邮箱列表/详情本地缓存相关的 IO 错误,不视为认证错误。 */
class MailCacheException(message: String) : Exception(message)
