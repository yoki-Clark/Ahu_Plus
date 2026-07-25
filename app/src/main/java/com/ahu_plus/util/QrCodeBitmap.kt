package com.ahu_plus.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QrCodeBitmap {
    private data class CacheKey(val content: String, val sizePx: Int)

    private const val MAX_CACHE_ENTRIES = 2
    private var cacheGeneration = 0L
    private val cache = object : LinkedHashMap<CacheKey, ImageBitmap>(MAX_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, ImageBitmap>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    fun cached(content: String, sizePx: Int): ImageBitmap? = synchronized(cache) {
        cache[CacheKey(content, sizePx)]
    }

    suspend fun createAsync(content: String, sizePx: Int): ImageBitmap = withContext(Dispatchers.Default) {
        cached(content, sizePx) ?: create(content, sizePx)
    }

    fun create(content: String, sizePx: Int): ImageBitmap {
        val key = CacheKey(content, sizePx)
        val generationAtStart = synchronized(cache) {
            cache[key]?.let { return it }
            cacheGeneration
        }
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val rowOffset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[rowOffset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        return bitmap.asImageBitmap().also { image ->
            synchronized(cache) {
                if (generationAtStart == cacheGeneration) cache[key] = image
            }
        }
    }

    fun clear() {
        synchronized(cache) {
            cacheGeneration++
            cache.clear()
        }
    }
}
