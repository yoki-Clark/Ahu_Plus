package com.ahu_plus.data.remote.market

import android.util.Base64
import com.google.gson.JsonParser
import com.ahu_plus.data.model.MarketNode
import okhttp3.MediaType.Companion.toMediaType

/**
 * 校园集市（api.zxs-bbs.cn）的网络层常量与身份字段工具。
 *
 * 把所有"端点 URL / 固定 Header / 板块列表 / 身份字段归一化与解析"
 * 集中在这里，方便：
 *  - 端点变动时只改一处
 *  - 单元测试可对纯函数（normalize / schoolFrom）做覆盖
 *  - UI 层（MarketScreen）不直接依赖 Repository
 */
object MarketApi {

    // ── 端点 ─────────────────────────────────────────
    const val BASE_URL = "https://api.zxs-bbs.cn/api/client"
    const val TOPICS_URL = "$BASE_URL/topics"
    const val TOPICS_TOP_URL = "$BASE_URL/topics/top"
    const val COMMENTS_URL = "$BASE_URL/comments"
    const val USER_NOTICES_URL = "$BASE_URL/user_notices"

    // ── Header / UA ──────────────────────────────────
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val REFERER =
        "https://servicewechat.com/wxc56be16e96fc1df1/66/page-frame.html"

    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // ── 板块（硬编码 V1）─────────────────────────────
    /**
     * 默认板块列表。V1 采用硬编码，待官方提供节点列表接口
     * （推测 `GET /api/client/nodes`）后再切换为动态拉取。
     */
    val DEFAULT_NODES: List<MarketNode> = listOf(
        MarketNode(3727, "新鲜事"),
        MarketNode(3728, "日常投稿"),
        MarketNode(3729, "二手闲置"),
        MarketNode(3730, "树洞"),
        MarketNode(3731, "表白墙")
    )

    const val DEFAULT_NODE_ID: Long = 3727L

    // ── 身份字段（Bearer JWT）归一化 / 解析 ──────────

    /**
     * 把用户粘进输入框的字符串归一化为请求头需要的格式。
     *  - 自动加 "Bearer " 前缀（大小写不敏感）
     *  - 去除首尾空白
     */
    fun normalizeIdentity(identity: String): String {
        val trimmed = identity.trim()
        return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
            trimmed
        } else {
            "Bearer $trimmed"
        }
    }

    /**
     * 从 JWT 的 payload 中解码 `school` 字段。
     * 返回 null 表示解析失败或字段缺失。
     */
    fun schoolFromIdentity(identity: String?): String? {
        val raw = identity?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val token = if (raw.startsWith("Bearer ", ignoreCase = true)) {
            raw.drop(7).trim()
        } else {
            raw
        }
        val parts = token.split(".")
        if (parts.size < 2) return null
        return runCatching {
            val payload = parts[1] + "=".repeat((4 - parts[1].length % 4) % 4)
            val json = String(
                Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP),
                Charsets.UTF_8
            )
            JsonParser.parseString(json).asJsonObject.get("school")?.asString
        }.getOrNull()
    }
}
