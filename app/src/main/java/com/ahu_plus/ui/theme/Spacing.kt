package com.ahu_plus.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全局间距 token — 8 档阶梯。
 *
 * 约定：
 * - `ScreenHorizontal` / `Card` / `Section` / `CardGap` 是页面级语义 token
 * - `xs` / `sm` / `md` / `lg` / `xl` 是组件内 4dp 递增阶梯
 */
object AhuSpacing {
    /** 屏幕左右内边距 — 16dp */
    val ScreenHorizontal = 16.dp

    /** Card 内部 padding — 16dp */
    val Card    = 16.dp

    /** Section 上下间距 — 16dp */
    val Section = 16.dp

    /** Card 与 Card 之间的垂直 gap — 12dp */
    val CardGap = 12.dp

    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}