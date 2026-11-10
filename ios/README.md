# AthleteIQ™ — iOS Battery Fitness Assessment (`ios/`)

This directory contains the iOS implementation of the **Sports Authority of India (SAI) 10-Test Battery Fitness Assessment** on the `sai` branch, built to mirror `app/` (Android) and `lab/` (Python AI analyzers) while enabling immediate testing on an Apple iPhone.

## Structure

1. **[`BatteryFitnessIOS/`](./BatteryFitnessIOS/) — Native Swift & SwiftUI iOS Package**
   - [`Sources/BatteryFitnessCore/BatteryTest.swift`](./BatteryFitnessIOS/Sources/BatteryFitnessCore/BatteryTest.swift): Defines all **10 SAI Battery Tests** with exact Concept Note §6.3.1 duration/size gates, Handbook attempt counts, age-group rules (`<12` vs `12+`), and the **4 on-device AI tests** (`height`, `sit_and_reach`, `vertical_jump`, `sit_ups`), **3 AI-planned tests** (`broad_jump`, `sprint_30m`, `shuttle_run`), and **3 Manual tests** (`weight`, `medicine_ball_throw`, `endurance_run`).
   - [`Sources/BatteryFitnessCore/SessionEngine.swift`](./BatteryFitnessIOS/Sources/BatteryFitnessCore/SessionEngine.swift): Implements `ATHLETE_WISE` and `TEST_WISE` traversal and proves order equivalence (`SessionEngine.orderEquivalence`).
   - [`Sources/BatteryFitnessCore/CaptureQualityGate.swift`](./BatteryFitnessIOS/Sources/BatteryFitnessCore/CaptureQualityGate.swift): Enforces §6.3.1 point-of-capture duration rejection, auto-stop at maximum duration, 720p/30fps checks, and 50% file-size tolerance for 1080p/120fps capture.
   - [`Sources/BatteryFitnessCore/Analyzers.swift`](./BatteryFitnessIOS/Sources/BatteryFitnessCore/Analyzers.swift): Native Swift implementation of all 4 AI analyzers (`HeightAnalyzer`, `SitAndReachAnalyzer`, `VerticalJumpAnalyzer`, `SitUpAnalyzer`) with Handbook foul auditing (Rules 2, 3a, 3b, 3c for Sit-Ups; knee straightness `≥ 168°` and hold stability `≥ 0.8s` for Sit & Reach) and 5-rung confidence banding (`GREEN`, `AMBER`, `RED`).
   - [`Sources/BatteryFitnessIOSApp/BatteryFitnessApp.swift`](./BatteryFitnessIOS/Sources/BatteryFitnessIOSApp/BatteryFitnessApp.swift): Native SwiftUI iOS views (`BatteryFitnessRootView`, `SessionActiveView`, `TestCellDetailView`) with English & Hindi (`हिन्दी`) support.
   - [`Tests/BatteryFitnessCoreTests/BatteryFitnessCoreTests.swift`](./BatteryFitnessIOS/Tests/BatteryFitnessCoreTests/BatteryFitnessCoreTests.swift): XCTest unit test suite covering all 10 tests, session sequencing equivalence, capture quality gate, and all 4 AI analyzers.

2. **[`iphone-pwa/`](./iphone-pwa/) — Instant Real-iPhone Live Camera App (iOS Safari / Home Screen App)**
   - [`index.html`](./iphone-pwa/index.html) & [`manifest.json`](./iphone-pwa/manifest.json): Standalone iOS WebKit app (`apple-mobile-web-app-capable`) matching Slide 7 of the DSeT submission deck (`Battery Assessment`, `New session`, `Athletes`, `Assessments`, `10-Test Battery Reference` with `AI` / `Planned` / `Manual` badges, `Athlete-wise` & `Test-wise` sequencing, live iPhone camera capture with MediaPipe Pose 33-point skeleton overlay, §6.3.1 duration gate, all 4 AI analyzers, manual entry for the remaining 6 tests, bilingual English/Hindi toggle, and full SAI Report Card).
   - [`serve_iphone.py`](./iphone-pwa/serve_iphone.py): Zero-dependency Python HTTPS server that generates a local self-signed TLS certificate so iOS Safari grants camera access over local Wi-Fi.

## Working with no internet

The pose tests (height, Sit & Reach, Vertical Jump, Broad Jump, sit-ups) need the MediaPipe runtime and pose models
(about 34 MB). They are not committed. Fetch them once while online:

```bash
python3 ios/iphone-pwa/fetch_vendor.py
```

The app then uses `vendor/mediapipe/` instead of the public CDN, and the service worker (`sw.js`) keeps the app and those
files on the phone. After one online visit the whole app, pose tests included, works with no network. The 30 m run,
shuttle, endurance and ball tests never needed the models.

Two things to know:

- Service workers only run over HTTPS (or `localhost`) with a certificate the browser trusts. A plain self-signed
  certificate lets the camera work after "visit this website", but the phone will not register the service worker, so
  the app cannot work offline. Fix it once with a local certificate authority:

  ```bash
  bash ios/iphone-pwa/make_trusted_cert.sh      # re-run if the Mac's Wi-Fi address changes
  ```

  AirDrop `ios/iphone-pwa/.certs/sai_local_ca.crt` to the iPhone, install it (Settings › General › VPN & Device
  Management), and turn it on under Settings › General › About › Certificate Trust Settings. The authority only covers
  private network addresses and `localhost`, so it cannot vouch for any public website. Its key stays in the git-ignored
  `.certs/` folder; do not share it. Alternatively, host the folder (with `vendor/`) on an HTTPS site with a valid
  certificate. The home screen shows "Works offline" only when the app and models are cached.
- A Home Screen app keeps its own storage, separate from Safari. Athletes and results entered in a Safari tab do not
  appear in the Home Screen app, so use one or the other for a session.
- The optional wall-photo auto-read of a written height (OCR) still downloads its engine from the internet. Without a
  connection the coach types the number instead.

## Syncing to the server

The Login screen and the report-cards screen have a **Server sync** card. Enter the address of the sync service
([`backend/`](../backend/README.md)) and the device key, tap **Test connection**, then **Sync now**.

- What is sent: athletes with a signed consent (and the signature image), their attempts and the rule audit. An athlete
  without a signed consent is never sent. Repeating a sync is safe.
- Videos and the scale photo are sent only if **Also upload attempt videos and the scale photo** is ticked. It is off by
  default because the consent text says videos stay on the phone; turn it on only for a server run by SAI or NeGD, and update
  the consent wording if uploads are meant to happen.
- A page served over `https://` cannot send to an `http://` server, so put the service behind HTTPS.

## How to Test on Your Apple iPhone Right Now

From the repository root (on branch `sai`):

```bash
python3 ios/iphone-pwa/serve_iphone.py
```

Then on your iPhone (connected to the same Wi-Fi as your Mac):
1. Open **Safari** and visit **`https://192.168.1.103:8443`** (or the Wi-Fi IP printed in the terminal).
2. On first load, tap **Show Details → visit this website** to trust the local development certificate so iOS Safari allows live camera capture.
3. Optional: Tap **Share → Add to Home Screen** in Safari to launch it as a full-screen standalone iOS app on your iPhone.
