package com.ahu_plus.data.repository.mail

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 网易 js6 接口的 XML 响应 → JsonObject 转换器。
 *
 * 2026-07-31 实测:js6/s 的 mbox:* 接口返回 XML(非 JSON),格式:
 * ```xml
 * <result><code>S_OK</code><array name="var"><object>
 *   <int name="fid">1</int><string name="name">收件箱</string>...
 * </object></array></result>
 * ```
 *
 * 转换规则:
 * - `<code>S_OK</code>` → `{"code": "S_OK"}`
 * - `<int|string|boolean|date name="X">v</int>` → `{"X": v}`
 * - `<object name="X">…</object>` → `{"X": {...}}`
 * - `<array name="X">…</array>` → `{"X": [...]}`
 * - array 内无 name 的子元素 → 数组项
 *
 * 转换后上层解析逻辑按 JSON 继续,字段名与 XML 一致(如 stats.unreadMessageCount)。
 */
object XmlJs6Converter {

    /** 判断响应是否为 js6 XML(含 <result> 根节点,XML 声明头可能在前)。 */
    fun isJs6Xml(body: String): Boolean =
        body.trimStart().startsWith("<") && body.contains("<result")

    /** 把 js6 XML 转为顶层 JsonObject(`{"code": "S_OK", ...}`)。 */
    fun toJson(body: String): JsonObject {
        val doc: Document = Jsoup.parse(body, "", org.jsoup.parser.Parser.xmlParser())
        val root = doc.selectFirst("result")
            ?: throw MailApiException(-1, "js6 XML 缺少 <result> 根节点")
        val out = JsonObject()
        for (child in root.children()) {
            convertChild(child, out)
        }
        return out
    }

    private fun convertChild(child: Element, out: JsonObject) {
        val name = child.attr("name").ifEmpty { child.tagName() }
        when (child.tagName()) {
            "code" -> out.add("code", JsonPrimitive(child.text().trim()))
            "int", "date" -> {
                val raw = child.text().trim()
                val num = raw.toLongOrNull()
                out.add(name, if (num != null) JsonPrimitive(num) else JsonPrimitive(raw))
            }
            "string" -> out.add(name, JsonPrimitive(child.text()))
            "boolean" -> out.add(name, JsonPrimitive(child.text().trim().equals("true", ignoreCase = true)))
            "object" -> out.add(name, convertObject(child))
            "array" -> out.add(name, convertArray(child))
            else -> out.add(name, JsonPrimitive(child.text()))
        }
    }

    private fun convertObject(el: Element): JsonObject {
        val obj = JsonObject()
        for (child in el.children()) {
            convertChild(child, obj)
        }
        return obj
    }

    private fun convertArray(el: Element): JsonArray {
        val arr = JsonArray()
        for (child in el.children()) {
            when (child.tagName()) {
                "object" -> arr.add(convertObject(child))
                "array" -> arr.add(convertArray(child))
                "int" -> child.text().trim().toLongOrNull()?.let { arr.add(JsonPrimitive(it)) }
                    ?: arr.add(JsonPrimitive(child.text()))
                "string" -> arr.add(JsonPrimitive(child.text()))
                "boolean" -> arr.add(JsonPrimitive(child.text().trim().equals("true", ignoreCase = true)))
                else -> arr.add(JsonPrimitive(child.text()))
            }
        }
        return arr
    }
}
