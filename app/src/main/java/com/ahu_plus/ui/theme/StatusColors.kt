package com.ahu_plus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 深浅主题各一份的语义色。
 *
 * 单个固定 [Color] 在深色主题里对比度会塌掉：Material 700/800 档（如 `0xFF1565C0`）
 * 画在 [AhuDarkSurface] 上几乎读不出来。所以凡是「自带含义、不能映射到 colorScheme 角色」
 * 的状态色（成绩分档、课程性质、办件状态…）都按本类型声明浅/深两档，读取时用 [current]。
 *
 * 主题判定沿用仓库既有约定 `MaterialTheme.colorScheme.background.luminance() < 0.5f`
 * （见 WeekGrid / ScheduleSettingsSheet），而不是 `isSystemInDarkTheme()`——后者会绕过
 * `AhuPlusTheme(darkTheme = ...)` 的显式覆盖，也读不到 dynamicColor 的实际明暗。
 */
@Immutable
data class AhuToneColor(
    /** 浅色主题取值（通常是 Material 600–800 档）。 */
    val light: Color,
    /** 深色主题取值（通常是同色相 200–300 档）。 */
    val dark: Color,
) {
    /** 按当前主题明暗取值。 */
    val current: Color
        @Composable get() =
            if (MaterialTheme.colorScheme.background.luminance() < 0.5f) dark else light
}

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
