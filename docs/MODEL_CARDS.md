# Model cards

One learned model is used, and it is not ours: MediaPipe Pose Landmarker. Everything else is classical computer vision with
fixed rules. No model was trained for this project and no athlete data was used to tune anything. **No accuracy figure in these
documents is a measurement on real athletes.** The plan to obtain one is in [VALIDATION.md](VALIDATION.md).

## 1. MediaPipe Pose Landmarker

| | |
|---|---|
| What it is | Google's pose estimation: 33 body landmarks per person, per frame, with a segmentation mask |
| Variants used | `pose_landmarker_full` first; `pose_landmarker_lite`, then the CPU, if the first cannot start |
| Runs | In the browser or the Android WebView, on the phone, from files bundled with the app. No video leaves the phone for inference |
| Licence | Apache 2.0 |
| Used by | Height, Sit & Reach, vertical jump, broad jump, sit-ups |
| Settings | Up to 2 poses; detection, presence and tracking confidence 0.55; segmentation masks on |
| Trained on | Google's data. We did not retrain or fine-tune it |

Known limits that matter here: landmarks jitter by a few pixels and can swing when a limb is occluded (the far-side arm and leg in a
side view); there is no fingertip landmark that is the middle finger, only the index fingertip, so the vertical jump uses the index
fingertip for both the standing and the jump reading so that a constant offset cancels; performance for children and for the
Indian population and kit has not been measured by us. Skin tone, clothing colour, lighting and background can change the quality
of the landmarks.

## 2. Video-motion timing (30 m run, shuttle run, endurance run)

Not a learned model. Frame differencing: the share of pixels that changed against the previous frame, and their centre of mass.
The start is the first sustained rise above the noise floor near the start line, the finish a crossing of a line by that centre.
Parameters are in [LLD.md](LLD.md). Needs a fixed phone, one athlete in view, and a scene where nothing else moves.

Checked on: simulated runs with known times (within about one frame, with the start picked up up to one or two frames late), and one
synthetic run encoded to video and decoded as the camera would be. **Not checked on real runs.** Expected weaknesses: another person
or a vehicle moving in view; the centre of mass lagging the torso by a few centimetres; the start of a slow, gentle first movement
being seen late; strong sun flicker or auto-exposure changes (the app rejects a scene that changes before the start).

## 3. Ball tracking (medicine ball throw)

Not a learned model. A background model (learned while idle, frozen when recording starts), foreground blobs of ball size, linked
across frames into a flight path; the landing is the first frame at which the ball's bottom reaches the ground line between two marks
tapped by the coach. The distance is read against a marker of known distance.

Checked on synthetic scenes with known landings (within 1 to 6 cm). **Not checked on real throws.** Expected weaknesses: a ball that
does not stand out from the ground or sky; a landing well off the tape line (the reading is in the image plane); a landing beyond the
frame; people or objects moving in the lane.

## 4. Rule checks (sit-ups, sit and reach, jumps)

Geometric rules on landmark angles and distances, written from the handbook: the three sit-up fouls, straight knees, hold time,
two-foot take-off, drift from the spot. They are not learned cheat detection. Their thresholds were set by reasoning and by
synthetic poses, not tuned on footage. Where the code is looser than the handbook it says so in [HANDBOOK_COMPLIANCE.md](HANDBOOK_COMPLIANCE.md).

## Confidence bands

Green: measured against a reference in the picture (a calibrated scale, a marker, a line) with nothing flagged. Amber: an estimate,
or a measurement with a stated limit (no reference, an extended scale, a hand touch that cannot be seen, uneven laps, a frame rate
below about 25). Red: rejected, with the reason. These are engineering judgements, not statistical intervals.

## What would change these cards

Real footage from at least 50 athletes (see [VALIDATION.md](VALIDATION.md)) gives the error of each test against a reference
measurement, by age group and sex, and shows which thresholds need to move.
