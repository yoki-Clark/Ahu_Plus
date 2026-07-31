package com.ahu_plus.data.model.mail

/**
 * 邮件列表中的单条摘要。
 *
 * 来源:`/js6/s?func=mbox:listMessages` 响应的数组元素。
 */
data class MailMessageSummary(
    /** 邮件 ID(Sirius 内部格式,如 "AAsAZABbKp06pUdXI5mjbKpz")。 */
    val id: String,
    /** 邮件主题。 */
    val subject: String,
    /** 发件人。 */
    val from: MailAddress,
    /** 收件人列表。 */
    val to: List<MailAddress>,
    /** 抄送列表(可空)。 */
    val cc: List<MailAddress>?,
    /** 时间戳(毫秒)。 */
    val date: Long,
    /** 邮件大小(字节)。 */
    val size: Long,
    /** 是否有附件。 */
    val hasAttachment: Boolean,
    /** 是否已读。 */
    val isRead: Boolean,
    /** 是否星标。 */
    val isStarred: Boolean,
    /** 是否已回复。 */
    val isReplied: Boolean,
    /** 是否已转发。 */
    val isForwarded: Boolean,
    /** Sirius 内部 tid(用于线程聚合)。 */
    val tid: String?,
    /** 文件夹 ID。 */
    val fid: Int,
)

/**
 * 邮件完整详情。
 *
 * 来源:`/js6/s?func=mbox:getMessageInfos` 或 `/js6/s?func=mbox:readMessage`。
 */
data class MailMessageDetail(
    val id: String,
    val subject: String,
    val from: MailAddress,
    val to: List<MailAddress>,
    val cc: List<MailAddress>?,
    val bcc: List<MailAddress>?,
    val date: Long,
    /** HTML 邮件体(可能含 Sirius 自定义 CSS)。 */
    val htmlBody: String,
    /** 纯文本邮件体(降级用)。 */
    val textBody: String?,
    /** 附件列表。 */
    val attachments: List<MailAttachment>?,
    /** 邮件头(部分场景需要,如 Resent-From/Sender)。 */
    val headers: Map<String, String>,
    val isRead: Boolean,
    val isStarred: Boolean,
    val size: Long,
)

/** 邮件地址(发件人/收件人)。 */
data class MailAddress(
    /** 邮箱地址,如 "noreply@github.com"。 */
    val address: String,
    /** 显示名,可空。 */
    val name: String?,
)

/** 邮件附件。 */
data class MailAttachment(
    val id: String,
    val name: String,
    val size: Long,
    val contentType: String,
    /** 是否需要二次签名下载(部分附件需要)。 */
    val needAuth: Boolean,
)
