from collections import OrderedDict, deque
from datetime import datetime
from ipaddress import ip_address, ip_network
from time import monotonic
from zoneinfo import ZoneInfo

from fastapi import FastAPI, Query, Request
from fastapi.responses import JSONResponse

from .config import Settings
from .cursor import CursorPosition, decode_cursor, encode_cursor
from .store import InMemoryIndexStore


class _GlobalRateLimiter:
    """Global QPS limiter to prevent resource exhaustion attacks."""

    def __init__(self, max_qps: int, clock=monotonic):
        if max_qps <= 0:
            raise ValueError("max_qps must be positive")
        self.max_qps = max_qps
        self.clock = clock
        self.recent_requests: deque[float] = deque(maxlen=max_qps)

    def allow(self) -> bool:
        now = self.clock()
        while self.recent_requests and now - self.recent_requests[0] > 1.0:
            self.recent_requests.popleft()
        if len(self.recent_requests) >= self.max_qps:
            return False
        self.recent_requests.append(now)
        return True


class _RateLimiter:
    def __init__(self, limit: int, max_keys: int = 10_000, clock=monotonic):
        if limit <= 0:
            raise ValueError("limit must be positive")
        if max_keys <= 0:
            raise ValueError("max_keys must be positive")
        self.limit = limit
        self.max_keys = max_keys
        self.clock = clock
        self.hits: OrderedDict[str, deque[float]] = OrderedDict()

    def allow(self, key: str) -> bool:
        now = self.clock()
        values = self.hits.get(key)
        if values is None:
            if len(self.hits) >= self.max_keys:
                self.hits.popitem(last=False)
            values = deque()
            self.hits[key] = values
        else:
            self.hits.move_to_end(key)
        while values and now - values[0] >= 60:
            values.popleft()
        if len(values) >= self.limit:
            return False
        values.append(now)
        return True


def create_app(store=None, settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    store = store or InMemoryIndexStore()
    global_limiter = _GlobalRateLimiter(settings.global_rate_limit_qps)
    limiter = _RateLimiter(
        settings.public_rate_limit_per_minute,
        settings.public_rate_limit_max_keys,
    )
    trusted_proxy_networks = _parse_networks(settings.trusted_proxy_cidrs)
    app = FastAPI(title="Ahu Plus Market Index", docs_url=None, redoc_url=None)

    @app.get("/health")
    @app.get("/healthz")
    async def healthz():
        return {"status": "ok"}

    @app.get("/market/readonly/feed")
    async def feed(request: Request, cursor: str | None = None, limit: int = Query(20)):
        if not global_limiter.allow():
            return JSONResponse(
                status_code=503,
                headers={"Retry-After": "1"},
                content={"status": "error", "code": "GLOBAL_RATE_LIMIT"},
            )
        client_host = _client_key(request, trusted_proxy_networks)
        if not limiter.allow(client_host):
            return JSONResponse(
                status_code=429,
                headers={"Retry-After": "60"},
                content={"status": "error", "code": "RATE_LIMITED"},
            )
        if cursor:
            try:
                position = decode_cursor(cursor, settings.index_cursor_secret)
            except ValueError:
                return JSONResponse(
                    status_code=400,
                    content={"status": "error", "code": "INVALID_CURSOR"},
                )
        else:
            position = None
        if position is not None and position.school_id != settings.source_school_id:
            return JSONResponse(
                status_code=400,
                content={"status": "error", "code": "INVALID_CURSOR"},
            )

        rows = store.query_page(settings.source_school_id, position, 21)
        if not rows:
            state = store.get_sync_state()
            if state.mode in {"bootstrap", "initializing"}:
                return JSONResponse(
                    status_code=503,
                    content={"status": "error", "code": "INDEX_INITIALIZING"},
                )
            return JSONResponse(
                status_code=200,
                content={
                    "status": "success",
                    "data": {
                        "ids": [],
                        "nextCursor": None,
                        "hasMore": False,
                        "sourceStatus": _public_source_status(state.mode),
                        "generatedAt": _now().isoformat(),
                    },
                },
            )

        has_more = len(rows) > 20
        rows = rows[:20]
        last = rows[-1]
        next_cursor = encode_cursor(
            CursorPosition(last.school_id, last.create_time, last.topic_id),
            settings.index_cursor_secret,
        )
        return {
            "status": "success",
            "data": {
                "ids": [row.topic_id for row in rows],
                "nextCursor": next_cursor if has_more else None,
                "hasMore": has_more,
                "sourceStatus": _public_source_status(store.get_sync_state().mode),
                "generatedAt": _now().isoformat(),
            },
        }

    @app.get("/market/readonly/archive/{topic_id}")
    async def archive(request: Request, topic_id: int):
        if not global_limiter.allow():
            return JSONResponse(
                status_code=503,
                headers={"Retry-After": "1"},
                content={"status": "error", "code": "GLOBAL_RATE_LIMIT"},
            )
        client_host = _client_key(request, trusted_proxy_networks)
        if not limiter.allow(client_host):
            return JSONResponse(
                status_code=429,
                headers={"Retry-After": "60"},
                content={"status": "error", "code": "RATE_LIMITED"},
            )
        if topic_id <= 0:
            return JSONResponse(
                status_code=400,
                content={"status": "error", "code": "INVALID_TOPIC_ID"},
            )
        archived = store.get_archive(settings.source_school_id, topic_id)
        if archived is None:
            return JSONResponse(
                status_code=404,
                content={"status": "error", "code": "ARCHIVE_NOT_FOUND"},
            )
        payload = dict(archived.payload)
        payload["source"] = "archive"
        payload["capturedAt"] = archived.captured_at.isoformat()
        return {
            "status": "success",
            "data": payload,
        }

    return app


def _now() -> datetime:
    return datetime.now(ZoneInfo("Asia/Shanghai"))


def _parse_networks(values: tuple[str, ...]):
    try:
        return tuple(ip_network(value, strict=False) for value in values)
    except ValueError as exc:
        raise RuntimeError("MARKET_TRUSTED_PROXY_CIDRS contains an invalid network") from exc


def _is_trusted_proxy(value: str, networks) -> bool:
    try:
        address = ip_address(value)
    except ValueError:
        return False
    return any(address in network for network in networks)


def _first_untrusted_forwarded_ip(value: str, trusted_proxy_networks) -> str | None:
    addresses = []
    for candidate in value.split(","):
        try:
            addresses.append(ip_address(candidate.strip()))
        except ValueError:
            continue
    for address in reversed(addresses):
        if not any(address in network for network in trusted_proxy_networks):
            return str(address)
    return None


def _client_key(request: Request, trusted_proxy_networks=()) -> str:
    """Use forwarded addresses only when the direct peer is trusted."""
    peer = request.client.host if request.client else "unknown"
    if not _is_trusted_proxy(peer, trusted_proxy_networks):
        return peer

    forwarded = request.headers.get("x-forwarded-for", "")
    client_ip = _first_untrusted_forwarded_ip(forwarded, trusted_proxy_networks)
    if client_ip:
        return client_ip

    real_ip = request.headers.get("x-real-ip", "").strip()
    try:
        return str(ip_address(real_ip))
    except ValueError:
        return peer


def _public_source_status(mode: str) -> str:
    return {
        "bootstrap": "initializing",
        "initializing": "initializing",
        "incremental": "ready",
    }.get(mode, mode)
