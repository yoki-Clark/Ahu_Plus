package com.ahu_plus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.ahu_plus.data.model.schedule.SchedulePaletteConfig
import com.ahu_plus.data.model.schedule.backgroundOrDefault

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

enum class CourseCardStyle(val storageValue: String, val label: String) {
    SOFT("soft", "柔和填充"),
    SOLID("solid", "鲜明纯色"),
    OUTLINE("outline", "轻盈描边");

    companion object {
        fun fromStorage(value: String): CourseCardStyle =
            entries.firstOrNull { it.storageValue == value } ?: SOFT
    }
}

data class CoursePalettePreset(
    val id: String,
    val name: String,
    val description: String,
    val colors: List<Color>,
)

data class CourseCardVisuals(
    val container: Color,
    val content: Color,
    val secondaryContent: Color,
    val outline: Color,
    val accent: Color,
)

data class ScheduleBackgroundVisuals(
    val canvas: Color,
    val evenCell: Color,
    val oddCell: Color,
    val todayCell: Color,
    val timeColumn: Color,
    val headerCell: Color,
    val gridLine: Color,
)

object CoursePalettes {
    private fun colors(vararg values: Long): List<Color> = values.map(::Color)

    val presets: List<CoursePalettePreset> = listOf(
        CoursePalettePreset(
            id = "campus_clear",
            name = "糖果粉彩",
            description = "ColorBrewer Set3，明亮柔和",
            colors = colors(
                0xFF8DD3C7, 0xFFFFFFB3, 0xFFBEBADA, 0xFFFB8072, 0xFF80B1D3,
                0xFFFDB462, 0xFFB3DE69, 0xFFFCCDE5, 0xFFD9D9D9, 0xFFBC80BD,
            ),
        ),
        CoursePalettePreset(
            id = "tableau10",
            name = "Tableau 10",
            description = "专业数据图表，均衡稳重",
            colors = colors(
                0xFF4E79A7, 0xFFF28E2C, 0xFFE15759, 0xFF76B7B2, 0xFF59A14F,
                0xFFEDC949, 0xFFAF7AA1, 0xFFFF9DA7, 0xFF9C755F, 0xFFBAB0AB,
            ),
        ),
        CoursePalettePreset(
            id = "observable10",
            name = "现代波普",
            description = "Observable 10，鲜活现代",
            colors = colors(
                0xFF4269D0, 0xFFEFB118, 0xFFFF725C, 0xFF6CC5B0, 0xFF3CA951,
                0xFFFF8AB7, 0xFFA463F2, 0xFF97BBF5, 0xFF9C6B4E, 0xFF9498A0,
            ),
        ),
        CoursePalettePreset(
            id = "paired",
            name = "明暗成对",
            description = "ColorBrewer Paired，反差最强",
            colors = colors(
                0xFFA6CEE3, 0xFF1F78B4, 0xFFB2DF8A, 0xFF33A02C, 0xFFFB9A99,
                0xFFE31A1C, 0xFFFDBF6F, 0xFFFF7F00, 0xFFCAB2D6, 0xFF6A3D9A,
            ),
        ),
        CoursePalettePreset(
            id = "nord",
            name = "北境极光",
            description = "Nord Frost + Aurora，冷暖分明",
            colors = colors(
                0xFF8FBCBB, 0xFF88C0D0, 0xFF81A1C1, 0xFF5E81AC, 0xFFBF616A,
                0xFFD08770, 0xFFEBCB8B, 0xFFA3BE8C, 0xFFB48EAD, 0xFF4C566A,
            ),
        ),
        CoursePalettePreset(
            id = "ahu_classic",
            name = "安大经典",
            description = "品牌蓝青为主，浓郁醒目",
            colors = CourseColors,
        ),
        CoursePalettePreset(
            id = "color_safe",
            name = "色觉友好",
            description = "Okabe–Ito 为主，高辨识度",
            colors = colors(
                0xFFE69F00, 0xFF56B4E9, 0xFF009E73, 0xFFF0E442, 0xFF0072B2,
                0xFFD55E00, 0xFFCC79A7, 0xFF303030, 0xFF999999, 0xFF6A3D9A,
            ),
        ),
        CoursePalettePreset(
            id = "custom",
            name = "自定义",
            description = "逐个编辑十个颜色槽位",
            colors = emptyList(),
        ),
    )

    val customColorBank: List<Color> = colors(
        0xFF315A8A, 0xFF4F7CAC, 0xFF3A86C8, 0xFF48A9C5,
        0xFF176B65, 0xFF2A9D8F, 0xFF5C8D5A, 0xFF7A9E62,
        0xFF6657A5, 0xFF8B6FA6, 0xFF9C5BD6, 0xFFB565A7,
        0xFFB45353, 0xFFD45D79, 0xFFE07A45, 0xFFC88754,
        0xFF9A7B4F, 0xFFB29A7B, 0xFF68798A, 0xFF7E858C,
        0xFF263859, 0xFF3F4E4F, 0xFF7C5C46, 0xFF6B705C,
    )

    fun preset(id: String): CoursePalettePreset =
        presets.firstOrNull { it.id == id } ?: presets.first()

    fun colors(config: SchedulePaletteConfig): List<Color> {
        val fallback = presets.first().colors
        if (config.presetId != "custom") return preset(config.presetId).colors.ifEmpty { fallback }
        val parsed = config.customColors.mapNotNull(::parseColor)
        return List(10) { index -> parsed.getOrNull(index) ?: fallback[index % fallback.size] }
    }

    fun courseKey(courseCode: String?, lessonId: Long, courseName: String): String =
        courseCode?.trim()?.takeIf { it.isNotEmpty() }?.let { "code:$it" }
            ?: if (lessonId != 0L) "lesson:$lessonId" else "name:${courseName.trim()}"

    fun colorFor(
        config: SchedulePaletteConfig,
        colorIndex: Int,
        courseKey: String,
    ): Color {
        config.courseOverrides[courseKey]?.let(::parseColor)?.let { return it }
        val palette = colors(config)
        return palette[Math.floorMod(colorIndex, palette.size)]
    }

    fun cardVisuals(
        base: Color,
        style: CourseCardStyle,
        colorScheme: ColorScheme,
        darkTheme: Boolean,
    ): CourseCardVisuals = when (style) {
        CourseCardStyle.SOFT -> {
            val amount = if (darkTheme) 0.34f else 0.22f
            val container = lerp(colorScheme.surface, base, amount)
            CourseCardVisuals(
                container = container,
                content = colorScheme.onSurface,
                secondaryContent = colorScheme.onSurfaceVariant,
                outline = base.copy(alpha = if (darkTheme) 0.55f else 0.38f),
                accent = base,
            )
        }

        CourseCardStyle.SOLID -> {
            val content = if (base.luminance() > 0.48f) Color(0xFF142033) else Color.White
            CourseCardVisuals(
                container = base,
                content = content,
                secondaryContent = content.copy(alpha = 0.82f),
                outline = Color.Transparent,
                accent = base,
            )
        }

        CourseCardStyle.OUTLINE -> CourseCardVisuals(
            container = lerp(colorScheme.surface, base, if (darkTheme) 0.12f else 0.07f),
            content = colorScheme.onSurface,
            secondaryContent = colorScheme.onSurfaceVariant,
            outline = base.copy(alpha = 0.72f),
            accent = base,
        )
    }

    fun backgroundVisuals(
        config: SchedulePaletteConfig,
        colorScheme: ColorScheme,
        darkTheme: Boolean,
    ): ScheduleBackgroundVisuals {
        val accent = colors(config).firstOrNull() ?: colorScheme.primary
        val background = config.backgroundOrDefault()
        val customBackground = parseColor(background.backgroundColor) ?: colorScheme.background
        val canvasTint = if (darkTheme) 0.075f else 0.045f
        val canvas = when (background.backgroundMode) {
            "solid" -> customBackground
            "image" -> Color.Transparent
            else -> lerp(colorScheme.background, accent, canvasTint)
        }
        val surfaceBase = when (background.backgroundMode) {
            "solid" -> lerp(customBackground, colorScheme.surface, if (darkTheme) 0.48f else 0.62f)
            else -> colorScheme.surface
        }
        val cellAlpha = if (background.backgroundMode == "image") {
            background.cellOpacity.coerceIn(0.08f, 1f)
        } else 1f
        return ScheduleBackgroundVisuals(
            canvas = canvas,
            evenCell = lerp(surfaceBase, accent, if (darkTheme) 0.045f else 0.025f).copy(alpha = cellAlpha),
            oddCell = lerp(colorScheme.surfaceVariant, accent, if (darkTheme) 0.075f else 0.045f).copy(alpha = cellAlpha),
            todayCell = lerp(surfaceBase, accent, if (darkTheme) 0.22f else 0.13f)
                .copy(alpha = cellAlpha.coerceAtLeast(0.72f)),
            timeColumn = lerp(surfaceBase, accent, if (darkTheme) 0.12f else 0.075f).copy(alpha = cellAlpha),
            headerCell = lerp(surfaceBase, accent, if (darkTheme) 0.10f else 0.06f).copy(alpha = cellAlpha),
            gridLine = lerp(colorScheme.outlineVariant, accent, 0.10f).copy(alpha = 0.42f),
        )
    }

    fun toStorage(color: Color): String =
        "#" + color.toArgb().toUInt().toString(16).padStart(8, '0').uppercase()

    fun parseColor(raw: String): Color? = runCatching {
        val hex = raw.removePrefix("#")
        when (hex.length) {
            6 -> Color((0xFF000000L or hex.toLong(16)).toInt())
            8 -> Color(hex.toLong(16).toInt())
            else -> null
        }
    }.getOrNull()
}
