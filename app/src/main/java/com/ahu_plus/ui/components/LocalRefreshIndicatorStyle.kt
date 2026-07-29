package com.ahu_plus.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.ahu_plus.data.local.RefreshIndicatorStyle

/**
 * 当前下拉刷新指示器样式,由 [com.ahu_plus.MainActivity] 收集 SessionManager flow 后在根部注入。
 *
 * [AhuPullToRefreshBox] 读取此值决定渲染哪种指示器,13 个调用点无需各自传参。
 * 默认值 [RefreshIndicatorStyle.DEFAULT] 保证 Preview / 未注入场景(如单元测试)不崩。
 */
val LocalRefreshIndicatorStyle = staticCompositionLocalOf { RefreshIndicatorStyle.DEFAULT }
