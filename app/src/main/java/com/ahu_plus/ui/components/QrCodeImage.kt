package com.ahu_plus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.ahu_plus.util.QrCodeBitmap

@Composable
fun rememberQrCodeImage(content: String, sizePx: Int): ImageBitmap? {
    var image by remember(content, sizePx) {
        mutableStateOf(QrCodeBitmap.cached(content, sizePx))
    }
    LaunchedEffect(content, sizePx) {
        if (image == null) image = QrCodeBitmap.createAsync(content, sizePx)
    }
    return image
}
