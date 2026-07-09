package com.ahu_plus.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 集市模块专用色板。
 *
 * 之前在 MarketScreen.kt 中散落大量 `Color(0xFF...)` 硬编码值，
 * 既不利于深色模式适配也不利于全局调色。
 * 这里集中托管：保持原有色值不变，只把来源统一。
 */
object MarketColors {
    // ── 通用状态色 ──
    val Success = Color(0xFF2A9D8F)
    val Error = Color(0xFFE53935)

    // ── 热榜 ──
    val HotFlame = Color(0xFFE65100)
    val HotFlameBg = Color(0xFFFFF3E0)
    val HotFlameText = Color(0xFF5D2A00)
    val HotFlameSubText = Color(0xFF8A4B12)
    val HotBadge = Color(0xFFFF9800)
    val HotBadgeGold = Color(0xFFFFC107)

    // ── 身份字段卡 ──
    val IdentityAccent = AhuBlue
    val IdentityAccentBg = AhuBlueLight

    // ── 热榜入口图标 ──
    val HotEntryIconBg = AhuBlueLight

    // ── 通知点赞色 ──
    val LikeRed = Color(0xFFE53935)

    // ── 搜索结果关键词高亮（替换服务端 <em class="highlight"> 标记） ──
    val SearchHighlight = Color(0xFFD32F2F)
}
