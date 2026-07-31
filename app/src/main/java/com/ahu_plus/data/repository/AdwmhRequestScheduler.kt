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

    /**
     * 串行执行一个 adwmh 请求。
     *
     * @param enforceGap 是否在请求前后强制 [minGapMs] 间隔。认证类端点(login/authcode)
     *  对突发请求敏感、易触发服务端限流,传 true 维持限速保护;读端点(qrcode/yue/session)
     *  单次请求不触发限流,传 false 可省掉 1.5s 白等。无论取值如何,[running] 串行化
     *  (同一时刻只有一个请求在执行)始终保留,避免并发请求导致服务端状态混乱。
     */
    suspend fun <T> execute(
        priority: AdwmhRequestPriority,
        enforceGap: Boolean = true,
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
            // 仅认证类端点需要遵守最小间隔;读端点之间不强制等待。
            if (enforceGap) {
                lastCompletedAtMs?.let { completedAt ->
                    val remaining = minGapMs - (nowMillis() - completedAt)
                    if (remaining > 0L) delayMillis(remaining)
                }
            }
            requestStarted = true
            return request()
        } finally {
            stateMutex.withLock {
                when {
                    ticket.finished -> Unit
                    ticket.started -> {
                        ticket.finished = true
                        // 仅认证类端点完成时刷新间隔基准;读端点不更新,避免拖慢后续读端点。
                        if (requestStarted && enforceGap) lastCompletedAtMs = nowMillis()
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
