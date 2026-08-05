from time import sleep

from .config import Settings
from .store import SqlAlchemyIndexStore
from .worker import Collector, HttpSourceClient


def run() -> None:
    settings = Settings.from_env()
    if not settings.database_url:
        raise RuntimeError("DATABASE_URL is required")
    if not settings.source_token:
        raise RuntimeError("MARKET_SOURCE_TOKEN is required")

    store = SqlAlchemyIndexStore(settings.database_url)
    source = HttpSourceClient(settings)
    collector = Collector(
        source,
        store,
        min_interval_seconds=settings.source_min_interval_seconds,
    )
    try:
        collector.bootstrap()
        while True:
            sleep(settings.source_poll_interval_seconds)
            collector.incremental_once()
    finally:
        source.close()


if __name__ == "__main__":
    run()
