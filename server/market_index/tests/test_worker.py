import unittest
from datetime import datetime

from server.market_index.source import SourcePage, TopicRecord
from server.market_index.store import InMemoryIndexStore
from server.market_index.worker import Collector, SourceHttpError


class FakeSource:
    def __init__(self, pages):
        self.pages = pages
        self.calls = []

    def fetch_page(self, page):
        self.calls.append(page)
        value = self.pages.get(page)
        if isinstance(value, Exception):
            raise value
        return value


class SequenceSource:
    def __init__(self, values):
        self.values = list(values)
        self.calls = []

    def fetch_page(self, page):
        self.calls.append(page)
        value = self.values.pop(0)
        if isinstance(value, Exception):
            raise value
        return value


class CollectorTests(unittest.TestCase):
    def test_incremental_stops_after_reaching_known_watermark(self):
        old = TopicRecord(2, 10681, datetime(2026, 8, 5, 11, 0), datetime(2026, 8, 5, 11, 0))
        new = TopicRecord(3, 10681, datetime(2026, 8, 5, 12, 0), datetime(2026, 8, 5, 12, 0))
        source = FakeSource({1: SourcePage([new, old], has_more=True, fingerprint="p1")})
        store = InMemoryIndexStore()
        store.upsert_records([old])
        store.sync_state.latest_watermark = old.create_time
        collector = Collector(source, store, sleep=lambda _: None)

        collector.incremental_once()

        self.assertEqual([1], source.calls)
        self.assertEqual([3, 2], store.topic_ids())
        self.assertEqual("incremental", store.sync_state.mode)

    def test_rate_limit_retries_same_page_with_backoff(self):
        record = TopicRecord(2, 10681, datetime(2026, 8, 5, 11, 0), datetime(2026, 8, 5, 11, 0))
        source = SequenceSource([
            SourceHttpError(429),
            SourcePage([record], has_more=False, fingerprint="p1"),
        ])
        store = InMemoryIndexStore()
        sleeps = []
        collector = Collector(source, store, sleep=sleeps.append)

        collector.bootstrap()

        self.assertEqual([1, 1], source.calls)
        self.assertEqual([30], sleeps)
        self.assertEqual("incremental", store.sync_state.mode)

    def test_bootstrap_persists_checkpoint_and_stops_on_empty_page(self):
        record = TopicRecord(2, 10681, datetime(2026, 8, 5, 11, 0), datetime(2026, 8, 5, 11, 0))
        source = FakeSource({1: SourcePage([record], has_more=True, fingerprint="p1"), 2: SourcePage([], has_more=False, fingerprint="p2")})
        store = InMemoryIndexStore()
        collector = Collector(source, store, sleep=lambda _: None)

        collector.bootstrap()

        self.assertEqual([1, 2], source.calls)
        self.assertEqual([2], store.topic_ids())
        self.assertEqual(3, store.sync_state.next_page)
        self.assertEqual("incremental", store.sync_state.mode)

    def test_bootstrap_tracks_topic_id_when_timestamps_are_equal(self):
        first = TopicRecord(2, 10681, datetime(2026, 8, 5, 11, 0), datetime(2026, 8, 5, 11, 0))
        second = TopicRecord(3, 10681, datetime(2026, 8, 5, 11, 0), datetime(2026, 8, 5, 11, 0))
        source = FakeSource({1: SourcePage([second, first], has_more=False, fingerprint="p1")})
        store = InMemoryIndexStore()
        collector = Collector(source, store, sleep=lambda _: None)

        collector.bootstrap()

        self.assertEqual(datetime(2026, 8, 5, 11, 0), store.sync_state.latest_watermark)
        self.assertEqual(3, store.sync_state.latest_topic_id)

    def test_bootstrap_keeps_highest_watermark_across_older_pages(self):
        newest = TopicRecord(9, 10681, datetime(2026, 8, 5, 12, 0), datetime(2026, 8, 5, 12, 0))
        older = TopicRecord(8, 10681, datetime(2026, 8, 5, 11, 0), datetime(2026, 8, 5, 11, 0))
        source = FakeSource({
            1: SourcePage([newest], has_more=True, fingerprint="p1"),
            2: SourcePage([older], has_more=False, fingerprint="p2"),
        })
        store = InMemoryIndexStore()
        collector = Collector(source, store, sleep=lambda _: None)

        collector.bootstrap()

        self.assertEqual(datetime(2026, 8, 5, 12, 0), store.sync_state.latest_watermark)
        self.assertEqual(9, store.sync_state.latest_topic_id)

    def test_auth_failure_pauses_without_retrying(self):
        source = FakeSource({1: SourceHttpError(401)})
        store = InMemoryIndexStore()
        collector = Collector(source, store, sleep=lambda _: None)

        collector.bootstrap()

        self.assertEqual([1], source.calls)
        self.assertEqual("paused", store.sync_state.mode)
        self.assertEqual("AUTH_EXPIRED", store.sync_state.last_error_code)

    def test_bootstrap_archives_every_source_row(self):
        record = TopicRecord(
            2,
            10681,
            datetime(2026, 8, 5, 11, 0),
            datetime(2026, 8, 5, 11, 0),
            payload_json='{"id":2,"comments":[{"id":8,"content":"预览"}]}',
        )
        source = FakeSource({1: SourcePage([record], has_more=False, fingerprint="p1")})
        store = InMemoryIndexStore()

        Collector(source, store, sleep=lambda _: None).bootstrap()

        archive = store.get_archive(10681, 2)
        self.assertIsNotNone(archive)
        self.assertEqual(2, archive.payload["id"])
        self.assertEqual("预览", archive.payload["comments"][0]["content"])

    def test_incremental_refreshes_archive_even_when_no_new_id_exists(self):
        old = TopicRecord(
            2,
            10681,
            datetime(2026, 8, 5, 11, 0),
            datetime(2026, 8, 5, 11, 0),
            payload_json='{"id":2,"content":"旧内容"}',
        )
        refreshed = TopicRecord(
            2,
            10681,
            datetime(2026, 8, 5, 11, 0),
            datetime(2026, 8, 5, 12, 0),
            payload_json='{"id":2,"content":"新内容"}',
        )
        source = FakeSource({1: SourcePage([refreshed], has_more=False, fingerprint="p1")})
        store = InMemoryIndexStore()
        store.upsert_records([old])
        store.sync_state.mode = "incremental"
        store.sync_state.latest_watermark = old.create_time
        store.sync_state.latest_topic_id = old.topic_id

        Collector(source, store, sleep=lambda _: None).incremental_once()

        self.assertEqual("新内容", store.get_archive(10681, 2).payload["content"])


if __name__ == "__main__":
    unittest.main()
