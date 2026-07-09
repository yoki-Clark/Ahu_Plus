package com.ahu_plus.data.model

import com.google.gson.annotations.SerializedName

/**
 * Gitee 远程 announcements.json 的顶层结构。
 *
 * 托管地址:
 *   https://gitee.com/yao-enqi/ahu-plus-update/raw/master/announcements/announcements.json
 *
 * 由维护者手写后 push 到 Gitee,客户端启动时零登录拉取。
 * 数据流与 [UpdateInfo] / 排考预测 完全同构。
 */
data class AnnouncementFeed(
    /** JSON schema 版本,供客户端校验;不匹配时仅 warning 仍尝试解析。 */
    @SerializedName("version")
    val version: Int = 1,

    /** 公告列表,可多条;客户端按 priority 排序逐条弹出。 */
    @SerializedName("announcements")
    val announcements: List<Announcement> = emptyList()
)

/**
 * 单条开发者公告。
 *
 * 时间字段为字符串(维护者好手写),格式 `yyyy-MM-dd` 或 `yyyy-MM-dd HH:mm`。
 * 留空语义:
 *   - `startAt` 空 = 立即生效
 *   - `expireAt` 空 = **默认有效期 1 天**(2026-06-27 改:防止旧公告反复弹)
 *     基准 = `startAt`(若非空)否则 `fetchedAt`(JSON 首次写入客户端的时刻);
 *     长公告请显式写 `expireAt`。
 *
 * 客户端用 [isActive] 做生效窗口 + 版本 + 已忽略判定。
 */
data class Announcement(
    /** 唯一 ID,用于"不再提示"去重。务必每条公告分配不同 id。 */
    @SerializedName("id")
    val id: String = "",

    /** 标题,显示在弹窗顶部。 */
    @SerializedName("title")
    val title: String = "",

    /** 正文,支持多行(\n)。 */
    @SerializedName("content")
    val content: String = "",

    /** 优先级:数值越大越先弹。多条公告按此降序排队。 */
    @SerializedName("priority")
    val priority: Int = 0,

    /** 生效时间(含),早于此时间不显示。空=立即生效。格式 yyyy-MM-dd[ HH:mm]。 */
    @SerializedName("startAt")
    val startAt: String = "",

    /** 过期时间(含),晚于此时间不再显示。空=默认有效期 1 天(2026-06-27 改)。格式 yyyy-MM-dd[ HH:mm]。 */
    @SerializedName("expireAt")
    val expireAt: String = "",

    /** 动作按钮文案,空=不显示按钮。 */
    @SerializedName("actionLabel")
    val actionLabel: String = "",

    /** 动作按钮点击打开的链接(外部浏览器),与 actionLabel 配套。 */
    @SerializedName("actionUrl")
    val actionUrl: String = "",

    /** 仅对 versionCode >= 此值的客户端显示;0=不限。 */
    @SerializedName("minVersionCode")
    val minVersionCode: Int = 0,

    /** 仅对 versionCode <= 此值的客户端显示;0=不限。 */
    @SerializedName("maxVersionCode")
    val maxVersionCode: Int = 0,

    /**
     * 是否可"不再提示"。true=用户可勾选忽略,之后不再弹;
     * false=每次启动都弹(用于紧急公告)。
     */
    @SerializedName("dismissible")
    val dismissible: Boolean = true
) {
    /** 有有效的动作按钮(文案 + 链接都非空)。 */
    fun hasAction(): Boolean = actionLabel.isNotBlank() && actionUrl.isNotBlank()

    /**
     * 判定此公告当前是否应展示。
     *
     * @param nowMillis     当前时间戳(便于测试注入)
     * @param versionCode   当前客户端 versionCode
     * @param dismissedIds  用户已"不再提示"的公告 id 集合
     * @param fetchedAt     公告 JSON 首次写入客户端的时间(毫秒);
     *                      默认 1 天有效期的基准。0 = 未知(老缓存/未写入),此时视为"已过期"
     *                      防止历史失效公告复活。
     */
    fun isActive(
        nowMillis: Long,
        versionCode: Int,
        dismissedIds: Set<String>,
        fetchedAt: Long = 0L
    ): Boolean {
        if (id.isBlank()) return false
        if (dismissible && id in dismissedIds) return false
        if (minVersionCode > 0 && versionCode < minVersionCode) return false
        if (maxVersionCode > 0 && versionCode > maxVersionCode) return false

        val start = parseTimeOrNull(startAt)
        if (start != null && nowMillis < start) return false
        // expireAt 空 = 默认有效期 1 天(基准 startAt 或 fetchedAt);
        // fetchedAt=0 视为"已过期"(老用户升级后老 JSON 没时间戳,不复活)。
        val expire = parseTimeOrNull(expireAt)
            ?: if (fetchedAt > 0L) (start ?: fetchedAt) + ONE_DAY_MILLIS
            else 0L
        if (expire == 0L || nowMillis > expire) return false

        return true
    }

    companion object {
        /** 默认有效期 1 天(2026-06-27 起统一行为,见 [Announcement] 类注释)。 */
        private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L

        /**
         * 解析 `yyyy-MM-dd` 或 `yyyy-MM-dd HH:mm` 为本地时区毫秒;
         * 空串 / 非法格式返回 null(视为无该侧限制)。
         */
        fun parseTimeOrNull(raw: String): Long? {
            val s = raw.trim()
            if (s.isEmpty()) return null
            val patterns = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd")
            for (p in patterns) {
                val parsed = runCatching {
                    java.text.SimpleDateFormat(p, java.util.Locale.getDefault())
                        .apply { isLenient = false }
                        .parse(s)
                }.getOrNull()
                if (parsed != null) return parsed.time
            }
            return null
        }
    }
}
