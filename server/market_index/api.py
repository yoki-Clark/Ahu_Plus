from datetime import datetime
from time import monotonic
from zoneinfo import ZoneInfo

from fastapi import FastAPI, Query, Request
from fastapi.responses import JSONResponse

from .config import Settings
from .cursor import CursorPosition, decode_cursor, encode_cursor
from .store import InMemoryIndexStore


class _RateLimiter:
    def __init__(self, limit: int, clock=monotonic):
        self.limit = limit
        self.clock = clock
        self.hits: dict[str, list[float]] = {}

    def allow(self, key: str) -> bool:
        now = self.clock()
        values = [value for value in self.hits.get(key, []) if now - value < 60]
        if len(values) >= self.limit:
            self.hits[key] = values
            return False
        values.append(now)
        self.hits[key] = values
        return True


def create_app(store=None, settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    store = store or InMemoryIndexStore()
    limiter = _RateLimiter(settings.public_rate_limit_per_minute)
    app = FastAPI(title="Ahu Plus Market Index", docs_url=None, redoc_url=None)

    @app.get("/health")
    @app.get("/healthz")
    async def healthz():
        return {"status": "ok"}

    @app.get("/market/readonly/feed")
    async def feed(request: Request, cursor: str | None = None, limit: int = Query(20)):
        client_host = _client_key(request)
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
        client_host = _client_key(request)
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


def _client_key(request: Request) -> str:
    """Use the client address forwarded by the trusted Caddy reverse proxy."""
    forwarded = request.headers.get("x-forwarded-for", "")
    if forwarded:
        return forwarded.split(",", 1)[0].strip() or "unknown"
    return request.headers.get("x-real-ip") or (
        request.client.host if request.client else "unknown"
    )


def _public_source_status(mode: str) -> str:
    return {
        "bootstrap": "initializing",
        "initializing": "initializing",
        "incremental": "ready",
    }.get(mode, mode)
