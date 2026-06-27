package com.yourname.ahu_plus.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Announcement.isActive 单元测试,重点覆盖 2026-06-27 的"默认 1 天有效期"行为。
 *
 * 默认有效期基准 = `startAt`(非空)否则 `fetchedAt`(JSON 首次写入客户端的时刻);
 * `fetchedAt=0` 视为"已过期"(防止老缓存复活)。
 */
class AnnouncementTest {

    private val oneDay = 24L * 60 * 60 * 1000
    // 维护者推送时刻(客户端首次写入 JSON 的时间)
    private val fetchedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .parse("2026-06-27 12:00:00")!!.time
    // 用户启动 App 的时间
    private val now = fetchedAt + 6 * 60 * 60 * 1000  // 推送后 6 小时

    private fun ann(
        id: String = "test-1",
        startAt: String = "",
        expireAt: String = "",
        dismissible: Boolean = true,
        minVersionCode: Int = 0,
        maxVersionCode: Int = 0
    ) = Announcement(
        id = id,
        title = "t",
        content = "c",
        priority = 0,
        startAt = startAt,
        expireAt = expireAt,
        actionLabel = "",
        actionUrl = "",
        minVersionCode = minVersionCode,
        maxVersionCode = maxVersionCode,
        dismissible = dismissible
    )

    private fun Announcement.checkActive(
        nowMillis: Long = now,
        dismissed: Set<String> = emptySet(),
        fetchTs: Long = fetchedAt
    ) = isActive(nowMillis, versionCode = 1, dismissedIds = dismissed, fetchedAt = fetchTs)

    // ── 默认 1 天有效期(2026-06-27 新行为) ───────────────────────────

    @Test
    fun `空 startAt 空 expireAt - 推送 6h 后仍展示`() {
        // 基准 = fetchedAt + 1 天;now 距 fetched 仅 6h
        assertTrue(ann().checkActive())
    }

    @Test
    fun `空 startAt 空 expireAt - 推送 23h59m 后仍展示`() {
        val justBefore = fetchedAt + oneDay - 60_000L
        assertTrue(ann().checkActive(nowMillis = justBefore))
    }

    @Test
    fun `空 startAt 空 expireAt - 推送 1d1m 后过期`() {
        val justAfter = fetchedAt + oneDay + 60_000L
        assertFalse(ann().checkActive(nowMillis = justAfter))
    }

    @Test
    fun `空 startAt 空 expireAt - 用户反复启动不会续命`() {
        // 关键正确性:nowMillis 变化不会重置 1 天基准
        val firstLaunch = fetchedAt + 2 * 60 * 60 * 1000
        val secondLaunch = fetchedAt + 23 * 60 * 60 * 1000  // 还在窗口内
        val thirdLaunch = fetchedAt + 25 * 60 * 60 * 1000   // 已过期
        assertTrue(ann().checkActive(nowMillis = firstLaunch))
        assertTrue(ann().checkActive(nowMillis = secondLaunch))
        assertFalse(ann().checkActive(nowMillis = thirdLaunch))
    }

    @Test
    fun `startAt 未来 空 expireAt - 基准 startAt 算 1 天`() {
        // startAt = fetchedAt + 12h,默认 expire = startAt + 1 天
        val future = fetchedAt + 12 * 60 * 60 * 1000
        val startStr = fmt(future)
        // 当前 now (= fetchedAt + 6h) 还没到 start,不展示
        assertFalse(ann(startAt = startStr).checkActive())
        // 到了 start 后 1 分钟 → 距默认 expire 仍有 12h → 展示
        assertTrue(ann(startAt = startStr).checkActive(nowMillis = future + 60_000L))
        // start + 1d + 1m → 过期
        assertFalse(ann(startAt = startStr).checkActive(nowMillis = future + oneDay + 60_000L))
    }

    @Test
    fun `显式 expireAt 长于 1 天 - 仍按显式值`() {
        val startStr = fmt(fetchedAt)
        val expireStr = fmt(fetchedAt + 7 * oneDay)
        // 推送后 5 天:超过默认 1 天但仍在显式 7 天窗口内
        assertTrue(
            ann(startAt = startStr, expireAt = expireStr)
                .checkActive(nowMillis = fetchedAt + 5 * oneDay)
        )
        // 推送后 8 天:过期
        assertFalse(
            ann(startAt = startStr, expireAt = expireStr)
                .checkActive(nowMillis = fetchedAt + 8 * oneDay)
        )
    }

    // ── fetchedAt=0 兜底(老缓存) ──────────────────────────────────

    @Test
    fun `fetchedAt=0 - 默认空 expireAt 视为已过期(防老缓存复活)`() {
        assertFalse(ann().checkActive(fetchTs = 0L))
    }

    @Test
    fun `fetchedAt=0 - 但显式 expireAt 仍生效`() {
        val expireStr = fmt(fetchedAt + oneDay)
        assertTrue(ann(expireAt = expireStr).checkActive(fetchTs = 0L))
    }

    // ── 原有行为保留 ───────────────────────────────────────────────

    @Test
    fun `dismissible=true 已被忽略 - 不展示`() {
        assertFalse(ann(id = "dup", dismissible = true).checkActive(dismissed = setOf("dup")))
    }

    @Test
    fun `dismissible=false 已被忽略 - 仍展示(紧急)`() {
        assertTrue(ann(id = "urgent", dismissible = false).checkActive(dismissed = setOf("urgent")))
    }

    @Test
    fun `id 空白 - 不展示`() {
        assertFalse(ann(id = "").checkActive())
    }

    @Test
    fun `minVersionCode 过滤`() {
        assertFalse(ann(minVersionCode = 100).checkActive())
    }

    private fun fmt(ts: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(ts)
}
