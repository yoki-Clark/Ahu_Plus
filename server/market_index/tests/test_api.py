import unittest
import json

import httpx

from server.market_index.api import create_app
from server.market_index.api import _RateLimiter
from server.market_index.config import Settings
from server.market_index.store import InMemoryIndexStore
from server.market_index.source import TopicRecord
from datetime import datetime


class ApiTests(unittest.IsolatedAsyncioTestCase):
    async def test_feed_does_not_claim_more_when_exactly_20_rows_exist(self):
        store = InMemoryIndexStore()
        store.add_topics(10681, list(range(20, 0, -1)))
        app = create_app(store, Settings(index_cursor_secret="test-secret"))

        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.get("/market/readonly/feed")

        payload = response.json()["data"]
        self.assertEqual(20, len(payload["ids"]))
        self.assertFalse(payload["hasMore"])
        self.assertIsNone(payload["nextCursor"])

    async def test_feed_rate_limits_public_clients(self):
        store = InMemoryIndexStore()
        store.add_topics(10681, [1])
        app = create_app(
            store,
            Settings(index_cursor_secret="test-secret", public_rate_limit_per_minute=1),
        )

        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            first = await client.get("/market/readonly/feed")
            second = await client.get("/market/readonly/feed")

        self.assertEqual(200, first.status_code)
        self.assertEqual(429, second.status_code)
        self.assertEqual("RATE_LIMITED", second.json()["code"])
        self.assertEqual("60", second.headers["Retry-After"])

    async def test_feed_rate_limit_ignores_forwarded_client_ip_from_untrusted_peer(self):
        store = InMemoryIndexStore()
        store.add_topics(10681, [1])
        app = create_app(
            store,
            Settings(index_cursor_secret="test-secret", public_rate_limit_per_minute=1),
        )

        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            first = await client.get(
                "/market/readonly/feed", headers={"X-Forwarded-For": "198.51.100.10"}
            )
            second = await client.get(
                "/market/readonly/feed", headers={"X-Forwarded-For": "198.51.100.11"}
            )

        self.assertEqual(200, first.status_code)
        self.assertEqual(429, second.status_code)

    async def test_feed_rate_limit_uses_forwarded_client_ip_from_trusted_proxy(self):
        store = InMemoryIndexStore()
        store.add_topics(10681, [1])
        app = create_app(
            store,
            Settings(
                index_cursor_secret="test-secret",
                public_rate_limit_per_minute=1,
                trusted_proxy_cidrs=("127.0.0.1/32",),
            ),
        )

        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            first = await client.get(
                "/market/readonly/feed", headers={"X-Forwarded-For": "198.51.100.10"}
            )
            second = await client.get(
                "/market/readonly/feed", headers={"X-Forwarded-For": "198.51.100.11"}
            )

        self.assertEqual(200, first.status_code)
        self.assertEqual(200, second.status_code)

    def test_rate_limiter_bounds_distinct_client_key_memory(self):
        limiter = _RateLimiter(limit=1, max_keys=2, clock=iter([0.0, 0.0, 0.0]).__next__)

        self.assertTrue(limiter.allow("client-a"))
        self.assertTrue(limiter.allow("client-b"))
        self.assertTrue(limiter.allow("client-c"))
        self.assertLessEqual(len(limiter.hits), 2)

    async def test_feed_returns_at_most_20_ids_and_signed_next_cursor(self):
        store = InMemoryIndexStore()
        store.add_topics(10681, list(range(25, 0, -1)))
        app = create_app(store, Settings(index_cursor_secret="test-secret"))

        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.get("/market/readonly/feed?limit=999")

        self.assertEqual(200, response.status_code)
        payload = response.json()["data"]
        self.assertEqual(20, len(payload["ids"]))
        self.assertTrue(payload["hasMore"])
        self.assertIsInstance(payload["nextCursor"], str)

    async def test_feed_cursor_moves_without_duplicates(self):
        store = InMemoryIndexStore()
        store.add_topics(10681, list(range(25, 0, -1)))
        app = create_app(store, Settings(index_cursor_secret="test-secret"))

        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            first = await client.get("/market/readonly/feed")
            cursor = first.json()["data"]["nextCursor"]
            second = await client.get(
                "/market/readonly/feed", params={"cursor": cursor}
            )

        first_ids = first.json()["data"]["ids"]
        second_ids = second.json()["data"]["ids"]
        self.assertEqual(25, len(first_ids) + len(second_ids))
        self.assertTrue(set(first_ids).isdisjoint(second_ids))

    async def test_feed_reports_initializing_when_index_is_empty(self):
        app = create_app(InMemoryIndexStore(), Settings(index_cursor_secret="test-secret"))

        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.get("/market/readonly/feed")

        self.assertEqual(503, response.status_code)
        self.assertEqual("INDEX_INITIALIZING", response.json()["code"])

    async def test_feed_maps_internal_incremental_mode_to_ready(self):
        store = InMemoryIndexStore()
        store.add_topics(10681, [1])
        store.sync_state.mode = "incremental"
        app = create_app(store, Settings(index_cursor_secret="test-secret"))

        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.get("/market/readonly/feed")

        self.assertEqual("ready", response.json()["data"]["sourceStatus"])

    async def test_health_alias_is_available(self):
        app = create_app(InMemoryIndexStore(), Settings(index_cursor_secret="test-secret"))

        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.get("/health")

        self.assertEqual(200, response.status_code)
        self.assertEqual("ok", response.json()["status"])

    async def test_archive_returns_previously_seen_source_row(self):
        store = InMemoryIndexStore()
        store.upsert_records([
            TopicRecord(
                7,
                10681,
                datetime(2026, 8, 5, 12, 0),
                datetime(2026, 8, 5, 12, 0),
                payload_json=json.dumps({
                    "id": 7,
                    "content": "已被源站删除",
                    "comments": [{"id": 1, "content": "预览评论"}],
                }),
            ),
        ])
        app = create_app(store, Settings(index_cursor_secret="test-secret"))

        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.get("/market/readonly/archive/7")

        self.assertEqual(200, response.status_code)
        self.assertEqual("success", response.json()["status"])
        self.assertEqual("已被源站删除", response.json()["data"]["content"])
        self.assertEqual("archive", response.json()["data"]["source"])

    async def test_archive_returns_not_found_for_unknown_id(self):
        app = create_app(InMemoryIndexStore(), Settings(index_cursor_secret="test-secret"))

        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.get("/market/readonly/archive/999")

        self.assertEqual(404, response.status_code)
        self.assertEqual("ARCHIVE_NOT_FOUND", response.json()["code"])

    async def test_archive_uses_same_public_rate_limit(self):
        store = InMemoryIndexStore()
        store.upsert_records([
            TopicRecord(
                7,
                10681,
                datetime(2026, 8, 5, 12, 0),
                datetime(2026, 8, 5, 12, 0),
                payload_json='{"id":7}',
            ),
        ])
        app = create_app(
            store,
            Settings(index_cursor_secret="test-secret", public_rate_limit_per_minute=1),
        )

        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            first = await client.get("/market/readonly/archive/7")
            second = await client.get("/market/readonly/archive/7")

        self.assertEqual(200, first.status_code)
        self.assertEqual(429, second.status_code)
        self.assertEqual("RATE_LIMITED", second.json()["code"])


if __name__ == "__main__":
    unittest.main()
