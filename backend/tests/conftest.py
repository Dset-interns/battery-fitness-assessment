import os

os.environ["BFA_AUTOSTART"] = "0"   # do not build the module-level app on import

import datetime as dt  # noqa: E402

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.config import Settings  # noqa: E402
from app.main import create_app  # noqa: E402

KEYS = "dk:device,ek:export,ak:admin"
DEV, EXP, ADM = {"X-API-Key": "dk"}, {"X-API-Key": "ek"}, {"X-API-Key": "ak"}


@pytest.fixture
def make_client(tmp_path):
    def build(**over):
        base = dict(database_url="sqlite:///:memory:", api_keys=KEYS, pseudonym_secret="test-secret",
                    media_dir=str(tmp_path / "media"), dev_open=False)
        base.update(over)
        return TestClient(create_app(Settings(**base)))
    return build


@pytest.fixture
def client(make_client):
    return make_client()


def consent(minor=False, rel="self", parent_height=False, signer="Riya Aggarwal", at="2026-11-09T10:00:00Z", **items):
    it = {"assess": True, "video": True, "data": True, "parent_height": parent_height}
    it.update(items)
    return {"version": 1, "signed_at": at, "signer_name": signer, "relationship": rel, "minor": minor, "items": it,
            "assessor": "P. Menon", "signature_png_base64": None}


def athlete(local_id="1", name="Riya Aggarwal", age=20, gender="F", school="DPS R.K. Puram", id_type="LOCAL", id_number="", cons=None, **kw):
    a = {"local_id": local_id, "name": name, "age_years": age, "gender": gender, "school": school, "id_type": id_type,
         "id_number": id_number, "assessment_date": "2026-11-09", "consent": cons or consent()}
    a.update(kw)
    return a


def attempt(n, value, valid=True, conf="GREEN", unit="cm", key=None, **kw):
    a = {"attempt_id": key or f"v:1:t:{n}", "attempt_no": n, "valid": valid, "value": value if valid else None, "unit": unit,
         "confidence": conf, "method": "AI method text with Riya Aggarwal inside", "audit": [{"label": "x", "detail": "y", "valid": valid}]}
    a.update(kw)
    return a


def batch(athletes, results=(), device="device-000001"):
    return {"device_id": device, "assessor": "P. Menon", "app_version": "test", "sent_at": "2026-11-09T10:05:00Z",
            "athletes": list(athletes), "results": list(results)}
