"""Settings, read from environment variables so the same image runs everywhere."""

from __future__ import annotations

import os
from dataclasses import dataclass, field


def _int(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, default))
    except ValueError:
        return default


@dataclass(frozen=True)
class Settings:
    # SQLite by default so the service runs with nothing installed; use PostgreSQL in production,
    # for example postgresql+psycopg://user:pass@db:5432/bfa
    database_url: str = field(default_factory=lambda: os.environ.get("DATABASE_URL", "sqlite:///./bfa.db"))
    # Scoped API keys: "key:scope,key:scope". Scopes: device (the app), export (de-identified reads), admin.
    api_keys: str = field(default_factory=lambda: os.environ.get("BFA_KEYS", ""))
    # Only for local development: accept requests with no key when no keys are configured.
    dev_open: bool = field(default_factory=lambda: os.environ.get("BFA_DEV_OPEN", "") == "1")
    # Secret for the pseudonym function. Keep it out of the analytics zone and never change it once data exists.
    pseudonym_secret: str = field(default_factory=lambda: os.environ.get("BFA_PSEUDONYM_SECRET", ""))
    media_dir: str = field(default_factory=lambda: os.environ.get("BFA_MEDIA_DIR", "./media"))
    max_media_mb: int = field(default_factory=lambda: _int("BFA_MAX_MEDIA_MB", 300))
    # Audit retention for videos and photos. The real period is set by SAI's service level agreement;
    # 0 means keep until it is purged by hand.
    retention_days: int = field(default_factory=lambda: _int("BFA_RETENTION_DAYS", 0))
    cors_origins: str = field(default_factory=lambda: os.environ.get("BFA_CORS_ORIGINS", "*"))
    max_batch_athletes: int = field(default_factory=lambda: _int("BFA_MAX_BATCH_ATHLETES", 500))


def get_settings() -> Settings:
    return Settings()
