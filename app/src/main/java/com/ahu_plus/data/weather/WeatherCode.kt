package com.ahu_plus.data.weather

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Open-Meteo WMO Weather Code 映射。
 *
 * 严重度从轻到重: 0 晴 < 1 阴雾 < 2 毛毛雨 < 3 雨雪 < 4 雷暴。
 * 与空气污染叠加: PM2.5 > 75 追加"戴口罩"提醒, 与天气严重度独立。
 *
 * 参考: https://open-meteo.com/en/docs (WMO Weather interpretation codes)
 */
object WeatherCode {

    /** 严重度。0 晴, 1 阴雾, 2 毛毛雨, 3 雨雪, 4 雷暴。 */
    fun severity(code: Int): Int = when (code) {
        0 -> 0
        1, 2 -> 0
        3 -> 1
        45, 48 -> 1
        51, 53, 55, 56, 57 -> 2
        61, 63, 65, 66, 67 -> 3
        71, 73, 75, 77 -> 3
        80, 81, 82 -> 3
        85, 86 -> 3
        95 -> 4
        96, 99 -> 4
        else -> 0
    }

    /** 是否需要带伞(severity >= 2)。 */
    fun needsUmbrella(code: Int): Boolean = severity(code) >= 2

    /** Compose 图标。 */
    fun icon(code: Int): ImageVector = when (code) {
        0 -> Icons.Filled.WbSunny
        1, 2 -> Icons.Filled.Cloud
        3 -> Icons.Filled.FilterDrama
        45, 48 -> Icons.Filled.Cloud
        51, 53, 55, 56, 57 -> Icons.Filled.Grain
        61, 63, 65, 66, 67 -> Icons.Filled.Umbrella
        71, 73, 75, 77 -> Icons.Filled.AcUnit
        80, 81, 82 -> Icons.Filled.WaterDrop
        85, 86 -> Icons.Filled.AcUnit
        95, 96, 99 -> Icons.Filled.Thunderstorm
        else -> Icons.Filled.WbSunny
    }

    /** 简短中文文案, 用于天气屏顶部描述。 */
    fun describe(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "少云"
        2 -> "多云"
        3 -> "阴"
        45 -> "雾"
        48 -> "雾凇"
        51, 53, 55 -> "毛毛雨"
        56, 57 -> "冻毛毛雨"
        61 -> "小雨"
        63 -> "中雨"
        65 -> "大雨"
        66, 67 -> "冻雨"
        71 -> "小雪"
        73 -> "中雪"
        75 -> "大雪"
        77 -> "雪粒"
        80 -> "阵雨"
        81 -> "强阵雨"
        82 -> "暴阵雨"
        85, 86 -> "阵雪"
        95 -> "雷暴"
        96, 99 -> "雷暴冰雹"
        else -> "未知"
    }
}