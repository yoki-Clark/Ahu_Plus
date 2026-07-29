package com.ahu_plus.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AdwmhRequestSchedulerTest {
    @Test
    fun `user action overtakes queued background work`() = runTest {
        var now = 0L
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val scheduler = AdwmhRequestScheduler(
            minGapMs = 1_500L,
            nowMillis = { now },
            delayMillis = { now += it },
        )

        val first = async {
            scheduler.execute(AdwmhRequestPriority.BACKGROUND) {
                order += "background-running"
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        firstStarted.await()

        val queuedBackground = async {
            scheduler.execute(AdwmhRequestPriority.BACKGROUND) {
                order += "background-queued"
            }
        }
        val paymentCode = async {
            scheduler.execute(AdwmhRequestPriority.USER_ACTION) {
                order += "payment-code"
            }
        }

        releaseFirst.complete(Unit)
        advanceUntilIdle()
        first.await()
        paymentCode.await()
        queuedBackground.await()

        assertEquals(
            listOf("background-running", "payment-code", "background-queued"),
            order,
        )
    }

    @Test
    fun `scheduler keeps minimum gap between requests`() = runTest {
        var now = 100L
        val starts = mutableListOf<Long>()
        val scheduler = AdwmhRequestScheduler(
            minGapMs = 1_500L,
            nowMillis = { now },
            delayMillis = { now += it },
        )

        scheduler.execute(AdwmhRequestPriority.USER_ACTION) {
            starts += now
            now += 200L
        }
        scheduler.execute(AdwmhRequestPriority.USER_ACTION) {
            starts += now
        }

        assertEquals(listOf(100L, 1_800L), starts)
    }

    @Test
    fun `read endpoints skip the minimum gap`() = runTest {
        var now = 100L
        val starts = mutableListOf<Long>()
        val scheduler = AdwmhRequestScheduler(
            minGapMs = 1_500L,
            nowMillis = { now },
            delayMillis = { now += it },
        )

        // 读端点（qrcode/yue）不强制间隔：背靠背执行，无 1.5s 等待
        scheduler.execute(AdwmhRequestPriority.USER_ACTION, enforceGap = false) {
            starts += now
            now += 200L
        }
        scheduler.execute(AdwmhRequestPriority.USER_ACTION, enforceGap = false) {
            starts += now
        }

        assertEquals(listOf(100L, 300L), starts)
    }

    @Test
    fun `read endpoint does not delay a following auth request`() = runTest {
        var now = 100L
        val starts = mutableListOf<Long>()
        val scheduler = AdwmhRequestScheduler(
            minGapMs = 1_500L,
            nowMillis = { now },
            delayMillis = { now += it },
        )

        // 读端点完成后不更新 lastCompletedAtMs，故紧接着的认证端点无需等待
        scheduler.execute(AdwmhRequestPriority.BACKGROUND, enforceGap = false) {
            starts += now
            now += 200L
        }
        scheduler.execute(AdwmhRequestPriority.USER_ACTION, enforceGap = true) {
            starts += now
        }

        assertEquals(listOf(100L, 300L), starts)
    }

    @Test
    fun `auth endpoint still enforces gap before a following read`() = runTest {
        var now = 100L
        val starts = mutableListOf<Long>()
        val scheduler = AdwmhRequestScheduler(
            minGapMs = 1_500L,
            nowMillis = { now },
            delayMillis = { now += it },
        )

        // 认证端点建立 gap 基准；其后的读端点虽豁免自身间隔，但认证端点已写入 lastCompletedAtMs，
        // 读端点 enforceGap=false 时不读该基准，故仍立刻执行——这正是读端点豁免的初衷。
        scheduler.execute(AdwmhRequestPriority.USER_ACTION, enforceGap = true) {
            starts += now
            now += 200L
        }
        scheduler.execute(AdwmhRequestPriority.USER_ACTION, enforceGap = false) {
            starts += now
        }

        assertEquals(listOf(100L, 300L), starts)
    }
}
