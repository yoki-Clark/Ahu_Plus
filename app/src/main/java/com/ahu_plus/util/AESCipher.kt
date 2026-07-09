package com.ahu_plus.util

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-CBC 加密工具。
 *
 * 移植自 Samueli924/chaoxing 的 api/cipher.py，
 * 用于超星学习通登录密码加密。
 *
 * 参数：
 *  - 算法: AES/CBC/PKCS5Padding
 *  - Key:  "u2oh6Vu^HWe4_AES" (16 字节)
 *  - IV:   同 Key
 *  - 输出: Base64 (NO_WRAP)
 */
object AESCipher {

    private const val SECRET = "u2oh6Vu^HWe4_AES"

    private val keyBytes = SECRET.toByteArray(Charsets.UTF_8)
    private val ivSpec = IvParameterSpec(keyBytes)
    private val keySpec = SecretKeySpec(keyBytes, "AES")

    /**
     * AES-CBC 加密 + Base64 编码。
     *
     * 与 Python 端 `AESCipher.encrypt()` 输出完全一致。
     */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
}
