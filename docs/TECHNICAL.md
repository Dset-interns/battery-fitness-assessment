# Technical documentation

## Toolchain

| Part | Version |
|---|---|
| Web app | One HTML file, plain JavaScript (ES modules), no framework, no build step |
| Pose AI | MediaPipe Tasks Vision 0.10.14 (Pose Landmarker full and lite), run in the browser on the phone |
| Android shell | Kotlin, Android Gradle Plugin 9.3.1, Gradle 9.7, JDK 17, `compileSdk 37`, `minSdk 29`, `targetSdk 35` |
| Backend | Python 3.10+, FastAPI, SQLAlchemy 2, Pydantic 2, PostgreSQL 16 (SQLite for development) |
| Python lab | `lab/`, MediaPipe and OpenCV; the original research harness (still on the pre-revision rules) |

## Licences

MediaPipe and the pose models: Apache 2.0. OpenCV: Apache 2.0 / BSD. FastAPI, Pydantic, SQLAlchemy, Uvicorn: MIT / BSD. The
project deliberately excludes AGPL-licensed models (for example Ultralytics YOLO) so that the work can be handed over with the
intellectual property vested in SAI. Tesseract.js (optional wall-photo OCR) is Apache 2.0 and is loaded from a CDN on demand.

## Repository layout

```
ios/iphone-pwa/    the app: index.html, sw.js, manifest.json, logos, fetch_vendor.py, serve_iphone.py
app/webapp/        Android WebView shell (the shipping Android build)
app/app/           earlier native Compose app, kept for reference, not in the default build
backend/           sync service, openapi.json, schema.sql, tests, Dockerfile, compose file
docs/              these documents
lab/               Python reference harness
ios/BatteryFitnessIOS/  earlier Swift package, kept for reference
```

## Run the app

```bash
python3 ios/iphone-pwa/fetch_vendor.py          # once, online: the pose runtime and models (about 34 MB, not committed)
python3 ios/iphone-pwa/serve_iphone.py          # https://localhost:8443 and the Mac's address for a phone on the same Wi-Fi
```

Service workers (offline) need HTTPS with a certificate the browser trusts: `localhost` qualifies, the self-signed phone address
does not. Host the folder on a valid HTTPS site for offline use on a phone. Details in [`../ios/README.md`](../ios/README.md).

## Build the Android APK

See [`../app/BUILD.md`](../app/BUILD.md): `fetch_vendor.py`, then `./gradlew :webapp:assembleRelease` with a keystore. Release
builds are deterministic.

## Run and test the backend

```bash
pip install -r backend/requirements-dev.txt
cd backend && BFA_DEV_OPEN=1 uvicorn app.main:app      # development only
pytest                                                   # 20 tests
python scripts/export_contract.py                        # regenerate openapi.json and schema.sql
```

With PostgreSQL: `docker compose up --build` (see [`../backend/README.md`](../backend/README.md) for the keys and secret).

## Testing done

| What | How | Result |
|---|---|---|
| Backend | 20 pytest tests | pass |
| Timing, ball, jump, sit-up logic | Synthetic data with known truth, and a synthetic run encoded to video and decoded as the camera would be | Within about one frame on the synthetic data, with the start picked up up to one or two frames late |
| App flow | Scripted browser run: login, register, consent, every test with a fake camera, report, PDF, sync to a live server | passes; every camera attempt is rejected because a fake camera shows no athlete |
| Load | `backend/scripts/load_test.py` against SQLite | 15,000 athletes (300,000 attempts) without failure, about 60 to 70 athletes per second |

## Not tested

Real athletes on real footage; real phones (iPhone Safari, the Android APK on a device or emulator); PostgreSQL under load; the
Docker image; any run at 10,000 users. These are the open items in [HANDBOOK_COMPLIANCE.md](HANDBOOK_COMPLIANCE.md).
