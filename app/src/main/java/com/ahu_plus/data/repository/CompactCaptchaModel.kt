package com.ahu_plus.data.repository

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fixed-shape int8-weight CNN ensemble for four-character adwmh captchas.
 * Activations remain float; per-output-channel scales dequantize weights while computing.
 */
internal class CompactCaptchaModel private constructor(
    private val charset: CharArray,
    private val centers: IntArray,
    private val networks: List<Network>,
) {
    fun recognize(rgbPixels: IntArray): String {
        require(rgbPixels.size == IMAGE_WIDTH * IMAGE_HEIGHT)
        return buildString(CHAR_COUNT) {
            repeat(CHAR_COUNT) { position ->
                val crop = makeCrop(rgbPixels, centers[position])
                val ensembleLogits = FloatArray(CLASS_COUNT)
                networks.forEach { network ->
                    val logits = network.infer(crop)
                    logits.indices.forEach { i -> ensembleLogits[i] += logits[i] }
                }
                var best = 0
                for (i in 1 until ensembleLogits.size) {
                    if (ensembleLogits[i] > ensembleLogits[best]) best = i
                }
                append(charset[best])
            }
        }
    }

    private fun makeCrop(pixels: IntArray, centerX: Int): FloatArray {
        val crop = FloatArray(IMAGE_HEIGHT * CROP_WIDTH * INPUT_CHANNELS)
        val left = centerX - CROP_WIDTH / 2
        var dst = 0
        repeat(IMAGE_HEIGHT) { y ->
            repeat(CROP_WIDTH) { cropX ->
                val sourceX = left + cropX
                val pixel = if (sourceX in 0 until IMAGE_WIDTH) {
                    pixels[y * IMAGE_WIDTH + sourceX]
                } else {
                    WHITE
                }
                crop[dst++] = ((pixel ushr 16) and 0xff) / 255f
                crop[dst++] = ((pixel ushr 8) and 0xff) / 255f
                crop[dst++] = (pixel and 0xff) / 255f
            }
        }
        return crop
    }

    private data class QuantizedLayer(
        val weights: ByteArray,
        val scales: FloatArray,
        val biases: FloatArray,
    )

    private data class Network(
        val conv1: QuantizedLayer,
        val conv2: QuantizedLayer,
        val dense: QuantizedLayer,
    ) {
        fun infer(input: FloatArray): FloatArray {
            val first = conv(
                input = input,
                inputHeight = IMAGE_HEIGHT,
                inputWidth = CROP_WIDTH,
                inputChannels = INPUT_CHANNELS,
                outputChannels = CONV1_CHANNELS,
                kernel = CONV1_KERNEL,
                stride = CONV1_STRIDE,
                layer = conv1,
            )
            val pooled = maxPool2x2(first, CONV1_HEIGHT, CONV1_WIDTH, CONV1_CHANNELS)
            val second = conv(
                input = pooled,
                inputHeight = POOL_HEIGHT,
                inputWidth = POOL_WIDTH,
                inputChannels = CONV1_CHANNELS,
                outputChannels = CONV2_CHANNELS,
                kernel = CONV2_KERNEL,
                stride = 1,
                layer = conv2,
            )
            return dense(second, dense)
        }

        private fun conv(
            input: FloatArray,
            inputHeight: Int,
            inputWidth: Int,
            inputChannels: Int,
            outputChannels: Int,
            kernel: Int,
            stride: Int,
            layer: QuantizedLayer,
        ): FloatArray {
            val outputHeight = (inputHeight - kernel) / stride + 1
            val outputWidth = (inputWidth - kernel) / stride + 1
            val output = FloatArray(outputHeight * outputWidth * outputChannels)
            var outputIndex = 0
            repeat(outputHeight) { y ->
                repeat(outputWidth) { x ->
                    repeat(outputChannels) { outputChannel ->
                        var sum = 0f
                        var weightIndex = outputChannel * kernel * kernel * inputChannels
                        repeat(kernel) { kernelY ->
                            val inputRow = ((y * stride + kernelY) * inputWidth + x * stride) * inputChannels
                            repeat(kernel) { kernelX ->
                                var inputIndex = inputRow + kernelX * inputChannels
                                repeat(inputChannels) {
                                    sum += input[inputIndex++] * layer.weights[weightIndex++].toInt()
                                }
                            }
                        }
                        val value = sum * layer.scales[outputChannel] + layer.biases[outputChannel]
                        output[outputIndex++] = if (value > 0f) value else 0f
                    }
                }
            }
            return output
        }

        private fun maxPool2x2(
            input: FloatArray,
            inputHeight: Int,
            inputWidth: Int,
            channels: Int,
        ): FloatArray {
            val outputHeight = inputHeight / 2
            val outputWidth = inputWidth / 2
            val output = FloatArray(outputHeight * outputWidth * channels)
            var outputIndex = 0
            repeat(outputHeight) { y ->
                repeat(outputWidth) { x ->
                    repeat(channels) { channel ->
                        var best = Float.NEGATIVE_INFINITY
                        repeat(2) { dy ->
                            repeat(2) { dx ->
                                val index = (((y * 2 + dy) * inputWidth + x * 2 + dx) * channels) + channel
                                if (input[index] > best) best = input[index]
                            }
                        }
                        output[outputIndex++] = best
                    }
                }
            }
            return output
        }

        private fun dense(input: FloatArray, layer: QuantizedLayer): FloatArray {
            val output = FloatArray(CLASS_COUNT)
            repeat(CLASS_COUNT) { outputChannel ->
                var sum = 0f
                var weightIndex = outputChannel * DENSE_INPUTS
                input.indices.forEach { inputIndex ->
                    sum += input[inputIndex] * layer.weights[weightIndex++].toInt()
                }
                output[outputChannel] = sum * layer.scales[outputChannel] + layer.biases[outputChannel]
            }
            return output
        }
    }

    companion object {
        const val IMAGE_HEIGHT = 40
        const val IMAGE_WIDTH = 100
        internal const val CROP_WIDTH = 48
        internal const val CHAR_COUNT = 4
        internal const val CLASS_COUNT = 36
        internal const val INPUT_CHANNELS = 3
        internal const val CONV1_CHANNELS = 24
        internal const val CONV1_KERNEL = 5
        internal const val CONV1_STRIDE = 2
        internal const val CONV1_HEIGHT = 18
        internal const val CONV1_WIDTH = 22
        internal const val POOL_HEIGHT = 9
        internal const val POOL_WIDTH = 11
        internal const val CONV2_CHANNELS = 48
        internal const val CONV2_KERNEL = 3
        internal const val CONV2_HEIGHT = 7
        internal const val CONV2_WIDTH = 9
        internal const val DENSE_INPUTS = CONV2_HEIGHT * CONV2_WIDTH * CONV2_CHANNELS

        private const val VERSION = 1
        private const val MAGIC = "AHUCAP1\u0000"
        private const val WHITE = -0x1
        private val EXPECTED_CENTERS = intArrayOf(12, 37, 62, 87)
        private const val CONV1_WEIGHTS = CONV1_CHANNELS * CONV1_KERNEL * CONV1_KERNEL * INPUT_CHANNELS
        private const val CONV2_WEIGHTS = CONV2_CHANNELS * CONV2_KERNEL * CONV2_KERNEL * CONV1_CHANNELS
        private const val DENSE_WEIGHTS = CLASS_COUNT * DENSE_INPUTS

        fun load(input: InputStream): CompactCaptchaModel = fromBytes(input.readBytes())

        internal fun fromBytes(bytes: ByteArray): CompactCaptchaModel {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(buffer.remaining() >= 8) { "模型文件过短" }
            val magic = ByteArray(8).also { buffer.get(it) }.toString(Charsets.US_ASCII)
            require(magic == MAGIC) { "模型 magic 不匹配" }
            require(buffer.int == VERSION) { "不支持的模型版本" }
            val modelCount = buffer.int
            require(modelCount in 1..5) { "模型数量非法" }
            require(buffer.int == IMAGE_HEIGHT && buffer.int == IMAGE_WIDTH) { "模型图片尺寸不匹配" }
            require(buffer.int == CROP_WIDTH && buffer.int == CHAR_COUNT) { "模型字符布局不匹配" }
            val centers = IntArray(CHAR_COUNT) { buffer.int }
            require(centers.contentEquals(EXPECTED_CENTERS)) { "模型字符中心不匹配" }
            val charsetLength = buffer.int
            require(charsetLength == CLASS_COUNT) { "模型字符集长度不匹配" }
            val charset = ByteArray(charsetLength).also { buffer.get(it) }
                .toString(Charsets.US_ASCII)
                .toCharArray()
            val networks = List(modelCount) {
                Network(
                    conv1 = buffer.readLayer(CONV1_WEIGHTS, CONV1_CHANNELS),
                    conv2 = buffer.readLayer(CONV2_WEIGHTS, CONV2_CHANNELS),
                    dense = buffer.readLayer(DENSE_WEIGHTS, CLASS_COUNT),
                )
            }
            require(!buffer.hasRemaining()) { "模型文件包含未识别数据" }
            return CompactCaptchaModel(charset, centers, networks)
        }

        internal fun expectedByteSize(modelCount: Int): Int {
            val header = 8 + 7 * Int.SIZE_BYTES + CHAR_COUNT * Int.SIZE_BYTES + CLASS_COUNT
            val perModel = CONV1_WEIGHTS + 2 * CONV1_CHANNELS * Float.SIZE_BYTES +
                CONV2_WEIGHTS + 2 * CONV2_CHANNELS * Float.SIZE_BYTES +
                DENSE_WEIGHTS + 2 * CLASS_COUNT * Float.SIZE_BYTES
            return header + modelCount * perModel
        }

        private fun ByteBuffer.readLayer(weightCount: Int, outputChannels: Int): QuantizedLayer {
            require(remaining() >= weightCount + outputChannels * 2 * Float.SIZE_BYTES) {
                "模型权重不完整"
            }
            val weights = ByteArray(weightCount).also { get(it) }
            val scales = FloatArray(outputChannels) { float }
            val biases = FloatArray(outputChannels) { float }
            require(scales.all { it.isFinite() && it >= 0f } && biases.all { it.isFinite() }) {
                "模型权重包含非法数值"
            }
            return QuantizedLayer(weights, scales, biases)
        }
    }
}
