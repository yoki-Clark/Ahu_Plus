from dataclasses import dataclass
from datetime import datetime
import hashlib
import json
from typing import Any


@dataclass(frozen=True)
class TopicRecord:
    topic_id: int
    school_id: int
    create_time: datetime
    discovered_at: datetime
    last_seen_at: datetime | None = None
    payload_json: str | None = None


@dataclass(frozen=True)
class SourcePage:
    records: list[TopicRecord]
    has_more: bool
    fingerprint: str


def parse_source_page(
    body: str,
    school_id: int,
    discovered_at: datetime | None = None,
    assume_configured_school_when_missing: bool = False,
) -> SourcePage:
    discovered_at = discovered_at or datetime.now()
    root = json.loads(body)
    if isinstance(root, dict) and root.get("status") not in (None, "success"):
        raise ValueError(str(root.get("msg") or "source request failed"))

    data: Any = root.get("data", root) if isinstance(root, dict) else root
    if isinstance(data, list):
        rows = data
        # The source list endpoint exposes no explicit end flag. Keep paging
        # while a page has rows; the collector stops on an empty or repeated page.
        has_more = bool(rows)
    elif isinstance(data, dict):
        rows = data.get("rows") or data.get("list") or data.get("items") or []
        has_more = bool(
            data.get("hasMore", data.get("has_more", data.get("hasNext", bool(rows))))
        )
    else:
        rows = []
        has_more = False

    by_id: dict[int, TopicRecord] = {}
    for raw in rows:
        if not isinstance(raw, dict):
            continue
        try:
            topic_id = int(raw.get("id", 0))
        except (TypeError, ValueError):
            continue
        if topic_id <= 0:
            continue

        school_info = raw.get("schoolInfo") or raw.get("school_info") or {}
        if not isinstance(school_info, dict):
            school_info = {}
        raw_school_id = (
            school_info.get("id")
            or school_info.get("schoolId")
            or raw.get("schoolId")
            or raw.get("school_id")
        )
        try:
            if raw_school_id is None and assume_configured_school_when_missing:
                raw_school_id = school_id
            if int(raw_school_id) != school_id:
                continue
        except (TypeError, ValueError):
            continue

        create_time = _parse_time(raw.get("createTime") or raw.get("create_time"))
        record = TopicRecord(
            topic_id=topic_id,
            school_id=school_id,
            create_time=create_time or discovered_at,
            discovered_at=discovered_at,
            last_seen_at=discovered_at,
            payload_json=json.dumps(
                raw,
                ensure_ascii=False,
                separators=(",", ":"),
                sort_keys=True,
            ),
        )
        previous = by_id.get(topic_id)
        if previous is None or record.create_time > previous.create_time:
            by_id[topic_id] = record

    records = sorted(
        by_id.values(), key=lambda item: (item.create_time, item.topic_id), reverse=True
    )
    fingerprint = hashlib.sha256(
        ",".join(str(record.topic_id) for record in records).encode("utf-8")
    ).hexdigest()
    return SourcePage(records=records, has_more=has_more, fingerprint=fingerprint)


def _parse_time(value: Any) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    value = value.strip()
    for candidate in (value, value.replace("Z", "+00:00")):
        try:
            parsed = datetime.fromisoformat(candidate)
            return parsed.replace(tzinfo=None)
        except ValueError:
            pass
    for pattern in ("%Y-%m-%d %H:%M:%S", "%Y/%m/%d %H:%M:%S"):
        try:
            return datetime.strptime(value, pattern)
        except ValueError:
            pass
    return None
