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

## How to Test on Your Apple iPhone Right Now

From the repository root (on branch `sai`):

```bash
python3 ios/iphone-pwa/serve_iphone.py
```

Then on your iPhone (connected to the same Wi-Fi as your Mac):
1. Open **Safari** and visit **`https://192.168.1.103:8443`** (or the Wi-Fi IP printed in the terminal).
2. On first load, tap **Show Details → visit this website** to trust the local development certificate so iOS Safari allows live camera capture.
3. Optional: Tap **Share → Add to Home Screen** in Safari to launch it as a full-screen standalone iOS app on your iPhone.
