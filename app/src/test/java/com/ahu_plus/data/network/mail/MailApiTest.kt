package com.ahu_plus.data.network.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MailApi] URL 构造与公共参数的单元测试。
 *
 * 纯 JVM 测试,不依赖 Robolectric。
 */
class MailApiTest {

    @Test
    fun `businessUrl 构造完整业务 API URL`() {
        val url = MailApi.businessUrl("/commonweb/account/getAccountBaseInfo")
        assertEquals(
            "https://wvpn.ahu.edu.cn/http/${MailApi.HEX_MAIL}/commonweb/account/getAccountBaseInfo",
            url,
        )
    }

    @Test
    fun `businessGetUrl 包含公共查询参数`() {
        val url = MailApi.businessGetUrl(
            "/cowork/api/biz/enter/accountInfo",
            deviceId = "test-device-id",
        )
        assertTrue("URL 应包含 _host", url.contains("_host=${MailApi.MAIL_HOST}"))
        assertTrue("URL 应包含 _deviceId", url.contains("_deviceId=test-device-id"))
        assertTrue("URL 应包含 _appName", url.contains("_appName=${MailApi.APP_NAME}"))
        assertTrue("URL 应包含 vpn-12-o1", url.contains("vpn-12-o1-${MailApi.MAIL_HOST}"))
    }

    @Test
    fun `businessGetUrl 额外参数正确拼接`() {
        val url = MailApi.businessGetUrl(
            "/commonweb/account/getAccountBaseInfo",
            deviceId = "dev",
            extra = mapOf("domain" to "stu.ahu.edu.cn", "account_name" to "g62314006"),
        )
        assertTrue("URL 应包含 domain 参数", url.contains("domain=stu.ahu.edu.cn"))
        assertTrue("URL 应包含 account_name 参数", url.contains("account_name=g62314006"))
    }

    @Test
    fun `wvpnSsoUrl 把网易 ssourl 转为 wvpn 反代 URL`() {
        val ssourl = "https://entryhz.qiye.163.com/domain/oa/Entry?" +
            "domain=stu.ahu.edu.cn&account_name=g62314006&time=1785463870171&enc=abc123"
        val url = MailApi.wvpnSsoUrl(ssourl)
        assertTrue("URL 应以 wvpn.ahu.edu.cn 开头", url.startsWith("https://wvpn.ahu.edu.cn/"))
        assertTrue("URL 应包含 hex_one_entry", url.contains(MailApi.HEX_ONE_ENTRY))
        assertTrue("URL 应包含 /domain/oa/Entry", url.contains(MailApi.PATH_DOMAIN_OA_ENTRY))
        assertTrue("URL 应保留 query 参数", url.contains("domain=stu.ahu.edu.cn"))
        assertTrue("URL 应保留 enc 参数", url.contains("enc=abc123"))
    }

    @Test
    fun `wvpnSsoUrl 无 query 时保持路径`() {
        val url = MailApi.wvpnSsoUrl("https://entryhz.qiye.163.com/domain/oa/Entry")
        assertEquals(
            "https://wvpn.ahu.edu.cn/https/${MailApi.HEX_ONE_ENTRY}/domain/oa/Entry",
            url,
        )
    }

    @Test
    fun `generateSsoUrlUrl 构造 generateSsoUrl 入口`() {
        val url = MailApi.generateSsoUrlUrl()
        assertTrue("URL 应包含 hex_one", url.contains(MailApi.HEX_ONE))
        assertTrue("URL 应包含 generateSsoUrl", url.contains("generateSsoUrl"))
    }

    @Test
    fun `cookieBridgeUrl 构造 cookie 中转桥 URL`() {
        val url = MailApi.cookieBridgeUrl(
            host = "mail.stu.ahu.edu.cn",
            scheme = "http",
            path = "/static/sirius-web/jump/index.html",
            timestamp = 1785463872801,
        )
        assertTrue("URL 应包含 method=get", url.contains("method=get"))
        assertTrue("URL 应包含 host=mail.stu.ahu.edu.cn", url.contains("host=mail.stu.ahu.edu.cn"))
        assertTrue("URL 应包含 vpn_timestamp", url.contains("vpn_timestamp=1785463872801"))
    }

    @Test
    fun `commonQuery 包含全部公共参数`() {
        val query = MailApi.commonQuery(deviceId = "test-id")
        assertEquals(11, query.size)
        assertEquals("", query["vpn-12-o1-${MailApi.MAIL_HOST}"])
        assertEquals(MailApi.MAIL_HOST, query["_host"])
        assertEquals("web", query["p"])
        assertEquals("test-id", query["_deviceId"])
        assertEquals("chrome", query["_device"])
        assertEquals("web", query["_system"])
        assertEquals(MailApi.APP_NAME, query["_appName"])
        assertEquals(MailApi.VERSION, query["_version"])
    }

    @Test
    fun `常量值与抓包数据一致`() {
        // hex 段是 wrdvpnisthebest! 加密产物,作为固定常量
        assertEquals("wvpn.ahu.edu.cn", MailApi.HOST)
        assertEquals("mail.stu.ahu.edu.cn", MailApi.MAIL_HOST)
        assertEquals("stu.ahu.edu.cn", MailApi.MAIL_DOMAIN)
        assertEquals("sirius", MailApi.PRODUCT)
        assertEquals("sirius-web", MailApi.APP_NAME)
        assertEquals("1.65.2", MailApi.VERSION)
        // hex 段长度应足够长(至少 60 字符)
        assertTrue("HEX_MAIL 长度应 > 60", MailApi.HEX_MAIL.length > 60)
    }
}
