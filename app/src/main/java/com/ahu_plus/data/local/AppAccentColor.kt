package com.ahu_plus.data.local

import androidx.compose.ui.graphics.Color

/**
 * 主题强调色（accent）- 影响 colorScheme 的 primary / primaryContainer / onPrimaryContainer /
 * secondary / secondaryContainer，用于"全局换肤"（批次一项8）。
 *
 * 与 [AppThemeMode] 同构：枚举 + [storageValue] + [fromStorageValue]，走 SessionManager 持久化。
 *
 * 设计约定：accent **只改 primary 系**；Hero 渐变（今日课程 Blue、余额 Green、GPA Violet、
 * 个人 banner Blue）保持语义固定、不随 accent 变，避免破坏品牌语义（见 plan.md Step 6）。
 * 默认 [BLUE] 与历史 colorScheme 完全一致，保证升级无视觉变化。
 */
enum class AppAccentColor(val storageValue: String) {

    /** 安大蓝（默认，= 历史 colorScheme） */
    BLUE("blue"),

    /** 青绿 */
    TEAL("teal"),

    /** 紫 */
    VIOLET("violet"),

    /** 橙 */
    ORANGE("orange"),

    /** 粉 */
    PINK("pink");

    companion object {
        fun fromStorageValue(value: String?): AppAccentColor =
            entries.firstOrNull { it.storageValue == value } ?: BLUE
    }
}

/**
 * accent 对应的 colorScheme 角色取值（浅色档）。
 * primaryContainer 选浅色调、onPrimaryContainer 选深色调，保证对比度。
 */
val AppAccentColor.lightPrimary: Color
    get() = when (this) {
        AppAccentColor.BLUE -> Color(0xFF1F5AA6)
        AppAccentColor.TEAL -> Color(0xFF168C80)
        AppAccentColor.VIOLET -> Color(0xFF7567C8)
        AppAccentColor.ORANGE -> Color(0xFFE07A1F)
        AppAccentColor.PINK -> Color(0xFFD81B60)
    }

val AppAccentColor.lightPrimaryContainer: Color
    get() = when (this) {
        AppAccentColor.BLUE -> Color(0xFFDCE8FF)
        AppAccentColor.TEAL -> Color(0xFFCDF1ED)
        AppAccentColor.VIOLET -> Color(0xFFE8E3FA)
        AppAccentColor.ORANGE -> Color(0xFFFFE6CC)
        AppAccentColor.PINK -> Color(0xFFFFD9E2)
    }

val AppAccentColor.lightOnPrimaryContainer: Color
    get() = when (this) {
        AppAccentColor.BLUE -> Color(0xFF102C5E)
        AppAccentColor.TEAL -> Color(0xFF063D38)
        AppAccentColor.VIOLET -> Color(0xFF2A2350)
        AppAccentColor.ORANGE -> Color(0xFF5A3500)
        AppAccentColor.PINK -> Color(0xFF5C0027)
    }

val AppAccentColor.lightSecondary: Color
    get() = when (this) {
        AppAccentColor.BLUE -> Color(0xFF168C80)
        AppAccentColor.TEAL -> Color(0xFF1F5AA6)
        AppAccentColor.VIOLET -> Color(0xFF168C80)
        AppAccentColor.ORANGE -> Color(0xFF168C80)
        AppAccentColor.PINK -> Color(0xFF168C80)
    }

/** accent 对应的 colorScheme 角色取值（深色档）。primary 提亮到 200-300 档。 */
val AppAccentColor.darkPrimary: Color
    get() = when (this) {
        AppAccentColor.BLUE -> Color(0xFF9BBEFF)
        AppAccentColor.TEAL -> Color(0xFF7BCFC4)
        AppAccentColor.VIOLET -> Color(0xFFC6BEF0)
        AppAccentColor.ORANGE -> Color(0xFFFFB877)
        AppAccentColor.PINK -> Color(0xFFFF8FAF)
    }

val AppAccentColor.darkPrimaryContainer: Color
    get() = when (this) {
        AppAccentColor.BLUE -> Color(0xFF24395F)
        AppAccentColor.TEAL -> Color(0xFF153F3B)
        AppAccentColor.VIOLET -> Color(0xFF322A5C)
        AppAccentColor.ORANGE -> Color(0xFF4A2C00)
        AppAccentColor.PINK -> Color(0xFF5C1A36)
    }

val AppAccentColor.darkOnPrimaryContainer: Color
    get() = when (this) {
        AppAccentColor.BLUE -> Color(0xFFE8ECF6)
        AppAccentColor.TEAL -> Color(0xFFE8ECF6)
        AppAccentColor.VIOLET -> Color(0xFFE8ECF6)
        AppAccentColor.ORANGE -> Color(0xFFE8ECF6)
        AppAccentColor.PINK -> Color(0xFFE8ECF6)
    }

val AppAccentColor.darkSecondary: Color
    get() = when (this) {
        AppAccentColor.BLUE -> Color(0xFF168C80)
        AppAccentColor.TEAL -> Color(0xFF9BBEFF)
        AppAccentColor.VIOLET -> Color(0xFF168C80)
        AppAccentColor.ORANGE -> Color(0xFF168C80)
        AppAccentColor.PINK -> Color(0xFF168C80)
    }
