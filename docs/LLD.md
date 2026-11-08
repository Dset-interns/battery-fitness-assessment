# Low-level design

Everything below is read from `ios/iphone-pwa/index.html` and `backend/`. Thresholds are the values in the code today; none is a
validated figure (see [MODEL_CARDS.md](MODEL_CARDS.md)).

## The web app

One file, no framework. Sections: tokens and components (CSS), print layout, the engine (pose helpers, per-test analysers,
`computeAttemptResult`), the UI layer (screens in `SCR.*`, actions in `ACT`), and the router with history handling.

**State** (`localStorage`, key `sai_ios_state_v5`): `athletes[]` (with `consent`, parental height), `results["athleteId:testKey"]`
(best attempt plus `attemptsHistory[]`), `session`, `sequencing`, `trackLines` (per timed test), `sync`, `assessor`, `theme`, `lang`.
**Media** (IndexedDB `bfa_media`, store `media`): attempt videos `v:athlete:test:n:stamp`, the scale photo `p:athlete:weight`, consent
signatures `s:athlete:consent:stamp`. An attempt keeps only metadata (key, duration, size, declaration, uploaded).

**Attempt result**: `{valid, value, unit, confidence GREEN|AMBER|RED, method, audit[], video?}`. A rejection has `valid:false`,
`value:null`, and the reason in `method` and `audit`.

**Capture** (`renderCapture`): `getUserMedia` (60 fps asked for the 30 m run, shuttle and ball tests; 30 fps for the pose tests and the endurance run), `MediaRecorder` on the
raw stream with a bitrate derived from the test's size limit (700 kbps for the endurance run), stop allowed after 2 s and the attempt
evaluated even if short, auto-stop at the test's maximum (17 minutes for endurance). Recording is disabled until the declaration is
confirmed and, for the timed and ball tests, the lines are set.

## Tests

### Pose tests (MediaPipe Pose Landmarker, full model with lite and CPU fallbacks, segmentation masks on)

- **Height**: pixel-to-cm from the wall reference (a sticky note of known size, a photo, or taps), head vertex from the segmentation mask. Unchanged from the original analyser.
- **Sit & Reach**: knees straight ≥ 165° and not lifted; hands within 15 cm of each other; reach measured from the **heel**, score = 23 cm + reach (the tape's 23 cm mark is the heel line); the farthest point must be held 1.8 s (the handbook says 2 s) without bouncing.
- **Vertical jump**: the index fingertip of the raised arm (wrist above the shoulder by 0.22 × shoulder-to-ankle, elbow ≥ 145°). **Standing reach** = a 0.4 s hold with the arm raised, ankles within 3.2 cm of the floor, fingertip steady within 3 cm. **Jump reach** = the highest fingertip in the airborne window (median of the two highest readings, at least 3 visible frames). Score = jump reach − standing reach, 3.5 to 130 cm. The airborne window needs both ankles, hips and shoulders to rise together; drift over 30 cm from the take-off spot is a foul; flight ≤ 0.75 s. Ankle lift is a cross-check: a gap beyond +25 or −12 cm downgrades to amber.
- **Broad jump**: two-foot take-off and landing, no walk-up, no stepping or falling backward after landing; take-off line (toes) to the back of the heels. Unchanged from the original analyser.
- **Sit-ups**: state machine supine (torso ≤ 28° and hip ≥ 104°) → curl (torso ≥ 46° and hip ≤ 90°) → supine. Count window 45 s from the first valid frame. Knees must be at most 100° (handbook: under 90°, plus 10° for pose noise). Fouls per rep: **2a** fingers not clasped (wrist-to-ear ratio > 0.85), **2b** elbow pushed off the floor or partial curl or elbows not reaching the knees, **2c** elbows and back not returned flat. There is no resting rule.

### Video-motion tests (no pose model; a fixed phone)

Frames are drawn to a 256 px wide canvas (512 for the ball) and compared with the previous frame (or a background model).

- **30 m run** (`sprintAnalyze`): per frame the share of pixels that changed by more than 22 grey levels and their centre. Noise floor from the first 8 to 24 frames. **Start** = first run of 3 frames above the floor near the start line, walked back across gaps of up to 2 frames. **Finish** = the motion centre crossing the finish line, interpolated between frames. Rejected if the phone moved (mean frame change > 8), no start or finish is seen, or the time is outside 2.5 to 20 s.
- **Shuttle** (`shuttleAnalyze`): position p along the course (0 start line, 1 far line). Phases: reach p ≥ 0.88, return to ≤ 0.7, reach ≤ 0.15, leave ≥ 0.3, reach ≥ 0.88, return ≤ 0.7, then cross p ≤ 0. Finish = that last crossing; a dip below 0 on the middle turn does not stop the clock. Time 7 to 30 s. Always amber.
- **Endurance** (`enduranceAnalyze`): one line; the direction is the side the athlete starts on. A crossing is counted after the centre has been 3% of the frame behind the line. Crossings within 8 s of the start are the departure and are dropped; the last lap's crossing stops the clock (2 laps of 400 m or 4 of 200 m). Time 1:20 to 17:00. Amber if the laps are very uneven (longest > 1.8 × shortest, or a lap under 20 s).
- **Medicine ball** (`ballAnalyze`): a background model learned while idle and frozen at record start. Foreground blobs beyond 1.2 m past the throw line, sized 0.2 to 4 × a ball of 0.22 m, compact (aspect 0.35 to 2.8, fill ≥ 0.35), linked frame to frame into a path (gate 20 m/s × frame time + 2 diameters). The path needs at least 4 points and 0.8 m of progress. **Landing** = the first point whose bottom reaches the ground line between the two tapped marks (within 0.3 diameter), interpolated between frames. Distance = along-lane offset from the line, scaled by the marker's distance. 1.2 to 25 m. Amber if the landing is beyond 1.6 × the marker distance or the tracking is sparse.

All lines and marks are fractions of the frame width and are kept per test until reset.

## Report and PDF

`recordBody(athlete)` renders the 4b layout once and is used for the phone view and the A4 page (`pdfAthlete`). BMI (`bmiH`) is shown from height and weight; the adult bands (18.5, 25, 30) only for age 18 and over. Print uses `#print-root` and `@page A4`; in the Android app `window.AndroidBridge.print()` opens the system print screen.

## Backend

`backend/app/main.py`, `models.py`, `deid.py`, `security.py`. Contract in `backend/openapi.json`, schema in `backend/schema.sql`
(both generated by `backend/scripts/export_contract.py`). A sync is applied in one transaction and is idempotent: athletes by
(device, local id), consents by signing time, attempts by attempt id. The server recomputes each result from the attempts with the
handbook's rule (highest for jumps, throws and sit-ups, lowest time for the 30 m run, shuttle and endurance, latest valid for height).
The de-identified result counts every attempt of the same pseudonym, so one person on two phones is one subject.

## Android shell

`app/webapp`: `WebAppActivity` answers requests for `https://appassets.androidplatform.net/*` from `assets/www`, with the types a
module script and WebAssembly need; it forwards the camera permission and the file chooser, and exposes `AndroidBridge.print` and
`saveFile`. See [`../app/BUILD.md`](../app/BUILD.md).
