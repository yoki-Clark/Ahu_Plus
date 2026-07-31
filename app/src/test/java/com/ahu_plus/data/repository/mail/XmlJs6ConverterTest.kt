package com.ahu_plus.data.repository.mail

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 基于 2026-07-31 实测响应的 js6 XML → JSON 转换测试。
 */
class XmlJs6ConverterTest {

    /** 实测 getAllFolders 响应(截取 3 个文件夹)。 */
    private val foldersXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <result>
        <code>S_OK</code>
        <array name="var"><object><int name="parent">0</int><object name="stats"><int name="messageCount">44</int><int name="unreadMessageCount">3</int><int name="messageSize">2889205</int></object><int name="id">1</int><string name="name">收件箱</string><object name="flags"><boolean name="system">true</boolean><boolean name="pop3">true</boolean></object></object><object><int name="parent">0</int><int name="id">3</int><string name="name">已发送</string><object name="stats"><int name="messageCount">2</int><int name="unreadMessageCount">0</int></object></object></array></result>
    """.trimIndent()

    /** 实测 listMessages 响应(1 封邮件)。 */
    private val messagesXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <result>
        <code>S_OK</code>
        <array name="var"><object><int name="fid">1</int><string name="subject">[GitHub] A third-party OAuth application has been added to your account</string><string name="tid">0a9fa614bdd00375kunme88e85822d90a8</string><string name="from">GitHub &lt;noreply@github.com&gt;</string><string name="id">AAsAZABbKp06pUdXI5mjbKpz</string><date name="receivedDate">2026-07-28 08:16:46</date><int name="size">2835</int><object name="flags"><boolean name="read">true</boolean></object><string name="to">yoki Clark &lt;74yoki@gmail.com&gt;</string></object></array></result>
    """.trimIndent()

    @Test
    fun `isJs6Xml detects xml`() {
        assertTrue(XmlJs6Converter.isJs6Xml(foldersXml))
        assertFalse(XmlJs6Converter.isJs6Xml("""{"code":0,"success":true}"""))
    }

    @Test
    fun `folders xml converts to var array with stats`() {
        val json = XmlJs6Converter.toJson(foldersXml)
        assertEquals("S_OK", json.get("code")?.asString)
        val varArr = json.getAsJsonArray("var")
        assertEquals(2, varArr.size())

        val inbox = varArr[0].asJsonObject
        assertEquals(1, inbox.get("id")?.asInt)
        assertEquals("收件箱", inbox.get("name")?.asString)
        assertEquals(3, inbox.getAsJsonObject("stats").get("unreadMessageCount").asInt)
        assertEquals(44, inbox.getAsJsonObject("stats").get("messageCount").asInt)
        assertTrue(inbox.getAsJsonObject("flags").get("system").asBoolean)
        assertTrue(inbox.getAsJsonObject("flags").get("pop3").asBoolean)
    }

    @Test
    fun `messages xml converts with address strings and date`() {
        val json = XmlJs6Converter.toJson(messagesXml)
        val msg = json.getAsJsonArray("var")[0].asJsonObject
        assertEquals("AAsAZABbKp06pUdXI5mjbKpz", msg.get("id")?.asString)
        assertEquals("GitHub <noreply@github.com>", msg.get("from")?.asString)
        assertEquals("2026-07-28 08:16:46", msg.get("receivedDate")?.asString)
        assertEquals(2835L, msg.get("size")?.asLong)
        assertTrue(msg.getAsJsonObject("flags").get("read").asBoolean)
    }

    @Test
    fun `unknown element falls back to text`() {
        val json = XmlJs6Converter.toJson("<result><code>S_OK</code><var>plain</var></result>")
        assertEquals("plain", json.get("var")?.asString)
    }

    @Test
    fun `json output remains parseable by gson`() {
        val json = XmlJs6Converter.toJson(messagesXml)
        // 转换结果必须能被 Gson 再解析(上层解析逻辑直接用 Gson)
        val roundTrip = JsonParser.parseString(json.toString()).asJsonObject
        assertEquals("S_OK", roundTrip.get("code")?.asString)
        assertTrue(roundTrip.getAsJsonArray("var").size() >= 1)
    }
}
