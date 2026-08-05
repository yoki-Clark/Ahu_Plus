package com.ahu_plus.data.repository

import com.google.gson.JsonParser
import com.ahu_plus.data.model.MarketTopic
import com.ahu_plus.data.model.MarketReadOnlyIndexPage
import com.ahu_plus.data.model.MarketReadOnlyIndexStatus
import com.ahu_plus.data.network.SecureHttpClientFactory
import com.ahu_plus.data.remote.market.MarketApi
import com.ahu_plus.data.remote.market.MarketReadOnlyIndexApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class MarketReadOnlyIndexRepository(
    private val baseUrl: String = MarketReadOnlyIndexApi.BASE_URL,
    private val client: OkHttpClient = SecureHttpClientFactory.create(),
) {

    suspend fun getPage(cursor: String? = null): Result<MarketReadOnlyIndexPage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${baseUrl.trimEnd('/')}/market/readonly/feed".toHttpUrl().newBuilder()
                    .addQueryParameter("limit", "20")
                    .apply { cursor?.takeIf { it.isNotBlank() }?.let { addQueryParameter("cursor", it) } }
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", MarketApi.USER_AGENT)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    parseResponse(response.code, response.body?.string().orEmpty()).getOrThrow()
                }
            }
        }

    suspend fun getArchivedTopic(topicId: Long): Result<MarketTopic> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(topicId > 0L) { "无效的帖子 ID" }
                val url = "${baseUrl.trimEnd('/')}/market/readonly/archive/$topicId".toHttpUrl()
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", MarketApi.USER_AGENT)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    parseArchiveResponse(response.code, response.body?.string().orEmpty()).getOrThrow()
                }
            }
        }

    internal companion object {
        fun parsePage(body: String): MarketReadOnlyIndexPage =
            parseResponse(200, body).getOrThrow()

        fun parseArchiveResponse(code: Int, body: String): Result<MarketTopic> = runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            require(code in 200..299) {
                root.get("msg")?.asString ?: "只读历史快照服务 HTTP $code"
            }
            require(root.get("status")?.asString == "success") {
                root.get("msg")?.asString ?: "只读历史快照服务返回失败"
            }
            val data = root.get("data")?.takeIf { it.isJsonObject }
                ?: error("只读历史快照返回格式异常")
            JsonParser.parseString(data.toString()).let { element ->
                com.ahu_plus.data.remote.JsonUtils.parseObject<MarketTopic>(element.toString())
            }?.takeIf { it.id > 0L }
                ?: error("只读历史快照解析失败")
        }

        fun parseResponse(code: Int, body: String): Result<MarketReadOnlyIndexPage> = runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            val errorCode = root.get("code")?.asString.orEmpty()
            if (code == 503 && errorCode == "INDEX_INITIALIZING") {
                return@runCatching MarketReadOnlyIndexPage(
                    ids = emptyList(),
                    nextCursor = null,
                    hasMore = false,
                    sourceStatus = MarketReadOnlyIndexStatus.INITIALIZING,
                    generatedAt = "",
                )
            }
            require(code in 200..299) { "只读索引服务 HTTP $code" }
            require(root.get("status")?.asString == "success") {
                root.get("msg")?.asString ?: "只读索引服务返回失败"
            }
            val data = root.getAsJsonObject("data")
            val ids = data.getAsJsonArray("ids")?.mapNotNull { element ->
                element.asLong.takeIf { it > 0L }
            }?.distinct()?.take(20).orEmpty()
            MarketReadOnlyIndexPage(
                ids = ids,
                nextCursor = data.get("nextCursor")?.takeUnless { it.isJsonNull }?.asString,
                hasMore = data.get("hasMore")?.asBoolean == true,
                sourceStatus = parseStatus(data.get("sourceStatus")?.asString),
                generatedAt = data.get("generatedAt")?.asString.orEmpty(),
            )
        }

        private fun parseStatus(value: String?): MarketReadOnlyIndexStatus = when (value?.lowercase()) {
            "ready" -> MarketReadOnlyIndexStatus.READY
            "initializing" -> MarketReadOnlyIndexStatus.INITIALIZING
            "stale" -> MarketReadOnlyIndexStatus.STALE
            "paused" -> MarketReadOnlyIndexStatus.PAUSED
            else -> MarketReadOnlyIndexStatus.IDLE
        }
    }
}
