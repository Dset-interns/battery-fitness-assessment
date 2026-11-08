# High-level design

## What it is

An app for the assessor that records the ten SAI battery tests on a phone. Each test is measured from video on the phone,
the result and the evidence are kept on the phone, and results go to a programme server after the video has been processed.
It works with no network once installed.

## Components

```
 Assessor's phone (Android app, or the web app in Safari / Chrome)             Programme server
 ┌─────────────────────────────────────────────────────────────┐              ┌──────────────────────────────┐
 │ Web app (ios/iphone-pwa/index.html, one file)                │              │ Sync service (backend/)       │
 │  screens: opening, login, register, consent, setup, session, │              │  POST /v1/sync   idempotent   │
 │           test, capture, report, roster                      │   HTTPS      │  POST /v1/media               │
 │  measurement: pose model (5 tests) + video motion (5 tests)  │ ───────────▶ │  identified zone  (admin)     │
 │  evidence: video per attempt, scale photo, consent signature │  results,    │  de-identification gateway    │
 │  storage: localStorage (records) + IndexedDB (media)         │  consent,    │  de-identified zone (export)  │
 │  offline: service worker (browser) / bundled assets (APK)    │  optional    │  PostgreSQL + media store     │
 └─────────────────────────────────────────────────────────────┘  media       └──────────────────────────────┘
        Android APK = the same page in a WebView shell (app/webapp), signed, reproducible
```

## Data flow for one athlete

1. **Register** (name, age, gender, school, programme ID, optional parental height). Age is fixed at registration.
2. **Consent**: the athlete, or a parent or guardian for under-18s, ticks the statements and signs on screen. No consent, no assessment.
3. **Session**: coach or individual mode, by test or by athlete, a roster of consented athletes.
4. **Each test**: the assessor confirms the declaration, sets up the scene if needed (taps lines), and records. The attempt is
   analysed on the phone, the result and a rule audit are stored with the video. Height and weight can also be typed in; no other test can.
5. **Report**: one A4 page per athlete (results, confidence, attempts, rule audit, BMI, consent, signatures) and a roster page; saved as PDF.
6. **Sync** (optional, when a server is configured): athletes with a signed consent, their attempts and the declaration. Videos
   and the scale photo are uploaded only if the assessor turns that on.

## Measurement

| Test | Method | Needs |
|---|---|---|
| 1 Height | Pose model with segmentation, pixel-to-cm from a wall reference | Wall reference, athlete against the wall |
| 2 Weight | Typed from the scale, with a photo of the display | The scale |
| 3 Sit & Reach | Pose model: fingertip against the heel line, 23 cm tape zero | Tape on the floor, side view |
| 4 Vertical jump | Pose model: fingertip at standing reach and at the top of the jump | Wall scale, side on |
| 5 Broad jump | Pose model: take-off line to back of the heels | Floor tape, side view |
| 6 Ball throw | Video: background model, ball flight, first ground contact against a marker of known distance | Tripod, tape, marker |
| 7 30 m run | Video motion: first movement to torso at the finish line | Tripod, two lines 30 m apart |
| 8 Shuttle | Video motion: four lengths, torso over the start line at the end | Tripod, two lines 10 m apart |
| 9 Sit-ups | Pose model: state machine with the three handbook fouls | Side view |
| 10 Endurance | Video motion: lap crossings of one line | Tripod, a 400 m or 200 m loop |

Every result carries a confidence band (green, amber, red) and a reason. A rejected attempt is shown as rejected with why;
it is never turned into zero.

## Two data zones

Identified data (name, school, ID, signed consent, every attempt, media) and de-identified data (a pseudonym, age band, gender,
month, best results) live in separate tables with separate API scopes. The gateway writes the second from the first on every
sync. See [DATA_PROTECTION.md](DATA_PROTECTION.md) and [`../backend/README.md`](../backend/README.md).

## Offline

- Browser: a service worker keeps the page and the pose runtime and models on the phone after one online visit. It needs HTTPS
  with a certificate the browser trusts.
- Android APK: the page and the pose files are inside the APK.
- Video never needs the network. Only sync does.

## Security and privacy decisions

- No athlete video is sent anywhere unless the assessor ticks the upload option; consent wording says videos stay on the phone.
- API keys are scoped (device, export, admin). The server refuses to start serving until keys and the pseudonym secret are set.
- The pseudonym secret is separate from the database and must not change once data exists.
- No Google Play Services; the Android build requests only camera and internet.

## Known limits of the design

- The video methods assume one athlete in view, a fixed phone, and a clear scene.
- The shuttle result is always amber: a hand touching the line cannot be seen from video.
- The 10,000-user target is a design aim, not a measurement; see [VALIDATION.md](VALIDATION.md).
