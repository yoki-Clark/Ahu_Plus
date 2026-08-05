package com.ahu_plus.data.repository

import com.ahu_plus.data.model.MarketReadOnlyIndexPage
import com.ahu_plus.data.model.MarketReadOnlyIndexStatus
import com.ahu_plus.data.model.MarketTopic
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MarketReadOnlyPageLoaderTest {

    @Test
    fun `loads details with max six concurrency and keeps index order`() = runTest {
        val ids = (1L..20L).toList()
        val current = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val loader = MarketReadOnlyPageLoader(
            indexPage = {
                Result.success(
                    MarketReadOnlyIndexPage(
                        ids = ids,
                        nextCursor = "next",
                        hasMore = true,
                        sourceStatus = MarketReadOnlyIndexStatus.READY,
                        generatedAt = "2026-08-05T12:00:00+08:00",
                    )
                )
            },
            detail = { id ->
                val active = current.incrementAndGet()
                maximum.updateAndGet { old -> maxOf(old, active) }
                delay(10)
                current.decrementAndGet()
                Result.success(MarketTopic(id = id, content = "topic-$id"))
            },
        )

        val loaded = loader.load(null).getOrThrow()

        assertEquals(ids, loaded.topics.map { it.id })
        assertEquals("next", loaded.nextCursor)
        assertEquals(20, loaded.requestedIds)
        assertTrue(maximum.get() <= 6)
    }

    @Test
    fun `keeps successful details when individual requests fail`() = runTest {
        val mutex = Mutex()
        val loader = MarketReadOnlyPageLoader(
            indexPage = {
                Result.success(
                    MarketReadOnlyIndexPage(
                        ids = listOf(1L, 2L, 3L),
                        nextCursor = null,
                        hasMore = false,
                        sourceStatus = MarketReadOnlyIndexStatus.READY,
                        generatedAt = "2026-08-05T12:00:00+08:00",
                    )
                )
            },
            detail = { id ->
                mutex.withLock {
                    if (id == 2L) Result.failure(IllegalStateException("missing"))
                    else Result.success(MarketTopic(id = id, content = "topic-$id"))
                }
            },
        )

        val loaded = loader.load(null).getOrThrow()

        assertEquals(listOf(1L, 3L), loaded.topics.map { it.id })
        assertEquals(listOf(2L), loaded.failedIds)
    }
}
