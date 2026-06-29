package com.yourname.ahu_plus.util

import okhttp3.FormBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 翼支付 / blade-pay 签名工具。
 *
 * 安大 ycard 的 `/blade-pay/pay` 端点要求所有请求按以下规则签名:
 * 1. 业务参数按 key 字典序排序
 * 2. 拼接 4 个签名字段 (APP_ID / NONCE / SIGN_TYPE / TIMESTAMP) + SECRET_KEY
 * 3. SHA-256 后转大写 hex 放 `SIGN` 字段
 *
 * 2026-06-29 实测 (testDebugUnitTest): 用本工具生成签名,服务端 (ycard.ahu.edu.cn)
 * 正常返回 orderid + passwordMap,完整三步流程可达确认支付阶段。
 */
object BladePaySigner {
    const val APP_ID = "56321"
    const val SECRET_KEY = "0osTIhce7uPvDKHz6aa67bhCukaKoYl4"
    const val SIGN_TYPE = "SHA256"

    private val TIME_FMT = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    private const val NONCE_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz"
    private val SECURE_RANDOM = SecureRandom()

    fun timestamp(): String = synchronized(TIME_FMT) {
        TIME_FMT.format(Date())
    }

    fun generateNonce(length: Int = 11): String = buildString(length) {
        repeat(length) { append(NONCE_CHARS[SECURE_RANDOM.nextInt(NONCE_CHARS.length)]) }
    }

    fun sha256HexUpper(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .uppercase()

    /**
     * 给业务参数追加 5 个签名字段,返回可直接 POST 的 [FormBody]。
     *
     * @param bizParams 业务参数,空字符串值会被过滤(避免破坏签名)
     */
    fun signedFormBody(bizParams: Map<String, String>): FormBody {
        val ts = timestamp()
        val nonce = generateNonce()

        // 签名字符串: 4 个签名头 + 按字典序排的业务字段 + SECRET_KEY
        val sorted = bizParams.filterValues { it.isNotEmpty() }.toSortedMap()
        val signSource = buildString {
            append("APP_ID=").append(APP_ID)
            append("&NONCE=").append(nonce)
            append("&SIGN_TYPE=").append(SIGN_TYPE)
            append("&TIMESTAMP=").append(ts)
            sorted.forEach { (k, v) -> append('&').append(k).append('=').append(v) }
            append("&SECRET_KEY=").append(SECRET_KEY)
        }
        val sign = sha256HexUpper(signSource)

        val fb = FormBody.Builder()
        bizParams.forEach { (k, v) -> fb.add(k, v) }
        fb.add("APP_ID", APP_ID)
        fb.add("TIMESTAMP", ts)
        fb.add("SIGN_TYPE", SIGN_TYPE)
        fb.add("NONCE", nonce)
        fb.add("SIGN", sign)
        return fb.build()
    }
}
