# Battery Fitness Assessment

**On-device AI measurement for India's 10 SAI Battery Fitness Assessment Tests.**
Offline-first. Budget Android phone. Operated by a PE teacher with no videography training.

Built for the National Hackathon on AI-Powered Battery Fitness Assessment
(NeGD · MeitY, in partnership with MYAS).

---

## The design thesis

Most approaches to this problem start by trying to solve monocular metric depth:
a phone camera sees a flat 2D image, so how do you turn pixels into centimetres?

We think that framing is a trap. The SAI Technical Handbook already puts a
graduated reference in the frame of every distance test:

| Test | Handbook equipment | Reference available |
|---|---|---|
| Height | Stadiometer **or steel ruler / tape measure against a wall** | Graduated scale, coplanar with the subject |
| Sit & Reach | Sit and reach box **or a ruler** | Graduated scale along the reach axis |
| Vertical Jump | *"A vertical wall prominently marked in centimetres up to 3.50 metres"* | Graduated scale, coplanar with the chalk mark |
| Broad Jump | Floor, **measuring tape**, marker | Graduated scale along the jump axis |
| Medicine Ball | **Steel measuring tape**, nails, marking powder | Graduated scale along the throw axis |
| 30 m Sprint | **30 m marked track** | Known 30 m baseline |
| Shuttle Run | **Two parallel lines, 10 m apart** | Known 10 m baseline |

So we read the same physical reference the human assessor reads. This digitises
the official protocol rather than replacing it. The remaining problem is
apparatus placement and framing — which is a UX problem, and one the app is
required to solve anyway.

### The measurement ladder

Every scale-dependent test degrades through explicit rungs, and the confidence
indicator reports **which rung produced the number**:

| Rung | Reference | Band |
|---|---|---|
| 1 | Printed marker of known size | GREEN |
| 2 | The protocol's own tape or marked wall | GREEN |
| 3 | Athlete's own measured height as a known length | AMBER |
| 4 | Device intrinsics + IMU geometry | AMBER |
| 5 | Manual entry | RED / explicit override |

This is the direct answer to the requirement to *"use the confidence indicator
to show when accuracy may be reduced… rather than silently giving wrong
results."*

### Rules, not learned cheat detection

The Handbook does not merely describe the sit-up, it enumerates the fouls in
writing — fingers clasped behind the neck, no pushing off the floor with the
elbow, elbows returned flat before the next rep, no resting between reps.

Because those rules are already written down, enforcing them needs **no training
data**. That matters: ML-based anti-cheat is explicitly out of scope for this
hackathon precisely because genuine and fraudulent footage does not exist to
train on. A geometric rule-checker is not a learned cheat detector — it is the
Handbook, executed.

The output is a per-rep audit table naming the rule each rejected rep failed.
A human assessor cannot reliably audit 40 reps against five rules in 45 seconds.

---

## Status

Honest, and kept current. Nothing below is aspirational.

### Built and verified

| Component | State | Evidence |
|---|---|---|
| Sit-up analyzer (Test 9) | Working | State machine + Handbook rules 2, 3a, 3b, 3c. **7/7 unit tests pass** |
| Vertical jump analyzer (Test 4) | Working | Take-off/landing detection, `h = g·t²/8`, frame-rate error propagated to cm |
| Height analyzer (Test 1) | Working | Reference-length scaling, segmentation-mask vertex, Frankfurt-plane check |
| Confidence banding | Working | Derived from pose detection rate, frame rate, stillness, reference lock |
| Graceful degradation | Verified | On undetectable footage all three return RED **with a stated reason**, never a false zero |
| Android app shell | Builds | Signed APK produced; domain model, bilingual resources, theme |
| No Play Services (C8) | **Verified** | 0 `com/google/android/gms` references in `classes.dex`; none in the dependency graph |
| Bilingual (Hindi + English) | **Verified** | `hi` locale packaged; `app_name` → `बैटरी फ़िटनेस` |
| Android 10 floor | **Verified** | `minSdk 29`, `targetSdk 37` |

### In progress

Room persistence layer · CameraX capture with per-test duration gate ·
Kotlin port of the analyzers onto MediaPipe Tasks · capture-guidance wizard ·
bilingual PDF report card.

### Designed, not built

Medicine ball throw (classical CV: Hough circles → Kalman → parabola fit
against the ground plane) · 30 m sprint (finish-line crossing + audio start —
the perpendicular full-track view needs ~23 m of lateral clearance that no
school ground reliably has) · shuttle run · endurance lap timing ·
production sync and de-identification gateway.

---

## Architecture

**Post-capture analysis, not real-time.** Video is mandatory for every test
anyway, so live processing is reserved for capture guidance (framing, angle,
exposure, stillness) while measurement runs afterwards at full resolution over
every frame. This decouples accuracy from device speed: on a budget phone a slow
processor costs latency, not precision. Teams that run everything live produce
worse numbers on exactly the device the challenge specifies.

```
lab/           Python reference implementation + validation harness
  analyzers/   pose.py · situp_counter.py · jump_flight.py · height_scale.py
app/           Android application (Kotlin, Compose, CameraX, MediaPipe Tasks)
backend/       FastAPI sync service + Dockerfile
docs/          HLD · LLD · Model Cards · Validation Report · User Guide
models/        MediaPipe .task files (fetched by script, not committed)
compliance/    Data-protection declaration, consent forms, data inventory
```

**Why a Python lab as well as the app:** MediaPipe emits the same 33-landmark
topology in Python and on Android, so thresholds tuned in the lab transfer
directly to Kotlin. A tuning iteration takes seconds in Python and
rebuild-deploy-record minutes on device. The lab also becomes the validation
harness the accuracy report requires.

---

## Toolchain

Verified working, 2026-08-08:

- JDK 17 · Gradle 9.7 · AGP 9.3.1 · Kotlin 2.3.21 · KSP 2.3.11
- Android SDK 37 · `minSdk 29` · MediaPipe `tasks-vision` 1.0.0
- Python 3.10 · `mediapipe` 1.0.0 · `opencv-python` 5.0.0

Three findings worth recording, since each costs an afternoon to rediscover:
AGP 9 has **built-in Kotlin support** and rejects `org.jetbrains.kotlin.android`
outright; **KSP lags Kotlin** (newest 2.3.11, no 2.4.x), and Room needs KSP, so
Kotlin is pinned to 2.3.21; current AndroidX requires **compileSdk 37**,
independent of `minSdk`.

Licensing is deliberate: MediaPipe and OpenCV are Apache 2.0 / BSD. **AGPL-3.0
models (notably Ultralytics YOLO) are excluded** — the winning solution goes to
production with IP vesting in SAI, and AGPL copyleft conflicts with that.

---

## Running the analyzer lab

```bash
cd lab
python3 -m venv .venv && ./.venv/bin/pip install -r requirements.txt

./.venv/bin/python test_situp_logic.py          # 7/7, no footage needed

./.venv/bin/python run.py probe   clips/x.mp4   # 720p / 30fps quality gate
./.venv/bin/python run.py situps  clips/x.mp4 --age 14
./.venv/bin/python run.py jump    clips/x.mp4
./.venv/bin/python run.py height  clips/x.mp4 --ref-px 412 --ref-cm 100
```

Building the app:

```bash
cd app && ./gradlew assembleDebug
```

---

## Data protection

Non-negotiable, and enforced in the repository itself:

- **No athlete video reaches any commercial cloud service.** All inference runs
  on-device or on local hardware.
- `.gitignore` excludes every video, image and model binary. Footage lives in
  `lab/clips/`, which is never committed and never uploaded.
- Validation footage uses consenting adults; any minor requires written
  parental consent obtained before recording.

---

## What we do not claim

Stated plainly, because a submission that names its gaps is more useful than one
that does not:

- **No accuracy figure here is a measurement yet.** The error estimates in the
  code comments are engineering derivations to be replaced by validation data.
- **Height cannot meet ±1 cm where hair is untied.** A human assessor presses the
  stadiometer through the hair; a camera sees the outside of it. That is a 1–4 cm
  bias, larger than the tolerance and larger than every other error source
  combined. There is no engineering fix — we will measure it and report it.
- **Vertical jump cannot meet ±2 cm at 30 fps.** One frame is 33 ms, which is
  ~4 cm on a typical flight time. That is arithmetic, not tuning. We report AMBER
  at 30 fps and claim GREEN only at 120 fps.
- **Percentiles and rankings are illustrative.** No validated Indian norm data
  exists for these tests, and anything presented otherwise would be unfounded.
