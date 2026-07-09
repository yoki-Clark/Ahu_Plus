package com.ahu_plus.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 学习通模块分类色板。
 *
 * 集中托管 ChaoxingTabScreen / HomeworkTabContent 等处的活动类型 / 文件类型色,
 * 之前散落在各 Composable 中以 `Color(0xFF...)` 硬编码,不便于全局调色与深色模式适配。
 */
object ChaoxingColors {
    // ── 消息 / 活动类型色 ──
    /** 签到 — 绿色 */
    val Signin = Color(0xFF4CAF50)
    /** 练习 — 蓝色 */
    val Exercise = Color(0xFF2196F3)
    /** 通知 — 橙色 */
    val Notice = Color(0xFFFF9800)
    /** 选人 — 紫色 */
    val Selection = Color(0xFF9C27B0)
    /** 其他活动 — 灰蓝 */
    val OtherActivity = Color(0xFF607D8B)

    /** 进行中 / 已参与状态色 — 复用 Signin 同色系 */
    val Active = Signin

    // ── 附件文件类型色 ──
    val FilePdf = Color(0xFFE53935)
    val FileDoc = Color(0xFF1565C0)
    val FileSheet = Color(0xFF2E7D32)
    val FileSlide = Color(0xFFEF6C00)
    val FileArchive = Color(0xFF6A1B9A)
    val FileImage = Color(0xFF00838F)
}
