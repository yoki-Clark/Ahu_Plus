package com.ahu_plus.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 5 组品牌渐变 + 3 组玻璃态补色，统一封装为 Brush 字段。
 *
 * 使用：
 * ```
 * Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
 *     Box(Modifier.background(AhuGradient.Blue.brush)) { ... }
 * }
 * // 或直接传给 AhuHeroCard：
 * AhuHeroCard(gradient = AhuGradient.Teal.brush) { ... }
 * ```
 *
 * 玻璃态（Glass）三色用于浮层、Popup、Dialog 内容，alpha=0.85 → 0.55 的渐隐
 * 让背景"渗透"上来，营造 Material You 的层次感。
 */
object AhuGradient {

    object Blue {
        val brush: Brush = Brush.linearGradient(
            listOf(AhuGradientBlueStart, AhuGradientBlueEnd),
        )
    }

    object Teal {
        val brush: Brush = Brush.linearGradient(
            listOf(AhuGradientTealStart, AhuGradientTealEnd),
        )
    }

    object Orange {
        val brush: Brush = Brush.linearGradient(
            listOf(AhuGradientOrangeStart, AhuGradientOrangeEnd),
        )
    }

    object Violet {
        val brush: Brush = Brush.linearGradient(
            listOf(AhuGradientVioletStart, AhuGradientVioletEnd),
        )
    }

    object Green {
        val brush: Brush = Brush.linearGradient(
            listOf(AhuGradientGreenStart, AhuGradientGreenEnd),
        )
    }

    // ── 玻璃态补色（alpha 渐隐，半透质感）──
    object GlassBlue {
        val brush: Brush = Brush.linearGradient(
            listOf(Color(0xD91F5AA6), Color(0x8C4B63A6)),
        )
    }

    object GlassTeal {
        val brush: Brush = Brush.linearGradient(
            listOf(Color(0xD9168C80), Color(0x8C2A9D8F)),
        )
    }

    object GlassViolet {
        val brush: Brush = Brush.linearGradient(
            listOf(Color(0xD97567C8), Color(0x8C1C2048)),
        )
    }

    /**
     * 由任意 tint 色派生「实心双色渐变」底 - 用于入口图标盒（AppHub、收藏坞、设置入口）。
     *
     * 规则：tint 向白提亮 12% 作起始色、向黑加深 8% 作结束色，营造立体光泽。
     * 统一收口之前 AppHubIcon 即时 lerp 的写法（批次一项42）。
     */
    fun forTint(color: Color): Brush = Brush.linearGradient(
        listOf(
            lerp(color, Color.White, 0.12f),
            lerp(color, Color.Black, 0.08f),
        ),
    )

    /**
     * 主色按钮渐变 - 用于主要按钮/强调操作，与 Hero 渐变呼应（批次一项13）。
     * 次要按钮仍用纯色以保持克制。
     */
    object Button {
        val blue: Brush = Brush.linearGradient(
            listOf(AhuGradientBlueStart, AhuGradientBlueEnd),
        )
        val teal: Brush = Brush.linearGradient(
            listOf(AhuGradientTealStart, AhuGradientTealEnd),
        )
        val orange: Brush = Brush.linearGradient(
            listOf(AhuGradientOrangeStart, AhuGradientOrangeEnd),
        )
        val violet: Brush = Brush.linearGradient(
            listOf(AhuGradientVioletStart, AhuGradientVioletEnd),
        )
        val green: Brush = Brush.linearGradient(
            listOf(AhuGradientGreenStart, AhuGradientGreenEnd),
        )
    }
}