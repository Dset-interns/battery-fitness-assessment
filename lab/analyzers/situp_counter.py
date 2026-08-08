"""
Test 9 — Sit-Ups.  Target: counting accuracy >= 95% (Concept Note §2).

WHY THIS TEST IS THE FLAGSHIP
-----------------------------
The Technical Handbook (Test No. IX) does not merely describe the movement, it
enumerates the *fouls* in writing:

    Rule 2  — No resting between sit-ups is permitted.
    Rule 3  — No sit up shall be counted in which the athlete does not:
      (a) Keep the fingers clasped behind the neck.
      (b) Bring both elbows forward in starting to sit-up without pushing off
          the floor with the elbow.
      (c) Return to starting position, with elbows flat on the surface before
          sitting up again.
    "A foul nullifies the count for that sit up."

Because those rules are already written down, enforcing them needs **no training
data**. That matters: Concept Note §8.5 forbids *ML-based* anti-cheat precisely
because "genuine and fraudulent footage" does not exist to train on. A geometric
rule-checker is not a learned cheat detector — it is the Handbook, executed.

The output is a per-rep audit table naming the rule each rejected rep failed.
A human assessor cannot reliably audit 40 reps against five rules in 45 seconds.
This can.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

from .pose import (
    LEFT_EAR, LEFT_ELBOW, LEFT_HIP, LEFT_KNEE, LEFT_SHOULDER, LEFT_WRIST,
    RIGHT_EAR, RIGHT_ELBOW, RIGHT_HIP, RIGHT_KNEE, RIGHT_SHOULDER, RIGHT_WRIST,
    Frame, angle_deg, band_from_signals, midpoint, pose_frames, probe,
)

# ---------------------------------------------------------------------------
# Tunables. Every one of these is a *starting point* to be calibrated against
# real footage during validation, not a shipped constant. Concept Note §11 and
# Submission §6 both grade methodology, so these must be reported, not hidden.
# ---------------------------------------------------------------------------

# Fraction of the observed hip-angle range used as hysteresis on each end.
# Adaptive banding beats absolute degrees because the hip angle a camera sees
# depends on lens height and body proportions, which vary across a 6-18 age range.
HYSTERESIS = 0.30

# Elbow-to-knee contact, expressed as a multiple of femur length so it scales
# with body size instead of assuming an adult frame.
ELBOW_KNEE_TOUCH = 0.60

# Wrist must stay near the ear for "fingers clasped behind the neck" (rule 3a),
# as a multiple of shoulder width.
WRIST_EAR_MAX = 1.10

# Elbow "flat on the surface" tolerance (rule 3c), as a multiple of shoulder
# width below the established floor baseline.
ELBOW_FLOOR_TOL = 0.55

# Downward elbow travel during the rise that indicates pushing off (rule 3b),
# as a multiple of shoulder width.
ELBOW_PUSHOFF = 0.22

# Rule 2 — resting. Gap between the end of one rep and the start of the next.
REST_GAP_SEC = 2.5

DOWN, RISING, UP, RETURNING = "DOWN", "RISING", "UP", "RETURNING"


@dataclass
class Rep:
    """One attempted sit-up, valid or not."""
    number: int
    t_start: float
    t_end: float
    valid: bool
    foul_rule: Optional[str] = None      # "3a" | "3b" | "3c" | "2"
    foul_detail: str = ""
    peak_hip_angle: float = 0.0
    min_elbow_knee: float = 0.0

    def as_row(self) -> str:
        status = "VALID" if self.valid else f"FOUL {self.foul_rule}"
        note = "" if self.valid else f"  — {self.foul_detail}"
        return (f"  {self.number:>3}  {self.t_start:>6.2f}s→{self.t_end:>6.2f}s  "
                f"{status:<9}{note}")


@dataclass
class SitUpResult:
    valid_count: int
    total_attempts: int
    reps: list[Rep] = field(default_factory=list)
    window_sec: int = 45
    confidence: str = "RED"
    confidence_reasons: list[str] = field(default_factory=list)
    detection_rate: float = 0.0
    fps: float = 0.0

    def report(self) -> str:
        lines = [
            "",
            "=" * 68,
            f"TEST 9 — SIT-UPS   ({self.window_sec}s window)",
            "=" * 68,
            f"  Valid reps      : {self.valid_count}",
            f"  Total attempts  : {self.total_attempts}",
            f"  Rejected        : {self.total_attempts - self.valid_count}",
            f"  Confidence      : {self.confidence}"
            + (f"  ({'; '.join(self.confidence_reasons)})"
               if self.confidence_reasons else ""),
            f"  Pose detected   : {self.detection_rate * 100:.1f}% of frames"
            f"  @ {self.fps:.1f} fps",
            "",
            "  PER-REP AUDIT",
            "  " + "-" * 64,
        ]
        lines += [r.as_row() for r in self.reps] or ["   (no reps detected)"]
        lines.append("")
        return "\n".join(lines)


def _percentile(values: list[float], q: float) -> float:
    if not values:
        return float("nan")
    s = sorted(values)
    k = max(0, min(len(s) - 1, int(round(q * (len(s) - 1)))))
    return s[k]


def signals_from_frames(detected: list[Frame]) -> list[dict]:
    """Reduce pose frames to the scalar signals the state machine consumes.

    Split out from `count_from_signals` for two reasons: it makes the state
    machine unit-testable without footage, and it mirrors the Kotlin structure
    where MediaPipe produces landmarks on-device and identical logic consumes
    them.
    """
    sig: list[dict] = []
    for f in detected:
        if not f.visible(LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP,
                         LEFT_KNEE, RIGHT_KNEE):
            continue
        sh = midpoint(f.p(LEFT_SHOULDER), f.p(RIGHT_SHOULDER))
        hp = midpoint(f.p(LEFT_HIP), f.p(RIGHT_HIP))
        kn = midpoint(f.p(LEFT_KNEE), f.p(RIGHT_KNEE))
        el = midpoint(f.p(LEFT_ELBOW), f.p(RIGHT_ELBOW))
        wr = midpoint(f.p(LEFT_WRIST), f.p(RIGHT_WRIST))
        ea = midpoint(f.p(LEFT_EAR), f.p(RIGHT_EAR))

        shoulder_w = max(1e-6, f.p(LEFT_SHOULDER).dist(f.p(RIGHT_SHOULDER)))
        femur = max(1e-6, hp.dist(kn))

        sig.append({
            "t": f.t,
            "hip_angle": angle_deg(sh, hp, kn),
            # Handbook: "finally touching elbows to knees"
            "elbow_knee": el.dist(kn) / femur,
            # Rule 3a: hands behind the neck
            "wrist_ear": wr.dist(ea) / shoulder_w,
            "elbow_y": el.y,
            "shoulder_y": sh.y,
            "shoulder_w": shoulder_w,
        })
    return sig


def count_from_signals(sig: list[dict],
                       window: int = 45,
                       fps: float = 30.0,
                       detection_rate: float = 1.0) -> SitUpResult:
    """The state machine. Pure function of the signal series — no video, no I/O."""
    if len(sig) < 10:
        return SitUpResult(0, 0, [], window, "RED",
                           ["insufficient pose detections to analyse"],
                           detection_rate, fps)

    # ---- Adaptive thresholds --------------------------------------------
    # Calibrate the DOWN/UP hip angles from this clip's own distribution
    # rather than hardcoding degrees that assume a particular camera height.
    angles = [s["hip_angle"] for s in sig if s["hip_angle"] == s["hip_angle"]]
    a_down, a_up = _percentile(angles, 0.90), _percentile(angles, 0.10)
    span = max(1e-6, a_down - a_up)
    th_down = a_down - HYSTERESIS * span      # above this  => lying back down
    th_up = a_up + HYSTERESIS * span          # below this  => curled up

    # Floor baseline for the elbows (rule 3c), taken from the most-supine frames.
    floor_elbow_y = _percentile([s["elbow_y"] for s in sig], 0.90)

    # ---- Pass 2: state machine ------------------------------------------
    state = DOWN
    reps: list[Rep] = []
    rep_no = 0
    t_start = sig[0]["t"]
    prev_end_t: Optional[float] = None

    # Per-rep accumulators
    foul: Optional[tuple[str, str]] = None
    peak_hip = 180.0
    min_ek = 9.9
    rise_min_elbow_y = 9.9

    def reset_rep():
        nonlocal foul, peak_hip, min_ek, rise_min_elbow_y
        foul, peak_hip, min_ek, rise_min_elbow_y = None, 180.0, 9.9, 9.9

    reset_rep()

    for s in sig:
        if s["t"] > window:               # Handbook: count stops at the whistle
            break
        ha, ek = s["hip_angle"], s["elbow_knee"]
        if ha != ha:                      # NaN guard
            continue

        peak_hip = min(peak_hip, ha)
        min_ek = min(min_ek, ek)

        # --- Rule 3a: fingers clasped behind the neck (checked continuously)
        if foul is None and s["wrist_ear"] > WRIST_EAR_MAX and state != DOWN:
            foul = ("3a", "hands left the back of the neck")

        # --- State transitions
        if state == DOWN:
            if ha < th_down:
                state = RISING
                rep_no_start = s["t"]
                t_start = s["t"]
                rise_min_elbow_y = s["elbow_y"]
                # --- Rule 2: resting between sit-ups
                if prev_end_t is not None and (s["t"] - prev_end_t) > REST_GAP_SEC:
                    foul = ("2", f"rested {s['t'] - prev_end_t:.1f}s before starting")

        elif state == RISING:
            # --- Rule 3b: pushing off the floor with the elbow.
            # Signature is the elbow travelling *down* (y increasing) while the
            # torso rises. We track the highest the elbow reached, then flag a
            # significant reversal back toward the floor.
            rise_min_elbow_y = min(rise_min_elbow_y, s["elbow_y"])
            if (foul is None
                    and (s["elbow_y"] - rise_min_elbow_y)
                    > ELBOW_PUSHOFF * s["shoulder_w"]):
                foul = ("3b", "elbow pushed down against the floor during the rise")
            if ek < ELBOW_KNEE_TOUCH or ha < th_up:
                state = UP

        elif state == UP:
            if ha > th_up + HYSTERESIS * span * 0.5:
                state = RETURNING

        elif state == RETURNING:
            if ha > th_down:
                # --- Rule 3c: elbows must return flat to the surface
                if (foul is None
                        and s["elbow_y"]
                        < floor_elbow_y - ELBOW_FLOOR_TOL * s["shoulder_w"]):
                    foul = ("3c", "elbows did not return flat to the surface")
                # Handbook: the rep is only a sit-up if the elbows reached the knees
                if foul is None and min_ek > ELBOW_KNEE_TOUCH:
                    foul = ("3", "elbows did not reach the knees")

                rep_no += 1
                reps.append(Rep(
                    number=rep_no, t_start=t_start, t_end=s["t"],
                    valid=foul is None,
                    foul_rule=foul[0] if foul else None,
                    foul_detail=foul[1] if foul else "",
                    peak_hip_angle=peak_hip, min_elbow_knee=min_ek,
                ))
                prev_end_t = s["t"]
                state = DOWN
                reset_rep()

    valid = sum(1 for r in reps if r.valid)
    band, reasons = band_from_signals(detection_rate, fps)
    return SitUpResult(
        valid_count=valid, total_attempts=len(reps), reps=reps,
        window_sec=window, confidence=band, confidence_reasons=reasons,
        detection_rate=detection_rate, fps=fps,
    )


def analyze(video_path: str, age: int = 14) -> SitUpResult:
    """Count valid sit-ups in a clip.

    `age` selects the Handbook window: 30 s under 12 years, 45 s for 12+.
    """
    window = 30 if age < 12 else 45
    info = probe(video_path)
    frames: list[Frame] = list(pose_frames(video_path))
    detected = [f for f in frames if f.detected]
    detection_rate = len(detected) / len(frames) if frames else 0.0

    sig = signals_from_frames(detected)
    return count_from_signals(sig, window=window, fps=info.fps,
                              detection_rate=detection_rate)
