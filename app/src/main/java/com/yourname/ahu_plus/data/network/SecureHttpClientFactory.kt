package com.yourname.ahu_plus.data.network

import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 统一的 OkHttp 客户端工厂。
 *
 * 把 trust-all SSL、超时、UA/Accept-Encoding 处理等横切关注点集中起来,
 * 避免每个 Repository 重复实现相同样板代码。
 *
 * 使用方式:
 *   val client = SecureHttpClientFactory.create(cookieJar = cookieJar)
 *   val casClient = SecureHttpClientFactory.create(cookieJar = cookieJar, disableGzip = true)
 */
object SecureHttpClientFactory {

    private const val DEFAULT_TIMEOUT_SEC = 15L

    // ── 共享的 trust-all SSL(仅用于 *.ahu.edu.cn 自签名证书)────

    private val trustAllManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val trustAllSslContext: SSLContext = run {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        ctx
    }

    /**
     * 创建一个 OkHttp 客户端。
     *
     * @param cookieJar Cookie 存储,null 表示无 Cookie 管理
     * @param followRedirects 是否自动跟随 302
     * @param disableGzip 是否移除 Accept-Encoding 头。CAS 服务端可能不接受 gzip,
     *                    其他业务 API 应该让 OkHttp 自动 gzip 解压以节省带宽。
     * @param extraInterceptors 额外的应用拦截器(在 network 拦截器之前)
     * @param connectTimeoutSec / readTimeoutSec 超时秒数
     * @param trustAll 是否禁用证书验证。仅对 *.ahu.edu.cn 自签名证书场景使用;
     *                 标准 HTTPS 域名(如 api.zxs-bbs.cn)应使用系统信任库。
     */
    fun create(
        cookieJar: CookieJar? = null,
        followRedirects: Boolean = true,
        disableGzip: Boolean = false,
        extraInterceptors: List<Interceptor> = emptyList(),
        connectTimeoutSec: Long = DEFAULT_TIMEOUT_SEC,
        readTimeoutSec: Long = DEFAULT_TIMEOUT_SEC,
        trustAll: Boolean = true
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
        if (trustAll) {
            builder.sslSocketFactory(trustAllSslContext.socketFactory, trustAllManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        builder
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .followRedirects(followRedirects)

        if (cookieJar != null) builder.cookieJar(cookieJar)

        // 应用拦截器:可以修改请求/响应
        extraInterceptors.forEach { builder.addInterceptor(it) }

        // 网络拦截器:移除 Accept-Encoding(仅 CAS 流程需要)
        if (disableGzip) {
            builder.addNetworkInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .removeHeader("Accept-Encoding")
                    .build()
                chain.proceed(req)
            }
        }

        return builder.build()
    }
}