package com.ahu_plus.data.repository

/**
 * 统一错误分类,覆盖所有 Repository 可能报告的错误类型。
 *
 * ViewModel 和 UI 层应基于 [ErrorKind] 决定提示文案、是否重试、是否重新登录,
 * 不再通过异常消息文本包含关系判断错误类型。
 *
 * 迁移路径:Repository 在协议知识最集中的位置抛出 [RepositoryException] 携带 [kind];
 * 尚未迁移的 Repository 由 [ErrorClassifier] 从异常类型或消息兜底分类。
 */
enum class ErrorKind {
    /** 认证或会话过期,需要重新登录。 */
    AUTH_EXPIRED,

    /** 权限不足,当前账号无权执行此操作。 */
    PERMISSION_DENIED,

    /** 网络不可达、DNS 失败或连接超时。 */
    NETWORK_UNREACHABLE,

    /** 被远端限流,需要退避后重试。 */
    RATE_LIMITED,

    /** 协议结构变化(响应格式与预期不符),可能需要 App 更新。 */
    PROTOCOL_CHANGED,

    /** 远端平台故障(5xx、维护页等),通常可稍后重试。 */
    PLATFORM_FAILURE,

    /** 用户输入不合法。 */
    INVALID_INPUT,

    /** 用户主动取消,不应显示为错误。 */
    CANCELLED,

    /** 未分类异常,保留原始消息供诊断。 */
    UNKNOWN,
}
