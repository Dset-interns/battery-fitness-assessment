"""API: sync, media, de-identified export, and the administrative actions that audit and withdrawal need."""

from __future__ import annotations

import base64
import binascii
import csv
import datetime as dt
import hashlib
import io
import os
import re
import time
import uuid

from fastapi import Depends, FastAPI, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError, OperationalError
from sqlalchemy.orm import Session

from . import __version__, schemas
from .config import Settings, get_settings
from .db import Database
from .deid import best_attempt, deidentify_subject, pseudonym
from .models import (Athlete, Attempt, Consent, DeidResult, DeidSubject, Device, Media, Result, SyncLog, utcnow)
from .security import require

CONSENT_VERSION = 1
NAMESPACE = uuid.UUID("6f1c2d0e-8a4b-4b8e-9f6a-0c5d2e7a1b33")
DEV_SECRET = "dev-secret-not-for-production"
PNG = b"\x89PNG\r\n\x1a\n"

TAGS = [
    {"name": "sync", "description": "From the app: athletes, signed consent and attempts. Idempotent."},
    {"name": "media", "description": "Videos and the scale photo kept for audit."},
    {"name": "export", "description": "De-identified data for the programme (scope export)."},
    {"name": "admin", "description": "Identified data, withdrawal of consent and retention (scope admin)."},
    {"name": "ops", "description": "Health and configuration."},
]


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    app = FastAPI(
        title="Battery Fitness Assessment sync service",
        version=__version__,
        description=(
            "Receives results from the assessor's phone after the video has been processed on the phone. "
            "Identified data (including signed consent) and de-identified data are kept in separate zones; the "
            "export endpoints read only the de-identified zone. Authenticate with `X-API-Key`; scopes: "
            "`device` (the app), `export`, `admin`."),
        openapi_tags=TAGS,
    )
    app.state.settings = settings
    app.state.db = Database(settings)
    origins = [o.strip() for o in settings.cors_origins.split(",") if o.strip()]
    app.add_middleware(CORSMiddleware, allow_origins=origins, allow_methods=["GET", "POST", "DELETE", "OPTIONS"],
                       allow_headers=["X-API-Key", "Content-Type"])

    def get_session(request: Request):
        yield from request.app.state.db.session()

    def secret() -> str:
        if settings.pseudonym_secret:
            return settings.pseudonym_secret
        if settings.dev_open:
            return DEV_SECRET
        raise HTTPException(503, "Server has no pseudonym secret configured (set BFA_PSEUDONYM_SECRET)")

    # ------------------------------------------------------------------------------------------------ ops
    @app.get("/healthz", response_model=schemas.Health, tags=["ops"], summary="Liveness and database check")
    def healthz(s: Session = Depends(get_session)):
        s.execute(select(1))
        return schemas.Health(status="ok", version=__version__, database="up")

    @app.get("/v1/config", response_model=schemas.ServerConfig, tags=["ops"], summary="Limits the app should respect")
    def config():
        return schemas.ServerConfig(version=__version__, max_batch_athletes=settings.max_batch_athletes,
                                    max_media_mb=settings.max_media_mb, retention_days=settings.retention_days,
                                    consent_version=CONSENT_VERSION)

    # ------------------------------------------------------------------------------------------------ sync
    @app.post("/v1/sync", response_model=schemas.SyncResponse, tags=["sync"], dependencies=[require("device")],
              summary="Send athletes, signed consent and attempts")
    def sync(batch: schemas.SyncBatch, s: Session = Depends(get_session)):
        """Safe to repeat: athletes, consents (by signing time), attempts (by attempt id) and results are upserted.

        An athlete without a complete consent is rejected, with a reason, and none of that athlete's data is stored.
        Two requests from the same phone arriving together can collide on a first insert; the batch is simply
        applied again, which is safe because it is idempotent.
        """
        for n in range(3):
            try:
                return _sync_once(batch, s)
            except (IntegrityError, OperationalError):
                s.rollback()
                if n == 2:
                    raise HTTPException(503, "Database busy, try again", headers={"Retry-After": "2"})
                time.sleep(0.2 * (n + 1))

    def _sync_once(batch: schemas.SyncBatch, s: Session):
        if len(batch.athletes) > settings.max_batch_athletes:
            raise HTTPException(413, f"At most {settings.max_batch_athletes} athletes per batch")
        sec = secret()
        now = utcnow()
        dev = s.get(Device, batch.device_id)
        if dev is None:
            dev = Device(id=batch.device_id, first_seen=now)
            s.add(dev)
        dev.assessor, dev.app_version, dev.last_seen = batch.assessor, batch.app_version, now

        rejected: list[schemas.Rejected] = []
        uids: dict[str, str] = {}
        for a in batch.athletes:
            c = a.consent
            if not (c.items.assess and c.items.video and c.items.data):
                rejected.append(schemas.Rejected(local_id=a.local_id, reason="Consent incomplete: the three required statements must be agreed"))
                continue
            if c.minor != (a.age_years < 18) or (c.minor and c.relationship == "self"):
                rejected.append(schemas.Rejected(local_id=a.local_id, reason="Consent signer does not match the athlete's age: under 18 needs a parent or guardian"))
                continue
            sig = None
            if c.signature_png_base64:
                try:
                    sig = base64.b64decode(c.signature_png_base64, validate=True)
                except (binascii.Error, ValueError):
                    sig = b""
                if not sig.startswith(PNG):
                    rejected.append(schemas.Rejected(local_id=a.local_id, reason="Signature is not a PNG image"))
                    continue
            uid = str(uuid.uuid5(NAMESPACE, f"{batch.device_id}:{a.local_id}"))
            pseud = pseudonym(sec, batch.device_id, a.local_id, a.id_type, a.id_number)
            row = s.get(Athlete, uid)
            if row is None:
                row = Athlete(uid=uid, device_id=batch.device_id, local_id=a.local_id, pseudonym=pseud, created_at=now)
                s.add(row)
            row.pseudonym = pseud
            row.name, row.age_years, row.gender, row.school = a.name, a.age_years, a.gender, a.school
            row.id_type, row.id_number, row.assessment_date = a.id_type, a.id_number, a.assessment_date
            share = c.items.parent_height
            row.mother_cm, row.mother_source = (a.mother_cm, a.mother_source) if share else (None, "")
            row.father_cm, row.father_source = (a.father_cm, a.father_source) if share else (None, "")
            s.flush()
            if s.scalar(select(Consent.id).where(Consent.athlete_uid == uid, Consent.signed_at == c.signed_at)) is None:
                s.add(Consent(athlete_uid=uid, version=c.version, signed_at=c.signed_at, signer_name=c.signer_name,
                              relationship_=c.relationship, minor=c.minor, item_assess=c.items.assess,
                              item_video=c.items.video, item_data=c.items.data, item_parent_height=c.items.parent_height,
                              assessor=c.assessor, signature_png=sig))
            d = deidentify_subject(pseud, a, c.items)
            sub = s.get(DeidSubject, pseud)
            if sub is None:
                s.add(DeidSubject(**d))
            else:
                for k, v in d.items():
                    setattr(sub, k, v)
            uids[a.local_id] = uid

        attempts_n = results_n = 0
        for r in batch.results:
            uid = uids.get(r.athlete_local_id) or str(uuid.uuid5(NAMESPACE, f"{batch.device_id}:{r.athlete_local_id}"))
            ath = s.get(Athlete, uid)
            if ath is None:
                rejected.append(schemas.Rejected(local_id=r.athlete_local_id, reason=f"Results for {r.test_key} ignored: athlete not synced"))
                continue
            known = {x.attempt_id: x for x in s.scalars(select(Attempt).where(Attempt.athlete_uid == uid, Attempt.test_key == r.test_key))}
            for at in r.attempts:
                row = known.get(at.attempt_id)
                if row is None:
                    row = known[at.attempt_id] = Attempt(athlete_uid=uid, test_key=r.test_key, attempt_id=at.attempt_id)
                    s.add(row)
                row.attempt_no, row.valid, row.value, row.unit = at.attempt_no, at.valid, at.value, at.unit
                row.confidence, row.method = at.confidence, at.method
                row.audit = [x.model_dump() for x in at.audit]
                row.video_key = at.video.key if at.video else None
                row.video_duration_s = at.video.duration_s if at.video else None
                row.video_size_bytes = at.video.size_bytes if at.video else None
                row.declaration = at.video.declaration if at.video else None
                attempts_n += 1
            allv = [{"attempt_no": x.attempt_no, "valid": x.valid, "value": x.value, "unit": x.unit, "confidence": x.confidence}
                    for x in known.values()]
            best = best_attempt(r.test_key, allv)
            vals = dict(status="recorded" if best else "rejected", best_value=best["value"] if best else None,
                        unit=best["unit"] if best else "", confidence=best["confidence"] if best else "RED", attempts_count=len(allv))
            res = s.scalar(select(Result).where(Result.athlete_uid == uid, Result.test_key == r.test_key))
            if res is None:
                s.add(Result(athlete_uid=uid, test_key=r.test_key, **vals))
            else:
                for k, v in vals.items():
                    setattr(res, k, v)
            # The de-identified result counts every attempt of the same person, including ones recorded on another phone.
            every = [{"attempt_no": x.attempt_no, "valid": x.valid, "value": x.value, "unit": x.unit, "confidence": x.confidence}
                     for x in s.scalars(select(Attempt).join(Athlete, Athlete.uid == Attempt.athlete_uid)
                                        .where(Athlete.pseudonym == ath.pseudonym, Attempt.test_key == r.test_key))]
            dbest = best_attempt(r.test_key, every)
            dvals = dict(status="recorded" if dbest else "rejected", best_value=dbest["value"] if dbest else None,
                         unit=dbest["unit"] if dbest else "", confidence=dbest["confidence"] if dbest else "RED", attempts_count=len(every))
            dres = s.scalar(select(DeidResult).where(DeidResult.pseudonym == ath.pseudonym, DeidResult.test_key == r.test_key))
            if dres is None:
                s.add(DeidResult(pseudonym=ath.pseudonym, test_key=r.test_key, **dvals))
            else:
                for k, v in dvals.items():
                    setattr(dres, k, v)
            results_n += 1

        s.add(SyncLog(device_id=batch.device_id, athletes_accepted=len(uids), athletes_rejected=len(rejected), attempts_upserted=attempts_n))
        s.commit()
        return schemas.SyncResponse(server_time=now, accepted_athletes=len(uids), attempts_upserted=attempts_n,
                                    results_upserted=results_n, rejected=rejected, athlete_uids=uids)

    # ----------------------------------------------------------------------------------------------- media
    @app.post("/v1/media", response_model=schemas.MediaOut, tags=["media"], dependencies=[require("device")],
              summary="Upload one video or the scale photo for an athlete")
    async def upload_media(athlete_uid: str = Form(...), test_key: str = Form(...), kind: str = Form(..., pattern="^(video|photo)$"),
                           key: str = Form(..., max_length=120), attempt_id: str | None = Form(None), sha256: str | None = Form(None),
                           file: UploadFile = File(...), s: Session = Depends(get_session)):
        ath = s.get(Athlete, athlete_uid)
        if ath is None:
            raise HTTPException(404, "Unknown athlete: sync the athlete first")
        safe = re.sub(r"[^A-Za-z0-9._-]", "_", key)[:100]
        folder = os.path.join(settings.media_dir, athlete_uid)
        os.makedirs(folder, exist_ok=True)
        path = os.path.join(folder, safe)
        h, size, limit = hashlib.sha256(), 0, settings.max_media_mb * 1048576
        with open(path + ".part", "wb") as out:
            while chunk := await file.read(1 << 20):
                size += len(chunk)
                if size > limit:
                    out.close(); os.remove(path + ".part")
                    raise HTTPException(413, f"File is larger than {settings.max_media_mb} MB")
                h.update(chunk); out.write(chunk)
        if size == 0:
            os.remove(path + ".part")
            raise HTTPException(422, "Empty file")
        if sha256 and sha256.lower() != h.hexdigest():
            os.remove(path + ".part")
            raise HTTPException(422, "SHA-256 does not match the uploaded bytes")
        os.replace(path + ".part", path)
        until = utcnow() + dt.timedelta(days=settings.retention_days) if settings.retention_days > 0 else None
        row = s.scalar(select(Media).where(Media.athlete_uid == athlete_uid, Media.key == key))
        if row is None:
            row = Media(athlete_uid=athlete_uid, key=key, test_key=test_key, kind=kind, size_bytes=size, sha256=h.hexdigest(), path=path)
            s.add(row)
        row.test_key, row.attempt_id, row.kind, row.mime = test_key, attempt_id, kind, file.content_type or ""
        row.size_bytes, row.sha256, row.path, row.uploaded_at, row.retain_until = size, h.hexdigest(), path, utcnow(), until
        s.commit()
        return schemas.MediaOut(key=key, kind=kind, size_bytes=size, sha256=h.hexdigest(), retain_until=until)

    # ----------------------------------------------------------------------------------------------- export
    def _subjects(s: Session, age_band: str | None, gender: str | None):
        q = select(DeidSubject)
        if age_band:
            q = q.where(DeidSubject.age_band == age_band)
        if gender:
            q = q.where(DeidSubject.gender == gender)
        return q

    @app.get("/v1/deidentified/subjects", response_model=schemas.DeidPage, tags=["export"], dependencies=[require("export")],
             summary="De-identified subjects with their results")
    def deid_subjects(limit: int = Query(100, ge=1, le=500), offset: int = Query(0, ge=0), age_band: str | None = None,
                      gender: str | None = Query(None, pattern="^[FMO]$"), s: Session = Depends(get_session)):
        q = _subjects(s, age_band, gender)
        total = s.scalar(select(func.count()).select_from(q.subquery()))
        rows = s.scalars(q.order_by(DeidSubject.pseudonym).limit(limit).offset(offset)).all()
        out = []
        for sub in rows:
            res = s.scalars(select(DeidResult).where(DeidResult.pseudonym == sub.pseudonym).order_by(DeidResult.test_key)).all()
            out.append(schemas.DeidSubjectOut(pseudonym=sub.pseudonym, age_years=sub.age_years, age_band=sub.age_band, gender=sub.gender,
                                              assessment_month=sub.assessment_month, mother_cm=sub.mother_cm, father_cm=sub.father_cm,
                                              results=[schemas.DeidResultOut(test_key=r.test_key, status=r.status, best_value=r.best_value,
                                                                              unit=r.unit, confidence=r.confidence, attempts_count=r.attempts_count) for r in res]))
        return schemas.DeidPage(total=total or 0, limit=limit, offset=offset, subjects=out)

    @app.get("/v1/deidentified/results.csv", tags=["export"], dependencies=[require("export")],
             summary="Every de-identified result as CSV", response_class=StreamingResponse)
    def deid_csv(s: Session = Depends(get_session)):
        buf = io.StringIO()
        w = csv.writer(buf)
        w.writerow(["pseudonym", "age_years", "age_band", "gender", "assessment_month", "test_key", "status", "best_value", "unit", "confidence", "attempts_count"])
        q = select(DeidSubject, DeidResult).join(DeidResult, DeidResult.pseudonym == DeidSubject.pseudonym).order_by(DeidSubject.pseudonym, DeidResult.test_key)
        for sub, r in s.execute(q):
            w.writerow([sub.pseudonym, sub.age_years, sub.age_band, sub.gender, sub.assessment_month, r.test_key, r.status,
                        "" if r.best_value is None else r.best_value, r.unit, r.confidence, r.attempts_count])
        buf.seek(0)
        return StreamingResponse(iter([buf.getvalue()]), media_type="text/csv",
                                 headers={"Content-Disposition": 'attachment; filename="bfa_deidentified_results.csv"'})

    @app.get("/v1/stats", response_model=schemas.Stats, tags=["export"], dependencies=[require("export")], summary="Counts")
    def stats(s: Session = Depends(get_session)):
        n = lambda m: s.scalar(select(func.count()).select_from(m)) or 0  # noqa: E731
        return schemas.Stats(devices=n(Device), athletes=n(Athlete), attempts=n(Attempt), media_files=n(Media), deidentified_subjects=n(DeidSubject))

    # ------------------------------------------------------------------------------------------------ admin
    @app.get("/v1/athletes/{athlete_uid}", tags=["admin"], dependencies=[require()], summary="One athlete with identity, consent and media (identified)")
    def athlete_detail(athlete_uid: str, s: Session = Depends(get_session)):
        a = s.get(Athlete, athlete_uid)
        if a is None:
            raise HTTPException(404, "Unknown athlete")
        cons = s.scalars(select(Consent).where(Consent.athlete_uid == athlete_uid).order_by(Consent.signed_at.desc())).all()
        media = s.scalars(select(Media).where(Media.athlete_uid == athlete_uid)).all()
        return {"uid": a.uid, "pseudonym": a.pseudonym, "name": a.name, "age_years": a.age_years, "gender": a.gender, "school": a.school,
                "id_type": a.id_type, "assessment_date": a.assessment_date.isoformat(),
                "consents": [{"signed_at": c.signed_at.isoformat(), "signer_name": c.signer_name, "relationship": c.relationship_, "minor": c.minor,
                              "assess": c.item_assess, "video": c.item_video, "data": c.item_data, "parent_height": c.item_parent_height,
                              "has_signature": bool(c.signature_png)} for c in cons],
                "media": [{"key": m.key, "kind": m.kind, "size_bytes": m.size_bytes, "retain_until": m.retain_until.isoformat() if m.retain_until else None} for m in media]}

    @app.delete("/v1/athletes/{athlete_uid}", tags=["admin"], dependencies=[require()], summary="Withdraw consent: erase the athlete everywhere")
    def withdraw(athlete_uid: str, s: Session = Depends(get_session)):
        """Removes the identified record, signed consent, attempts, media files and the de-identified subject."""
        a = s.get(Athlete, athlete_uid)
        if a is None:
            raise HTTPException(404, "Unknown athlete")
        files = 0
        for m in s.scalars(select(Media).where(Media.athlete_uid == athlete_uid)):
            try:
                os.remove(m.path); files += 1
            except OSError:
                pass
        pseud = a.pseudonym
        s.delete(a)
        s.flush()
        # The de-identified subject goes too, unless the same person is still held under another phone's record.
        if s.scalar(select(func.count()).select_from(Athlete).where(Athlete.pseudonym == pseud)) == 0:
            sub = s.get(DeidSubject, pseud)
            if sub is not None:
                s.delete(sub)
        s.commit()
        return {"erased": True, "media_files_removed": files}

    @app.post("/v1/admin/purge-expired", tags=["admin"], dependencies=[require()], summary="Delete videos and photos past their retention date")
    def purge(s: Session = Depends(get_session)):
        n = 0
        for m in s.scalars(select(Media).where(Media.retain_until.is_not(None), Media.retain_until < utcnow())):
            try:
                os.remove(m.path)
            except OSError:
                pass
            s.delete(m); n += 1
        s.commit()
        return {"purged": n}

    return app


app = create_app() if os.environ.get("BFA_AUTOSTART", "1") == "1" else None
