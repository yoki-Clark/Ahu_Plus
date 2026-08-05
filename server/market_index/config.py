from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    database_url: str = "sqlite+pysqlite:///:memory:"
    index_cursor_secret: str = "development-only-secret"
    source_base_url: str = "https://api.zxs-bbs.cn/api/client"
    source_token: str = ""
    source_school_id: int = 10681
    source_poll_interval_seconds: float = 120.0
    source_min_interval_seconds: float = 1.0
    public_rate_limit_per_minute: int = 30
    public_rate_limit_max_keys: int = 10_000
    trusted_proxy_cidrs: tuple[str, ...] = ()

    @classmethod
    def from_env(cls) -> "Settings":
        secret = os.environ.get("MARKET_INDEX_CURSOR_SECRET", "").strip()
        if not secret:
            raise RuntimeError("MARKET_INDEX_CURSOR_SECRET is required")
        return cls(
            database_url=os.environ.get("DATABASE_URL", "").strip(),
            index_cursor_secret=secret,
            source_base_url=os.environ.get(
                "MARKET_SOURCE_BASE_URL", cls.source_base_url
            ).strip(),
            source_token=os.environ.get("MARKET_SOURCE_TOKEN", "").strip(),
            source_school_id=int(
                os.environ.get("MARKET_SOURCE_SCHOOL_ID", str(cls.source_school_id))
            ),
            source_poll_interval_seconds=float(
                os.environ.get("MARKET_POLL_INTERVAL_SECONDS", "120")
            ),
            source_min_interval_seconds=float(
                os.environ.get("MARKET_SOURCE_MIN_INTERVAL_SECONDS", "1")
            ),
            public_rate_limit_per_minute=int(
                os.environ.get("MARKET_PUBLIC_RATE_LIMIT_PER_MINUTE", "30")
            ),
            public_rate_limit_max_keys=int(
                os.environ.get("MARKET_PUBLIC_RATE_LIMIT_MAX_KEYS", "10000")
            ),
            trusted_proxy_cidrs=tuple(
                value.strip()
                for value in os.environ.get("MARKET_TRUSTED_PROXY_CIDRS", "").split(",")
                if value.strip()
            ),
        )
