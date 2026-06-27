package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.BuildConfig
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.Announcement
import com.yourname.ahu_plus.data.model.AnnouncementFeed
import com.yourname.ahu_plus.data.network.ResilientDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 开发者公告数据源 — 从 Gitee 仓库拉取 announcements.json,缓存到 SessionManager。
 *
 * 数据流(与 [ExamDataRepository] 完全同构,零登录):
 *   yao-enqi/ahu-plus-update (Gitee)
 *     └─ announcements/announcements.json
 *        → AnnouncementRepository.fetchRemote()
 *        → SessionManager.saveAnnouncementsJson()
 *        → getActiveAnnouncements() 过滤生效窗口/版本/已忽略 + 按 priority 排序
 *        → AnnouncementManager 入队逐条弹窗
 *
 * 网络配置照搬 ExamDataRepository:HTTP/1.1 + COMPATIBLE_TLS + ResilientDns,
 * 绕开部分国产 ROM(vivo 等)的 HTTP/2 / TLS 协商问题。
 */
class AnnouncementRepository(
    private val sessionManager: SessionManager
) {
    private val gson = GsonProvider.instance

    /** 策略 A: HTTP/1.1 + COMPATIBLE_TLS + 手动 302。 */
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionSpecs(listOf(ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
        .dns(ResilientDns)
        .build()

    /** 策略 B: OkHttp 默认自动跟 302 + HTTP/2 + MODERN_TLS 兜底。 */
    private val fallbackClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dns(ResilientDns)
        .build()

    private val userAgent = "Ahu_Plus/${BuildConfig.VERSION_NAME} (Android)"

    companion object {
        private const val TAG = "AnnouncementRepo"

        /** Gitee 公开仓库 raw URL。维护者更新公告后覆盖同一文件。 */
        const val REMOTE_URL =
            "https://gitee.com/yao-enqi/ahu-plus-update/raw/master/announcements/announcements.json"

        /** JSON schema 版本号,供客户端校验。 */
        const val SCHEMA_VERSION = 1
    }

    // ─────────────────────────────────────────────────────────
    // 拉取与缓存
    // ─────────────────────────────────────────────────────────

    /**
     * 从 Gitee 拉取最新公告 JSON,写入缓存。
     * 失败时返回 Result.failure,不修改已有缓存。
     */
    suspend fun fetchRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        // 策略 A: HTTP/1.1 + COMPATIBLE_TLS + 手动 302
        val strategyA = runCatching { fetchWithManualRedirect(client) }
        if (strategyA.isSuccess) return@withContext Result.success(Unit)
        Log.w(TAG, "策略 A 失败: ${strategyA.exceptionOrNull()?.message}")

        // 策略 B: OkHttp 默认自动跟 302
        val strategyB = runCatching { fetchWithAutoRedirect(fallbackClient) }
        if (strategyB.isSuccess) return@withContext Result.success(Unit)
        Log.w(TAG, "策略 B 失败: ${strategyB.exceptionOrNull()?.message}")

        val err = strategyA.exceptionOrNull() ?: strategyB.exceptionOrNull()
        Log.e(TAG, "fetchRemote 全部失败: ${err?.message}", err)
        Result.failure(err ?: Exception("未知错误"))
    }

    /** 策略 A:手动跟踪 302,每跳打印日志。 */
    private suspend fun fetchWithManualRedirect(client: OkHttpClient) {
        var currentUrl = REMOTE_URL.toHttpUrl()
        var redirectCount = 0
        val maxRedirects = 5

        while (true) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json, */*;q=0.8")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isRedirect) {
                val location = response.header("Location")
                    ?: error("HTTP ${response.code} 但无 Location 头")
                response.close()
                val nextUrl = currentUrl.resolve(location)
                    ?: error("无法解析 Location: $location")
                currentUrl = nextUrl
                redirectCount++
                check(redirectCount <= maxRedirects) { "重定向超过 $maxRedirects 次" }
                continue
            }

            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                error("HTTP $code (final=$currentUrl)")
            }

            val body = response.body?.string() ?: error("empty body")
            persist(body)
            return
        }
    }

    /** 策略 B:让 OkHttp 自动跟 302(HTTP/2 + MODERN_TLS)。 */
    private suspend fun fetchWithAutoRedirect(client: OkHttpClient) {
        val request = Request.Builder()
            .url(REMOTE_URL)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, */*;q=0.8")
            .get()
            .build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            error("HTTP $code (final=${response.request.url})")
        }

        val body = response.body?.string() ?: error("empty body")
        persist(body)
    }

    /** 校验 schema 后落盘。 */
    private suspend fun persist(body: String) {
        val parsed = gson.fromJson(body, AnnouncementFeed::class.java)
            ?: error("invalid JSON")
        if (parsed.version != SCHEMA_VERSION) {
            Log.w(TAG, "schema version mismatch: ${parsed.version} (expected $SCHEMA_VERSION), still use")
        }
        sessionManager.saveAnnouncementsJson(body)
        Log.w(TAG, "✓ 成功: ${parsed.announcements.size} announcements, ${body.length} bytes")
    }

    // ─────────────────────────────────────────────────────────
    // 读取与过滤
    // ─────────────────────────────────────────────────────────

    /** 解析缓存的全部公告(未过滤);无缓存 / 解析失败返回空。 */
    private fun getCachedFeed(): AnnouncementFeed? {
        val json = sessionManager.getAnnouncementsJson() ?: return null
        return runCatching { gson.fromJson(json, AnnouncementFeed::class.java) }.getOrNull()
    }

    /**
     * 返回缓存里的**全部**公告(不过滤过期/已忽略),按 priority 降序排序。
     * 供「我的 → 通知公告」历史列表使用。
     */
    fun getAllAnnouncements(): List<Announcement> {
        val feed = getCachedFeed() ?: return emptyList()
        return feed.announcements
            .filter { it.id.isNotBlank() }
            .sortedWith(compareByDescending<Announcement> { it.priority }.thenByDescending { it.id })
    }

    /**
     * 返回当前应展示的公告列表,已过滤掉:未生效 / 已过期 / 版本不匹配 / 已忽略,
     * 并按 priority 降序(高优先级先弹)、再按 id 稳定排序。
     *
     * @param nowMillis 当前时间(默认 System.currentTimeMillis,测试可注入)
     */
    fun getActiveAnnouncements(
        nowMillis: Long = System.currentTimeMillis()
    ): List<Announcement> {
        val feed = getCachedFeed() ?: return emptyList()
        val versionCode = BuildConfig.VERSION_CODE
        val dismissed = sessionManager.getDismissedAnnouncementIds()
        val fetchedAt = sessionManager.getAnnouncementsFetchedAt()
        return feed.announcements
            .filter { it.isActive(nowMillis, versionCode, dismissed, fetchedAt) }
            .sortedWith(compareByDescending<Announcement> { it.priority }.thenBy { it.id })
    }
}
