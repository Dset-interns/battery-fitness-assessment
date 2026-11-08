"""Engine and session handling. SQLite for development, PostgreSQL in production."""

from __future__ import annotations

from collections.abc import Iterator

from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker

from .config import Settings
from .models import Base


def make_engine(url: str) -> Engine:
    if url.startswith("sqlite"):
        from sqlalchemy.pool import StaticPool
        kw = {"connect_args": {"check_same_thread": False}}
        if ":memory:" in url:
            kw["poolclass"] = StaticPool          # one shared in-memory database for tests
        eng = create_engine(url, **kw)
        if ":memory:" not in url:
            from sqlalchemy import event

            @event.listens_for(eng, "connect")
            def _pragmas(dbapi, _):                       # concurrent writers wait instead of failing
                cur = dbapi.cursor()
                cur.execute("PRAGMA journal_mode=WAL"); cur.execute("PRAGMA busy_timeout=30000"); cur.execute("PRAGMA synchronous=NORMAL")
                cur.close()
        return eng
    # A modest pool per worker; scale out by adding workers and replicas, and put PgBouncer in front for many replicas.
    return create_engine(url, pool_size=10, max_overflow=20, pool_pre_ping=True)


class Database:
    def __init__(self, settings: Settings):
        self.engine = make_engine(settings.database_url)
        self._sessions = sessionmaker(self.engine, expire_on_commit=False)
        Base.metadata.create_all(self.engine)

    def session(self) -> Iterator[Session]:
        s = self._sessions()
        try:
            yield s
        finally:
            s.close()
