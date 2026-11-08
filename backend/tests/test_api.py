import base64
import json

from conftest import ADM, DEV, EXP, KEYS, athlete, attempt, batch, consent

PNG = base64.b64encode(b"\x89PNG\r\n\x1a\n" + b"0" * 50).decode()


def test_health_and_config(client):
    assert client.get("/healthz").json()["status"] == "ok"
    c = client.get("/v1/config").json()
    assert c["max_batch_athletes"] == 500 and c["consent_version"] == 1


# ---------------------------------------------------------------------------------------------- sync
def test_sync_stores_athlete_consent_and_results(client):
    cons = consent(parent_height=True)
    cons["signature_png_base64"] = PNG
    r = client.post("/v1/sync", headers=DEV, json=batch(
        [athlete(cons=cons, mother_cm=158, mother_source="measured")],
        [{"athlete_local_id": "1", "test_key": "vertical_jump", "attempts": [attempt(1, 34), attempt(2, 41), attempt(3, 0, valid=False, conf="RED")]}]))
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["accepted_athletes"] == 1 and body["attempts_upserted"] == 3 and body["results_upserted"] == 1 and body["rejected"] == []
    uid = body["athlete_uids"]["1"]
    d = client.get(f"/v1/athletes/{uid}", headers=ADM).json()
    assert d["name"] == "Riya Aggarwal" and d["consents"][0]["has_signature"] is True and d["consents"][0]["signer_name"] == "Riya Aggarwal"


def test_sync_is_idempotent(client):
    payload = batch([athlete()], [{"athlete_local_id": "1", "test_key": "sit_ups", "attempts": [attempt(1, 28, unit="reps", key="v:1:sit_ups:1")]}])
    a = client.post("/v1/sync", headers=DEV, json=payload).json()
    b = client.post("/v1/sync", headers=DEV, json=payload).json()
    assert a["athlete_uids"] == b["athlete_uids"]
    s = client.get("/v1/stats", headers=EXP).json()
    assert s["athletes"] == 1 and s["attempts"] == 1 and s["deidentified_subjects"] == 1


def test_results_arrive_in_a_later_batch(client):
    client.post("/v1/sync", headers=DEV, json=batch([athlete()]))
    r = client.post("/v1/sync", headers=DEV, json=batch([], [{"athlete_local_id": "1", "test_key": "sit_ups", "attempts": [attempt(1, 30, unit="reps")]}]))
    assert r.json()["results_upserted"] == 1 and r.json()["rejected"] == []


def test_results_for_unknown_athlete_are_reported(client):
    r = client.post("/v1/sync", headers=DEV, json=batch([], [{"athlete_local_id": "99", "test_key": "sit_ups", "attempts": [attempt(1, 30, unit="reps")]}]))
    assert r.json()["results_upserted"] == 0 and "not synced" in r.json()["rejected"][0]["reason"]


def test_incomplete_consent_is_rejected_and_nothing_is_stored(client):
    r = client.post("/v1/sync", headers=DEV, json=batch([athlete(cons=consent(video=False))]))
    assert r.json()["accepted_athletes"] == 0 and "Consent incomplete" in r.json()["rejected"][0]["reason"]
    assert client.get("/v1/stats", headers=EXP).json()["athletes"] == 0


def test_minor_needs_a_guardian(client):
    bad = athlete(local_id="2", name="Meera Nair", age=11, cons=consent(minor=True, rel="self", signer="Meera Nair"))
    mismatch = athlete(local_id="3", name="Old Enough", age=20, cons=consent(minor=True, rel="parent"))
    ok = athlete(local_id="4", name="Aarav Sharma", age=14, gender="M", cons=consent(minor=True, rel="parent", signer="Sunita Sharma", at="2026-11-09T11:00:00Z"))
    r = client.post("/v1/sync", headers=DEV, json=batch([bad, mismatch, ok])).json()
    assert r["accepted_athletes"] == 1 and {x["local_id"] for x in r["rejected"]} == {"2", "3"}


def test_bad_signature_is_rejected(client):
    c = consent(); c["signature_png_base64"] = base64.b64encode(b"not a png").decode()
    assert "PNG" in client.post("/v1/sync", headers=DEV, json=batch([athlete(cons=c)])).json()["rejected"][0]["reason"]


def test_validation_errors_are_422(client):
    assert client.post("/v1/sync", headers=DEV, json=batch([athlete(age=3)])).status_code == 422
    assert client.post("/v1/sync", headers=DEV, json={"device_id": "short"}).status_code == 422


def test_batch_size_limit(make_client):
    c = make_client(max_batch_athletes=2)
    r = c.post("/v1/sync", headers=DEV, json=batch([athlete(local_id=str(i), name=f"A{i}") for i in range(3)]))
    assert r.status_code == 413


# ------------------------------------------------------------------------------------------- result rules
def _result_for(client, test_key, attempts, unit):
    client.post("/v1/sync", headers=DEV, json=batch([athlete()], [{"athlete_local_id": "1", "test_key": test_key, "attempts": attempts}]))
    page = client.get("/v1/deidentified/subjects", headers=EXP).json()
    return next(r for r in page["subjects"][0]["results"] if r["test_key"] == test_key)


def test_best_result_rules(make_client):
    assert _result_for(make_client(), "vertical_jump", [attempt(1, 34), attempt(2, 41), attempt(3, 38)], "cm")["best_value"] == 41
    assert _result_for(make_client(), "sprint_30m", [attempt(1, 5.2, unit="s"), attempt(2, 4.8, unit="s")], "s")["best_value"] == 4.8
    assert _result_for(make_client(), "height", [attempt(1, 158.9, conf="AMBER"), attempt(2, 159.3, conf="AMBER")], "cm")["best_value"] == 159.3
    rej = _result_for(make_client(), "sit_and_reach", [attempt(1, 0, valid=False, conf="RED"), attempt(2, 0, valid=False, conf="RED")], "cm")
    assert rej["status"] == "rejected" and rej["best_value"] is None and rej["attempts_count"] == 2


# --------------------------------------------------------------------------------------- de-identification
def test_deidentified_export_has_no_personal_data(client):
    a = athlete(name="Riya Aggarwal", school="DPS R.K. Puram", id_type="AADHAAR", id_number="XXXX XXXX 4821", cons=consent(signer="Riya Aggarwal"))
    client.post("/v1/sync", headers=DEV, json=batch([a], [{"athlete_local_id": "1", "test_key": "sit_ups", "attempts": [attempt(1, 28, unit="reps", key="v:1:sit_ups:1", video={"key": "v:1:sit_ups:1", "duration_s": 12, "size_bytes": 1000, "declaration": {"assessor": "P. Menon"}})]}]))
    text = client.get("/v1/deidentified/subjects", headers=EXP).text + client.get("/v1/deidentified/results.csv", headers=EXP).text
    for secret in ["Riya", "Aggarwal", "DPS", "Puram", "4821", "AADHAAR", "Menon", "v:1:sit_ups", "AI method text", "2026-11-09", "device-000001"]:
        assert secret not in text, secret
    s = json.loads(client.get("/v1/deidentified/subjects", headers=EXP).text)["subjects"][0]
    assert s["pseudonym"].startswith("S-") and s["age_band"] == "18+" and s["assessment_month"] == "2026-11"
    assert s["mother_cm"] is None and s["results"][0]["best_value"] == 28


def test_parental_height_only_with_its_consent(client):
    yes = athlete(local_id="1", name="A One", cons=consent(parent_height=True, signer="A One"), mother_cm=158, father_cm=172, mother_source="measured", father_source="self")
    no = athlete(local_id="2", name="B Two", cons=consent(parent_height=False, signer="B Two", at="2026-11-09T10:01:00Z"), mother_cm=160, father_cm=175)
    client.post("/v1/sync", headers=DEV, json=batch([yes, no]))
    subs = {s["age_years"]: s for s in client.get("/v1/deidentified/subjects", headers=EXP).json()["subjects"]}
    rows = client.get("/v1/deidentified/subjects", headers=EXP).json()["subjects"]
    heights = sorted([(s["mother_cm"], s["father_cm"]) for s in rows], key=str)
    assert (158, 172) in heights and (None, None) in heights and len(rows) == 2


def test_pseudonym_follows_the_programme_id_across_phones(client):
    a1 = athlete(local_id="1", id_type="APAAR", id_number="APAAR-7781")
    a2 = athlete(local_id="55", id_type="APAAR", id_number="apaar 7781")
    client.post("/v1/sync", headers=DEV, json=batch([a1], device="phone-aaaaaaa1"))
    client.post("/v1/sync", headers=DEV, json=batch([a2], device="phone-bbbbbbb2"))
    assert client.get("/v1/stats", headers=EXP).json()["deidentified_subjects"] == 1
    l1 = athlete(local_id="1", id_type="LOCAL")
    l2 = athlete(local_id="1", id_type="LOCAL", name="Other Person")
    client.post("/v1/sync", headers=DEV, json=batch([l1], device="phone-aaaaaaa1"))
    client.post("/v1/sync", headers=DEV, json=batch([l2], device="phone-bbbbbbb2"))
    assert client.get("/v1/stats", headers=EXP).json()["deidentified_subjects"] == 3


def test_csv_export(client):
    client.post("/v1/sync", headers=DEV, json=batch([athlete()], [{"athlete_local_id": "1", "test_key": "sit_ups", "attempts": [attempt(1, 28, unit="reps")]}]))
    r = client.get("/v1/deidentified/results.csv", headers=EXP)
    lines = r.text.strip().splitlines()
    assert r.headers["content-type"].startswith("text/csv") and lines[0].startswith("pseudonym,age_years") and len(lines) == 2 and ",sit_ups,recorded,28.0,reps,GREEN,1" in lines[1]


# --------------------------------------------------------------------------------------------------- auth
def test_authentication_and_scopes(client):
    p = batch([athlete()])
    assert client.post("/v1/sync", json=p).status_code == 401
    assert client.post("/v1/sync", headers={"X-API-Key": "wrong"}, json=p).status_code == 401
    assert client.post("/v1/sync", headers=EXP, json=p).status_code == 403
    assert client.get("/v1/deidentified/subjects", headers=DEV).status_code == 403
    assert client.get("/v1/stats", headers=DEV).status_code == 403
    assert client.get("/v1/athletes/x", headers=EXP).status_code == 403
    assert client.get("/v1/athletes/x", headers=ADM).status_code == 404
    assert client.post("/v1/sync", headers=ADM, json=p).status_code == 200


def test_unconfigured_server_refuses_and_dev_mode_allows(make_client):
    assert make_client(api_keys="").post("/v1/sync", json=batch([athlete()])).status_code == 503
    assert make_client(api_keys="", dev_open=True, pseudonym_secret="").post("/v1/sync", json=batch([athlete()])).status_code == 200
    assert make_client(pseudonym_secret="").post("/v1/sync", headers=DEV, json=batch([athlete()])).status_code == 503


# -------------------------------------------------------------------------------------------------- media
def _uid(client):
    return client.post("/v1/sync", headers=DEV, json=batch([athlete()])).json()["athlete_uids"]["1"]


def test_media_upload_retention_withdrawal_and_purge(make_client, tmp_path):
    c = make_client(retention_days=30, max_media_mb=1)
    uid = _uid(c)
    data = b"x" * 5000
    import hashlib
    ok = c.post("/v1/media", headers=DEV, data={"athlete_uid": uid, "test_key": "sit_ups", "kind": "video", "key": "v:1:sit_ups:1", "sha256": hashlib.sha256(data).hexdigest()},
                files={"file": ("a.mp4", data, "video/mp4")})
    assert ok.status_code == 200 and ok.json()["size_bytes"] == 5000 and ok.json()["retain_until"]
    bad = c.post("/v1/media", headers=DEV, data={"athlete_uid": uid, "test_key": "sit_ups", "kind": "video", "key": "v2", "sha256": "0" * 64}, files={"file": ("a.mp4", data, "video/mp4")})
    assert bad.status_code == 422
    big = c.post("/v1/media", headers=DEV, data={"athlete_uid": uid, "test_key": "sit_ups", "kind": "video", "key": "v3"}, files={"file": ("a.mp4", b"x" * (1048576 + 10), "video/mp4")})
    assert big.status_code == 413
    assert c.post("/v1/media", headers=DEV, data={"athlete_uid": "nope", "test_key": "sit_ups", "kind": "video", "key": "k"}, files={"file": ("a", data, "video/mp4")}).status_code == 404
    assert len(list((tmp_path / "media" / uid).iterdir())) == 1       # no partial files left behind
    assert c.get("/v1/stats", headers=EXP).json()["media_files"] == 1
    w = c.delete(f"/v1/athletes/{uid}", headers=ADM).json()
    assert w == {"erased": True, "media_files_removed": 1}
    s = c.get("/v1/stats", headers=EXP).json()
    assert s["athletes"] == 0 and s["media_files"] == 0 and s["deidentified_subjects"] == 0
    assert c.delete(f"/v1/athletes/{uid}", headers=ADM).status_code == 404


def test_purge_expired(make_client):
    c = make_client(retention_days=1)
    uid = _uid(c)
    c.post("/v1/media", headers=DEV, data={"athlete_uid": uid, "test_key": "weight", "kind": "photo", "key": "p:1:weight"}, files={"file": ("p.jpg", b"jpg", "image/jpeg")})
    assert c.post("/v1/admin/purge-expired", headers=ADM).json() == {"purged": 0}
    import datetime as dt
    from sqlalchemy import update
    from app.models import Media
    with c.app.state.db._sessions() as s:
        s.execute(update(Media).values(retain_until=dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=1))); s.commit()
    assert c.post("/v1/admin/purge-expired", headers=ADM).json() == {"purged": 1}
    assert c.get("/v1/stats", headers=EXP).json()["media_files"] == 0


def test_same_person_on_two_phones_combines_attempts_in_the_export(client):
    a1, a2 = athlete(local_id="1", id_type="APAAR", id_number="APAAR-9"), athlete(local_id="7", id_type="APAAR", id_number="APAAR-9")
    client.post("/v1/sync", headers=DEV, json=batch([a1], [{"athlete_local_id": "1", "test_key": "vertical_jump", "attempts": [attempt(1, 34, key="a1")]}], device="phone-aaaaaaa1"))
    client.post("/v1/sync", headers=DEV, json=batch([a2], [{"athlete_local_id": "7", "test_key": "vertical_jump", "attempts": [attempt(1, 44, key="b1")]}], device="phone-bbbbbbb2"))
    subs = client.get("/v1/deidentified/subjects", headers=EXP).json()["subjects"]
    assert len(subs) == 1 and subs[0]["results"][0]["best_value"] == 44 and subs[0]["results"][0]["attempts_count"] == 2
    uids = [client.post("/v1/sync", headers=DEV, json=batch([a1], device="phone-aaaaaaa1")).json()["athlete_uids"]["1"],
            client.post("/v1/sync", headers=DEV, json=batch([a2], device="phone-bbbbbbb2")).json()["athlete_uids"]["7"]]
    client.delete(f"/v1/athletes/{uids[0]}", headers=ADM)
    assert client.get("/v1/stats", headers=EXP).json()["deidentified_subjects"] == 1    # still held under the other phone
    client.delete(f"/v1/athletes/{uids[1]}", headers=ADM)
    assert client.get("/v1/stats", headers=EXP).json()["deidentified_subjects"] == 0
