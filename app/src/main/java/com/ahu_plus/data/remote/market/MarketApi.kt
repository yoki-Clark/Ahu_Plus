package com.ahu_plus.data.remote.market

import com.google.gson.JsonParser
import com.ahu_plus.data.model.MarketNode
import okhttp3.MediaType.Companion.toMediaType
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

data class MarketIdentityMetadata(
    val school: String,
    val schoolId: Long,
    val expiresAtEpochSeconds: Long,
)

data class ParsedMarketIdentity(
    val normalizedToken: String,
    val metadata: MarketIdentityMetadata,
)

enum class MarketIdentityExpiryState {
    VALID,
    EXPIRING_SOON,
    EXPIRED,
}

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

    const val IMPORT_URI_PREFIX = "ahuplus://market/import"
    const val EXPIRY_WARNING_SECONDS = 3L * 24 * 60 * 60
    private val expiryDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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
            "Bearer ${trimmed.drop(7).trim()}"
        } else {
            "Bearer $trimmed"
        }
    }

    /**
     * 从 JWT 的 payload 中解码 `school` 字段。
     * 返回 null 表示解析失败或字段缺失。
     */
    fun schoolFromIdentity(identity: String?): String? {
        return identity?.let(::parseIdentity)?.getOrNull()?.metadata?.school
    }

    /**
     * 解析并校验集市 JWT 的结构和必要字段。这里只做客户端格式校验，服务端仍会验证签名。
     */
    fun parseIdentity(identity: String): Result<ParsedMarketIdentity> = runCatching {
        val normalized = normalizeIdentity(identity)
        val token = normalized.removePrefix("Bearer ").trim()
        require(token.isNotBlank()) { "集市身份字段为空" }
        val parts = token.split('.')
        require(parts.size == 3 && parts.all { it.isNotBlank() }) { "不是有效的 JWT 身份字段" }

        val header = JsonParser.parseString(decodeJwtPart(parts[0])).asJsonObject
        require(header.get("alg")?.asString?.isNotBlank() == true) { "JWT 头部缺少签名算法" }
        val payload = JsonParser.parseString(decodeJwtPart(parts[1])).asJsonObject
        val school = payload.get("school")?.asString?.trim().orEmpty()
        val schoolId = payload.get("schoolID")?.asLong ?: 0L
        val expiresAt = payload.get("exp")?.asLong ?: 0L
        require(school.isNotBlank() && schoolId > 0L) { "JWT 中缺少学校身份" }
        require(expiresAt > 0L) { "JWT 中缺少有效期" }

        ParsedMarketIdentity(
            normalizedToken = normalized,
            metadata = MarketIdentityMetadata(
                school = school,
                schoolId = schoolId,
                expiresAtEpochSeconds = expiresAt,
            ),
        )
    }

    /** 解析桌面工具生成的 `ahuplus://market/import` URI。 */
    fun parseImportUri(value: String): Result<ParsedMarketIdentity> = runCatching {
        val uri = URI(value.trim())
        require(uri.scheme.equals("ahuplus", ignoreCase = true)) { "不是 Ahu_Plus 导入链接" }
        require(uri.host.equals("market", ignoreCase = true) && uri.path == "/import") {
            "不是集市身份导入链接"
        }
        val params = parseQuery(uri.rawQuery)
        require(params["v"] == "1") { "不支持的导入链接版本" }
        require(!params["nonce"].isNullOrBlank()) { "导入链接缺少随机校验值" }
        val token = params["token"]?.takeIf { it.isNotBlank() }
            ?: error("导入链接中没有身份字段")
        parseIdentity(token).getOrThrow()
    }

    fun expiryState(
        expiresAtEpochSeconds: Long,
        nowEpochSeconds: Long = Instant.now().epochSecond,
    ): MarketIdentityExpiryState = when {
        expiresAtEpochSeconds <= nowEpochSeconds -> MarketIdentityExpiryState.EXPIRED
        expiresAtEpochSeconds - nowEpochSeconds <= EXPIRY_WARNING_SECONDS ->
            MarketIdentityExpiryState.EXPIRING_SOON
        else -> MarketIdentityExpiryState.VALID
    }

    fun expiryLabel(
        metadata: MarketIdentityMetadata,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val date = Instant.ofEpochSecond(metadata.expiresAtEpochSeconds)
            .atZone(zoneId)
            .toLocalDate()
            .format(expiryDateFormatter)
        return "有效期至 $date"
    }

    /** 返回最紧急的一条过期提示，供页面 Snackbar 使用。 */
    fun expiryWarning(
        identities: Iterable<String>,
        nowEpochSeconds: Long = Instant.now().epochSecond,
    ): String? {
        val parsed = identities.mapNotNull { parseIdentity(it).getOrNull() }
        val expired = parsed
            .filter { expiryState(it.metadata.expiresAtEpochSeconds, nowEpochSeconds) == MarketIdentityExpiryState.EXPIRED }
            .minByOrNull { it.metadata.expiresAtEpochSeconds }
        if (expired != null) return "${expired.metadata.school}的集市身份已过期，请重新导入"

        val expiring = parsed
            .filter { expiryState(it.metadata.expiresAtEpochSeconds, nowEpochSeconds) == MarketIdentityExpiryState.EXPIRING_SOON }
            .minByOrNull { it.metadata.expiresAtEpochSeconds }
            ?: return null
        val remaining = expiring.metadata.expiresAtEpochSeconds - nowEpochSeconds
        val days = ((remaining + 86_399L) / 86_400L).coerceAtLeast(1L)
        return "${expiring.metadata.school}的集市身份将在 $days 天内过期"
    }

    private fun decodeJwtPart(value: String): String {
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        return String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        require(!rawQuery.isNullOrBlank()) { "导入链接缺少参数" }
        return rawQuery.split('&').associate { pair ->
            val parts = pair.split('=', limit = 2)
            val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(parts.getOrElse(1) { "" }, StandardCharsets.UTF_8.name())
            key to value
        }
    }
}
