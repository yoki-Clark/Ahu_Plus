package com.ahu_plus.ui.components

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 沉浸式状态栏图标色控制(批次三项58)。
 *
 * Hero 顶满状态栏时,渐变 Hero 是深色背景,状态栏图标必须为浅色;滚动到实色顶栏后,
 * 图标色回归主题(浅色主题深图标 / 深色主题浅色图标)。
 *
 * - [immersive] 为 false 时不干预(交给 `enableEdgeToEdge` 默认行为)。
 * - [fraction] 顶栏滚动渐变进度(0=顶静止 Hero 可见,1=已滚到实色);< 0.5 强制浅色图标,
 *   >= 0.5 按主题。阈值硬切,不做动画,避免字色中间态闪烁。
 * - 离屏 onDispose 恢复主题默认,避免图标色泄漏到其他屏。
 *
 * 注意:Hero 渐变是品牌色固定(蓝/绿等),不随应用主题变,故"Hero 可见"档始终浅色图标,
 * 不按 isDark 分支。
 */
@Composable
fun applyImmersiveStatusBarAppearance(
    immersive: Boolean,
    fraction: Float,
) {
    if (!immersive) return
    val view = LocalView.current
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Hero 可见(顶部)始终浅色图标;滚到实色后按主题。
    val lightStatusBars = if (fraction < 0.5f) true else isDark
    // key 含 isDark:Hero 可见时切主题,fraction 不变但 isDark 变,需重跑 effect 同步图标色,
    // 否则 onDispose 会用旧 isDark 恢复,导致下一屏图标色短暂错乱。
    DisposableEffect(lightStatusBars, isDark) {
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = lightStatusBars
        }
        onDispose {
            // 恢复主题默认:深色主题浅图标,浅色主题深图标
            if (window != null) {
                WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = isDark
            }
        }
    }
}
