from dataclasses import dataclass
from datetime import datetime, timedelta
import gzip
import hashlib
import json
from typing import Iterable

from sqlalchemy import Engine, create_engine, text
from sqlalchemy.exc import IntegrityError

from .cursor import CursorPosition
from .source import TopicRecord


@dataclass
class SyncState:
    next_page: int = 1
    mode: str = "bootstrap"
    latest_watermark: datetime | None = None
    latest_topic_id: int | None = None
    last_success_at: datetime | None = None
    last_error_code: str | None = None
    consecutive_failures: int = 0
    last_fingerprint: str | None = None


@dataclass(frozen=True)
class ArchiveRecord:
    topic_id: int
    school_id: int
    payload: dict
    payload_hash: str
    compressed_payload: bytes
    captured_at: datetime
    payload_codec: str = "json+gzip"


def _compress_payload(payload_json: str) -> tuple[str, bytes, int]:
    raw = payload_json.encode("utf-8")
    return (
        hashlib.sha256(raw).hexdigest(),
        gzip.compress(raw, compresslevel=9, mtime=0),
        len(raw),
    )


def _archive_from_json(
    topic_id: int,
    school_id: int,
    payload_json: str,
    captured_at: datetime,
) -> ArchiveRecord:
    payload_hash, compressed_payload, _ = _compress_payload(payload_json)
    return ArchiveRecord(
        topic_id=topic_id,
        school_id=school_id,
        payload=json.loads(payload_json),
        payload_hash=payload_hash,
        compressed_payload=compressed_payload,
        captured_at=captured_at,
    )


class InMemoryIndexStore:
    def __init__(self):
        self.records: dict[int, TopicRecord] = {}
        self.archives: dict[int, ArchiveRecord] = {}
        self.sync_state = SyncState()

    def add_topics(self, school_id: int, topic_ids: Iterable[int]) -> None:
        base = datetime(2026, 1, 1)
        for topic_id in topic_ids:
            self.records[int(topic_id)] = TopicRecord(
                topic_id=int(topic_id),
                school_id=school_id,
                create_time=base + timedelta(seconds=int(topic_id)),
                discovered_at=base,
                last_seen_at=base,
            )

    def upsert_records(self, records: Iterable[TopicRecord]) -> None:
        records = list(records)
        for record in records:
            existing = self.records.get(record.topic_id)
            if existing is None:
                self.records[record.topic_id] = record
                continue
            self.records[record.topic_id] = TopicRecord(
                topic_id=record.topic_id,
                school_id=record.school_id,
                create_time=max(record.create_time, existing.create_time),
                discovered_at=existing.discovered_at,
                last_seen_at=max(
                    existing.last_seen_at or existing.discovered_at,
                    record.last_seen_at or record.discovered_at,
                ),
                payload_json=record.payload_json or existing.payload_json,
            )
            if record.payload_json:
                archive = _archive_from_json(
                    record.topic_id,
                    record.school_id,
                    record.payload_json,
                    record.last_seen_at or record.discovered_at,
                )
                if self.archives.get(record.topic_id, None) is None or \
                        self.archives[record.topic_id].payload_hash != archive.payload_hash:
                    self.archives[record.topic_id] = archive
        for record in records:
            if record.topic_id not in self.records:
                continue
            if record.payload_json and record.topic_id not in self.archives:
                self.archives[record.topic_id] = _archive_from_json(
                    record.topic_id,
                    record.school_id,
                    record.payload_json,
                    record.last_seen_at or record.discovered_at,
                )

    def query_page(
        self, school_id: int, cursor: CursorPosition | None, limit: int
    ) -> list[TopicRecord]:
        rows = [record for record in self.records.values() if record.school_id == school_id]
        rows.sort(key=lambda item: (item.create_time, item.topic_id), reverse=True)
        if cursor is not None:
            rows = [
                row
                for row in rows
                if (row.create_time, row.topic_id)
                < (cursor.create_time, cursor.topic_id)
            ]
        return rows[:limit]

    def topic_ids(self) -> list[int]:
        return [row.topic_id for row in self.query_page(10681, None, len(self.records))]

    def get_sync_state(self) -> SyncState:
        return self.sync_state

    def save_sync_state(self, state: SyncState) -> None:
        self.sync_state = state

    def get_archive(self, school_id: int, topic_id: int) -> ArchiveRecord | None:
        archive = self.archives.get(topic_id)
        return archive if archive and archive.school_id == school_id else None


class SqlAlchemyIndexStore:
    def __init__(self, engine_or_url: Engine | str, source_key: str = "ahu-circle"):
        self.engine = (
            create_engine(engine_or_url, pool_pre_ping=True, pool_size=5, max_overflow=0)
            if isinstance(engine_or_url, str)
            else engine_or_url
        )
        self.source_key = source_key

    def upsert_records(self, records: Iterable[TopicRecord]) -> None:
        records = list(records)
        with self.engine.begin() as connection:
            for record in records:
                params = {
                    "topic_id": record.topic_id,
                    "school_id": record.school_id,
                    "create_time": record.create_time,
                    "discovered_at": record.discovered_at,
                    "last_seen_at": record.last_seen_at or record.discovered_at,
                }
                updated = connection.execute(
                    text("""
                    UPDATE market_readonly_topic_index
                        SET school_id = :school_id,
                            create_time = CASE
                                WHEN create_time <= :create_time THEN :create_time
                                ELSE create_time
                            END,
                            last_seen_at = CASE
                                WHEN last_seen_at < :last_seen_at THEN :last_seen_at
                                ELSE last_seen_at
                            END
                        WHERE topic_id = :topic_id
                    """),
                    params,
                )
                if not updated.rowcount:
                    try:
                        with connection.begin_nested():
                            connection.execute(
                                text("""
                                    INSERT INTO market_readonly_topic_index
                                        (topic_id, school_id, create_time, discovered_at, last_seen_at)
                                    VALUES
                                        (:topic_id, :school_id, :create_time, :discovered_at, :last_seen_at)
                                """),
                                params,
                            )
                    except IntegrityError:
                        # A concurrent writer inserted a newer row; keep the outer transaction usable.
                        pass
                if record.payload_json:
                    payload_hash, compressed_payload, payload_bytes = _compress_payload(
                        record.payload_json
                    )
                    archive_params = {
                        "topic_id": record.topic_id,
                        "school_id": record.school_id,
                        "payload_codec": "json+gzip",
                        "payload_hash": payload_hash,
                        "payload_bytes": payload_bytes,
                        "payload_compressed": compressed_payload,
                        "captured_at": record.last_seen_at or record.discovered_at,
                    }
                    existing_archive = connection.execute(
                        text("""
                            SELECT payload_hash
                            FROM market_readonly_topic_archive
                            WHERE topic_id = :topic_id AND school_id = :school_id
                        """),
                        archive_params,
                    ).scalar_one_or_none()
                    if existing_archive == payload_hash:
                        continue
                    if existing_archive is None:
                        connection.execute(
                            text("""
                                INSERT INTO market_readonly_topic_archive
                                    (topic_id, school_id, payload_codec, payload_hash,
                                     payload_bytes, payload_compressed, captured_at)
                                VALUES
                                    (:topic_id, :school_id, :payload_codec, :payload_hash,
                                     :payload_bytes, :payload_compressed, :captured_at)
                            """),
                            archive_params,
                        )
                    else:
                        connection.execute(
                            text("""
                                UPDATE market_readonly_topic_archive
                                SET payload_codec = :payload_codec,
                                    payload_hash = :payload_hash,
                                    payload_bytes = :payload_bytes,
                                    payload_compressed = :payload_compressed,
                                    captured_at = :captured_at
                                WHERE topic_id = :topic_id AND school_id = :school_id
                            """),
                            archive_params,
                        )

    def query_page(
        self, school_id: int, cursor: CursorPosition | None, limit: int
    ) -> list[TopicRecord]:
        clauses = ["school_id = :school_id"]
        params: dict[str, object] = {"school_id": school_id, "limit": limit}
        if cursor is not None:
            clauses.append(
                "(create_time < :cursor_time OR "
                "(create_time = :cursor_time AND topic_id < :cursor_topic_id))"
            )
            params["cursor_time"] = cursor.create_time
            params["cursor_topic_id"] = cursor.topic_id
        statement = text(
            "SELECT topic_id, school_id, create_time, discovered_at, last_seen_at "
            "FROM market_readonly_topic_index WHERE "
            + " AND ".join(clauses)
            + " ORDER BY create_time DESC, topic_id DESC LIMIT :limit"
        )
        with self.engine.connect() as connection:
            rows = connection.execute(statement, params).mappings().all()
        return [
            TopicRecord(
                topic_id=int(row["topic_id"]),
                school_id=int(row["school_id"]),
                create_time=_as_datetime(row["create_time"]),
                discovered_at=_as_datetime(row["discovered_at"]),
                last_seen_at=_as_datetime(row["last_seen_at"]),
            )
            for row in rows
        ]

    def get_sync_state(self) -> SyncState:
        with self.engine.connect() as connection:
            row = connection.execute(
                text("""
                    SELECT next_page, mode, latest_watermark, latest_topic_id,
                           last_success_at, last_error_code, consecutive_failures,
                           last_fingerprint
                    FROM market_readonly_sync_state
                    WHERE source_key = :source_key
                """),
                {"source_key": self.source_key},
            ).mappings().first()
        if row is None:
            return SyncState()
        return SyncState(
            next_page=int(row["next_page"]),
            mode=row["mode"],
            latest_watermark=row["latest_watermark"],
            latest_topic_id=row["latest_topic_id"],
            last_success_at=row["last_success_at"],
            last_error_code=row["last_error_code"],
            consecutive_failures=int(row["consecutive_failures"]),
            last_fingerprint=row["last_fingerprint"],
        )

    def save_sync_state(self, state: SyncState) -> None:
        params = {
            "source_key": self.source_key,
            "next_page": state.next_page,
            "mode": state.mode,
            "latest_watermark": state.latest_watermark,
            "latest_topic_id": state.latest_topic_id,
            "last_success_at": state.last_success_at,
            "last_error_code": state.last_error_code,
            "consecutive_failures": state.consecutive_failures,
            "last_fingerprint": state.last_fingerprint,
        }
        with self.engine.begin() as connection:
            updated = connection.execute(
                text("""
                    UPDATE market_readonly_sync_state
                    SET next_page = :next_page,
                        mode = :mode,
                        latest_watermark = :latest_watermark,
                        latest_topic_id = :latest_topic_id,
                        last_success_at = :last_success_at,
                        last_error_code = :last_error_code,
                        consecutive_failures = :consecutive_failures,
                        last_fingerprint = :last_fingerprint
                    WHERE source_key = :source_key
                """),
                params,
            )
            if not updated.rowcount:
                connection.execute(
                    text("""
                        INSERT INTO market_readonly_sync_state
                            (source_key, next_page, mode, latest_watermark, latest_topic_id,
                             last_success_at, last_error_code, consecutive_failures, last_fingerprint)
                        VALUES
                            (:source_key, :next_page, :mode, :latest_watermark, :latest_topic_id,
                             :last_success_at, :last_error_code, :consecutive_failures, :last_fingerprint)
                    """),
                    params,
                )

    def get_archive(self, school_id: int, topic_id: int) -> ArchiveRecord | None:
        with self.engine.connect() as connection:
            row = connection.execute(
                text("""
                    SELECT topic_id, school_id, payload_codec, payload_hash,
                           payload_compressed, captured_at
                    FROM market_readonly_topic_archive
                    WHERE topic_id = :topic_id AND school_id = :school_id
                """),
                {"topic_id": topic_id, "school_id": school_id},
            ).mappings().first()
        if row is None:
            return None
        compressed = bytes(row["payload_compressed"])
        payload_json = gzip.decompress(compressed).decode("utf-8")
        return ArchiveRecord(
            topic_id=int(row["topic_id"]),
            school_id=int(row["school_id"]),
            payload=json.loads(payload_json),
            payload_hash=str(row["payload_hash"]),
            compressed_payload=compressed,
            captured_at=_as_datetime(row["captured_at"]),
            payload_codec=str(row["payload_codec"]),
        )


def _as_datetime(value: datetime | str) -> datetime:
    if isinstance(value, datetime):
        return value
    return datetime.fromisoformat(value.replace("Z", "+00:00")).replace(tzinfo=None)
