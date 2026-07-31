package com.ahu_plus.data.repository

import android.content.Context

/**
 * 智慧安大验证码本地识别器(占位实现)。
 *
 * 基于 TFLite 模型(captcha_crnn.tflite + captcha_charset.json)的设备端 CRNN 识别器。
 * 当前为 stub 实现,[isAvailable] 恒返回 false,[recognize] 恒返回 null,
 * [AdwmhCardRepository.autoLogin] 会自动回退到手动验证码输入流程(AdwmhCaptchaDialog)。
 *
 * 后续接入真实 TFLite 模型时,替换 [isAvailable] 和 [recognize] 的实现即可,
 * 不需要修改 [AdwmhCardRepository]。
 *
 * @param context Application context(预留,用于后续从 assets 加载 TFLite 模型)
 */
class AdwmhCaptchaRecognizer(context: Context) {
    /** TFLite 模型是否已加载且可用。当前 stub 恒返回 false。 */
    val isAvailable: Boolean = false

    /**
     * 识别验证码图片。
     * @param bytes 验证码图片字节流(来自 GET /remind/authcode)
     * @return 识别结果文本,失败返回 null
     */
    fun recognize(bytes: ByteArray): String? = null
}
