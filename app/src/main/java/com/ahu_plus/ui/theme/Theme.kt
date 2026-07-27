package com.ahu_plus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ahu_plus.data.local.AppAccentColor
import com.ahu_plus.data.local.darkOnPrimaryContainer
import com.ahu_plus.data.local.darkPrimary
import com.ahu_plus.data.local.darkPrimaryContainer
import com.ahu_plus.data.local.darkSecondary
import com.ahu_plus.data.local.lightOnPrimaryContainer
import com.ahu_plus.data.local.lightPrimary
import com.ahu_plus.data.local.lightPrimaryContainer
import com.ahu_plus.data.local.lightSecondary

private val DarkColorScheme = darkColorScheme(
    primary = AhuBlueDark,
    onPrimary = ColorTokens.DarkOnPrimary,
    primaryContainer = ColorTokens.DarkPrimaryContainer,
    onPrimaryContainer = AhuDarkOnSurface,
    secondary = AhuTeal,
    onSecondary = ColorTokens.DarkOnPrimary,
    secondaryContainer = ColorTokens.DarkSecondaryContainer,
    onSecondaryContainer = AhuDarkOnSurface,
    tertiary = AhuOrange,
    onTertiary = ColorTokens.DarkOnPrimary,
    background = AhuDarkBackground,
    onBackground = AhuDarkOnSurface,
    surface = AhuDarkSurface,
    onSurface = AhuDarkOnSurface,
    surfaceVariant = AhuDarkSurfaceVariant,
    onSurfaceVariant = AhuDarkOnSurfaceVariant,
    outline = AhuDarkOutline,
    outlineVariant = AhuDarkOutline.copy(alpha = 0.72f),
    error = ColorTokens.DarkError,
    errorContainer = ColorTokens.DarkErrorContainer,
)

private val LightColorScheme = lightColorScheme(
    primary = AhuBlue,
    onPrimary = ColorTokens.LightOnPrimary,
    primaryContainer = AhuBlueLight,
    onPrimaryContainer = ColorTokens.LightOnPrimaryContainer,
    secondary = AhuTeal,
    onSecondary = ColorTokens.LightOnPrimary,
    secondaryContainer = Color(0xFFD4F3EF),
    onSecondaryContainer = Color(0xFF063D38),
    tertiary = AhuOrange,
    onTertiary = ColorTokens.LightOnPrimary,
    background = AhuLightBackground,
    onBackground = AhuLightOnSurface,
    surface = AhuLightSurface,
    onSurface = AhuLightOnSurface,
    surfaceVariant = AhuLightSurfaceVariant,
    onSurfaceVariant = AhuLightOnSurfaceVariant,
    outline = AhuLightOutline,
    outlineVariant = AhuLightOutline.copy(alpha = 0.72f),
    error = AhuRed,
    errorContainer = Color(0xFFFFDAD7),
)

@Composable
fun AhuPlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    accentColor: AppAccentColor = AppAccentColor.BLUE,
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    // accent 覆盖 primary 系（dynamicColor 时也覆盖，保证用户选的主题色生效）。
    // Hero 渐变不走 colorScheme，保持语义固定不受影响。
    val colorScheme = baseScheme.copy(
        primary = if (darkTheme) accentColor.darkPrimary else accentColor.lightPrimary,
        primaryContainer = if (darkTheme) accentColor.darkPrimaryContainer else accentColor.lightPrimaryContainer,
        onPrimaryContainer = if (darkTheme) accentColor.darkOnPrimaryContainer else accentColor.lightOnPrimaryContainer,
        secondary = if (darkTheme) accentColor.darkSecondary else accentColor.lightSecondary,
    )

    // 注：MaterialExpressiveTheme / MotionScheme 在当前 material3(BOM 2026.02.01)中仍为
    // internal,无法外部访问。暂用标准 MaterialTheme;Expressive 动效改为在关键组件手写
    // spring/tween 实现(见 Dashboard 列表动画、Tab Crossfade)。待 API 公开后可一行切换。
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AhuMaterialShapes,
        content = content
    )
}

private object ColorTokens {
    val LightOnPrimary = Color.White
    val LightOnPrimaryContainer = Color(0xFF102C5E)
    val DarkOnPrimary = Color(0xFF08111F)
    val DarkPrimaryContainer = Color(0xFF24395F)
    val DarkSecondaryContainer = Color(0xFF153F3B)
    val DarkError = Color(0xFFFFB4AB)
    val DarkErrorContainer = Color(0xFF5F1515)
}
