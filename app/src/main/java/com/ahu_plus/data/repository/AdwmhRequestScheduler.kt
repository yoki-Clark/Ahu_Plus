package com.ahu_plus.data.repository

import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.PriorityQueue

enum class AdwmhRequestPriority(val rank: Int) {
    BACKGROUND(0),
    USER_ACTION(1),
}

/** Serializes adwmh traffic while allowing user-visible work to overtake queued prefetches. */
internal class AdwmhRequestScheduler(
    private val minGapMs: Long,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
) {
    private class Ticket(
        val priority: AdwmhRequestPriority,
        val sequence: Long,
    ) {
        val ready = CompletableDeferred<Unit>()
        var started = false
        var finished = false
    }

    private val stateMutex = Mutex()
    private val queue = PriorityQueue<Ticket>(
        compareByDescending<Ticket> { it.priority.rank }.thenBy { it.sequence },
    )
    private var sequence = 0L
    private var running = false
    private var lastCompletedAtMs: Long? = null

    suspend fun <T> execute(
        priority: AdwmhRequestPriority,
        request: suspend () -> T,
    ): T {
        val ticket = stateMutex.withLock {
            Ticket(priority, sequence++).also {
                queue += it
                dispatchNextLocked()
            }
        }
        var requestStarted = false
        try {
            ticket.ready.await()
            lastCompletedAtMs?.let { completedAt ->
                val remaining = minGapMs - (nowMillis() - completedAt)
                if (remaining > 0L) delayMillis(remaining)
            }
            requestStarted = true
            return request()
        } finally {
            stateMutex.withLock {
                when {
                    ticket.finished -> Unit
                    ticket.started -> {
                        ticket.finished = true
                        if (requestStarted) lastCompletedAtMs = nowMillis()
                        running = false
                        dispatchNextLocked()
                    }
                    else -> queue.remove(ticket)
                }
            }
        }
    }

    private fun dispatchNextLocked() {
        if (running) return
        val next = queue.poll() ?: return
        running = true
        next.started = true
        next.ready.complete(Unit)
    }
}
