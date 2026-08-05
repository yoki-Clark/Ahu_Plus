from .api import create_app
from .config import Settings
from .store import SqlAlchemyIndexStore


settings = Settings.from_env()
if not settings.database_url:
    raise RuntimeError("DATABASE_URL is required")

store = SqlAlchemyIndexStore(settings.database_url)
app = create_app(store, settings)
