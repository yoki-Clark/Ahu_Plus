package com.ahu_plus.ui.screen.market

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahu_plus.ui.theme.AhuShapes
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun MarketQrScannerDialog(
    onDismiss: () -> Unit,
    onDecoded: (String) -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = AhuShapes.Dialog,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "扫描集市身份二维码",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
                if (permissionGranted) {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                    ) {
                        QrCameraPreview(onDecoded = onDecoded)
                        Text(
                            text = "将电脑上的二维码放入画面",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.58f))
                                .padding(12.dp),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("需要相机权限才能扫描二维码，也可以返回后手动粘贴身份字段。")
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("允许相机权限")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCameraPreview(onDecoded: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDecoded by rememberUpdatedState(onDecoded)
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )

    DisposableEffect(lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        val decoded = AtomicBoolean(false)
        val disposed = AtomicBoolean(false)

        cameraProviderFuture.addListener({
            runCatching {
                if (disposed.get()) return@runCatching
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(analyzerExecutor) { image ->
                            try {
                                if (!decoded.get()) {
                                    runCatching { decodeQr(image) }.getOrNull()?.let { result ->
                                        if (decoded.compareAndSet(false, true)) {
                                            previewView.post { currentOnDecoded(result) }
                                        }
                                    }
                                }
                            } finally {
                                image.close()
                            }
                        }
                    }
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed.set(true)
            analysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
            analyzerExecutor.shutdown()
        }
    }
}

private fun decodeQr(image: androidx.camera.core.ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer.duplicate()
    val bytes = ByteArray(image.width * image.height)
    for (row in 0 until image.height) {
        val rowStart = row * plane.rowStride
        for (column in 0 until image.width) {
            bytes[row * image.width + column] = buffer.get(rowStart + column * plane.pixelStride)
        }
    }
    var source: LuminanceSource = PlanarYUVLuminanceSource(
        bytes,
        image.width,
        image.height,
        0,
        0,
        image.width,
        image.height,
        false,
    )
    val counterClockwiseRotations =
        (360 - ((image.imageInfo.rotationDegrees % 360) + 360) % 360) % 360 / 90
    repeat(counterClockwiseRotations) {
        if (source.isRotateSupported) source = source.rotateCounterClockwise()
    }
    val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }
    return runCatching {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    }.getOrNull().also { reader.reset() }
}
