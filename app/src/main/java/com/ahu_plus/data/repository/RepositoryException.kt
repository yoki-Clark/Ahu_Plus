package com.ahu_plus.data.repository

/**
 * 携带 [ErrorKind] 的结构化 Repository 异常。
 *
 * Repository 在识别到具体错误时应抛出此异常,使 ViewModel 无需通过消息文本判断错误类型。
 * 保留了 [cause] 链以便诊断,同时不泄露技术细节给 UI 层。
 *
 * 与现有领域异常(如 [YcardAuthExpiredException])共存:
 * 迁移期间 [ErrorClassifier] 会将领域异常映射到对应 [ErrorKind]。
 */
class RepositoryException(
    val kind: ErrorKind,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: kind.name, cause)
