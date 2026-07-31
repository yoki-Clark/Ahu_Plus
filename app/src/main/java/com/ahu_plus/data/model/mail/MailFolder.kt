package com.ahu_plus.data.model.mail

/**
 * 邮箱文件夹(收件箱/发件箱/草稿/已发送/垃圾邮件/自定义)。
 *
 * 来源:`/js6/s?func=mbox:getAllFolders` 响应。
 */
data class MailFolder(
    /** 文件夹 ID(如 1=收件箱,2=草稿,3=已发送,4=垃圾邮件)。 */
    val fid: Int,
    /** 文件夹显示名。 */
    val name: String,
    /** 未读邮件数。 */
    val unreadCount: Int,
    /** 邮件总数。 */
    val totalCount: Int,
    /** 子文件夹(自定义嵌套时使用)。 */
    val children: List<MailFolder>?,
    /** 是否为系统文件夹(收件箱等)。 */
    val isSystem: Boolean,
)

/** 邮件统计(来自 `/js6/s?func=mbox:statMessages`)。 */
data class MailFolderStat(
    val fid: Int,
    val name: String,
    val unreadCount: Int,
    val totalCount: Int,
)
