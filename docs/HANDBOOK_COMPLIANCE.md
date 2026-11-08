# Handbook and rulings: what is built

Against the revised Technical Handbook (Talent Hunt – KHOJ, October 2026), the NeGD and SAI conflict rulings (C01 to C59) and the
SAI guidance that the revised handbook must be followed and that there is no single common rule for all tests.

Status: ✅ built and checked as far as the notes say, ⚠️ built with a stated difference, ❌ not built, ➖ process item outside the code.
"Checked" means checked on simulated data or in a scripted run, not on real athletes: see [VALIDATION.md](VALIDATION.md).

## Handbook, test by test

| # | Handbook | Status | What is built, and any difference |
|---|---|---|---|
| 1 | Height, cm; wall stadiometer, ruler or tape; bare feet, heels, buttocks, upper back to the wall | ✅ | Measured from video against a wall reference. The original analyser, unchanged |
| 2 | Weight, kg, rounded to the nearest kg | ✅ | Typed from the scale, rounded to a whole kg, with a required photo of the display |
| 3 | Sit & Reach: tape 0 to 53 cm, 23 cm at the heel line, V position, hold 2 s, three attempts, score is the tape reading | ⚠️ | Score = 23 cm + reach measured from the heel; hold 1.8 s; three attempts. The tape is not read: the reading is computed from a scale. The V position and the 30 cm heel gap are not checked from the side view |
| 4 | Vertical jump: standing reach, then jump reach, score is the difference; dominant arm, from the spot, no shuffling or approach steps, three attempts | ✅ | The index fingertip is tracked for both readings; drift over 30 cm from the spot is a foul. Ankle lift is a cross-check |
| 5 | Standing broad jump: two-foot take-off and landing, no falling backward, line to the back of the heels, three attempts | ✅ | The original analyser, unchanged |
| 6 | Backward overhead medicine ball throw: 2 kg male, 1 kg female, three attempts, distance in m and cm | ⚠️ | Measured from video (first ground contact against a marker of known distance), from the throw line. The handbook says "from the point of release", read here as the line. Shown in metres |
| 7 | 30 m run: two trials, first movement to torso at the finish line, two decimals | ⚠️ | Built from video motion. The start can be picked up one or two frames late. One athlete at a time |
| 8 | 4 × 10 m shuttle: one attempt, two decimals, touch the line with a hand | ⚠️ | Built from video motion. Always amber: touching cannot be seen. The handbook runs two athletes together; the app measures one |
| 9 | Sit-ups: 45 s, one trial, fingers clasped (2a), elbows not pushing off (2b), elbows return flat (2c), knees under 90°, heels within 30 cm | ⚠️ | 45 s window, no resting rule, fouls 2a to 2c. Knee limit 100° (90° plus 10° for pose noise). Heel-to-buttock distance is not checked. The 45 s window starts at the first valid frame, not at "go" |
| 10 | 800 m endurance run, minutes and seconds, one attempt | ⚠️ | Built from video lap crossings (400 m or 200 m laps). One athlete at a time; needs a loop that passes the camera line |
| – | Parental height: mother and father separately, measured or self-reported, with consent | ✅ | Registration fields, kept only with its consent, shown on the report. No estimate is calculated (the handbook leaves the method to the programme) |
| – | Order and names of the ten tests; units | ✅ | Same order. Names follow the handbook. Units and precision as above |
| – | Age: the handbook has no age split now | ✅ | Age is recorded, not used by any rule (ruling C30: age at registration) |

## Rulings

| Ruling | Status | Notes |
|---|---|---|
| C01 iOS build mandatory | ⚠️ | The iPhone app is the web app added to the Home Screen. No native iOS project: Xcode was not available. The old Swift package predates the handbook and is not part of the app |
| C02 backend, API spec, DB schema | ✅ | `backend/`, `openapi.json`, `schema.sql`. Other "handover document" artefacts: not known |
| C03 AI measures all tests (height, weight optional) | ✅ | Eight tests plus height are measured by AI; weight is typed with a photo |
| C04 production architecture: sync, de-identification gateway, 10,000+ users | ⚠️ | Sync and gateway built; stateless, scales by replicas. Not tested on PostgreSQL, behind a load balancer or at 10,000 users |
| C05, C33 Round 1 on 13 November 2026 | ➖ | |
| C06 demo video with consent forms | ❌ | Not made. The app can produce the signed consent PDF |
| C07, C47 22-item checklist, everything mandatory | ❌ | We do not have the checklist. These documents are a start |
| C08, C09, C17, C40, C55, C58 scoring, portal, archive, folders, resubmission, .md files | ➖ | Documents are Markdown in `docs/` |
| C10 sync alongside the consent form | ✅ | The signed consent and its signature go in the same sync |
| C11 offline demonstration | ⚠️ | Works offline on `localhost`, and in the Android app. A phone browser needs a valid HTTPS site for the service worker |
| C13 no Google Play Services | ✅ | The Android build requests camera and internet only |
| C14, C36 retention per SAI's SLA | ⚠️ | Retention is a setting. The SLA period has not been given to us |
| C15 technical documentation | ✅ | These documents. Not replaced by the HLD and LLD |
| C16, C32, C53 accuracy on Indian footage, offline data, 50 subjects | ❌ | Plan only: [VALIDATION.md](VALIDATION.md) |
| C18 no live login | ✅ | The assessor enters a name; the phone holds a scoped key |
| C19 endurance timed and distance-tracked without a device on the athlete | ✅ | From video |
| C20 manual vs electronic timing tolerance | ➖ | Informational; used in the validation plan |
| C21 vertical jump: reach minus standing reach | ✅ | |
| C22 shuttle: one attempt | ✅ | |
| C23 attempts at the assessor's discretion, a video per attempt | ✅ | The list of attempts cannot be reset; each attempt has its own video |
| C24 handbook vs concept note | ⚠️ | We followed the revised handbook; we do not hold the concept note |
| C26, C28, C52 coach batch mode, both sequencing options | ✅ | Coach and individual; by test and by athlete; all tests required |
| C27 over-length auto-stop, oversized compressed, short evaluated | ⚠️ | Auto-stop and short-attempt evaluation built. Oversize is held down by a bitrate budget, not compressed afterwards; a clip over the limit by more than 50% is flagged |
| C29 video processed on the phone, then information pushed | ✅ | |
| C31 Sit & Reach zero point per the handbook | ✅ | 23 cm at the heel line |
| C35 signatures mandatory, digital allowed | ⚠️ | In-app signed consent. The programme's own signed documents are outside the app |
| C37 size limits are not a rejection criterion | ✅ | |
| C38 endurance: record start and finish, hard stop at 17 minutes | ✅ | |
| C39 no manual fallback except height and weight | ✅ | |
| C41 OpenAPI mandatory | ✅ | `backend/openapi.json` |
| C42 9 videos and 1 photo per athlete, no override | ✅ | One video per attempt, a photo for the weight, no manual override |
| C43 athlete and coach authenticity declaration on each video | ⚠️ | Built with draft wording; official wording would replace it |
| C44 BMI scale in the report | ⚠️ | Adult bands from 18; under 18 shows the number only until an age-based reference is confirmed |
| C45 test order per the latest document | ✅ | |
| C48 sprint start at first movement, end at torso on the line | ✅ | |
| C49 height by any measuring instrument | ✅ | |
| C50 units and precision | ✅ | |
| C51 minimum durations | ⚠️ | The attached document was not given to us; durations are configured per test |
| C57 reproducible signed APK | ⚠️ | Built, signed and byte-identical across clean builds. Not run on a device or emulator. See [`../app/BUILD.md`](../app/BUILD.md) |
| C59 demo narration | ❌ | Not made |

## Open items, most important first

1. Validate on real footage (C16, C32, C53): none of the camera-based measurements has been run on a real athlete except by simulation.
2. Run the APK on a real phone; build an iOS project where Xcode is available (C01, C57).
3. Load-test on PostgreSQL behind a load balancer (C04).
4. Make the demo video and narration with consents (C06, C59); obtain and complete the 22-item checklist (C07, C47).
5. Settle with SAI: the video retention period, the consent and declaration wording, the BMI reference for under-18s, and whether the
   videos should be uploaded (the consent wording currently says they are not).
6. The Python lab, the old Compose app and the Swift package still carry the pre-revision rules. They are not part of the shipping app.
