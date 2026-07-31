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
 *
 * 本组托管「浅深表现一致、单值即可」的色；需深浅换档的色见 [AhuToneColors]。
 */
object AhuStatusColors {
    /** 正常 / 已签到 / 已通过 - 绿色 */
    val NormalGreen = Color(0xFF27AE60)

    /** 迟到 / 警告 - 橙色 */
    val WarningOrange = Color(0xFFE67E22)

    /** 未知 / 缺勤记录缺失 - 灰色 */
    val UnknownGray = Color(0xFF888888)

    /** 操作 / 信息强调 - 蓝色 */
    val ActionBlue = Color(0xFF2F80ED)

    /** App 图标品牌色 - 靛紫 */
    val AppIndigo = Color(0xFF6C63FF)

    /** 倒计时紧急提醒背景 - 浅粉 */
    val UrgentPink = Color(0xFFFFCDD2)

    /** 二维码过期提示 - 琥珀（前景） */
    val QrStaleAmber = Color(0xFFFFE082)

    /** 二维码过期提示 - 琥珀（背景） */
    val QrStaleAmberBg = Color(0xFFFFA000)
}

/**
 * 深浅双档业务语义色（[AhuToneColor] 形式）。
 *
 * 集中收口之前散落在各业务页面的裸 `Color(0xFF...)` 硬编码（批次一项71）——这些色
 * 在深色 surface 上若沿用浅色档会对比度塌掉，必须按主题换档。读取时用 [AhuToneColor.current]。
 */
object AhuToneColors {

    /** 第三方服务入口 - 紫（学习通/WeLearn 分组） */
    val ThirdPartyPurple = AhuToneColor(light = Color(0xFF9B59B6), dark = Color(0xFFB39DDB))

    /** "关于"页/灰蓝图标 - Slate */
    val AboutSlate = AhuToneColor(light = Color(0xFF607D8B), dark = Color(0xFFB0BEC5))

    /** 空气质量"轻度污染" - 橙 */
    val AqOrange = AhuToneColor(light = Color(0xFFFFA726), dark = Color(0xFFFFCC80))

    /** 空气质量"良好" - 绿 */
    val AqGood = AhuToneColor(light = Color(0xFF66BB6A), dark = Color(0xFFA5D6A7))

    /** 降雨概率 PoP - 蓝 */
    val RainBlue = AhuToneColor(light = Color(0xFF1E88E5), dark = Color(0xFF64B5F6))

    /** 倒计时 1~3 小时背景 - 浅琥珀 */
    val CountdownAmber = AhuToneColor(light = Color(0xFFFFE0B2), dark = Color(0xFFFFCC80))

    /** 倒计时弧进度 - 蓝（CountdownArc 默认参数兜底） */
    val CountdownArcBlue = AhuToneColor(light = Color(0xFF2196F3), dark = Color(0xFF64B5F6))

    /** 电费/水电 - Teal */
    val UtilityTeal = AhuToneColor(light = Color(0xFF00A6A6), dark = Color(0xFF4DBDBD))

    /** 一卡通/电费趋势图主色 - 蓝紫（驱动渐变填充） */
    val TrendPrimary = AhuToneColor(light = Color(0xFF4F73C8), dark = Color(0xFF7B9AE0))

    /** 补考标签 - 红 */
    val ExamRetakeRed = AhuToneColor(light = Color(0xFFE53935), dark = Color(0xFFEF9A9A))

    /** 缓考标签 - 橙 */
    val ExamDeferredOrange = AhuToneColor(light = Color(0xFFFB8C00), dark = Color(0xFFFFCC80))

    /** 我的某信息项图标 - 琥珀 */
    val MyInfoAmber = AhuToneColor(light = Color(0xFFB7791F), dark = Color(0xFFE0B366))

    /** 使用指南高亮 - 红 */
    val GuideHighlightRed = AhuToneColor(light = Color(0xFFD32F2F), dark = Color(0xFFEF5353))

    /** QR 加载失败/错误 - 红 */
    val QrErrorRed = AhuToneColor(light = Color(0xFFEF5350), dark = Color(0xFFEF9A9A))

    /** 空教室占用率柱背景 - 浅灰 */
    val BarBackground = AhuToneColor(light = Color(0xFFE0E0E0), dark = Color(0xFF3A4253))

    /** 空教室已过去时段 - 灰 */
    val PastUnitGray = AhuToneColor(light = Color(0xFFBDBDBD), dark = Color(0xFF4A5263))

    /** 空教室忙碌单元 - 浅灰 */
    val BusyUnitGray = AhuToneColor(light = Color(0xFFEEEEEE), dark = Color(0xFF2A313D))

    /** 空教室当前时间指示线 - 蓝 */
    val NowIndicatorBlue = AhuToneColor(light = Color(0xFF1976D2), dark = Color(0xFF64B5F6))

    /** 手势签到未激活点 - 灰 */
    val GestureDotGray = AhuToneColor(light = Color(0xFFBDBDBD), dark = Color(0xFF4A5263))

    /** 手势签到激活色 - 蓝 */
    val GestureActiveBlue = AhuToneColor(light = Color(0xFF2196F3), dark = Color(0xFF64B5F6))
}
