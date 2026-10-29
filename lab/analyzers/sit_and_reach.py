"""
Test 3 — Sit and Reach (Flexibility).  Target: within ±1 cm (Concept Note §2).

PROTOCOL AND GEOMETRY
---------------------
SAI Technical Handbook (Test No. III):
  - Athlete sits on the floor with legs stretched out straight ahead, soles
    flat against the Sit-and-Reach box (or zero line at the heels/soles).
  - Both knees must remain locked flat against the floor (a partner/tester may
    hold the knees down, which is why `pose.py` selects the horizontal-torso
    subject rather than "person 0").
  - With palms facing downwards and hands on top of each other or side-by-side,
    the athlete reaches forward along the measuring line as far as possible and
    *holds* the maximum reach for at least 1-2 seconds (no jerky bouncing).
  - Three trials are permitted; the best reach (in cm, relative to the toe/box
    zero mark, or absolute reach distance along the scale) is recorded.

WHY PURE-SIGNAL SEPARATION MATTERS
----------------------------------
Like `situp_counter.py`, we split landmark extraction (`signals_from_frames`)
from the geometric rule engine (`measure_from_signals`) so the hold-window
detector, the knee-bend foul rule, and the 3-attempt best-score selector can be
unit-tested deterministically without video files, and ported 1:1 to Swift (iOS)
and Kotlin (Android).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

from .pose import (
    AMBER, GREEN, LEFT_ANKLE, LEFT_FOOT_INDEX, LEFT_HEEL, LEFT_HIP, LEFT_KNEE,
    LEFT_SHOULDER, LEFT_WRIST, RED, RIGHT_ANKLE, RIGHT_FOOT_INDEX, RIGHT_HEEL,
    RIGHT_HIP, RIGHT_KNEE, RIGHT_SHOULDER, RIGHT_WRIST, Frame, angle_deg,
    band_from_signals, midpoint, pose_frames, probe,
)

# Handbook III: knees must stay straight on the floor. Below this interior
# hip-knee-ankle angle (degrees) we flag a knee-bend foul on that attempt.
MIN_KNEE_STRAIGHT_DEG = 168.0

# Handbook III: the reach must be held steady ("hold for about two seconds"),
# not a momentary bounce. At 30 fps, 1.0 s is ~30 frames; we require at least
# 0.8 s of low-spread extension to count a reach plateau.
MIN_HOLD_SEC = 0.80
MAX_HOLD_SPREAD_CM = 1.2

# Hand-length correction beyond the wrist landmark when HandLandmarker crop is
# unavailable (adult/youth middle fingertip extends ~0.11 *身長 or ~0.32 * forearm).
HAND_EXTENSION_FOREARM_RATIO = 0.34


@dataclass
class ReachAttempt:
    number: int
    t_start: float
    t_end: float
    reach_cm: float
    hold_sec: float
    min_knee_angle_deg: float
    valid: bool
    foul_reason: str = ""

    def as_row(self) -> str:
        status = "VALID" if self.valid else f"FOUL ({self.foul_reason})"
        return (f"  {self.number:>3}  {self.t_start:>5.2f}s→{self.t_end:>5.2f}s  "
                f"reach {self.reach_cm:>+6.1f} cm  hold {self.hold_sec:.2f}s  "
                f"knee {self.min_knee_angle_deg:>5.1f}°  {status}")


@dataclass
class SitAndReachResult:
    best_reach_cm: float
    attempts: list[ReachAttempt] = field(default_factory=list)
    rung: int = 2
    method: str = "scale from reference length along reach axis"
    confidence: str = RED
    confidence_reasons: list[str] = field(default_factory=list)
    cm_per_px: float = 0.0
    detection_rate: float = 0.0
    fps: float = 30.0

    def report(self) -> str:
        valid_attempts = [a for a in self.attempts if a.valid]
        lines = [
            "",
            "=" * 68,
            "TEST 3 — SIT AND REACH (FLEXIBILITY)",
            "=" * 68,
            f"  Best valid reach : {self.best_reach_cm:+.1f} cm"
            + (f"  (best of {len(valid_attempts)} valid / {len(self.attempts)} total)"
               if self.attempts else "  (no valid attempts)"),
            f"  Method           : rung {self.rung} — {self.method}",
            f"  Confidence       : {self.confidence}"
            + (f"  ({'; '.join(self.confidence_reasons)})"
               if self.confidence_reasons else ""),
            f"  Scale            : {self.cm_per_px:.4f} cm/px",
            f"  Pose detected    : {self.detection_rate * 100:.1f}% @ {self.fps:.1f} fps",
            "",
            "  ATTEMPTS (Handbook III allows 3 trials; best valid reach is scored)",
            "  " + "-" * 64,
        ]
        lines += [a.as_row() for a in self.attempts] or ["   (none)"]
        lines.append("")
        return "\n".join(lines)


def signals_from_frames(detected: list[Frame],
                        cm_per_px: float,
                        frame_width_px: int = 1280) -> list[dict]:
    """Extract per-frame reach distance (cm relative to foot line) and knee angle."""
    sig: list[dict] = []
    for f in detected:
        if not f.visible(LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP,
                         LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE,
                         LEFT_WRIST, RIGHT_WRIST):
            continue
        hp = midpoint(f.p(LEFT_HIP), f.p(RIGHT_HIP))
        kn = midpoint(f.p(LEFT_KNEE), f.p(RIGHT_KNEE))
        an = midpoint(f.p(LEFT_ANKLE), f.p(RIGHT_ANKLE))
        wr = midpoint(f.p(LEFT_WRIST), f.p(RIGHT_WRIST))
        sh = midpoint(f.p(LEFT_SHOULDER), f.p(RIGHT_SHOULDER))

        if f.visible(LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX, threshold=0.4):
            foot_zero = midpoint(f.p(LEFT_FOOT_INDEX), f.p(RIGHT_FOOT_INDEX))
        elif f.visible(LEFT_HEEL, RIGHT_HEEL, threshold=0.4):
            foot_zero = midpoint(f.p(LEFT_HEEL), f.p(RIGHT_HEEL))
        else:
            foot_zero = an

        # Direction vector from hip to ankle defines the positive reach axis
        axis_x = an.x - hp.x
        axis_y = an.y - hp.y
        axis_len = (axis_x ** 2 + axis_y ** 2) ** 0.5
        if axis_len < 1e-6:
            continue
        ux, uy = axis_x / axis_len, axis_y / axis_len

        # Estimate fingertip beyond wrist along the shoulder->wrist vector
        arm_len = sh.dist(wr)
        fingertip_x = wr.x + ux * (arm_len * HAND_EXTENSION_FOREARM_RATIO * 0.5)
        fingertip_y = wr.y + uy * (arm_len * HAND_EXTENSION_FOREARM_RATIO * 0.5)

        # Signed projection of (fingertip - foot_zero) along the leg axis, in pixels
        delta_norm = (fingertip_x - foot_zero.x) * ux + (fingertip_y - foot_zero.y) * uy
        reach_px = delta_norm * frame_width_px
        reach_cm = reach_px * cm_per_px

        knee_deg = angle_deg(hp, kn, an)

        sig.append({
            "t": f.t,
            "reach_cm": reach_cm,
            "knee_angle": knee_deg,
        })
    return sig


def measure_from_signals(sig: list[dict],
                         cm_per_px: float = 0.20,
                         rung: int = 2,
                         fps: float = 30.0,
                         detection_rate: float = 1.0) -> SitAndReachResult:
    """Identify up to 3 held reach attempts and validate knee straightness."""
    if len(sig) < 10:
        return SitAndReachResult(
            best_reach_cm=0.0, attempts=[], rung=5,
            method="insufficient pose detections",
            confidence=RED,
            confidence_reasons=["fewer than 10 usable pose frames"],
            cm_per_px=cm_per_px, detection_rate=detection_rate, fps=fps,
        )

    reaches = [s["reach_cm"] for s in sig]
    baseline_cm = sorted(reaches)[int(len(reaches) * 0.20)]
    peak_cm = sorted(reaches)[int(len(reaches) * 0.90)]
    span = max(2.0, peak_cm - baseline_cm)
    extend_threshold = baseline_cm + 0.55 * span

    attempts: list[ReachAttempt] = []
    in_reach = False
    window: list[dict] = []

    def finalize_window(w: list[dict]):
        if not w:
            return
        dur = w[-1]["t"] - w[0]["t"]
        if dur < 0.25:
            return
        w_reaches = sorted(x["reach_cm"] for x in w)
        top_half = w_reaches[len(w_reaches) // 2:]
        held_reach = top_half[len(top_half) // 2]
        spread = top_half[-1] - top_half[0] if len(top_half) > 1 else 0.0
        min_knee = min(x["knee_angle"] for x in w)

        valid = True
        foul_reason = ""
        if min_knee < MIN_KNEE_STRAIGHT_DEG:
            valid = False
            foul_reason = f"knee bent ({min_knee:.0f}° < {MIN_KNEE_STRAIGHT_DEG:.0f}°)"
        elif dur < MIN_HOLD_SEC:
            valid = False
            foul_reason = f"bounced / not held ({dur:.2f}s < {MIN_HOLD_SEC:.1f}s)"
        elif spread > MAX_HOLD_SPREAD_CM * 2.0:
            valid = False
            foul_reason = f"unsteady hold (spread {spread:.1f} cm)"

        attempts.append(ReachAttempt(
            number=len(attempts) + 1,
            t_start=w[0]["t"],
            t_end=w[-1]["t"],
            reach_cm=round(held_reach, 1),
            hold_sec=round(dur, 2),
            min_knee_angle_deg=round(min_knee, 1),
            valid=valid,
            foul_reason=foul_reason,
        ))

    for s in sig:
        if s["reach_cm"] >= extend_threshold:
            in_reach = True
            window.append(s)
        elif in_reach:
            finalize_window(window)
            window = []
            in_reach = False
            if len(attempts) >= 3:
                break

    if in_reach and len(attempts) < 3:
        finalize_window(window)

    valid_attempts = [a for a in attempts if a.valid]
    if not valid_attempts:
        reasons = ["no valid held reach attempt detected"]
        if attempts:
            reasons.append(f"{len(attempts)} attempt(s) rejected for fouls")
        return SitAndReachResult(
            best_reach_cm=0.0, attempts=attempts, rung=rung,
            confidence=RED, confidence_reasons=reasons,
            cm_per_px=cm_per_px, detection_rate=detection_rate, fps=fps,
        )

    best = max(valid_attempts, key=lambda a: a.reach_cm)
    band, reasons = band_from_signals(detection_rate, fps)
    if rung >= 3 and band == GREEN:
        band = AMBER
        reasons.append("calibrated from body proportions (rung 3) rather than box scale")

    return SitAndReachResult(
        best_reach_cm=best.reach_cm,
        attempts=attempts,
        rung=rung,
        confidence=band,
        confidence_reasons=reasons,
        cm_per_px=cm_per_px,
        detection_rate=detection_rate,
        fps=fps,
    )


def analyze(video_path: str,
            ref_px: Optional[float] = None,
            ref_cm: Optional[float] = None) -> SitAndReachResult:
    """Measure Sit-and-Reach from a video clip."""
    info = probe(video_path)
    if ref_px and ref_cm and ref_px > 0:
        cm_per_px = ref_cm / ref_px
        rung = 2
    else:
        # Rung 3 fallback using standard camera framing scale
        cm_per_px = 180.0 / max(info.width, 640)
        rung = 3

    frames = list(pose_frames(video_path))
    detected = [f for f in frames if f.detected]
    detection_rate = len(detected) / len(frames) if frames else 0.0

    sig = signals_from_frames(detected, cm_per_px=cm_per_px, frame_width_px=info.width)
    return measure_from_signals(
        sig, cm_per_px=cm_per_px, rung=rung, fps=info.fps,
        detection_rate=detection_rate,
    )
