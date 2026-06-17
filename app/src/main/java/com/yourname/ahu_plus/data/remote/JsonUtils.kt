package com.yourname.ahu_plus.data.remote

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yourname.ahu_plus.data.GsonProvider

/**
 * 通用 JSON 解析工具。
 *
 * 集市 / 教务 / 校园码 等模块都涉及对后端响应（`{status, msg, data:{...}}`）
 * 的手工抽取。把这些工具集中在这里，减少每个 Repository 重复的样板。
 */
object JsonUtils {

    private val gson: Gson = GsonProvider.instance
    @PublishedApi internal val publishedGson: Gson get() = gson

    /**
     * 解析顶层响应，自动剥掉 `data` 包装。
     *  - 如果根是对象且带 `data` 字段，返回 `data`
     *  - 否则返回整个根
     */
    fun parseData(body: String): JsonElement = JsonParser.parseString(body).let { root ->
        if (root.isJsonObject && root.asJsonObject.has("data")) {
            root.asJsonObject.get("data")
        } else {
            root
        }
    }

    /**
     * 解析一个"列表"型响应。
     * 兼容三种后端格式：
     *  1. `{data: [...]}` 直接数组
     *  2. `{data: {rows: [...]}}` 分页
     *  3. `[{...}]` 裸数组
     */
    fun parseRows(body: String): List<JsonElement> {
        val data = parseData(body)
        return when {
            data.isJsonArray -> data.asJsonArray.toList()
            data.isJsonObject && data.asJsonObject.has("rows") ->
                data.asJsonObject.getAsJsonArray("rows").toList()
            data.isJsonObject -> listOf(data)
            else -> emptyList()
        }
    }

    /**
     * 解析顶层 status / msg 错误。
     * 业务层应该只在响应中带有 `status` 字段时调用；
     * 如果 status == "success" 返回 null，否则抛出带 msg 的异常。
     */
    fun checkStatus(body: String, defaultError: String = "请求失败"): String? {
        val root = JsonParser.parseString(body)
        if (!root.isJsonObject) return null
        val status = root.asJsonObject.get("status")?.asString ?: return null
        if (status == "success") return null
        val msg = root.asJsonObject.get("msg")?.asString ?: defaultError
        return "$defaultError：$msg"
    }

    /**
     * 解析 status + data 结构，data 可能是对象或数组。
     * 失败时返回 `Result.failure`；成功时返回解析后的对象（data 为空时为 null）。
     */
    inline fun <T> parseEnvelope(
        body: String,
        errorPrefix: String,
        transform: (JsonElement) -> T?
    ): Result<T?> {
        return runCatching {
            val root = JsonParser.parseString(body)
            if (root.isJsonObject) {
                val obj = root.asJsonObject
                val status = obj.get("status")?.asString
                if (status != null && status != "success") {
                    val msg = obj.get("msg")?.asString ?: "未知错误"
                    throw Exception("$errorPrefix：$msg")
                }
            }
            val data = parseData(body)
            transform(data)
        }
    }

    /**
     * 把列表里每个 JsonElement 反序列化为 [T]，跳过解析失败 / id == 0L 的脏数据。
     */
    inline fun <reified T> parseRowsSafe(body: String): List<T> {
        return parseRows(body).mapNotNull { element ->
            runCatching { publishedGson.fromJson(element, T::class.java) }.getOrNull()
        }.filter { !isFilteredZeroId(it) }
    }

    /**
     * 泛型过滤：对于有 `id: Long` 字段的数据类，跳过 id == 0L 的条目。
     * 通过反射取 `id` 字段；不要求严格类型。
     */
    fun isFilteredZeroId(obj: Any): Boolean {
        return runCatching {
            val idField = obj.javaClass.declaredFields.firstOrNull { it.name == "id" }
                ?: return false
            idField.isAccessible = true
            val value = idField.get(obj)
            (value as? Long) == 0L
        }.getOrDefault(false)
    }

    /** 直接反序列化整段 JSON 到指定类型。 */
    inline fun <reified T> parseObject(body: String): T? = runCatching {
        publishedGson.fromJson(body, T::class.java)
    }.getOrNull()
}
