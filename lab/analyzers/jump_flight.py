"""
Test 4 — Standing Vertical Jump.  Target: within ±2 cm (Concept Note §2).

METHOD AND ITS HONEST LIMITS
----------------------------
Concept Note §5.4 *mandates* take-off and landing detection, so the flight-time
path must exist regardless of what else we do:

        h = g·t² / 8            (t = total airborne time)

Two things must be said plainly, because Submission §6 grades methodology and
§6.7 asks explicitly which benchmarks we cannot meet:

1. FRAME RATE IS THE ERROR FLOOR. At 30 fps one frame is 33 ms, and
   dh/dt = g·t/4, so for a typical 0.5 s flight a single-frame ambiguity is
   ~4 cm — twice the ±2 cm target. This is arithmetic, not a tuning problem.
   Concept Note §3 says the same thing ("a one-frame error shifts the result by
   2-4 cm"). We therefore report AMBER at 30 fps and only claim GREEN at 120 fps+.

2. FLIGHT TIME AND THE HANDBOOK MEASURE DIFFERENT THINGS. Handbook Test IV
   scores `jump reach − standing reach`: a *fingertip* displacement read off a
   wall marked to 3.50 m. Flight time yields *centre-of-mass* rise. These are
   correlated but not equal — an athlete who is not fully extended at the apex
   scores lower by the wall method than by flight time. The wall-mark method is
   the ground truth; flight time is the equipment-free fallback. When a marked
   wall is present the app should prefer reading the chalk mark and use flight
   time as a cross-check. That ladder is the solution document's §3 thesis.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

from .pose import (
    AMBER, GREEN, LEFT_ANKLE, LEFT_HEEL, LEFT_HIP, RED, RIGHT_ANKLE,
    RIGHT_HEEL, RIGHT_HIP, Frame, midpoint, pose_frames, probe,
)

GRAVITY = 9.81

# Feet are considered airborne once they rise above the standing baseline by
# this multiple of the athlete's own hip height — self-scaling, so it does not
# assume a camera distance or a body size.
AIRBORNE_RATIO = 0.035

# Reject blips: a real jump is airborne for at least this long.
MIN_FLIGHT_SEC = 0.15
MAX_FLIGHT_SEC = 1.20


@dataclass
class Attempt:
    number: int
    takeoff_t: float
    landing_t: float
    flight_sec: float
    height_cm: float
    frame_uncertainty_cm: float

    def as_row(self) -> str:
        return (f"  {self.number:>3}  take-off {self.takeoff_t:>6.2f}s  "
                f"landing {self.landing_t:>6.2f}s  flight {self.flight_sec:>5.3f}s  "
                f"→ {self.height_cm:>5.1f} cm  (±{self.frame_uncertainty_cm:.1f} cm)")


@dataclass
class JumpResult:
    best_height_cm: float
    attempts: list[Attempt] = field(default_factory=list)
    confidence: str = "RED"
    confidence_reasons: list[str] = field(default_factory=list)
    fps: float = 0.0
    method: str = "flight-time (g·t²/8)"

    def report(self) -> str:
        lines = [
            "",
            "=" * 68,
            "TEST 4 — STANDING VERTICAL JUMP",
            "=" * 68,
            f"  Method          : {self.method}",
            f"  Best of {len(self.attempts)} attempts : "
            f"{self.best_height_cm:.1f} cm" if self.attempts
            else "  No valid attempts detected",
            f"  Confidence      : {self.confidence}"
            + (f"  ({'; '.join(self.confidence_reasons)})"
               if self.confidence_reasons else ""),
            f"  Frame rate      : {self.fps:.1f} fps",
            "",
            "  ATTEMPTS (Handbook IV allows three; best is scored)",
            "  " + "-" * 64,
        ]
        lines += [a.as_row() for a in self.attempts] or ["   (none)"]
        lines.append("")
        return "\n".join(lines)


def _percentile(values: list[float], q: float) -> float:
    if not values:
        return float("nan")
    s = sorted(values)
    k = max(0, min(len(s) - 1, int(round(q * (len(s) - 1)))))
    return s[k]


def analyze(video_path: str) -> JumpResult:
    info = probe(video_path)
    frames = [f for f in pose_frames(video_path) if f.detected]

    series: list[tuple[float, float, float]] = []   # (t, feet_y, hip_span)
    for f in frames:
        if not f.visible(LEFT_ANKLE, RIGHT_ANKLE, LEFT_HIP, RIGHT_HIP):
            continue
        # Prefer heels (Handbook measures from the floor contact) and fall back
        # to ankles when heels are occluded by footwear or trouser hems.
        if f.visible(LEFT_HEEL, RIGHT_HEEL, threshold=0.6):
            feet = midpoint(f.p(LEFT_HEEL), f.p(RIGHT_HEEL))
        else:
            feet = midpoint(f.p(LEFT_ANKLE), f.p(RIGHT_ANKLE))
        hip = midpoint(f.p(LEFT_HIP), f.p(RIGHT_HIP))
        series.append((f.t, feet.y, abs(feet.y - hip.y)))

    if len(series) < 10:
        return JumpResult(0.0, [], RED,
                          ["insufficient pose detections"], info.fps)

    # Standing baseline: the feet spend most of the clip on the ground, so the
    # high-y percentile is the floor. (y grows downward in image space.)
    floor_y = _percentile([s[1] for s in series], 0.85)
    hip_span = _percentile([s[2] for s in series], 0.50)
    airborne_threshold = floor_y - AIRBORNE_RATIO * hip_span

    # ---- Detect airborne intervals --------------------------------------
    attempts: list[Attempt] = []
    in_air = False
    takeoff_t = 0.0
    n = 0

    for t, feet_y, _ in series:
        airborne = feet_y < airborne_threshold
        if airborne and not in_air:
            in_air, takeoff_t = True, t
        elif not airborne and in_air:
            in_air = False
            flight = t - takeoff_t
            if not (MIN_FLIGHT_SEC <= flight <= MAX_FLIGHT_SEC):
                continue                      # noise, or a shuffle not a jump
            n += 1
            height_m = GRAVITY * flight * flight / 8.0
            # Propagate the one-frame timing ambiguity into centimetres:
            #   dh/dt = g·t/4
            unc_m = (GRAVITY * flight / 4.0) * (1.0 / info.fps)
            attempts.append(Attempt(
                number=n, takeoff_t=takeoff_t, landing_t=t, flight_sec=flight,
                height_cm=height_m * 100.0,
                frame_uncertainty_cm=unc_m * 100.0,
            ))

    if not attempts:
        return JumpResult(0.0, [], RED,
                          ["no airborne interval detected"], info.fps)

    best = max(attempts, key=lambda a: a.height_cm)

    # Confidence is dominated by frame rate here, not by pose quality.
    reasons: list[str] = []
    if info.fps >= 100:
        band = GREEN
    elif info.fps >= 55:
        band = AMBER
        reasons.append(f"{info.fps:.0f} fps limits precision to "
                       f"±{best.frame_uncertainty_cm:.1f} cm")
    else:
        band = AMBER
        reasons.append(f"{info.fps:.0f} fps gives ±{best.frame_uncertainty_cm:.1f} cm "
                       f"— cannot meet the ±2 cm target; 120 fps capture needed")

    return JumpResult(
        best_height_cm=best.height_cm, attempts=attempts,
        confidence=band, confidence_reasons=reasons, fps=info.fps,
    )
