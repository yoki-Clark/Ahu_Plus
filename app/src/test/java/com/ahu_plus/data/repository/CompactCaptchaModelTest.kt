package com.ahu_plus.data.repository

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CompactCaptchaModelTest {
    @Test
    fun `deployed model matches Python export parity fixture`() {
        val asset = listOf(
            File("src/main/assets/captcha_compact.bin"),
            File("app/src/main/assets/captcha_compact.bin"),
        ).firstOrNull(File::isFile) ?: error("captcha_compact.bin not found")
        val model = asset.inputStream().use(CompactCaptchaModel::load)
        val pixels = IntArray(CompactCaptchaModel.IMAGE_WIDTH * CompactCaptchaModel.IMAGE_HEIGHT) { index ->
            val x = index % CompactCaptchaModel.IMAGE_WIDTH
            val y = index / CompactCaptchaModel.IMAGE_WIDTH
            val red = (x * 17 + y * 3) and 0xff
            val green = (x * 5 + y * 11) and 0xff
            val blue = (x * 13 + y * 7) and 0xff
            (0xff shl 24) or (red shl 16) or (green shl 8) or blue
        }

        assertEquals("BQWW", model.recognize(pixels))
    }

    @Test
    fun `loads fixed binary format and runs deterministic inference`() {
        val bytes = zeroModelBytes()

        val model = CompactCaptchaModel.fromBytes(bytes)
        val result = model.recognize(
            IntArray(CompactCaptchaModel.IMAGE_WIDTH * CompactCaptchaModel.IMAGE_HEIGHT) { -1 },
        )

        assertEquals("AAAA", result)
    }

    @Test
    fun `rejects a model with invalid magic`() {
        val bytes = zeroModelBytes().also { it[0] = 'X'.code.toByte() }

        try {
            CompactCaptchaModel.fromBytes(bytes)
            fail("invalid magic should fail")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun zeroModelBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(CompactCaptchaModel.expectedByteSize(1))
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("AHUCAP1\u0000".toByteArray(Charsets.US_ASCII))
        buffer.putInt(1)
        buffer.putInt(1)
        buffer.putInt(CompactCaptchaModel.IMAGE_HEIGHT)
        buffer.putInt(CompactCaptchaModel.IMAGE_WIDTH)
        buffer.putInt(CompactCaptchaModel.CROP_WIDTH)
        buffer.putInt(CompactCaptchaModel.CHAR_COUNT)
        intArrayOf(12, 37, 62, 87).forEach(buffer::putInt)
        buffer.putInt(CompactCaptchaModel.CLASS_COUNT)
        buffer.put("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toByteArray(Charsets.US_ASCII))

        putLayer(
            buffer,
            CompactCaptchaModel.CONV1_CHANNELS * CompactCaptchaModel.CONV1_KERNEL *
                CompactCaptchaModel.CONV1_KERNEL * CompactCaptchaModel.INPUT_CHANNELS,
            CompactCaptchaModel.CONV1_CHANNELS,
        )
        putLayer(
            buffer,
            CompactCaptchaModel.CONV2_CHANNELS * CompactCaptchaModel.CONV2_KERNEL *
                CompactCaptchaModel.CONV2_KERNEL * CompactCaptchaModel.CONV1_CHANNELS,
            CompactCaptchaModel.CONV2_CHANNELS,
        )
        putLayer(
            buffer,
            CompactCaptchaModel.CLASS_COUNT * CompactCaptchaModel.DENSE_INPUTS,
            CompactCaptchaModel.CLASS_COUNT,
        )
        return buffer.array()
    }

    private fun putLayer(buffer: ByteBuffer, weights: Int, outputs: Int) {
        buffer.put(ByteArray(weights))
        repeat(outputs) { buffer.putFloat(1f) }
        repeat(outputs) { buffer.putFloat(0f) }
    }
}
