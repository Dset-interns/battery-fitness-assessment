# Validation plan

**Status: not done.** Nothing in this repository has been measured against real athletes. The only checks so far are on simulated
data (see [TECHNICAL.md](TECHNICAL.md)). Rulings C16, C32 and C53 ask for the reverse: reported figures must come from Indian
footage, each test must be confirmed against offline reference data, and there must be at least 50 test subjects.

## Subjects

- At least 50, and enough in each group to say something: both sexes, and children (under 12), adolescents (12 to 17) and adults.
  Suggested minimum of 8 per sex per age group, 50 in total as the floor.
- Indian athletes, recorded in the conditions the programme will use: a school ground, a mat or floor indoors, a field, a phone on a
  tripod, a budget Android phone and an iPhone.
- Written consent before recording: the athlete, or a parent or guardian for any minor. Use the app's consent screen so the same
  record travels with the data. Demo footage needs the same consents (ruling C06).

## Reference measurements (taken at the same time as the app)

| Test | Reference | Pair with |
|---|---|---|
| Height | Stadiometer, to 0.1 cm | App height, same session, hair tied and untied noted |
| Weight | The scale's display (already typed) | Photo vs typed value (transcription check) |
| Sit & Reach | The tape reading by the assessor, to 0.5 cm | App score, per attempt |
| Vertical jump | Chalk mark minus standing reach on the wall scale, to 0.5 cm | App score, per attempt |
| Broad jump | Tape from line to heels, to 0.5 cm | App distance, per attempt |
| Ball throw | Steel tape from line to first contact, to 1 cm | App distance, per attempt |
| 30 m run | Electronic timing gates, or a second camera at 120 fps | App time, per trial |
| Shuttle run | Electronic gates, or a second camera at 120 fps | App time |
| Sit-ups | Two assessors counting valid repetitions independently from the video, with the fouls marked | App count and each foul, per rep |
| Endurance | A stopwatch started at the signal and a lap counter, or gates | App time |

Manual timing carries its own error (ruling C20: about ±0.24 s up to 200 m), so use gates or a high-speed camera as the reference
for the short runs, and report the stopwatch only as a comparison.

## What to report

For each test: the number of athletes and attempts, the bias (mean of app minus reference) and its spread, the limits of agreement
(bias ± 1.96 standard deviations), the largest errors, and the share of attempts rejected with each reason. Sit-ups: the count error
and, per rep, agreement on valid versus foul (counting accuracy is defined by posture and angle detection, ruling C32). Split by age
group, sex, device, and lighting. Report the confidence bands against the real error: how often green is within tolerance.

## Tolerances to test against

Use the handbook's units and precision: weight to the nearest kg; height and jumps and reach in cm; ball in m and cm; 30 m run and
shuttle to two decimals; endurance in minutes and seconds. Where SAI sets a tolerance, use it. Where none is given, state the observed
error rather than a pass or fail.

## Known risks to look at first

- The start of the timed tests being seen a frame or two late, so times read slightly short.
- The ball test when the ball is hard to see, or lands off the tape.
- The sit-up knee limit (100°) and the count window, which start at the first valid frame rather than at "go".
- The vertical jump fingertip at the top of the jump with the far-side arm hidden.
- Children, whose proportions differ from adults for the segment-based fallbacks.

## Software load test

Separately from athlete validation: run `backend/scripts/load_test.py` against the PostgreSQL stack behind a load balancer, with the
number of phones and athletes the programme expects (ruling C04 names 10,000 or more users), and record throughput, error rate and
latency. The only run so far was on SQLite.

## Deliverables of the validation

A table per test, the raw paired data as CSV, the footage list (not the footage, which stays under the consent terms), the
protocol, and the dates and conditions. Update [MODEL_CARDS.md](MODEL_CARDS.md) with the measured figures.
