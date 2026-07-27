package com.ahu_plus.data.local

/**
 * 全局字号缩放档位（批次一项40）- App 内独立于系统字号的"字体大小"调节，
 * 用于无障碍。通过 [androidx.compose.ui.platform.LocalDensity] 的 fontScale 注入。
 *
 * 与 [AppThemeMode] / [AppAccentColor] 同构：枚举 + [storageValue] + [fromStorageValue]，
 * 走 SessionManager 持久化（退登保留，属用户偏好）。
 */
enum class AppFontScale(val storageValue: String, val factor: Float) {

    /** 小 - 0.85x */
    SMALL("small", 0.85f),

    /** 标准 - 1.0x（默认） */
    NORMAL("normal", 1.0f),

    /** 大 - 1.15x */
    LARGE("large", 1.15f),

    /** 超大 - 1.3x */
    EXTRA_LARGE("extra_large", 1.3f);

    companion object {
        fun fromStorageValue(value: String?): AppFontScale =
            entries.firstOrNull { it.storageValue == value } ?: NORMAL
    }
}
