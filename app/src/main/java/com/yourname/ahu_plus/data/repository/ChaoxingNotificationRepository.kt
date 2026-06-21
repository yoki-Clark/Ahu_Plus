package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 外部通知服务(2026-06-20 集成 Phase 5)。
 *
 * 移植自 Samueli924/chaoxing 的 `api/notification.py`:
 *   - ServerChan (Server酱 sct.ftqq.com) — 微信推送
 *   - Qmsg (Qmsg酱 qmsg.zendee.cn) — QQ 推送
 *   - Bark (api.day.app) — iOS 推送
 *   - Telegram Bot (api.telegram.org) — Telegram 推送
 *
 * 学习任务完成 / 异常时调用 [send] 推送通知。
 */
class ChaoxingNotificationRepository(
    private val sessionManager: SessionManager,
) {
    companion object {
        private const val TAG = "CxNotify"
    }

    private val client: OkHttpClient = SecureHttpClientFactory.create(trustAll = false)

    /** 支持的通知服务 */
    enum class Provider(val label: String) {
        DISABLED("禁用"),
        SERVERCHAN("Server 酱"),
        QMSG("Qmsg 酱"),
        BARK("Bark"),
        TELEGRAM("Telegram Bot"),
    }

    /**
     * 发送通知。
     *
     * @param message 通知内容
     * @return 成功返回 Result.success,失败返回 Result.failure(配置缺失或 HTTP 错误)
     */
    suspend fun send(message: String): Result<Unit> = withContext(Dispatchers.IO) {
        val provider = runCatching { Provider.valueOf(sessionManager.getCxNotifyProvider()) }
            .getOrDefault(Provider.DISABLED)
        if (provider == Provider.DISABLED) return@withContext Result.success(Unit)

        val url = sessionManager.getCxNotifyUrl()
        if (url.isBlank()) {
            Log.w(TAG, "$provider 未配置 URL,跳过通知")
            return@withContext Result.success(Unit)
        }

        try {
            val (requestUrl, body) = when (provider) {
                Provider.SERVERCHAN -> {
                    // Server 酱 GET: ?title=...&desp=...
                    val sep = if (url.contains("?")) "&" else "?"
                    val fullUrl = url + sep + "title=" + java.net.URLEncoder.encode("超星学习通", "UTF-8") +
                        "&desp=" + java.net.URLEncoder.encode(message, "UTF-8")
                    fullUrl to null
                }
                Provider.QMSG -> {
                    // Qmsg: POST msg=...
                    val req = FormBody.Builder().add("msg", message).build()
                    url to req
                }
                Provider.BARK -> {
                    // Bark: /{key}/{title}/{body} 或 ?title=&body=
                    val sep = if (url.contains("?")) "&" else "/"
                    val fullUrl = if (url.endsWith("/")) {
                        url + java.net.URLEncoder.encode("超星学习通", "UTF-8") + "/" +
                            java.net.URLEncoder.encode(message, "UTF-8")
                    } else {
                        url + sep + "title=" + java.net.URLEncoder.encode("超星学习通", "UTF-8") +
                            "&body=" + java.net.URLEncoder.encode(message, "UTF-8")
                    }
                    fullUrl to null
                }
                Provider.TELEGRAM -> {
                    // Telegram Bot: POST chat_id + text
                    val chatId = sessionManager.getCxNotifyTgChatId()
                    val req = FormBody.Builder()
                        .add("chat_id", chatId)
                        .add("text", message)
                        .build()
                    url to req
                }
                else -> url to null
            }

            val reqBuilder = Request.Builder().url(requestUrl)
            if (body != null) {
                reqBuilder.post(body)
            } else {
                reqBuilder.get()
            }
            val resp = client.newCall(reqBuilder.build()).execute()
            val code = resp.code
            val respText = resp.body?.string().orEmpty().take(200)
            resp.close()
            if (code in 200..299) {
                Log.i(TAG, "[$provider] 推送成功: $code")
                Result.success(Unit)
            } else {
                Log.w(TAG, "[$provider] 推送失败: $code, $respText")
                Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$provider] 推送异常", e)
            Result.failure(e)
        }
    }
}
