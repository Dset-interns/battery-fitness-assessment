"""
Test 1 — Height.  Target: within ±1 cm (Concept Note §2).

THE MEASUREMENT LADDER
----------------------
A phone camera sees a flat 2D image. Converting pixels to centimetres needs a
real-world length in the same plane as the subject. Concept Note §5.1 rules out
leaning on a stadiometer ("without reliance on a stadiometer"), but Handbook
Test I lists the field alternative directly:

    Equipment: Stadiometer (or steel ruler or tape measure placed against a wall)

So the graduated scale a human assessor reads is already in frame. This analyzer
reads the same reference. Rungs, in preference order (solution document §3):

    Rung 1  printed marker of known size          -> GREEN
    Rung 2  the protocol's own wall tape          -> GREEN
    Rung 3  athlete's own known height            -> AMBER   (not for Test 1)
    Rung 4  device intrinsics + IMU geometry      -> AMBER
    Rung 5  manual entry                          -> RED / explicit override

The band reported is the rung that actually produced the number, which is what
Concept Note §8.2 asks for: surface reduced accuracy rather than "silently
giving wrong results".

TWO THINGS THAT DOMINATE THE ERROR
----------------------------------
1. HAIR. A human assessor presses the stadiometer through the hair to the skull.
   A camera sees the *outside* of it. Thick, oiled, tied or braided hair adds
   1-4 cm — larger than the ±1 cm tolerance and larger than every other error
   source combined. There is no engineering fix. Instruct hair flat and untied,
   then MEASURE the effect during validation (same subject tied vs untied) and
   report the delta as a named limitation.

2. CAMERA HEIGHT. The vertex sits a few centimetres forward of the wall plane
   while heels/buttocks/upper back touch it. Viewed from a camera at mid-body
   height, that offset projects upward as a systematic over-read — order 1.5 cm
   at 1.7 m standoff with a 5 cm offset. Placing the lens at roughly vertex
   height puts the offset along the optical axis where it contributes almost
   nothing. It is a free instruction in the setup wizard and it is decisive for
   a ±1 cm target.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision as mp_vision

from .pose import (
    AMBER, GREEN, LEFT_ANKLE, LEFT_EAR, LEFT_HEEL, LEFT_SHOULDER, RED,
    RIGHT_ANKLE, RIGHT_EAR, RIGHT_HEEL, RIGHT_SHOULDER, ensure_model, probe,
)

# Reject the measurement if the stable-frame spread exceeds this — the athlete
# was still moving, so no single number is defensible.
MAX_SPREAD_CM = 1.5

# Frankfurt plane check: the ear-eye line should be within this of horizontal.
MAX_HEAD_TILT_DEG = 8.0


@dataclass
class HeightResult:
    height_cm: float
    rung: int
    method: str
    confidence: str = RED
    confidence_reasons: list[str] = field(default_factory=list)
    frames_used: int = 0
    spread_cm: float = 0.0
    cm_per_px: float = 0.0
    head_tilt_deg: float = 0.0
    notes: list[str] = field(default_factory=list)

    def report(self) -> str:
        lines = [
            "",
            "=" * 68,
            "TEST 1 — HEIGHT",
            "=" * 68,
            f"  Height          : {self.height_cm:.1f} cm",
            f"  Method          : rung {self.rung} — {self.method}",
            f"  Confidence      : {self.confidence}"
            + (f"  ({'; '.join(self.confidence_reasons)})"
               if self.confidence_reasons else ""),
            f"  Stable frames   : {self.frames_used}   spread {self.spread_cm:.2f} cm",
            f"  Scale           : {self.cm_per_px:.4f} cm/px",
            f"  Head tilt       : {self.head_tilt_deg:.1f}° from horizontal",
        ]
        if self.notes:
            lines.append("")
            lines.append("  NOTES")
            lines += [f"    - {n}" for n in self.notes]
        lines.append("")
        return "\n".join(lines)


def analyze(video_path: str,
            ref_px: Optional[float] = None,
            ref_cm: Optional[float] = None) -> HeightResult:
    """Measure standing height from a clip.

    `ref_px` / `ref_cm` are the two-point calibration: the pixel length of a
    known real-world span visible in frame (the wall tape, or a printed marker).
    Supplying them puts the measurement on rung 2; omitting them means we cannot
    honestly produce centimetres at all, and we say so rather than guessing.
    """
    info = probe(video_path)
    notes: list[str] = []

    if not ref_px or not ref_cm:
        return HeightResult(
            height_cm=0.0, rung=5,
            method="no scale reference supplied — manual entry required",
            confidence=RED,
            confidence_reasons=["no known length in frame; pixels cannot become cm"],
            notes=["Place a tape measure against the wall (Handbook Test I "
                   "equipment) or a printed marker, then re-measure."],
        )

    cm_per_px = ref_cm / ref_px

    # Segmentation is required: MediaPipe Pose has no skull-top landmark, so the
    # vertex must come from the mask's topmost body pixel, not from a keypoint.
    options = mp_vision.PoseLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=ensure_model()),
        running_mode=mp_vision.RunningMode.VIDEO,
        num_poses=1,
        output_segmentation_masks=True,
        min_pose_detection_confidence=0.6,
    )

    samples: list[float] = []
    tilts: list[float] = []
    cap = cv2.VideoCapture(video_path)
    idx = 0

    with mp_vision.PoseLandmarker.create_from_options(options) as landmarker:
        while True:
            ok, bgr = cap.read()
            if not ok:
                break
            rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
            image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            res = landmarker.detect_for_video(image, int(idx * 1000.0 / info.fps))
            idx += 1

            if not res.pose_landmarks or not res.segmentation_masks:
                continue

            lms = res.pose_landmarks[0]
            mask = res.segmentation_masks[0].numpy_view()
            h, w = mask.shape[:2]

            body = mask > 0.5
            rows = np.where(body.any(axis=1))[0]
            if rows.size == 0:
                continue

            # --- Vertex: topmost body pixel (outside of the hair, see docstring)
            vertex_y = float(rows.min())

            # --- Feet: prefer the heel landmarks; they are the Handbook's
            # "feet contacting standing surface". Fall back to the mask bottom.
            try:
                heel_y = max(lms[LEFT_HEEL].y, lms[RIGHT_HEEL].y) * h
                ankle_y = max(lms[LEFT_ANKLE].y, lms[RIGHT_ANKLE].y) * h
                feet_y = heel_y if heel_y >= ankle_y else ankle_y
            except (IndexError, AttributeError):
                feet_y = float(rows.max())

            # --- Frankfurt plane proxy: ear line should be near horizontal
            try:
                dx = (lms[RIGHT_EAR].x - lms[LEFT_EAR].x) * w
                dy = (lms[RIGHT_EAR].y - lms[LEFT_EAR].y) * h
                tilts.append(abs(np.degrees(np.arctan2(dy, abs(dx) + 1e-6))))
            except (IndexError, AttributeError):
                pass

            samples.append((feet_y - vertex_y) * cm_per_px)

    cap.release()

    if len(samples) < 5:
        return HeightResult(0.0, 5, "insufficient stable frames", RED,
                            ["fewer than 5 usable frames"], len(samples))

    # Handbook Test I has the athlete "hold full breath" — take the median of
    # the stable window rather than any single frame.
    arr = np.array(sorted(samples))
    lo, hi = int(len(arr) * 0.25), max(int(len(arr) * 0.75), int(len(arr) * 0.25) + 1)
    core = arr[lo:hi]
    height_cm = float(np.median(core))
    spread = float(core.max() - core.min())
    tilt = float(np.median(tilts)) if tilts else 0.0

    reasons: list[str] = []
    band = GREEN
    if spread > MAX_SPREAD_CM:
        band = AMBER
        reasons.append(f"frame-to-frame spread {spread:.1f} cm — athlete not still")
    if tilt > MAX_HEAD_TILT_DEG:
        band = AMBER
        reasons.append(f"head tilted {tilt:.0f}° — Frankfurt plane not horizontal")
    if info.fps < 24:
        reasons.append(f"low frame rate ({info.fps:.0f} fps)")

    notes.append("Vertex read from the segmentation mask, i.e. the OUTSIDE of the "
                 "hair. Untied or thick hair biases this HIGH by 1-4 cm.")
    notes.append("Place the lens at approximately vertex height to avoid a "
                 "systematic over-read from the wall-plane offset.")

    return HeightResult(
        height_cm=height_cm, rung=2,
        method="scale from known reference length in frame",
        confidence=band, confidence_reasons=reasons,
        frames_used=len(samples), spread_cm=spread,
        cm_per_px=cm_per_px, head_tilt_deg=tilt, notes=notes,
    )
