package com.ahu_plus.ui.screen.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 头像圆形裁剪页。
 *
 * 从相册选图后进入:双指缩放 + 单指拖动调整取景,圆形窗口实时预览。确认时按当前
 * 变换从原图裁出中心正方形区域,缩放到 512x512 交回调([onConfirm])。
 *
 * 取景窗口为容器内切圆(留 8% 边),遮罩用 Path EvenOdd(圆外暗色,圆内透明露图)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarCropScreen(
    imageUri: Uri,
    onConfirm: (Bitmap) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffsetX by remember { mutableFloatStateOf(0f) }
    var userOffsetY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf<IntSize?>(null) }

    LaunchedEffect(imageUri) {
        loading = true
        userScale = 1f
        userOffsetX = 0f
        userOffsetY = 0f
        bitmap = withContext(Dispatchers.IO) { decodeSampledBitmap(context, imageUri, 1080) }
        loading = false
    }

    fun doCrop() {
        val bmp = bitmap ?: return
        val size = containerSize ?: return
        val W = size.width.toFloat()
        val H = size.height.toFloat()
        val cx = W / 2f
        val cy = H / 2f
        val r = (min(W, H) / 2f) * 0.92f
        val baseScale = min(W, H) / max(bmp.width, bmp.height).toFloat()
        val totalScale = baseScale * userScale
        val left = (W - bmp.width * totalScale) / 2f + userOffsetX
        val top = (H - bmp.height * totalScale) / 2f + userOffsetY
        // 容器坐标 (cx-r, cy-r) -> 原图像素坐标
        var cropX = ((cx - r) - left) / totalScale
        var cropY = ((cy - r) - top) / totalScale
        var side = (2f * r) / totalScale
        // clamp 到原图范围内
        cropX = cropX.coerceIn(0f, (bmp.width - side).toFloat().coerceAtLeast(0f))
        cropY = cropY.coerceIn(0f, (bmp.height - side).toFloat().coerceAtLeast(0f))
        val maxSide = min(bmp.width - cropX, bmp.height - cropY).toFloat()
        side = min(side, maxSide).coerceAtLeast(1f)
        if (side.toInt() < 1) return
        val cropped = Bitmap.createBitmap(
            bmp, cropX.toInt(), cropY.toInt(), side.toInt(), side.toInt()
        )
        val scaled = Bitmap.createScaledBitmap(cropped, 512, 512, true)
        if (cropped != scaled) cropped.recycle()
        onConfirm(scaled)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("裁剪头像") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { doCrop() }, enabled = bitmap != null && !loading) {
                        Text("完成")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                ),
            )
        },
        containerColor = Color.Black,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (loading || bitmap == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val W = constraints.maxWidth.toFloat()
                    val H = constraints.maxHeight.toFloat()
                    val cx = W / 2f
                    val cy = H / 2f
                    val r = (min(W, H) / 2f) * 0.92f
                    val bmp = bitmap!!
                    val baseScale = min(W, H) / max(bmp.width, bmp.height).toFloat()
                    val totalScale = baseScale * userScale
                    val iw = bmp.width * totalScale
                    val ih = bmp.height * totalScale
                    val left = (W - iw) / 2f + userOffsetX
                    val top = (H - ih) / 2f + userOffsetY

                    LaunchedEffect(W, H) { containerSize = IntSize(W.toInt(), H.toInt()) }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    userScale = (userScale * zoom).coerceIn(0.5f, 6f)
                                    userOffsetX += pan.x
                                    userOffsetY += pan.y
                                }
                            }
                    ) {
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = IntOffset(left.toInt(), top.toInt()),
                            dstSize = IntSize(iw.toInt().coerceAtLeast(1), ih.toInt().coerceAtLeast(1)),
                        )
                        // 圆外暗色遮罩(EvenOdd:矩形减去圆),圆内透明露出图片
                        val maskPath = Path().apply {
                            addRect(Rect(Offset.Zero, Offset(size.width, size.height)))
                            addOval(Rect(Offset(cx - r, cy - r), Offset(cx + r, cy + r)))
                            fillType = PathFillType.EvenOdd
                        }
                        drawPath(maskPath, Color.Black.copy(alpha = 0.55f))
                        drawCircle(
                            color = Color.White,
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2f),
                        )
                    }
                }
            }
            if (!loading && bitmap != null) {
                Text(
                    text = "双指缩放、单指拖动调整取景",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * 解码相册图片,按 [reqMax] 采样降分辨率避免 OOM。
 * 返回原始方向的 Bitmap(不旋转)。
 */
private fun decodeSampledBitmap(context: android.content.Context, uri: Uri, reqMax: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (w / 2 >= reqMax || h / 2 >= reqMax) {
            w /= 2
            h /= 2
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    } catch (e: Exception) {
        null
    }
}
