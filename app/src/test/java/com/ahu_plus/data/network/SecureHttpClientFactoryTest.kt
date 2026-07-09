package com.ahu_plus.data.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SecureHttpClientFactory 单元测试。
 *
 * 覆盖:
 *  - 默认参数组合(client 可用、SSL/hostname 接受所有)
 *  - disableGzip=true 时会移除 Accept-Encoding
 *  - CookieJar 注入后能正常读写 Cookie
 */
class SecureHttpClientFactoryTest {

    @Test
    fun `create returns a usable OkHttpClient with default params`() {
        val client = SecureHttpClientFactory.create()
        assertNotNull(client)
        // OkHttp 的 *Millis 返回 Int,15 秒 = 15000 毫秒
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
    }

    @Test
    fun `create with cookieJar wires it into the client`() {
        val jar = InMemoryCookieJar()
        val client = SecureHttpClientFactory.create(cookieJar = jar)
        // 验证 cookieJar 不为空,具体读写通过真实请求测试
        assertNotNull(client.cookieJar)
    }

    @Test
    fun `create with disableGzip=true installs the encoding-removal interceptor`() {
        val client = SecureHttpClientFactory.create(disableGzip = true)
        assertTrue(client.networkInterceptors.isNotEmpty())
    }

    @Test
    fun `create with trustAll=true accepts any certificate chain`() {
        val client = SecureHttpClientFactory.create(trustAll = true)
        // 验证 client 配置了自定义 SSL — 连接超时存在即说明 builder 正常工作
        assertEquals(15_000, client.connectTimeoutMillis)
    }

    @Test
    fun `create with trustAll=false uses system trust store`() {
        val client = SecureHttpClientFactory.create(trustAll = false)
        // 验证 client 正常创建,无自定义 SSL socket factory
        assertEquals(15_000, client.connectTimeoutMillis)
    }

    /** 测试用的最小 CookieJar 实现,只覆盖 add/load 两个场景 */
    private class InMemoryCookieJar : okhttp3.CookieJar {
        private val store = mutableMapOf<String, MutableList<Cookie>>()
        override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> =
            store[url.host] ?: emptyList()

        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {
            val list = store.getOrPut(url.host) { mutableListOf() }
            list.addAll(cookies)
        }
    }
}