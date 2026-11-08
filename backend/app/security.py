"""Scoped API keys. No login for the assessor (ruling C18): the phone holds a key, the server holds the scopes."""

from __future__ import annotations

import hmac

from fastapi import Depends, Header, HTTPException, Request

from .config import Settings

SCOPES = ("device", "export", "admin")


def parse_keys(raw: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for part in raw.split(","):
        part = part.strip()
        if ":" in part:
            key, scope = part.rsplit(":", 1)
            if key and scope in SCOPES:
                out[key] = scope
    return out


def require(*allowed: str):
    """Dependency: the key must carry one of the allowed scopes. admin always passes."""
    def check(request: Request, x_api_key: str | None = Header(default=None)) -> str:
        settings: Settings = request.app.state.settings
        keys = parse_keys(settings.api_keys)
        if not keys:
            if settings.dev_open:
                return "admin"
            raise HTTPException(503, "Server has no API keys configured (set BFA_KEYS)")
        scope = None
        if x_api_key:
            for k, s in keys.items():
                if hmac.compare_digest(k, x_api_key):
                    scope = s
        if scope is None:
            raise HTTPException(401, "Missing or invalid API key", headers={"WWW-Authenticate": "ApiKey"})
        if scope != "admin" and scope not in allowed:
            raise HTTPException(403, f"This key cannot use this endpoint (scope {scope})")
        return scope
    return Depends(check)
