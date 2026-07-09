package com.ahu_plus.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 课表课程调色板 (10 色)。
 *
 * 用于 [com.ahu_plus.ui.screen.schedule.WeekGrid] 中按 [colorIndex] 选色。
 * 杜绝与浅色背景相近的颜色——所有色值均有足够的饱和度/深度,文字用白色清晰可辨。
 */
val CourseColors: List<Color> = listOf(
    AhuBlue,           // 0  深蓝
    AhuTeal,           // 1  青绿
    AhuIndigo,         // 2  靛紫
    AhuGreen,          // 3  翠绿
    AhuOrange,         // 4  橙黄
    AhuRed,            // 5  朱红
    AhuViolet,         // 6  紫罗兰
    Color(0xFF3D7CC9), // 7  明蓝 (替代原先太浅的 AhuBlueLight)
    Color(0xFFE76F51), // 8  珊瑚
    Color(0xFF1F7A8C), // 9  深青
)
