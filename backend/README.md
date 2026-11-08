# Sync service and de-identification gateway

Receives results from the assessor's phone **after** the video has been processed on the phone (ruling C29). The video
itself stays on the phone unless it is uploaded for audit (rulings C23, C36, C42). Built with FastAPI and SQLAlchemy;
PostgreSQL in production, SQLite for development.

| File | What it is |
|---|---|
| [`openapi.json`](openapi.json) | The API contract (OpenAPI 3.1), generated from the code. Interactive docs at `/docs` when the service runs. |
| [`schema.sql`](schema.sql) | The PostgreSQL schema, generated from `app/models.py`. |
| `app/` | The service. `deid.py` is the de-identification gateway. |
| `tests/` | 20 tests: sync, consent rules, result rules, de-identification, auth, media, withdrawal. |
| `Dockerfile`, `docker-compose.yml` | The service, and the service with PostgreSQL. |

Regenerate the contract after changing the code: `python backend/scripts/export_contract.py`.

## Two zones

| Zone | Tables | Contents | Scope |
|---|---|---|---|
| Identified | `devices`, `athletes`, `consents`, `attempts`, `results`, `media`, `sync_log` | Name, school, ID, signed consent with signature, every attempt, the rule audit, the media index | `admin` |
| De-identified | `deid_subjects`, `deid_results` | A pseudonym, age and age band, gender, assessment month, best result and confidence per test | `export` |

The gateway (`app/deid.py`) writes the de-identified zone on every sync. A pseudonym is HMAC-SHA256 under
`BFA_PSEUDONYM_SECRET`; if the athlete has an APAAR, Aadhaar or NSRS number it is derived from that, so one athlete on two
phones is one subject, and their attempts from both phones count toward one result. Name, school, ID number, signer,
assessor, video keys, free-text methods and the exact date are never copied. Parental height is copied only when its
optional consent was given. A test asserts that none of those strings appears in any export.

For a hardened deployment give each zone its own PostgreSQL schema and role: the `export` role may read only
`deid_*`, so a leak of that role cannot reach an identity. Keep the pseudonym secret out of the analytics environment and
never change it once data exists, because that re-keys every pseudonym.

## Endpoints

Authenticate with `X-API-Key`. A key has one scope: `device` (the app), `export`, or `admin` (which can do everything).

| Method and path | Scope | Purpose |
|---|---|---|
| `POST /v1/sync` | device | Athletes, signed consent and attempts. Idempotent. An athlete without complete consent is rejected with a reason and none of their data is stored. Under-18s need a parent or guardian signer. |
| `POST /v1/media` | device | One video or the scale photo for an athlete: size limit, SHA-256 check, retention date. |
| `GET /v1/deidentified/subjects` | export | De-identified subjects with results, paged, filterable by age band and gender. |
| `GET /v1/deidentified/results.csv` | export | Every de-identified result as CSV. |
| `GET /v1/stats` | export | Counts. |
| `GET /v1/athletes/{uid}` | admin | Identity, consent history and media of one athlete. |
| `DELETE /v1/athletes/{uid}` | admin | Withdrawal of consent: erases the identified record, consent, attempts, media files and the de-identified subject. |
| `POST /v1/admin/purge-expired` | admin | Deletes videos and photos past their retention date. |
| `GET /healthz`, `GET /v1/config` | none | Liveness, and the limits the app should respect. |

The best result per test follows the handbook: the highest value for jumps, throws and sit-ups, the lowest time for the
30 m run, shuttle and endurance, and the latest valid reading for height. It is computed on the server from the attempts.

## Run it

Development, with nothing to install except Python:

```bash
pip install -r backend/requirements-dev.txt
cd backend
BFA_DEV_OPEN=1 uvicorn app.main:app --reload          # no keys, a development secret: never in production
pytest
```

With PostgreSQL:

```bash
cd backend
export BFA_DEVICE_KEY=$(openssl rand -hex 24) BFA_EXPORT_KEY=$(openssl rand -hex 24) BFA_ADMIN_KEY=$(openssl rand -hex 24)
export BFA_PSEUDONYM_SECRET=$(openssl rand -hex 32)
docker compose up --build
```

Keep those four values somewhere safe. Put the service behind HTTPS (a reverse proxy or load balancer): the phone app
refuses to send to a plain `http://` server from an `https://` page.

| Setting | Default | Meaning |
|---|---|---|
| `DATABASE_URL` | `sqlite:///./bfa.db` | `postgresql+psycopg://user:pass@host:5432/bfa` in production |
| `BFA_KEYS` | none | `key:scope,key:scope`. With none set the service refuses requests (503) unless `BFA_DEV_OPEN=1` |
| `BFA_PSEUDONYM_SECRET` | none | Required to sync (503 without it) |
| `BFA_MEDIA_DIR` | `./media` | Where videos and photos are written; use a volume or object storage in production |
| `BFA_MAX_MEDIA_MB` | 300 | Per file |
| `BFA_RETENTION_DAYS` | 0 | Audit retention for media; the real period is SAI's SLA (rulings C14, C36). 0 keeps until purged |
| `BFA_CORS_ORIGINS` | `*` | Restrict to the app's origin in production |
| `BFA_MAX_BATCH_ATHLETES` | 500 | Per sync request |

## Scale, and what has and has not been measured

The service is stateless, so it scales by running more replicas behind a load balancer. All state is in PostgreSQL and the
media store. A sync is one idempotent batch per phone, so a retry after a dropped connection is harmless, and two requests
from one phone that collide on a first insert are retried by the server.

`scripts/load_test.py` sends many phones' batches (each athlete with all ten tests and two attempts). On a laptop with
**SQLite**, which allows one writer at a time, it stored 15,000 athletes (300,000 attempts) with no failures at about 60 to
70 athletes per second. That is the floor, not a result for production: it has **not** been run against PostgreSQL, behind a
load balancer, or at the scale of the rulings (10,000 or more users). Do that before relying on it. For many replicas put
PgBouncer in front of PostgreSQL.

Not built: rate limiting and TLS (do both at the proxy), per-assessor accounts (the rulings ask for no live login, so a
phone holds a scoped key), and object storage for media (files go to `BFA_MEDIA_DIR`).
