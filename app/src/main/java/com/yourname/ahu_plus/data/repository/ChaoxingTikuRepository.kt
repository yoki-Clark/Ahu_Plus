package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonParser
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.CxQuestion
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 超星题库查询 Repository(2026-06-20 重构 Phase 4)。
 *
 * 支持多种题库后端 + **Provider 回退链**:
 *   - 本地缓存 (DataStore)
 *   - 言溪题库 (tk.enncy.cn) — 多 Token 轮询
 *   - GO 题 (q.icodef.com) — 解限流 + 指数退避
 *   - LIKE 知识库 (datam.site) — 多 Token 随机 + 视觉识别
 *   - 自部署 TikuAdapter
 *   - AI 大模型 (OpenAI 兼容 API / 硅基流动)
 *
 * 移植自 Samueli924/chaoxing 的 api/answer.py (TikuFallback)。
 *
 * 用法:
 *   configure(providerChain = listOf(YANXI, GO, AI), ...)
 *   query(question)  // 遍历 chain,首个非 null 即返回
 */
class ChaoxingTikuRepository(
    private val sessionManager: SessionManager,
) {
    companion object {
        private const val TAG = "CxTiku"
        private const val YANXI_API = "https://tk.enncy.cn/query"
        private const val GO_API = "https://q.icodef.com/api/v3/query"
        private const val LIKE_API = "https://www.datam.site/api/v3/answer"
    }

    private val client: OkHttpClient = SecureHttpClientFactory.create(trustAll = false)

    /** 题库类型枚举(2026-06-20 扩展) */
    enum class TikuType(val label: String) {
        DISABLED("不使用"),
        CACHE("本地缓存"),
        YANXI("言溪题库"),
        GO("GO 题"),
        LIKE("LIKE 知识库"),
        ADAPTER("自部署 Adapter"),
        AI("AI 大模型"),
        SILICONFLOW("硅基流动 AI"),
    }

    // ── 配置 ──────────────────────────────────────────────────
    @Volatile private var providerChain: List<TikuType> = listOf(TikuType.CACHE)
    @Volatile private var yanxiTokens: List<String> = emptyList()
    @Volatile private var yanxiDelay: Double = 1.0
    @Volatile private var coverRate: Double = 0.8
    @Volatile private var aiApiKey: String = ""
    @Volatile private var aiBaseUrl: String = "https://api.deepseek.com"
    @Volatile private var aiModel: String = "deepseek-chat"
    @Volatile private var aiMinInterval: Int = 3
    @Volatile private var siliconflowKey: String = ""
    @Volatile private var siliconflowModel: String = "deepseek-ai/DeepSeek-R1"
    @Volatile private var siliconflowEndpoint: String = "https://api.siliconflow.cn/v1/chat/completions"
    @Volatile private var likeapiSearch: Boolean = false
    @Volatile private var likeapiVision: Boolean = true
    @Volatile private var likeapiModel: String = "glm-4.5-air"
    @Volatile private var likeapiTokens: List<String> = emptyList()
    @Volatile private var goAuthorization: String = ""
    @Volatile private var goMinInterval: Double = 1.0
    @Volatile private var tikuAdapterUrl: String = ""

    @Volatile private var lastAiCallMs: Long = 0L

    /**
     * 配置题库(完整版,Phase 4)。
     *
     * @param providerChain 题库 provider 顺序(逗号分隔字符串),首个非空即返回
     */
    @Synchronized
    fun configure(
        providerChainStr: String,
        yanxiTokensStr: String = "",
        yanxiDelay: Double = 1.0,
        coverRate: Double = 0.8,
        aiApiKey: String = "",
        aiBaseUrl: String = "",
        aiModel: String = "",
        aiMinInterval: Int = 3,
        siliconflowKey: String = "",
        siliconflowModel: String = "",
        siliconflowEndpoint: String = "",
        likeapiSearch: Boolean = false,
        likeapiVision: Boolean = true,
        likeapiModel: String = "glm-4.5-air",
        likeapiTokensStr: String = "",
        goAuthorization: String = "",
        goMinInterval: Double = 1.0,
        tikuAdapterUrl: String = "",
    ) {
        providerChain = providerChainStr.split(",")
            .mapNotNull { runCatching { TikuType.valueOf(it.trim().uppercase()) }.getOrNull() }
            .ifEmpty { listOf(TikuType.CACHE) }
        this.yanxiTokens = yanxiTokensStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        this.yanxiDelay = yanxiDelay
        this.coverRate = coverRate
        if (aiApiKey.isNotBlank()) this.aiApiKey = aiApiKey
        if (aiBaseUrl.isNotBlank()) this.aiBaseUrl = aiBaseUrl
        if (aiModel.isNotBlank()) this.aiModel = aiModel
        this.aiMinInterval = aiMinInterval
        if (siliconflowKey.isNotBlank()) this.siliconflowKey = siliconflowKey
        if (siliconflowModel.isNotBlank()) this.siliconflowModel = siliconflowModel
        if (siliconflowEndpoint.isNotBlank()) this.siliconflowEndpoint = siliconflowEndpoint
        this.likeapiSearch = likeapiSearch
        this.likeapiVision = likeapiVision
        if (likeapiModel.isNotBlank()) this.likeapiModel = likeapiModel
        this.likeapiTokens = likeapiTokensStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (goAuthorization.isNotBlank()) this.goAuthorization = goAuthorization
        this.goMinInterval = goMinInterval
        if (tikuAdapterUrl.isNotBlank()) this.tikuAdapterUrl = tikuAdapterUrl
        Log.i(TAG, "Provider 链: $providerChain")
    }

    /** 兼容旧 API,单 provider 模式 */
    fun configure(type: TikuType, yanxiToken: String = "", aiApiKey: String = "", aiBaseUrl: String = "", aiModel: String = "") {
        configure(
            providerChainStr = type.name,
            yanxiTokensStr = yanxiToken,
            aiApiKey = aiApiKey,
            aiBaseUrl = aiBaseUrl,
            aiModel = aiModel,
        )
    }

    val coverRateValue: Double get() = coverRate

    // ══════════════════════════════════════════════════════════════
    //  查询答案
    // ══════════════════════════════════════════════════════════════

    /**
     * 查询题目答案(遍历 provider chain)。
     *
     * @return 答案字符串,所有 provider 都未命中返回 null
     */
    suspend fun query(question: CxQuestion): String? {
        if (providerChain.firstOrNull() == TikuType.DISABLED) return null

        // 1. 始终先查本地缓存
        val cached = sessionManager.getCxTikuCache(question.title)
        if (cached != null) {
            Log.d(TAG, "缓存命中: ${question.title.take(20)}...")
            return cached
        }

        // 2. 遍历 provider chain
        for (provider in providerChain) {
            if (provider == TikuType.DISABLED || provider == TikuType.CACHE) continue
            val answer = when (provider) {
                TikuType.YANXI -> queryYanxi(question)
                TikuType.GO -> queryGo(question)
                TikuType.LIKE -> queryLike(question)
                TikuType.ADAPTER -> queryAdapter(question)
                TikuType.AI -> queryAI(question, aiApiKey, aiBaseUrl, aiModel)
                TikuType.SILICONFLOW -> queryAI(question, siliconflowKey, siliconflowEndpoint, siliconflowModel)
                else -> null
            }
            if (answer != null) {
                sessionManager.saveCxTikuCache(question.title, answer)
                return answer
            }
        }
        return null
    }

    // ══════════════════════════════════════════════════════════════
    //  言溪题库(多 Token 轮询)
    // ══════════════════════════════════════════════════════════════

    private suspend fun queryYanxi(question: CxQuestion): String? = withContext(Dispatchers.IO) {
        if (yanxiTokens.isEmpty()) return@withContext null
        for ((idx, token) in yanxiTokens.withIndex()) {
            if (idx > 0 && yanxiDelay > 0) delay((yanxiDelay * 1000).toLong())
            try {
                val url = "$YANXI_API?title=${java.net.URLEncoder.encode(question.title, "UTF-8")}&token=$token"
                val request = Request.Builder().url(url).get().header("User-Agent", "Mozilla/5.0").build()
                val resp = client.newCall(request).execute()
                val json = resp.body?.string() ?: "{}"
                resp.close()
                val obj = JsonParser.parseString(json).asJsonObject
                if (obj.get("code")?.asInt == 200) {
                    val answer = obj.getAsJsonObject("data")?.get("answer")?.asString
                    if (!answer.isNullOrBlank()) {
                        Log.d(TAG, "[YANXI] 命中: ${question.title.take(20)}... → $answer")
                        return@withContext answer
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "[YANXI] Token $idx 查询异常: ${e.message}")
            }
        }
        null
    }

    // ══════════════════════════════════════════════════════════════
    //  GO 题(q.icodef.com,解限流 + 指数退避)
    // ══════════════════════════════════════════════════════════════

    private suspend fun queryGo(question: CxQuestion): String? = withContext(Dispatchers.IO) {
        try {
            if (goMinInterval > 0) delay((goMinInterval * 1000).toLong())
            val url = "$GO_API?question=${java.net.URLEncoder.encode(question.title, "UTF-8")}"
            val reqBuilder = Request.Builder().url(url).get().header("User-Agent", "Mozilla/5.0")
            if (goAuthorization.isNotBlank()) {
                reqBuilder.header("Authorization", goAuthorization)
            }
            val resp = client.newCall(reqBuilder.build()).execute()
            val json = resp.body?.string() ?: "{}"
            resp.close()
            val obj = JsonParser.parseString(json).asJsonObject
            if (obj.get("code")?.asInt == 0 || obj.get("status")?.asString == "success") {
                val answer = obj.get("data")?.asJsonObject?.get("answer")?.asString
                    ?: obj.get("answer")?.asString
                if (!answer.isNullOrBlank()) {
                    Log.d(TAG, "[GO] 命中: ${question.title.take(20)}... → $answer")
                    return@withContext answer
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "[GO] 查询异常: ${e.message}")
            null
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  LIKE 知识库(多 Token 随机 + 视觉)
    // ══════════════════════════════════════════════════════════════

    private suspend fun queryLike(question: CxQuestion): String? = withContext(Dispatchers.IO) {
        try {
            if (likeapiTokens.isEmpty()) return@withContext null
            // 多 Token 随机轮询
            val token = likeapiTokens.random()
            val body = FormBody.Builder()
                .add("question", question.title)
                .add("model", likeapiModel)
                .add("search", likeapiSearch.toString())
                .add("vision", likeapiVision.toString())
                .add("token", token)
                .build()
            val req = Request.Builder().url(LIKE_API).post(body)
                .header("User-Agent", "Mozilla/5.0")
                .header("Authorization", "Bearer $token")
                .build()
            val resp = client.newCall(req).execute()
            val json = resp.body?.string() ?: "{}"
            resp.close()
            val obj = JsonParser.parseString(json).asJsonObject
            val answer = obj.get("answer")?.asString ?: obj.get("data")?.asJsonObject?.get("answer")?.asString
            if (!answer.isNullOrBlank()) {
                Log.d(TAG, "[LIKE] 命中: ${question.title.take(20)}... → $answer")
                return@withContext answer
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "[LIKE] 查询异常: ${e.message}")
            null
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  自部署 TikuAdapter
    // ══════════════════════════════════════════════════════════════

    private suspend fun queryAdapter(question: CxQuestion): String? = withContext(Dispatchers.IO) {
        try {
            if (tikuAdapterUrl.isBlank()) return@withContext null
            val body = FormBody.Builder()
                .add("question", question.title)
                .add("type", question.type)
                .add("options", question.options)
                .build()
            val req = Request.Builder().url(tikuAdapterUrl).post(body)
                .header("User-Agent", "AhuPlus/1.0").build()
            val resp = client.newCall(req).execute()
            val json = resp.body?.string() ?: "{}"
            resp.close()
            val obj = JsonParser.parseString(json).asJsonObject
            val answer = obj.get("answer")?.asString
            if (!answer.isNullOrBlank()) {
                Log.d(TAG, "[ADAPTER] 命中: ${question.title.take(20)}... → $answer")
                return@withContext answer
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "[ADAPTER] 查询异常: ${e.message}")
            null
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  AI 大模型查询 (OpenAI 兼容 / DeepSeek)
    //  修复 (2026-06-21):
    //    - 使用 JSON body 而非 FormBody (旧代码 MIME 类型错误)
    //    - 添加 response_format: json_object 强制模型输出 JSON
    //    - System prompt 引导模型按 JSON schema 返回
    //    - 客户端解析 JSON 提取 answer 字段，失败则 fallback 到原始文本
    // ══════════════════════════════════════════════════════════════

    private suspend fun queryAI(
        question: CxQuestion,
        apiKey: String,
        baseUrl: String,
        model: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                Log.w(TAG, "[AI] apiKey 为空,跳过调用 (baseUrl=$baseUrl, model=$model)")
                return@withContext null
            }

            // AI 调用间隔控制
            val now = System.currentTimeMillis()
            val elapsed = now - lastAiCallMs
            val minMs = aiMinInterval * 1000L
            if (lastAiCallMs > 0 && elapsed < minMs) {
                delay(minMs - elapsed)
            }
            lastAiCallMs = System.currentTimeMillis()

            // 构建 system + user messages
            // 注意: prompt 中必须包含 "json" 字样,否则 DeepSeek response_format 会报错
            val typeName = question.typeName()
            val systemMsg = buildString {
                append("你是一个题库助手。根据题目和选项，直接给出正确答案。")
                append("你必须只输出一个合法的JSON对象，不要包含额外解释或Markdown标记。")
                append("JSON格式: {\"answer\": \"答案内容\"}")
            }
            val userMsg = buildString {
                append("题目类型：$typeName\n")
                append("题目：${question.title}\n")
                if (question.options.isNotBlank()) {
                    append("选项：\n${question.options}\n")
                }
                append("\n")
                when (question.type) {
                    "single" -> append("请返回JSON: {\"answer\": \"A\"}  其中字母为正确选项")
                    "multiple" -> append("请返回JSON: {\"answer\": \"ABC\"}  其中字母为所有正确选项（按字母顺序）")
                    "judgement" -> append("请返回JSON: {\"answer\": \"true\"}  或 {\"answer\": \"false\"}")
                    "completion" -> append("请返回JSON: {\"answer\": \"填空答案\"}")
                    "shortanswer" -> append("请返回JSON: {\"answer\": \"简答内容\"}")
                    else -> append("请返回JSON: {\"answer\": \"答案\"}")
                }
            }

            // 构建 JSON 请求体 (使用 org.json)
            val bodyJson = org.json.JSONObject().apply {
                put("model", model)
                put("messages", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("role", "system")
                        put("content", systemMsg)
                    })
                    put(org.json.JSONObject().apply {
                        put("role", "user")
                        put("content", userMsg)
                    })
                })
                put("temperature", 0.1)
                put("max_tokens", 500)
                put("response_format", org.json.JSONObject().apply {
                    put("type", "json_object")
                })
            }.toString()

            val requestBody = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(requestBody)
                .header("Authorization", "Bearer $apiKey")
                .build()
            val resp = client.newCall(request).execute()
            val respBody = resp.body?.string() ?: "{}"
            val code = resp.code
            resp.close()

            if (code != 200) {
                Log.w(TAG, "[AI] HTTP $code: ${respBody.take(200)}")
                return@withContext null
            }

            val obj = JsonParser.parseString(respBody).asJsonObject
            val choices = obj.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val content = choices[0].asJsonObject
                    .getAsJsonObject("message")
                    ?.get("content")?.asString?.trim()
                    ?.removeSurrounding("```json")
                    ?.removeSurrounding("```")
                    ?.trim()
                if (!content.isNullOrBlank()) {
                    // 优先解析 JSON: 提取 answer 字段
                    try {
                        val answerObj = JsonParser.parseString(content).asJsonObject
                        val answer = answerObj.get("answer")?.asString?.trim()
                        if (!answer.isNullOrBlank()) {
                            Log.d(TAG, "[AI] 命中: ${question.title.take(20)}... → $answer")
                            return@withContext answer
                        }
                    } catch (_: Exception) { /* fall through */ }
                    // Fallback: 直接使用原始内容（去除引号）
                    val answer = content.trim().removeSurrounding("\"")
                    if (answer.isNotBlank()) {
                        Log.d(TAG, "[AI] 命中(raw): ${question.title.take(20)}... → $answer")
                        return@withContext answer
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "[AI] 查询异常: ${e.message}")
            null
        }
    }

    /**
     * 检查 AI/SiliconFlow 大模型连通性 (2026-06-21 修复: 使用 JSON body)。
     */
    suspend fun checkLlmConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val key = siliconflowKey.ifBlank { aiApiKey }
            val url = if (siliconflowKey.isNotBlank()) siliconflowEndpoint else "$aiBaseUrl/chat/completions"
            val model = if (siliconflowKey.isNotBlank()) siliconflowModel else aiModel
            if (key.isBlank()) return@withContext Result.failure(IllegalStateException("AI Key 未配置"))

            val bodyJson = org.json.JSONObject().apply {
                put("model", model)
                put("messages", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("role", "user")
                        put("content", "ping")
                    })
                })
                put("max_tokens", 10)
            }.toString()

            val requestBody = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(url).post(requestBody)
                .header("Authorization", "Bearer $key")
                .build()
            val resp = client.newCall(req).execute()
            val code = resp.code
            resp.close()
            if (code in 200..299) Result.success("$code OK") else Result.failure(Exception("HTTP $code"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  工具
    // ══════════════════════════════════════════════════════════════

    private fun CxQuestion.typeName(): String = when (type) {
        "single" -> "单选"
        "multiple" -> "多选"
        "judgement" -> "判断"
        "completion" -> "填空"
        "shortanswer" -> "简答"
        else -> ""
    }
}
