package com.ahu_plus.data.model.schedule

/**
 * 课表卡片外观偏好。
 *
 * 颜色使用 #RRGGBB / #AARRGGBB 字符串保存，避免 DataStore 层依赖 Compose Color。
 */
data class SchedulePaletteConfig(
    val version: Int = 2,
    val presetId: String = DEFAULT_PRESET_ID,
    val cardStyle: String = DEFAULT_CARD_STYLE,
    val customColors: List<String> = emptyList(),
    val courseOverrides: Map<String, String> = emptyMap(),
    /** nullable 用于兼容升级前已保存的 Gson JSON。 */
    val background: ScheduleBackgroundConfig? = null,
) {
    companion object {
        const val DEFAULT_PRESET_ID = "campus_clear"
        const val DEFAULT_CARD_STYLE = "soft"
    }
}

data class ScheduleBackgroundConfig(
    /** classic / blocks / clean */
    val gridStyle: String = "classic",
    val showGridLines: Boolean = true,
    val alternatingRows: Boolean = true,
    val highlightToday: Boolean = true,
    val blockGapDp: Float = 2f,
    val blockRadiusDp: Float = 7f,
    /** palette / solid / image */
    val backgroundMode: String = "palette",
    val backgroundColor: String = "#FFF7FAFF",
    val backgroundImageUri: String = "",
    /** crop / fit */
    val imageScale: String = "crop",
    val imageOpacity: Float = 1f,
    val overlayStrength: Float = 0.38f,
    val cellOpacity: Float = 0.82f,
)

fun SchedulePaletteConfig.backgroundOrDefault(): ScheduleBackgroundConfig =
    background ?: ScheduleBackgroundConfig()
