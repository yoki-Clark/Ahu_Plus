package com.ahu_plus.data.developer

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.jsoup.Jsoup

enum class DeveloperPayloadType { EMPTY, JSON, HTML, TEXT, INVALID_JSON }

data class DeveloperPayloadAnalysis(
    val type: DeveloperPayloadType,
    val summary: String,
    val details: List<String>,
    val formatted: String,
)

object DeveloperPayloadAnalyzer {
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    fun analyze(input: String): DeveloperPayloadAnalysis {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return DeveloperPayloadAnalysis(DeveloperPayloadType.EMPTY, "没有输入内容", emptyList(), "")
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return runCatching {
                val element = JsonParser.parseString(trimmed)
                val details = when {
                    element.isJsonArray -> listOf("顶层类型：数组", "元素数量：${element.asJsonArray.size()}")
                    element.isJsonObject -> listOf(
                        "顶层类型：对象",
                        "字段数量：${element.asJsonObject.size()}",
                        "字段：${element.asJsonObject.keySet().take(30).joinToString()}",
                    )
                    else -> listOf("顶层类型：${element.javaClass.simpleName}")
                }
                DeveloperPayloadAnalysis(
                    type = DeveloperPayloadType.JSON,
                    summary = "JSON 解析成功",
                    details = details,
                    formatted = prettyGson.toJson(element),
                )
            }.getOrElse { error ->
                DeveloperPayloadAnalysis(
                    type = DeveloperPayloadType.INVALID_JSON,
                    summary = "JSON 解析失败",
                    details = listOf(error.message ?: error.javaClass.simpleName),
                    formatted = trimmed,
                )
            }
        }

        if (trimmed.startsWith("<") || "<html" in trimmed.lowercase() || "<!doctype" in trimmed.lowercase()) {
            val document = Jsoup.parse(trimmed)
            val forms = document.select("form")
            val inputs = document.select("input[name]").map { it.attr("name") }.distinct()
            val links = document.select("a[href]")
            val scripts = document.select("script")
            val details = buildList {
                add("标题：${document.title().ifBlank { "(无)" }}")
                add("表单：${forms.size}，输入项：${inputs.size}")
                add("链接：${links.size}，脚本：${scripts.size}")
                if (inputs.isNotEmpty()) add("输入字段：${inputs.take(30).joinToString()}")
                if (inputs.any { it.equals("lt", ignoreCase = true) }) add("检测到 CAS 登录表单字段 lt")
            }
            return DeveloperPayloadAnalysis(
                type = DeveloperPayloadType.HTML,
                summary = "HTML 解析成功",
                details = details,
                formatted = document.outerHtml(),
            )
        }

        return DeveloperPayloadAnalysis(
            type = DeveloperPayloadType.TEXT,
            summary = "普通文本",
            details = listOf(
                "字符数：${trimmed.length}",
                "行数：${trimmed.lineSequence().count()}",
            ),
            formatted = trimmed,
        )
    }
}
