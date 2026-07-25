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
}
