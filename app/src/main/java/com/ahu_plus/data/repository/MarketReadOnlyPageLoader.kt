package com.ahu_plus.data.repository

import com.ahu_plus.data.model.MarketReadOnlyIndexPage
import com.ahu_plus.data.model.MarketReadOnlyLoadedPage
import com.ahu_plus.data.model.MarketTopic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class MarketReadOnlyPageLoader(
    private val indexPage: suspend (String?) -> Result<MarketReadOnlyIndexPage>,
    private val detail: suspend (Long) -> Result<MarketTopic>,
    private val maxConcurrency: Int = 6,
) {
    init {
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }
    }

    suspend fun load(cursor: String?): Result<MarketReadOnlyLoadedPage> = supervisorScope {
        val page = indexPage(cursor).getOrElse { return@supervisorScope Result.failure(it) }
        val semaphore = Semaphore(maxConcurrency)
        val results = page.ids.map { topicId ->
            async {
                val result = try {
                    semaphore.withPermit { detail(topicId) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Result.failure(error)
                }
                topicId to result
            }
        }.awaitAll()
        val topics = results.mapNotNull { (_, result) -> result.getOrNull() }
        val failedIds = results.mapNotNull { (topicId, result) ->
            topicId.takeIf { result.isFailure }
        }
        Result.success(
            MarketReadOnlyLoadedPage(
                topics = topics,
                nextCursor = page.nextCursor,
                hasMore = page.hasMore,
                failedIds = failedIds,
                requestedIds = page.ids.size,
                sourceStatus = page.sourceStatus,
            )
        )
    }
}
