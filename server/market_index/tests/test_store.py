import os
import tempfile
import unittest
import json
from datetime import datetime

from sqlalchemy import create_engine, text

from server.market_index.cursor import CursorPosition
from server.market_index.source import TopicRecord
from server.market_index.store import InMemoryIndexStore, SqlAlchemyIndexStore


class SqlAlchemyStoreTests(unittest.TestCase):
    def setUp(self):
        handle, self.path = tempfile.mkstemp(suffix=".sqlite3")
        os.close(handle)
        self.engine = create_engine(f"sqlite+pysqlite:///{self.path}")
        with self.engine.begin() as connection:
            connection.execute(text("""
                CREATE TABLE market_readonly_topic_index (
                    topic_id BIGINT PRIMARY KEY,
                    school_id BIGINT NOT NULL,
                    create_time DATETIME NOT NULL,
                    discovered_at DATETIME NOT NULL,
                    last_seen_at DATETIME NOT NULL
                )
            """))
            connection.execute(text("""
                CREATE TABLE market_readonly_topic_archive (
                    topic_id BIGINT PRIMARY KEY,
                    school_id BIGINT NOT NULL,
                    payload_codec VARCHAR(32) NOT NULL,
                    payload_hash CHAR(64) NOT NULL,
                    payload_bytes INT NOT NULL,
                    payload_compressed BLOB NOT NULL,
                    captured_at DATETIME NOT NULL
                )
            """))
            connection.execute(text("""
                CREATE TABLE market_readonly_sync_state (
                    source_key VARCHAR(64) PRIMARY KEY,
                    next_page INTEGER NOT NULL,
                    mode VARCHAR(32) NOT NULL,
                    latest_watermark DATETIME NULL,
                    latest_topic_id BIGINT NULL,
                    last_success_at DATETIME NULL,
                    last_error_code VARCHAR(64) NULL,
                    consecutive_failures INTEGER NOT NULL,
                    last_fingerprint VARCHAR(128) NULL
                )
            """))
        self.store = SqlAlchemyIndexStore(self.engine, source_key="ahu-circle")

    def tearDown(self):
        self.engine.dispose()
        os.remove(self.path)

    def test_upsert_and_keyset_query_round_trip(self):
        now = datetime(2026, 8, 5, 12, 0)
        self.store.upsert_records([
            TopicRecord(2, 10681, now, now, now),
            TopicRecord(1, 10681, datetime(2026, 8, 5, 11), now, now),
        ])

        first = self.store.query_page(10681, None, 1)
        second = self.store.query_page(
            10681,
            CursorPosition(10681, first[0].create_time, first[0].topic_id),
            10,
        )

        self.assertEqual([2], [record.topic_id for record in first])
        self.assertEqual([1], [record.topic_id for record in second])

    def test_upsert_preserves_first_discovered_at_and_updates_last_seen_at(self):
        create_time = datetime(2026, 8, 5, 12, 0)
        first_seen = datetime(2026, 8, 5, 12, 1)
        second_seen = datetime(2026, 8, 5, 12, 5)
        self.store.upsert_records([
            TopicRecord(2, 10681, create_time, first_seen, first_seen),
        ])
        self.store.upsert_records([
            TopicRecord(2, 10681, create_time, second_seen, second_seen),
        ])

        record = self.store.query_page(10681, None, 1)[0]

        self.assertEqual(first_seen, record.discovered_at)
        self.assertEqual(second_seen, record.last_seen_at)

    def test_sync_state_round_trip(self):
        state = self.store.get_sync_state()
        state.next_page = 7
        state.mode = "paused"
        state.last_error_code = "AUTH_EXPIRED"
        self.store.save_sync_state(state)

        restored = self.store.get_sync_state()

        self.assertEqual(7, restored.next_page)
        self.assertEqual("paused", restored.mode)
        self.assertEqual("AUTH_EXPIRED", restored.last_error_code)

    def test_archive_round_trip_and_hash_deduplicates_unchanged_payload(self):
        now = datetime(2026, 8, 5, 12, 0)
        record = TopicRecord(
            2,
            10681,
            now,
            now,
            payload_json=json.dumps({"id": 2, "comments": [{"id": 8}]}),
        )
        self.store.upsert_records([record])
        first = self.store.get_archive(10681, 2)
        self.store.upsert_records([record])
        second = self.store.get_archive(10681, 2)

        self.assertEqual({"id": 2, "comments": [{"id": 8}]}, first.payload)
        self.assertEqual(first.payload_hash, second.payload_hash)
        self.assertEqual(first.compressed_payload, second.compressed_payload)

    def test_existing_index_row_still_updates_changed_archive_payload(self):
        now = datetime(2026, 8, 5, 12, 0)
        self.store.upsert_records([
            TopicRecord(2, 10681, now, now, payload_json='{"id":2,"content":"旧"}'),
        ])
        self.store.upsert_records([
            TopicRecord(2, 10681, now, now, payload_json='{"id":2,"content":"新"}'),
        ])

        self.assertEqual("新", self.store.get_archive(10681, 2).payload["content"])


class InMemoryStoreTests(unittest.TestCase):
    def test_archive_accepts_generator_input(self):
        now = datetime(2026, 8, 5, 12, 0)
        store = InMemoryIndexStore()

        store.upsert_records(iter([
            TopicRecord(2, 10681, now, now, payload_json='{"id":2}'),
        ]))

        self.assertEqual(2, store.get_archive(10681, 2).payload["id"])


if __name__ == "__main__":
    unittest.main()
