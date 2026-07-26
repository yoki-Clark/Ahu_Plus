package com.ahu_plus.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 跨模块通用状态色板（考勤 / 成绩 / 培养计划 / 关于页等共用）。
 *
 * 之前 `0xFF27AE60` / `0xFFE67E22` / `0xFF888888` 等值在多个 Composable 文件中
 * 各自重复硬编码，这里集中托管，保持原色值不变，只统一来源。
 */
object AhuStatusColors {
    /** 正常 / 已签到 / 已通过 — 绿色 */
    val NormalGreen = Color(0xFF27AE60)

    /** 迟到 / 警告 — 橙色 */
    val WarningOrange = Color(0xFFE67E22)

    /** 未知 / 缺勤记录缺失 — 灰色 */
    val UnknownGray = Color(0xFF888888)

    /** 操作 / 信息强调 — 蓝色 */
    val ActionBlue = Color(0xFF2F80ED)

    /** App 图标品牌色 — 靛紫 */
    val AppIndigo = Color(0xFF6C63FF)

    /** 倒计时紧急提醒背景 — 浅粉 */
    val UrgentPink = Color(0xFFFFCDD2)

    /** 二维码过期提示 — 琥珀（前景） */
    val QrStaleAmber = Color(0xFFFFE082)

    /** 二维码过期提示 — 琥珀（背景） */
    val QrStaleAmberBg = Color(0xFFFFA000)
}
