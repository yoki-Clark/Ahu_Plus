package com.ahu_plus.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ahu_plus.data.diagnostic.SafeLog as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android image decoding wrapper for the compact, fully local captcha model. */
class AdwmhCaptchaRecognizer(
    context: Context,
) {
    private val model: CompactCaptchaModel?

    val isAvailable: Boolean
        get() = model != null

    init {
        model = try {
            context.assets.open(ASSET_MODEL).use(CompactCaptchaModel::load)
                .also { Log.i(TAG, "本地验证码模型加载成功") }
        } catch (e: Throwable) {
            Log.w(TAG, "本地验证码模型不可用，将回退手动输入: ${e.javaClass.simpleName}")
            null
        }
    }

    suspend fun recognize(imageBytes: ByteArray): String? = withContext(Dispatchers.Default) {
        val localModel = model ?: return@withContext null
        val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return@withContext null
        val scaled = if (decoded.width == CompactCaptchaModel.IMAGE_WIDTH &&
            decoded.height == CompactCaptchaModel.IMAGE_HEIGHT
        ) {
            decoded
        } else {
            Bitmap.createScaledBitmap(
                decoded,
                CompactCaptchaModel.IMAGE_WIDTH,
                CompactCaptchaModel.IMAGE_HEIGHT,
                true,
            ).also { decoded.recycle() }
        }
        try {
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            localModel.recognize(pixels)
        } catch (e: Throwable) {
            Log.w(TAG, "本地验证码识别失败: ${e.javaClass.simpleName}")
            null
        } finally {
            scaled.recycle()
        }
    }

    companion object {
        private const val TAG = "AdwmhCaptchaRecognizer"
        private const val ASSET_MODEL = "captcha_compact.bin"
    }
}
