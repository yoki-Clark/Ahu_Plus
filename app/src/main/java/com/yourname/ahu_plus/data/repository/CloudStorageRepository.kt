package com.yourname.ahu_plus.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 腾讯云对象存储（COS）Repository。
 *
 * 基于 OkHttp + COS REST API 实现（Android 兼容）。
 * 配置从 assets/cos_config.properties 读取。
 */
class CloudStorageRepository(private val context: Context) {

    private val secretId: String
    private val secretKey: String
    private val bucket: String
    private val region: String

    init {
        val props = Properties()
        context.assets.open("cos_config.properties").use { props.load(it) }
        secretId = props.getProperty("COS_SECRET_ID")
            ?: throw IllegalStateException("COS_SECRET_ID 未配置")
        secretKey = props.getProperty("COS_SECRET_KEY")
            ?: throw IllegalStateException("COS_SECRET_KEY 未配置")
        bucket = props.getProperty("COS_BUCKET")
            ?: throw IllegalStateException("COS_BUCKET 未配置")
        region = props.getProperty("COS_REGION")
            ?: throw IllegalStateException("COS_REGION 未配置")
    }

    private val host: String get() = "$bucket.cos.$region.myqcloud.com"
    private val baseUrl: String get() = "https://$host"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── 上传 ──────────────────────────────────────────────

    suspend fun uploadString(
        cosKey: String,
        content: String,
        contentType: String = "application/json"
    ): Unit = withContext(Dispatchers.IO) {
        val body = content.toByteArray(Charsets.UTF_8)
        val url = "$baseUrl/${encodeKey(cosKey)}"
        val authorization = buildAuthorization("PUT", "/$cosKey", contentType)

        val request = Request.Builder()
            .url(url)
            .put(body.toRequestBody(contentType.toMediaType()))
            .header("Host", host)
            .header("Content-Type", contentType)
            .header("Authorization", authorization)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw Exception("COS upload failed: HTTP ${response.code} - $errorBody")
            }
        }
        Log.d(TAG, "上传成功: $cosKey (${body.size} bytes)")
    }

    suspend fun uploadFile(cosKey: String, localFilePath: String): Unit =
        withContext(Dispatchers.IO) {
            val file = File(localFilePath)
            val body = file.readBytes()
            val contentType = "application/octet-stream"
            val url = "$baseUrl/${encodeKey(cosKey)}"
            val authorization = buildAuthorization("PUT", "/$cosKey", contentType)

            val request = Request.Builder()
                .url(url)
                .put(body.toRequestBody(contentType.toMediaType()))
                .header("Host", host)
                .header("Content-Type", contentType)
                .header("Authorization", authorization)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("COS upload failed: HTTP ${response.code}")
                }
            }
            Log.d(TAG, "上传文件成功: $cosKey")
        }

    // ── 下载 ──────────────────────────────────────────────

    suspend fun downloadAsString(cosKey: String): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl/${encodeKey(cosKey)}"
        val authorization = buildAuthorization("GET", "/$cosKey", "")

        val request = Request.Builder()
            .url(url)
            .header("Host", host)
            .header("Authorization", authorization)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("COS download failed: HTTP ${response.code}")
            }
            response.body?.string() ?: throw Exception("COS download: empty body")
        }
    }

    suspend fun downloadToFile(cosKey: String, localFilePath: String): File =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/${encodeKey(cosKey)}"
            val authorization = buildAuthorization("GET", "/$cosKey", "")

            val request = Request.Builder()
                .url(url)
                .header("Host", host)
                .header("Authorization", authorization)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("COS download failed: HTTP ${response.code}")
                }
                val bytes = response.body?.bytes() ?: throw Exception("COS download: empty body")
                File(localFilePath).apply { writeBytes(bytes) }
            }
        }

    // ── 删除 ──────────────────────────────────────────────

    suspend fun deleteFile(cosKey: String): Unit = withContext(Dispatchers.IO) {
        val url = "$baseUrl/${encodeKey(cosKey)}"
        val authorization = buildAuthorization("DELETE", "/$cosKey", "")

        val request = Request.Builder()
            .url(url)
            .delete()
            .header("Host", host)
            .header("Authorization", authorization)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) {
                throw Exception("COS delete failed: HTTP ${response.code}")
            }
        }
        Log.d(TAG, "删除成功: $cosKey")
    }

    // ── URL ───────────────────────────────────────────────

    fun getFileUrl(cosKey: String): String = "$baseUrl/${encodeKey(cosKey)}"

    fun getSignedUrl(cosKey: String, expiredSeconds: Long = 3600L): String {
        val now = System.currentTimeMillis() / 1000
        val signTime = "$now;${now + expiredSeconds}"
        val signKey = hmacSha1(secretKey, signTime)
        val httpString = "get\n/$cosKey\n\nhost=$host\n"
        val httpStringSha1 = sha1Hex(httpString)
        val strToSign = "sha1\n$signTime\n$httpStringSha1\n"
        val signature = hmacSha1(signKey, strToSign)
        return "$baseUrl/${encodeKey(cosKey)}?sign-time=${urlEncode(signTime)}&" +
            "sign=${urlEncode(signature)}&" +
            "key-time=${urlEncode(signTime)}"
    }

    fun shutdown() { /* OkHttp 自动管理连接池 */ }

    // ── COS V1 签名 ──────────────────────────────────────

    /**
     * 构建 COS Authorization 头。
     *
     * 签名流程（与 Python SDK cos_auth.py 一致）：
     * 1. HttpString = "{method}\n{path}\n{params}\n{headers}\n"
     * 2. StringToSign = "sha1\n{signTime}\n{sha1(HttpString)}\n"
     * 3. SignKey = HMAC-SHA1(SecretKey, SignTime)
     * 4. Signature = HMAC-SHA1(SignKey, StringToSign)
     */
    private fun buildAuthorization(
        method: String,
        path: String,
        contentType: String
    ): String {
        val now = System.currentTimeMillis() / 1000
        val signTime = "${now - 60};${now + 3600}"

        // 构建 HttpString（headers 只包含 host）
        val headers = "host=$host"
        val httpString = "${method.lowercase()}\n$path\n\n$headers\n"

        // StringToSign
        val httpStringSha1 = sha1Hex(httpString)
        val strToSign = "sha1\n$signTime\n$httpStringSha1\n"

        // 签名
        val signKey = hmacSha1(secretKey, signTime)
        val signature = hmacSha1(signKey, strToSign)

        return "q-sign-algorithm=sha1" +
            "&q-ak=$secretId" +
            "&q-sign-time=$signTime" +
            "&q-key-time=$signTime" +
            "&q-header-list=host" +
            "&q-url-param-list=" +
            "&q-signature=$signature"
    }

    private fun hmacSha1(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun sha1Hex(data: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun encodeKey(key: String): String {
        return key.split("/").joinToString("/") { urlEncode(it) }
    }

    private fun urlEncode(s: String): String {
        return URLEncoder.encode(s, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    companion object {
        private const val TAG = "CloudStorage"
    }
}
