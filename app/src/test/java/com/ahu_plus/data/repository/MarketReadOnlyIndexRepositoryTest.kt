package com.ahu_plus.data.repository

import com.ahu_plus.data.model.MarketReadOnlyIndexStatus
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketReadOnlyIndexRepositoryTest {

    @Test
    fun `getPage parses a server response and always requests twenty ids`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"status":"success","data":{"ids":[3,2,1],"nextCursor":"next","hasMore":true,"sourceStatus":"ready","generatedAt":"2026-08-05T12:00:00+08:00"}}"""
            )
        )
        server.start()
        try {
            val repository = MarketReadOnlyIndexRepository(
                baseUrl = server.url("/").toString().trimEnd('/'),
                client = OkHttpClient(),
            )

            val page = kotlinx.coroutines.runBlocking { repository.getPage("cursor").getOrThrow() }
            val request = server.takeRequest()

            assertEquals("/market/readonly/feed?limit=20&cursor=cursor", request.path)
            assertEquals(listOf(3L, 2L, 1L), page.ids)
            assertEquals(MarketReadOnlyIndexStatus.READY, page.sourceStatus)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `getPage treats index initializing as a non-error status`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(503).setBody(
                """{"status":"error","code":"INDEX_INITIALIZING"}"""
            )
        )
        server.start()
        try {
            val repository = MarketReadOnlyIndexRepository(
                baseUrl = server.url("/").toString().trimEnd('/'),
                client = OkHttpClient(),
            )

            val page = kotlinx.coroutines.runBlocking { repository.getPage() }.getOrThrow()

            assertEquals(MarketReadOnlyIndexStatus.INITIALIZING, page.sourceStatus)
            assertEquals(emptyList<Long>(), page.ids)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `getPage reports rate limiting as a failure`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        server.start()
        try {
            val repository = MarketReadOnlyIndexRepository(
                baseUrl = server.url("/").toString().trimEnd('/'),
                client = OkHttpClient(),
            )

            val result = kotlinx.coroutines.runBlocking { repository.getPage() }

            assertTrue(result.isFailure)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `parses server feed envelope and clamps no client fields`() {
        val page = MarketReadOnlyIndexRepository.parsePage(
            """
            {
              "status":"success",
              "data":{
                "ids":[39350265,39345281],
                "nextCursor":"cursor-1",
                "hasMore":true,
                "sourceStatus":"ready",
                "generatedAt":"2026-08-05T12:00:00+08:00"
              }
            }
            """.trimIndent()
        )

        assertEquals(listOf(39350265L, 39345281L), page.ids)
        assertEquals("cursor-1", page.nextCursor)
        assertTrue(page.hasMore)
        assertEquals(MarketReadOnlyIndexStatus.READY, page.sourceStatus)
    }

    @Test
    fun `maps initializing error response to typed failure`() {
        val result = MarketReadOnlyIndexRepository.parseResponse(
            503,
            """{"status":"error","code":"INDEX_INITIALIZING"}"""
        )

        assertEquals(MarketReadOnlyIndexStatus.INITIALIZING, result.getOrThrow().sourceStatus)
    }

    @Test
    fun `getArchivedTopic requests the server snapshot and parses source marker`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"status":"success","data":{"id":7,"content":"已删除正文","comments":[{"id":1,"content":"预览"}],"source":"archive","capturedAt":"2026-08-05T12:00:00"}}"""
            )
        )
        server.start()
        try {
            val repository = MarketReadOnlyIndexRepository(
                baseUrl = server.url("/").toString().trimEnd('/'),
                client = OkHttpClient(),
            )

            val topic = kotlinx.coroutines.runBlocking {
                repository.getArchivedTopic(7).getOrThrow()
            }
            val request = server.takeRequest()

            assertEquals("/market/readonly/archive/7", request.path)
            assertEquals(7L, topic.id)
            assertEquals("已删除正文", topic.content)
            assertEquals("archive", topic.source)
            assertEquals("预览", topic.topComments.single().content)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `getArchivedTopic reports archive not found as failure`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"status":"error","code":"ARCHIVE_NOT_FOUND"}"""
            )
        )
        server.start()
        try {
            val repository = MarketReadOnlyIndexRepository(
                baseUrl = server.url("/").toString().trimEnd('/'),
                client = OkHttpClient(),
            )

            val result = kotlinx.coroutines.runBlocking { repository.getArchivedTopic(7) }

            assertTrue(result.isFailure)
        } finally {
            server.shutdown()
        }
    }
}
