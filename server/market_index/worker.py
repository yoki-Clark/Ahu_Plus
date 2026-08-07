from dataclasses import replace
from time import sleep as real_sleep

import httpx

from .config import Settings
from .source import SourcePage, parse_source_page
from .store import InMemoryIndexStore


class SourceHttpError(RuntimeError):
    def __init__(self, status_code: int):
        super().__init__(f"source HTTP {status_code}")
        self.status_code = status_code


def _sanitize_request(request: httpx.Request) -> None:
    """Redact sensitive headers from logs to prevent credential leaks."""
    if "Authorization" in request.headers:
        request.headers["Authorization"] = "Bearer ***REDACTED***"


def _sanitize_response(response: httpx.Response) -> None:
    """Redact sensitive headers from response logs."""
    pass  # Response typically doesn't contain credentials, but hook is required


class HttpSourceClient:
    def __init__(self, settings: Settings, client: httpx.Client | None = None):
        self.settings = settings
        self.client = client or httpx.Client(
            timeout=15.0,
            event_hooks={
                "request": [_sanitize_request],
                "response": [_sanitize_response],
            },
        )

    def fetch_page(self, page: int) -> SourcePage:
        response = self.client.get(
            f"{self.settings.source_base_url.rstrip('/')}/topics",
            params={"page": page},
            headers={
                "Authorization": self.settings.source_token,
                "User-Agent": "Mozilla/5.0 AhuPlusMarketWorker/1.0",
                "xweb_xhr": "1",
                "Content-Type": "application/json",
                "Tenant": "7",
                "Referer": "https://servicewechat.com/wxc56be16e96fc1df1/66/page-frame.html",
                "Accept-Language": "zh-CN,zh;q=0.9",
                "Accept": "application/json",
            },
        )
        if response.status_code < 200 or response.status_code >= 300:
            raise SourceHttpError(response.status_code)
        return parse_source_page(
            response.text,
            school_id=self.settings.source_school_id,
            assume_configured_school_when_missing=True,
        )

    def close(self) -> None:
        self.client.close()


class Collector:
    def __init__(
        self,
        source,
        store,
        sleep=real_sleep,
        min_interval_seconds: float = 1.0,
    ):
        self.source = source
        self.store = store
        self.sleep = sleep
        self.min_interval_seconds = min_interval_seconds

    def bootstrap(self) -> None:
        state = self.store.get_sync_state()
        if state.mode == "incremental":
            self.incremental_once()
            return
        state.mode = "bootstrap"
        self.store.save_sync_state(state)
        page = state.next_page
        previous_fingerprint = state.last_fingerprint
        repeated_pages = 0
        while True:
            try:
                source_page = self.source.fetch_page(page)
            except SourceHttpError as exc:
                if exc.status_code in (401, 403):
                    state.mode = "paused"
                    state.last_error_code = "AUTH_EXPIRED"
                    state.consecutive_failures += 1
                    self.store.save_sync_state(state)
                    return
                if exc.status_code == 429:
                    state.consecutive_failures += 1
                    self.store.save_sync_state(state)
                    self.sleep(min(30 * (2 ** min(state.consecutive_failures - 1, 3)), 300))
                    continue
                state.mode = "stale"
                state.last_error_code = f"SOURCE_HTTP_{exc.status_code}"
                state.consecutive_failures += 1
                self.store.save_sync_state(state)
                return

            self.store.upsert_records(source_page.records)
            newest = max(
                source_page.records,
                key=lambda item: (item.create_time, item.topic_id),
                default=None,
            )
            watermark_candidates = [
                value
                for value in [
                    (
                        state.latest_watermark,
                        state.latest_topic_id if state.latest_topic_id is not None else -1,
                    )
                    if state.latest_watermark is not None
                    else None,
                    (newest.create_time, newest.topic_id) if newest else None,
                ]
                if value is not None
            ]
            latest_key = max(watermark_candidates, default=None)
            state = replace(
                state,
                next_page=page + 1,
                mode="bootstrap",
                latest_watermark=latest_key[0] if latest_key else state.latest_watermark,
                last_success_at=max(
                    (record.discovered_at for record in source_page.records),
                    default=state.last_success_at,
                ),
                latest_topic_id=latest_key[1] if latest_key else state.latest_topic_id,
                last_error_code=None,
                consecutive_failures=0,
                last_fingerprint=source_page.fingerprint,
            )
            self.store.save_sync_state(state)

            if not source_page.has_more or not source_page.records:
                state.mode = "incremental"
                self.store.save_sync_state(state)
                return
            if previous_fingerprint == source_page.fingerprint:
                repeated_pages += 1
            else:
                repeated_pages = 0
            if repeated_pages >= 1:
                state.mode = "incremental"
                self.store.save_sync_state(state)
                return
            previous_fingerprint = source_page.fingerprint
            page += 1
            self.sleep(self.min_interval_seconds)

    def incremental_once(self) -> None:
        state = self.store.get_sync_state()
        state.mode = "incremental"
        self.store.save_sync_state(state)
        page = 1
        while True:
            try:
                source_page = self.source.fetch_page(page)
            except SourceHttpError as exc:
                if exc.status_code in (401, 403):
                    state.mode = "paused"
                    state.last_error_code = "AUTH_EXPIRED"
                elif exc.status_code == 429:
                    state.last_error_code = "RATE_LIMITED"
                    state.consecutive_failures += 1
                    self.store.save_sync_state(state)
                    self.sleep(min(30 * (2 ** min(state.consecutive_failures - 1, 3)), 300))
                    return
                else:
                    state.mode = "stale"
                    state.last_error_code = f"SOURCE_HTTP_{exc.status_code}"
                state.consecutive_failures += 1
                self.store.save_sync_state(state)
                return

            known_key = (
                state.latest_watermark,
                state.latest_topic_id if state.latest_topic_id is not None else 10**18,
            )
            self.store.upsert_records(source_page.records)
            new_records = [
                record
                for record in source_page.records
                if state.latest_watermark is None
                or (record.create_time, record.topic_id) > known_key
            ]
            if not new_records:
                state.last_error_code = None
                state.consecutive_failures = 0
                self.store.save_sync_state(state)
                return

            newest = max(new_records, key=lambda item: (item.create_time, item.topic_id))
            state.latest_watermark = newest.create_time
            state.latest_topic_id = newest.topic_id
            state.last_success_at = newest.discovered_at
            state.last_error_code = None
            state.consecutive_failures = 0
            self.store.save_sync_state(state)

            if not source_page.has_more or len(new_records) != len(source_page.records):
                return
            page += 1
            self.sleep(self.min_interval_seconds)
