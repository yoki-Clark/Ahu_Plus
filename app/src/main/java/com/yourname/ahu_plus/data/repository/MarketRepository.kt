package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.MarketComment
import com.yourname.ahu_plus.data.model.MarketCommentReplies
import com.yourname.ahu_plus.data.model.MarketIdentity
import com.yourname.ahu_plus.data.model.MarketNotice
import com.yourname.ahu_plus.data.model.MarketNoticeContent
import com.yourname.ahu_plus.data.model.MarketNoticePage
import com.yourname.ahu_plus.data.model.MarketNoticeTopic
import com.yourname.ahu_plus.data.model.MarketTopic
import com.yourname.ahu_plus.data.model.MarketUser
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import com.yourname.ahu_plus.data.remote.JsonUtils
import com.yourname.ahu_plus.data.remote.market.MarketApi
import com.yourname.ahu_plus.data.remote.market.applyMarketHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MarketRepository(
    private val sessionManager: SessionManager
) {
    private val gson = GsonProvider.instance
    private val client = SecureHttpClientFactory.create()

    // ══════════════════════════════════════════════════════
    //  GET
    // ══════════════════════════════════════════════════════

    suspend fun getTopics(page: Int = 1): Result<List<MarketTopic>> = withContext(Dispatchers.IO) {
        requestMarketJson("${MarketApi.TOPICS_URL}?page=$page")
            .mapCatching { body -> JsonUtils.parseRowsSafe<MarketTopic>(body) }
    }

    suspend fun getTopTopics(): Result<List<MarketTopic>> = withContext(Dispatchers.IO) {
        requestMarketJson(MarketApi.TOPICS_TOP_URL)
            .mapCatching { body -> JsonUtils.parseRowsSafe<MarketTopic>(body) }
    }

    suspend fun getTopic(topicId: Long): Result<MarketTopic> = withContext(Dispatchers.IO) {
        requestMarketJson("${MarketApi.TOPICS_URL}/$topicId").mapCatching { body ->
            val data = JsonUtils.parseData(body)
            if (data.isJsonObject) {
                JsonUtils.parseObject<MarketTopic>(body)
                    ?: throw Exception("帖子详情解析失败")
            } else {
                JsonUtils.parseRowsSafe<MarketTopic>(body).firstOrNull()
                    ?: throw Exception("帖子详情解析失败")
            }
        }
    }

    suspend fun getComments(
        topicId: Long,
        page: Int = 1,
        sort: String = "hot"
    ): Result<List<MarketComment>> = withContext(Dispatchers.IO) {
        requestMarketJson("${MarketApi.COMMENTS_URL}?topic_id=$topicId&sort=$sort&page=$page")
            .mapCatching { body -> JsonUtils.parseRowsSafe<MarketComment>(body) }
    }

    suspend fun getCommentReplies(
        topicId: Long,
        commentId: Long,
        page: Int = 1,
        pageSize: Int = 6
    ): Result<MarketCommentReplies> = withContext(Dispatchers.IO) {
        requestMarketJson(
            "${MarketApi.COMMENTS_URL}?topic_id=$topicId&comment_id=$commentId" +
                "&sort=time&page=$page&page_size=$pageSize"
        ).mapCatching { body -> parseCommentReplies(body, page, pageSize) }
    }

    suspend fun getNotices(page: Int = 1): Result<MarketNoticePage> = withContext(Dispatchers.IO) {
        requestMarketJson("${MarketApi.USER_NOTICES_URL}?page=$page")
            .mapCatching { body -> parseNotices(body, page) }
    }

    /**
     * 搜索帖子 —— 通过 `?content=<keyword>` 参数命中正文/标题包含关键词的帖子。
     * 后端返回格式与 topics 列表一致：
     * `{status, code, msg, data: {page, rows: [...]}}`
     *
     * 响应是单页结果，不分页（社区版）；用户清空或换关键词时由调用方重新触发。
     */
    suspend fun searchTopics(
        content: String,
        page: Int = 1
    ): Result<List<MarketTopic>> = withContext(Dispatchers.IO) {
        val keyword = content.trim()
        if (keyword.isBlank()) {
            return@withContext Result.success(emptyList())
        }
        val encoded = java.net.URLEncoder.encode(keyword, "UTF-8")
        requestMarketJson("${MarketApi.TOPICS_URL}?page=$page&content=$encoded")
            .mapCatching { body -> JsonUtils.parseRowsSafe<MarketTopic>(body) }
    }

    // ══════════════════════════════════════════════════════
    //  POST
    // ══════════════════════════════════════════════════════

    /**
     * 发布新帖。V1 仅支持纯文本，固定不发图片与 @ 提及。
     */
    suspend fun createTopic(
        title: String,
        content: String,
        nodeId: Long,
        isAnon: Boolean
    ): Result<Long> = withContext(Dispatchers.IO) {
        val payload = gson.toJson(topicPayload(title, content, nodeId, isAnon))
        postMarketJson(MarketApi.TOPICS_URL, payload).mapCatching { body ->
            parseCreatedTopicId(body)
        }
    }

    /**
     * 发布评论或回复。
     */
    suspend fun createComment(
        topicId: Long,
        content: String,
        commentId: Long = 0L,
        replyId: Long = 0L,
        targetUserId: Long = 0L
    ): Result<MarketComment> = withContext(Dispatchers.IO) {
        val payload = gson.toJson(commentPayload(topicId, content, commentId, replyId, targetUserId))
        postMarketJson(MarketApi.COMMENTS_URL, payload).mapCatching { body ->
            parseCreatedComment(body, topicId)
        }
    }

    // ══════════════════════════════════════════════════════
    //  Request / Response 底层
    // ══════════════════════════════════════════════════════

    private fun requestMarketJson(url: String, identity: String? = null): Result<String> {
        val token = resolveToken(identity)
            ?: return Result.failure(Exception("请先填写集市 API 身份字段"))
        return try {
            val request = Request.Builder().url(url).applyMarketHeaders(token).build()
            executeAndRead(url, request)
        } catch (e: Exception) {
            Log.e(TAG, "集市加载失败", e)
            Result.failure(e)
        }
    }

    private fun postMarketJson(url: String, jsonBody: String, identity: String? = null): Result<String> {
        val token = resolveToken(identity)
            ?: return Result.failure(Exception("请先填写集市 API 身份字段"))
        return try {
            val body = jsonBody.toRequestBody(MarketApi.JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .applyMarketHeaders(token)
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                Log.i(TAG, "market POST ${response.request.url.encodedPath} HTTP ${response.code}")
                if (response.code == 401 || response.code == 403) {
                    return Result.failure(Exception("集市身份字段已失效，请重新获取"))
                }
                if (!response.isSuccessful) {
                    return Result.failure(
                        Exception("集市发布失败 HTTP ${response.code}：${responseBody.take(160)}")
                    )
                }
                Result.success(responseBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "集市发布失败", e)
            Result.failure(e)
        }
    }

    private fun executeAndRead(url: String, request: Request): Result<String> {
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.i(TAG, "market ${response.request.url.encodedPath} HTTP ${response.code}")
            if (response.code == 401 || response.code == 403) {
                return Result.failure(Exception("集市身份字段已失效，请重新获取"))
            }
            if (!response.isSuccessful) {
                return Result.failure(Exception("集市加载失败 HTTP ${response.code}"))
            }
            Result.success(body)
        }
    }

    private fun resolveToken(identity: String?): String? {
        val raw = (identity ?: sessionManager.getMarketApiIdentity())
            ?.takeIf { it.isNotBlank() }
        return raw?.let { MarketApi.normalizeIdentity(it) }
    }

    // ══════════════════════════════════════════════════════
    //  Identity management
    // ══════════════════════════════════════════════════════

    suspend fun saveIdentity(identity: String) {
        sessionManager.saveMarketApiIdentity(MarketApi.normalizeIdentity(identity))
    }

    suspend fun clearIdentity() {
        sessionManager.clearMarketApiIdentity()
    }

    fun getSavedIdentity(): String? = sessionManager.getMarketApiIdentity()

    fun getAllIdentities(): List<MarketIdentity> = sessionManager.getMarketIdentities()

    fun getSelectedIdentityIds(): Set<String> = sessionManager.getSelectedIdentityIds()

    suspend fun saveMarketIdentities(identities: List<MarketIdentity>) {
        sessionManager.saveMarketIdentities(identities)
    }

    suspend fun setSelectedIdentityIds(ids: Set<String>) {
        sessionManager.saveSelectedIdentityIds(ids)
    }

    fun getBlockPinned(): Boolean = sessionManager.getBlockPinned()

    suspend fun setBlockPinned(enabled: Boolean) {
        sessionManager.setBlockPinned(enabled)
    }

    fun getBlockKeywords(): List<String> = sessionManager.getBlockKeywords()

    suspend fun saveBlockKeywords(keywords: List<String>) {
        sessionManager.saveBlockKeywords(keywords)
    }

    fun getFilterNodeIds(): List<Long> = sessionManager.getFilterNodeIds()

    suspend fun saveFilterNodeIds(nodeIds: List<Long>) {
        sessionManager.saveFilterNodeIds(nodeIds)
    }

    /**
     * 多校园身份：并发拉取每个 token 的 topics，合并去重。
     * @return (合并后的帖子列表, topicId → 学校名的映射)
     */
    suspend fun getTopicsMulti(
        identities: List<MarketIdentity>,
        page: Int = 1
    ): Result<Pair<List<MarketTopic>, Map<Long, String>>> = withContext(Dispatchers.IO) {
        if (identities.size <= 1) {
            val topics = getTopics(page).getOrElse { emptyList() }
            val school = identities.firstOrNull()?.school
            val map = if (school != null) topics.associate { it.id to school } else emptyMap()
            return@withContext Result.success(Pair(topics, map))
        }
        coroutineScope {
            val deferred = identities.map { identity ->
                async {
                    val school = identity.school
                    val topics = runCatching {
                        requestMarketJson("${MarketApi.TOPICS_URL}?page=$page", identity.token)
                            .getOrThrow()
                            .let { JsonUtils.parseRowsSafe<MarketTopic>(it) }
                    }.getOrDefault(emptyList())
                    Pair(topics, school)
                }
            }
            val results = deferred.map { it.await() }
            val allTopics = results.flatMap { it.first }
                .distinctBy { it.id }
                .sortedByDescending { it.id }
            val schoolMap = mutableMapOf<Long, String>()
            for ((topics, school) in results) {
                if (school != null) {
                    for (topic in topics) {
                        schoolMap[topic.id] = school
                    }
                }
            }
            Result.success(Pair(allTopics, schoolMap))
        }
    }

    // ══════════════════════════════════════════════════════
    //  Payload 构造
    // ══════════════════════════════════════════════════════

    private fun topicPayload(
        title: String, content: String, nodeId: Long, isAnon: Boolean
    ): JsonObject = JsonObject().apply {
        addProperty("title", title.ifBlank { "none" })
        addProperty("content", content)
        addProperty("is_anon", if (isAnon) 1 else 0)
        addProperty("link_type", 0)
        addProperty("link_people", "")
        addProperty("link_info", "")
        addProperty("node_id", nodeId)
        add("imgs", com.google.gson.JsonArray())
        addProperty("source", "xcx")
        addProperty("school_sub_address_id", "")
        addProperty("is_ad_fake", 0)
    }

    private fun commentPayload(
        topicId: Long,
        content: String,
        commentId: Long,
        replyId: Long,
        targetUserId: Long
    ): JsonObject = JsonObject().apply {
        addProperty("is_fake", 0)
        addProperty("target_user_id", targetUserId)
        addProperty("topic_id", topicId)
        addProperty("comment_id", commentId)
        addProperty("reply_id", replyId)
        addProperty("content", content)
        add("imgs", com.google.gson.JsonArray())
        addProperty("source", "xcx")
        addProperty("is_ad_fake", 0)
    }

    // ══════════════════════════════════════════════════════
    //  Response 解析
    // ══════════════════════════════════════════════════════

    private fun parseCommentReplies(
        body: String,
        fallbackPage: Int,
        fallbackPageSize: Int
    ): MarketCommentReplies {
        val rows = JsonUtils.parseRowsSafe<MarketComment>(body)
        val data = JsonUtils.parseData(body)
        return if (data.isJsonObject) {
            val obj = data.asJsonObject
            MarketCommentReplies(
                count = obj.get("count")?.asInt ?: rows.size,
                page = obj.get("page")?.asInt ?: fallbackPage,
                rows = rows,
                loading = false,
                pageSize = obj.get("pageSize")?.asInt ?: fallbackPageSize
            )
        } else {
            MarketCommentReplies(
                count = rows.size,
                page = fallbackPage,
                rows = rows,
                loading = false,
                pageSize = fallbackPageSize
            )
        }
    }

    private fun parseNotices(body: String, fallbackPage: Int): MarketNoticePage {
        val root = JsonParser.parseString(body)
        if (!root.isJsonObject) throw Exception("通知列表返回格式异常")
        val obj = root.asJsonObject
        val status = obj.get("status")?.asString
        if (status != null && status != "success") {
            val msg = obj.get("msg")?.asString ?: "通知列表加载失败"
            throw Exception("通知列表加载失败：$msg")
        }
        val data = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw Exception("通知列表响应缺少 data 字段")
        val count = data.get("count")?.asInt ?: 0
        val page = data.get("page")?.asInt ?: fallbackPage
        val rowsArray = data.getAsJsonArray("rows")
        val rows = rowsArray?.mapNotNull { element ->
            runCatching { parseNoticeElement(element) }.getOrNull()
        }.orEmpty()
        return MarketNoticePage(count = count, page = page, rows = rows)
    }

    private fun parseNoticeElement(element: JsonElement): MarketNotice? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        return MarketNotice(
            id = obj.get("id")?.asLong ?: 0L,
            actionType = obj.get("actionType")?.asInt ?: 0,
            actionTypeText = obj.get("actionTypeText")?.asString.orEmpty(),
            type = obj.get("type")?.asString.orEmpty(),
            createTime = obj.get("createTime")?.asString.orEmpty(),
            topic = parseNoticeTopic(obj.get("topic")),
            senderUserInfo = parseUserInfo(obj.getAsJsonObject("senderUserInfo")),
            sendContent = parseNoticeContent(obj.get("sendContent")),
            targetContent = parseNoticeContent(obj.get("targetContent")),
            commentContent = parseNoticeContent(obj.get("commentContent")),
            replyContent = parseNoticeContent(obj.get("replyContent"))
        )
    }

    private fun parseNoticeTopic(element: JsonElement?): MarketNoticeTopic? {
        if (element == null || !element.isJsonObject) return null
        val obj = element.asJsonObject
        // topic.data.imgs → 展平为 List<String>
        val imgs = obj.getAsJsonObject("data")
            ?.getAsJsonArray("imgs")
            ?.mapNotNull { it.takeIf(JsonElement::isJsonPrimitive)?.asString }
            .orEmpty()
        return MarketNoticeTopic(
            id = obj.get("id")?.asLong ?: 0L,
            title = obj.get("title")?.asString.orEmpty(),
            content = obj.get("content")?.asString.orEmpty(),
            imgs = imgs
        )
    }

    private fun parseNoticeContent(element: JsonElement?): MarketNoticeContent? {
        if (element == null || !element.isJsonObject) return null
        val obj = element.asJsonObject
        return MarketNoticeContent(
            id = obj.get("id")?.asLong ?: 0L,
            content = obj.get("content")?.asString.orEmpty(),
            topicId = obj.get("topicId")?.asLong ?: 0L,
            commentId = obj.get("commentId")?.asLong ?: 0L,
            replyId = obj.get("replyId")?.asLong ?: 0L
        )
    }

    private fun parseUserInfo(element: JsonObject?): MarketUser? {
        if (element == null) return null
        return MarketUser(
            uuid = element.get("uuid")?.asLong ?: 0L,
            nickname = element.get("nickname")?.asString.orEmpty(),
            avatar = element.get("avatar")?.asString.orEmpty()
        )
    }

    private fun parseCreatedTopicId(body: String): Long {
        val root = JsonParser.parseString(body)
        if (!root.isJsonObject) {
            throw Exception("集市发布返回格式异常")
        }
        val obj = root.asJsonObject
        val status = obj.get("status")?.asString
        if (status != null && status != "success") {
            val msg = obj.get("msg")?.asString ?: "发布失败"
            throw Exception("集市发布失败：$msg")
        }
        val data = obj.get("data")
            ?: throw Exception("集市发布响应缺少 data 字段")
        val id = when {
            data.isJsonObject && data.asJsonObject.has("id") ->
                data.asJsonObject.get("id").asLong
            data.isJsonPrimitive && data.asJsonPrimitive.isNumber ->
                data.asLong
            else -> throw Exception("集市发布响应缺少 topic id")
        }
        if (id <= 0L) throw Exception("集市发布返回了无效的 topic id")
        return id
    }

    private fun parseCreatedComment(body: String, fallbackTopicId: Long): MarketComment {
        val root = JsonParser.parseString(body)
        if (!root.isJsonObject) {
            throw Exception("评论发布返回格式异常")
        }
        val obj = root.asJsonObject
        val status = obj.get("status")?.asString
        if (status != null && status != "success") {
            val msg = obj.get("msg")?.asString ?: "发表失败"
            throw Exception("评论发表失败：$msg")
        }
        val data = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw Exception("评论发表响应缺少 data 字段")
        val commentElement = data.get("comment")?.takeIf { it.isJsonObject }
            ?: throw Exception("评论发表响应缺少 data.comment 字段")

        val parsed = runCatching {
            gson.fromJson(commentElement, MarketComment::class.java)
        }.getOrNull() ?: throw Exception("评论对象解析失败")

        // 响应里 topic 是嵌套对象，我们用本地 topicId 平铺回模型
        return parsed.copy(topicId = fallbackTopicId)
    }

    private companion object {
        const val TAG = "MarketRepo"
    }
}
