package com.ahu_plus.data.model.mail

/**
 * 教育邮箱账户完整信息。
 *
 * 来源:`/cowork/api/biz/enter/accountInfo` 响应。
 * 抓包样本(student_id@stu.ahu.edu.cn,昵称,安徽大学)。
 */
data class MailAccountInfo(
    /** 网易企业账号 ID,如 "123456789"。 */
    val qiyeAccountId: String,
    /** 学号(不带域),如 "student_id"。 */
    val accountName: String,
    /** 完整邮箱地址,如 "student_id@stu.ahu.edu.cn"。 */
    val email: String,
    /** 昵称,如 "昵称"。 */
    val nickName: String,
    /** 发件人显示名,通常与 [nickName] 一致。 */
    val senderName: String,
    /** 网易云信 IM 账号 ID(可对接实时推送,首版不用)。 */
    val yunxinAccountId: String?,
    /** 网易云信 IM token(首版不用)。 */
    val yunxinToken: String?,
    /** 网易云信 token 过期时间戳(毫秒,首版不用)。 */
    val yunxinTokenExpire: Long?,
    /** 绑定手机号,如 "18155375903"(已脱敏处理展示)。 */
    val authMobile: String?,
    /** 机构名,如 "安徽大学"。 */
    val orgName: String,
    /** 展示用邮箱(可能含 alias)。 */
    val displayEmail: String,
    /** 默认发件人。 */
    val defaultSender: MailSender?,
    /** 机构 logo URL。 */
    val domainLogo: String?,
)

/** 邮箱账户基础信息(轻量版,来自 `/commonweb/account/getAccountBaseInfo`)。 */
data class MailAccountBaseInfo(
    val email: String,
    val nickName: String,
    val orgName: String,
)

/** 发件人/收件人显示信息。 */
data class MailSender(
    val email: String,
    val nickName: String,
    val senderName: String,
)

/** 账户关系(来自 `/commonweb/account/getAccountRelation`)。 */
data class MailAccountRelation(
    val email: String,
    val nickName: String,
    val isPublic: Boolean,
    val alias: List<String>,
    val sharedAccounts: List<String>,
)
