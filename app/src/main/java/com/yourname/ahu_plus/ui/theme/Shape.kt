package com.yourname.ahu_plus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material3 标准 Shape 槽位 — 接入 MaterialTheme.shapes 后，所有未指定 shape 的
 * Surface/Card/Button/TextField 会自动走这套 token。
 */
val AhuMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/**
 * 语义化业务 Shape token — 与 b30e3f6 commit 引入的 AhuUi.AhuShapes 完全一致，
 * 拆到 theme 包后保留 object 别名以便全项目渐进替换 import。
 *
 * 使用：
 * ```
 * Card(shape = AhuShapes.Card) { ... }
 * BottomSheet(shape = AhuShapes.BottomSheet)
 * ```
 */
object AhuShapes {
    /** 标准卡片/输入框/对话框次要容器 — 12dp */
    val Card        = RoundedCornerShape(12.dp)

    /** Hero/账单等强调卡片 — 16dp */
    val LargeCard   = RoundedCornerShape(16.dp)

    /** 方形图标盒 — 14dp */
    val IconBox     = RoundedCornerShape(14.dp)

    /** 标签/Pill chip — 999dp 圆角 */
    val Pill        = RoundedCornerShape(999.dp)

    /** Dialog — 20dp */
    val Dialog      = RoundedCornerShape(20.dp)

    /** BottomSheet — 仅顶部 20dp 圆角 */
    val BottomSheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /** BottomSheet 别名 */
    val Sheet: androidx.compose.ui.graphics.Shape get() = BottomSheet
}