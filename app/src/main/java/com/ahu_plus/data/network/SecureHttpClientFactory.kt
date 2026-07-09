package com.ahu_plus.data.network

import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
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
 *   val adwmhClient = SecureHttpClientFactory.create(cookieJar = cookieJar, tls12Only = true)
 */
object SecureHttpClientFactory {

    private const val DEFAULT_TIMEOUT_SEC = 15L

    // ── 共享连接池与调度器 (2026-06-24 性能优化) ──────────────
    //
    // 整个 App 30+ 个 OkHttpClient 共享同一个 ConnectionPool/Dispatcher,
    // 让相同 host 的 HTTPS 连接得以复用,避免重复 TLS 握手 (校园网下省 100-300ms/次)。
    //
    // - sharedPool: 16 idle keep 5min。校园网常见 host 6-8 个,16 足够覆盖。
    // - sharedDispatcher: maxRequests=24 (默认 64) / maxRequestsPerHost=6 (默认 5),
    //   稍调高单 host 并发以适配 InitCoordinator 并行预热;但保守的总并发避免风控。
    //
    // 注: 不同 SSL/Cookie/Authenticator 的 client 仍是独立实例,但底层连接池是共享的 ——
    // OkHttp 用 (host, port, sslSocketFactory, hostnameVerifier, ...) 作为连接复用 key,
    // trustAll client 与默认 client 各自走自己的连接,正确隔离。
    private val sharedPool = ConnectionPool(16, 5, TimeUnit.MINUTES)
    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 24
        maxRequestsPerHost = 6
    }

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
     * 仅 TLS 1.2 的 ConnectionSpec。
     *
     * adwmh.ahu.edu.cn 的 nginx 在 TLS 1.3 下完成 SSL 握手但永不返回
     * HTTP 响应，必须降级到 TLS 1.2。通过 OkHttp 的 ConnectionSpec
     * 限制 TLS 版本是最可靠的方式（直接控制握手参数，不依赖 SSLContext
     * 或 SSLSocketFactory 的版本限制行为在各 Android 版本间的差异）。
     */
    private val tls12OnlySpec = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
        .tlsVersions(TlsVersion.TLS_1_2)
        .allEnabledCipherSuites()
        .build()

    /**
     * 创建一个 OkHttp 客户端。
     *
     * @param cookieJar Cookie 存储,null 表示无 Cookie 管理
     * @param followRedirects 是否自动跟随 302
     * @param disableGzip 是否移除 Accept-Encoding 头。CAS 服务端可能不接受 gzip,
     *                    其他业务 API 应该让 OkHttp 自动 gzip 解压以节省带宽。
     * @param extraInterceptors 额外的应用拦截器(在 network 拦截器之前)
     * @param connectTimeoutSec / readTimeoutSec 超时秒数
     * @param trustAll 是否禁用证书验证。**默认 false**,仅对 *.ahu.edu.cn 自签名证书
     *                 调用点显式声明 trustAll = true;标准 HTTPS 域名(如 api.zxs-bbs.cn /
     *                 openahu.org / 集市头像 CDN)必须保持默认值,否则一旦 MITM 接管
     *                 流量,本工厂创建的所有客户端都会变成开放代理。
     *                 历史背景:本参数曾默认 true,见 2026-06-24 安全审查改为 false。
     * @param tls12Only 是否仅启用 TLS 1.2。adwmh.ahu.edu.cn 的 nginx
     *                 在 TLS 1.3 下接受握手但永不发送 HTTP 响应，必须降级。
     * @param authenticator OkHttp Authenticator;用于 401/403 时自动重认证。
     *                      推荐传入 [SessionAuthenticator](https://one.ahu.edu.cn)。
     * @param sessionExpiredInterceptor 嗅探 HTML 表单型 session 过期(安大门户典型);
     *                                  通常是 [SessionAuthenticator.asInterceptor]。
     */
    fun create(
        cookieJar: CookieJar? = null,
        followRedirects: Boolean = true,
        disableGzip: Boolean = false,
        extraInterceptors: List<Interceptor> = emptyList(),
        connectTimeoutSec: Long = DEFAULT_TIMEOUT_SEC,
        readTimeoutSec: Long = DEFAULT_TIMEOUT_SEC,
        trustAll: Boolean = false,
        tls12Only: Boolean = false,
        authenticator: Authenticator? = null,
        sessionExpiredInterceptor: Interceptor? = null,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectionPool(sharedPool)
            .dispatcher(sharedDispatcher)
        if (trustAll) {
            builder.sslSocketFactory(trustAllSslContext.socketFactory, trustAllManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        if (tls12Only) {
            // 关键:必须保留 ConnectionSpec.CLEARTEXT,否则 OkHttp 在遇到
            // HTTP URL(即使是 302 跳转到 http://)时会抛出
            // "CLEARTEXT communication not enabled for client"。OkHttp
            // 默认的 connectionSpecs 包含 [MODERN_TLS, COMPATIBLE_TLS,
            // CLEARTEXT],直接覆盖会丢失 CLEARTEXT 槽位,导致 HTTP 跳转失败。
            builder.connectionSpecs(listOf(tls12OnlySpec, ConnectionSpec.CLEARTEXT))
        }
        builder
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .followRedirects(followRedirects)

        if (cookieJar != null) builder.cookieJar(cookieJar)

        // Session 过期嗅探拦截器(先于其他拦截器,优先识别 HTML 表单型过期)
        if (sessionExpiredInterceptor != null) {
            builder.addInterceptor(sessionExpiredInterceptor)
        }

        // 应用拦截器:可以修改请求/响应
        extraInterceptors.forEach { builder.addInterceptor(it) }

        // Authenticator(401/403 触发)
        if (authenticator != null) {
            builder.authenticator(authenticator)
        }

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
