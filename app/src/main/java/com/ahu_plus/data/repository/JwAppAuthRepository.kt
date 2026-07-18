package com.ahu_plus.data.repository

import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.jwapp.JwAppAccount
import com.ahu_plus.data.network.SecureHttpClientFactory
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.decodeBase64
import java.io.IOException
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

sealed interface JwAppLoginResult {
    data object Success : JwAppLoginResult
    data class ChooseAccount(val cid: String, val accounts: List<JwAppAccount>) : JwAppLoginResult
}

class JwAppAuthRequiredException : IOException("教务平台登录已过期")

class JwAppAuthRepository(
    private val sessionManager: SessionManager,
) {
    private val client = SecureHttpClientFactory.create(
        trustAll = true,
        connectTimeoutSec = 15,
        readTimeoutSec = 20,
    )

    @Volatile
    private var token: String? = sessionManager.getJwAppToken()

    fun restoreSession() {
        token = sessionManager.getJwAppToken()
    }

    fun savedUsername(): String? = sessionManager.getJwAppUsername()

    fun savedPassword(): String? = sessionManager.getJwAppPassword()

    fun isLoggedIn(): Boolean {
        val current = token ?: sessionManager.getJwAppToken().also { token = it }
        return !current.isNullOrBlank() && !isExpired(current)
    }

    suspend fun login(username: String, password: String): Result<JwAppLoginResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encrypted = encryptPassword(password)
                val url = "$BASE/token/password/passwordLogin".toHttpUrl().newBuilder()
                    .addQueryParameter("username", username)
                    .addQueryParameter("password", encrypted)
                    .addQueryParameter("appId", "APP_ID")
                    .addQueryParameter("deviceId", "DEVICE_ID")
                    .addQueryParameter("osType", "OS_TYPE")
                    .addQueryParameter("geo", "GEO")
                    .build()
                val json = executeLoginRequest(url.toString())
                when (json.get("code")?.asInt) {
                    0 -> {
                        val idToken = json.getAsJsonObject("data")?.get("idToken")?.asString
                            ?.takeIf { it.isNotBlank() }
                            ?: throw IOException("教务平台未返回登录令牌")
                        token = idToken
                        sessionManager.saveJwAppSession(username, password, idToken)
                        JwAppLoginResult.Success
                    }
                    100000 -> {
                        val data = json.getAsJsonObject("data")
                            ?: throw IOException("教务平台未返回可选账号")
                        val cid = data.get("cid")?.asString.orEmpty()
                        val accounts: List<JwAppAccount> = data.getAsJsonArray("accounts")?.map { item: com.google.gson.JsonElement ->
                            com.ahu_plus.data.GsonProvider.instance.fromJson(item, JwAppAccount::class.java)
                        }.orEmpty()
                        if (cid.isBlank() || accounts.isEmpty()) {
                            throw IOException("教务平台未返回可选账号")
                        }
                        JwAppLoginResult.ChooseAccount(cid, accounts)
                    }
                    else -> throw IOException(loginError(json))
                }
            }
        }

    suspend fun chooseAccount(
        accountId: String,
        cid: String,
        username: String,
        password: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE/token/login/accountChoose".toHttpUrl().newBuilder()
                .addQueryParameter("accountId", accountId)
                .addQueryParameter("cid", cid)
                .build()
            val json = executeLoginRequest(url.toString())
            if (json.get("code")?.asInt != 0) throw IOException(loginError(json))
            val idToken = json.getAsJsonObject("data")?.get("idToken")?.asString
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("教务平台未返回登录令牌")
            token = idToken
            sessionManager.saveJwAppSession(username, password, idToken)
        }
    }

    suspend fun clearSession() {
        token = null
        sessionManager.clearJwAppSession()
    }

    internal fun executeAuthorized(url: String): String {
        val current = token ?: sessionManager.getJwAppToken().also { token = it }
        if (current.isNullOrBlank() || isExpired(current)) throw JwAppAuthRequiredException()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/plain, */*")
            .header("Authorization", current)
            .header("X-Id-Token", current)
            .header("userToken", current)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) throw JwAppAuthRequiredException()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("教室课表请求失败: HTTP ${response.code}")
            if (body.isBlank()) throw IOException("教室课表返回空响应")
            return body
        }
    }

    private fun executeLoginRequest(url: String) = client.newCall(
        Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(null))
            .header("Accept", "application/json, text/plain, */*")
            .build()
    ).execute().use { response ->
        val body = response.body?.string().orEmpty()
        val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        if (!response.isSuccessful) {
            throw IOException(json?.let(::loginError) ?: "教务平台登录失败: HTTP ${response.code}")
        }
        json ?: throw IOException("教务平台登录返回格式异常")
    }

    private fun loginError(json: com.google.gson.JsonObject): String {
        return sequenceOf("message", "msg", "error_description")
            .mapNotNull { key -> json.get(key)?.takeUnless { it.isJsonNull }?.asString }
            .firstOrNull { it.isNotBlank() }
            ?.let { if (it.contains("Bad credentials", ignoreCase = true)) "教务账号或密码错误" else it }
            ?: "教务账号或密码错误"
    }

    companion object {
        private const val BASE = "https://jwapp.ahu.edu.cn"
        private const val PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCFY5N+9UX+0BF+xz1svFguI4CIDvmQ" +
                "TfINkOZ1HOO3ltBNHGQTUirUPQTyEph/+q/l8b16YYw3I2fyTH6y15s3tHf5jMei+R/" +
                "20jFRGo5udwVJUwq/RozKQIRzCtPYkXG4YWBnHKhXalZ5K2fhd5i/QtB016nVugH/7e" +
                "iBDWbKVwIDAQAB"

        internal fun encryptPassword(password: String): String {
            val keyBytes = PUBLIC_KEY.decodeBase64()?.toByteArray()
                ?: throw IllegalStateException("教务平台公钥无效")
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return okio.ByteString.of(*cipher.doFinal(password.toByteArray(Charsets.UTF_8))).base64()
        }

        internal fun isExpired(jwt: String, nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
            val payload = jwt.split('.').getOrNull(1)?.decodeBase64()?.utf8() ?: return true
            val exp = runCatching {
                JsonParser.parseString(payload).asJsonObject.get("exp")?.asLong
            }.getOrNull() ?: return true
            return exp <= nowSeconds + 30
        }
    }
}
