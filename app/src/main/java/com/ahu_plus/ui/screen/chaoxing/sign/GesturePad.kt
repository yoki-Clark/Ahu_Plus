package com.ahu_plus.ui.screen.chaoxing.sign

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.tooling.preview.Preview
import com.ahu_plus.ui.theme.AhuPlusTheme
import com.ahu_plus.ui.theme.AhuToneColors
import kotlin.math.hypot

/**
 * 九宫格手势绘制板(2026-06-24)。
 *
 * 3×3 圆点,手指划过的点按经过顺序记入路径并连线高亮,抬手后通过 [onPathChange]
 * 回调输出数字串(左上→右下编号 1-9,与超星手势码格式对齐)。
 *
 * 命中判定:手指落在某点 [hitRadiusFactor]×格距 半径内即记入(不重复)。
 */
@Composable
fun GesturePad(
    onPathChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color = Color.Unspecified,
    activeColor: Color = Color.Unspecified,
) {
    // 默认色走深浅双档 token(默认参数不能调用 @Composable,故在体内 fallback)
    val resolvedDotColor = if (dotColor != Color.Unspecified) dotColor else AhuToneColors.GestureDotGray.current
    val resolvedActiveColor = if (activeColor != Color.Unspecified) activeColor else AhuToneColors.GestureActiveBlue.current
    // 当前已选点的编号路径(1-9)
    var path by remember { mutableStateOf<List<Int>>(emptyList()) }
    // 当前手指位置,用于画"正在连向手指"的活动线段
    var dragPoint by remember { mutableStateOf<Offset?>(null) }
    // Canvas 实际尺寸,变化时才重新计算九点坐标(避免每次拖动/绘制都重新分配)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val centers = remember(canvasSize) {
        dotCenters(canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(8.dp)
            .semantics {
                contentDescription = "九宫格手势签到板,请由他人协助按顺序点选正确的手势图案"
                stateDescription = if (path.isEmpty()) {
                    "尚未选择任何点"
                } else {
                    "已选择 ${path.size} 个点,顺序为 ${path.joinToString("-") { (it + 1).toString() }}"
                }
                customActions = listOf(
                    CustomAccessibilityAction("清除已选手势") {
                        path = emptyList()
                        onPathChange("")
                        true
                    },
                )
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .onSizeChanged { canvasSize = it }
                .pointerInput(centers) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            path = emptyList()
                            val cell = size.width / 3f
                            hitIndex(offset, centers, cell * 0.33f)?.let { path = listOf(it) }
                            dragPoint = offset
                        },
                        onDrag = { change, _ ->
                            val cell = size.width / 3f
                            dragPoint = change.position
                            hitIndex(change.position, centers, cell * 0.33f)?.let { idx ->
                                if (idx !in path) path = path + idx
                            }
                        },
                        onDragEnd = {
                            dragPoint = null
                            onPathChange(path.joinToString("") { (it + 1).toString() })
                        },
                        onDragCancel = {
                            dragPoint = null
                            onPathChange(path.joinToString("") { (it + 1).toString() })
                        },
                    )
                },
        ) {
            val cell = size.width / 3f
            val dotR = cell * 0.12f
            val ringR = cell * 0.30f

            // 连线(已选点之间)
            for (i in 0 until path.size - 1) {
                drawLine(
                    color = resolvedActiveColor,
                    start = centers[path[i]],
                    end = centers[path[i + 1]],
                    strokeWidth = 8f,
                )
            }
            // 正在连向手指的活动线段
            val last = path.lastOrNull()
            if (last != null && dragPoint != null) {
                drawLine(
                    color = resolvedActiveColor.copy(alpha = 0.5f),
                    start = centers[last],
                    end = dragPoint!!,
                    strokeWidth = 8f,
                )
            }
            // 9 个圆点:已选的画高亮环
            centers.forEachIndexed { idx, c ->
                val selected = idx in path
                drawCircle(
                    color = if (selected) resolvedActiveColor else resolvedDotColor,
                    radius = dotR,
                    center = c,
                )
                if (selected) {
                    drawCircle(
                        color = resolvedActiveColor,
                        radius = ringR,
                        center = c,
                        style = Stroke(width = 4f),
                    )
                }
            }
        }
    }
}

/** 9 个点的中心坐标(行优先,index 0..8 → 编号 1..9) */
private fun dotCenters(w: Float, h: Float): List<Offset> {
    val cellW = w / 3f
    val cellH = h / 3f
    return (0 until 9).map { i ->
        val row = i / 3
        val col = i % 3
        Offset(cellW * col + cellW / 2f, cellH * row + cellH / 2f)
    }
}

/** 返回落在某点命中半径内的 index,无则 null */
private fun hitIndex(p: Offset, centers: List<Offset>, radius: Float): Int? {
    centers.forEachIndexed { idx, c ->
        if (hypot(p.x - c.x, p.y - c.y) <= radius) return idx
    }
    return null
}

@Preview(name = "Gesture Pad", showBackground = true)
@Composable
private fun PreviewGesturePad() {
    AhuPlusTheme {
        GesturePad(
            onPathChange = {},
            modifier = Modifier.padding(24.dp)
        )
    }
}
