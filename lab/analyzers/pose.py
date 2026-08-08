"""
Shared pose utilities for the Battery Fitness analyzers.

This module is deliberately written to mirror what the Kotlin implementation
will do on-device with `com.google.mediapipe:tasks-vision`. MediaPipe emits the
same 33-landmark topology in both Python and Android, so thresholds tuned here
transfer directly to the app. Keep the logic here portable: no numpy-only tricks
that have no cheap Kotlin equivalent.

Reference: Concept Note §6.2 — every AI-measured test must expose
  (a) how the model detects the event,
  (b) how the detection becomes a measurement,
  (c) a confidence indicator.
"""

from __future__ import annotations

import math
import os
import urllib.request
from dataclasses import dataclass, field
from typing import Iterator, Optional

import cv2
import mediapipe as mp
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision as mp_vision

# ----------------------------------------------------------------------------
# MediaPipe Pose landmark indices (33-point BlazePose topology).
# Named so the analyzer code reads like the Handbook protocol it implements.
# ----------------------------------------------------------------------------
NOSE = 0
LEFT_EAR, RIGHT_EAR = 7, 8
LEFT_SHOULDER, RIGHT_SHOULDER = 11, 12
LEFT_ELBOW, RIGHT_ELBOW = 13, 14
LEFT_WRIST, RIGHT_WRIST = 15, 16
LEFT_HIP, RIGHT_HIP = 23, 24
LEFT_KNEE, RIGHT_KNEE = 25, 26
LEFT_ANKLE, RIGHT_ANKLE = 27, 28
LEFT_HEEL, RIGHT_HEEL = 29, 30
LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX = 31, 32

MODEL_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "models")
MODEL_FILE = "pose_landmarker_full.task"
MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
    "pose_landmarker_full/float16/latest/pose_landmarker_full.task"
)


def ensure_model() -> str:
    """Download the PoseLandmarker model on first use.

    Kept out of git (Submission §3.4 permits a download script for files that
    would otherwise bloat the repo).
    """
    os.makedirs(MODEL_DIR, exist_ok=True)
    path = os.path.abspath(os.path.join(MODEL_DIR, MODEL_FILE))
    if not os.path.exists(path):
        print(f"Downloading pose model -> {path}")
        urllib.request.urlretrieve(MODEL_URL, path)
    return path


# ----------------------------------------------------------------------------
# Geometry helpers
# ----------------------------------------------------------------------------

@dataclass(frozen=True)
class Pt:
    """A landmark in *normalised* image space: x,y in [0,1], y grows downward."""
    x: float
    y: float
    visibility: float = 1.0

    def dist(self, other: "Pt") -> float:
        return math.hypot(self.x - other.x, self.y - other.y)


def angle_deg(a: Pt, b: Pt, c: Pt) -> float:
    """Interior angle at vertex `b` formed by a-b-c, in degrees (0..180).

    Used for the hip angle that drives the sit-up state machine and the knee
    angle that verifies Handbook setup rules.
    """
    v1x, v1y = a.x - b.x, a.y - b.y
    v2x, v2y = c.x - b.x, c.y - b.y
    n1 = math.hypot(v1x, v1y)
    n2 = math.hypot(v2x, v2y)
    if n1 < 1e-9 or n2 < 1e-9:
        return float("nan")
    cos = max(-1.0, min(1.0, (v1x * v2x + v1y * v2y) / (n1 * n2)))
    return math.degrees(math.acos(cos))


def midpoint(a: Pt, b: Pt) -> Pt:
    return Pt((a.x + b.x) / 2.0, (a.y + b.y) / 2.0, min(a.visibility, b.visibility))


# ----------------------------------------------------------------------------
# Frame container
# ----------------------------------------------------------------------------

@dataclass
class Frame:
    """One analysed video frame."""
    index: int
    t: float                      # seconds from clip start
    landmarks: list[Pt] = field(default_factory=list)
    detected: bool = False

    def p(self, idx: int) -> Pt:
        return self.landmarks[idx]

    def visible(self, *idxs: int, threshold: float = 0.5) -> bool:
        """True when every requested landmark is confidently visible.

        Clothing occlusion (Concept Note §8.3 — salwar kameez, long kurtas) shows
        up here as dropped visibility, which is how the analyzers degrade
        gracefully instead of emitting a confident wrong number.
        """
        return all(self.landmarks[i].visibility >= threshold for i in idxs)


# ----------------------------------------------------------------------------
# Video -> pose frames
# ----------------------------------------------------------------------------

@dataclass
class ClipInfo:
    fps: float
    width: int
    height: int
    frame_count: int
    duration: float


def probe(video_path: str) -> ClipInfo:
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise FileNotFoundError(f"Cannot open video: {video_path}")
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    n = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    cap.release()
    return ClipInfo(fps=fps, width=w, height=h, frame_count=n,
                    duration=(n / fps if fps else 0.0))


def pose_frames(video_path: str,
                model_path: Optional[str] = None) -> Iterator[Frame]:
    """Yield a `Frame` per video frame, with pose landmarks attached.

    Runs in VIDEO mode (not LIVE_STREAM) because we analyse *post-capture*.
    That is the architectural choice from the solution document §4.2: it
    decouples measurement accuracy from device speed, so a 3 GB budget phone
    pays a latency cost rather than an accuracy cost.
    """
    model_path = model_path or ensure_model()
    info = probe(video_path)

    options = mp_vision.PoseLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=model_path),
        running_mode=mp_vision.RunningMode.VIDEO,
        num_poses=2,               # protocol puts a partner/tester in frame
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )

    cap = cv2.VideoCapture(video_path)
    idx = 0
    with mp_vision.PoseLandmarker.create_from_options(options) as landmarker:
        while True:
            ok, bgr = cap.read()
            if not ok:
                break
            rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            ts_ms = int(idx * 1000.0 / info.fps)

            result = landmarker.detect_for_video(mp_image, ts_ms)
            frame = Frame(index=idx, t=idx / info.fps)

            if result.pose_landmarks:
                # Athlete selection: pick the subject by role, not by index.
                # Handbook IX puts a partner holding the feet in frame and
                # Handbook III allows a tester holding the knees, so "person 0"
                # is not reliably the athlete.
                chosen = _select_athlete(result.pose_landmarks)
                frame.landmarks = [
                    Pt(lm.x, lm.y, getattr(lm, "visibility", 1.0)) for lm in chosen
                ]
                frame.detected = True

            yield frame
            idx += 1
    cap.release()


def _select_athlete(all_poses) -> list:
    """Choose the athlete among multiple detected people.

    Heuristic: the athlete is the person whose torso is most horizontal
    (sit-ups, sit & reach) or, failing that, the largest subject in frame.
    A coach tap overrides this in the app — we never silently guess in a way
    that cannot be corrected.
    """
    if len(all_poses) == 1:
        return all_poses[0]

    best, best_score = all_poses[0], -1.0
    for lms in all_poses:
        try:
            sh = midpoint(Pt(lms[LEFT_SHOULDER].x, lms[LEFT_SHOULDER].y),
                          Pt(lms[RIGHT_SHOULDER].x, lms[RIGHT_SHOULDER].y))
            hp = midpoint(Pt(lms[LEFT_HIP].x, lms[LEFT_HIP].y),
                          Pt(lms[RIGHT_HIP].x, lms[RIGHT_HIP].y))
        except (IndexError, AttributeError):
            continue
        dx, dy = abs(sh.x - hp.x), abs(sh.y - hp.y)
        torso = math.hypot(dx, dy)
        if torso < 1e-6:
            continue
        # horizontality in [0,1]; 1.0 == torso perfectly parallel to the floor
        horizontality = dx / torso
        score = horizontality + 0.3 * torso   # tie-break toward the nearer body
        if score > best_score:
            best, best_score = lms, score
    return best


# ----------------------------------------------------------------------------
# Confidence banding — the §6.2(c) requirement, and §8.2's instruction to
# surface reduced accuracy rather than "silently giving wrong results".
# ----------------------------------------------------------------------------

GREEN, AMBER, RED = "GREEN", "AMBER", "RED"


def band_from_signals(detection_rate: float,
                      fps: float,
                      extra_penalty: float = 0.0) -> tuple[str, list[str]]:
    """Map measurable capture-quality signals onto a confidence band.

    Returns the band plus the human-readable reasons, so the app can tell the
    operator *why* a measurement is amber instead of just colouring it.
    """
    reasons: list[str] = []
    score = 1.0

    if detection_rate < 0.90:
        score -= 0.35
        reasons.append(f"pose lost in {(1 - detection_rate) * 100:.0f}% of frames")
    if fps < 27:
        score -= 0.25
        reasons.append(f"low frame rate ({fps:.0f} fps)")
    score -= extra_penalty

    if score >= 0.8:
        return GREEN, reasons
    if score >= 0.5:
        return AMBER, reasons
    return RED, reasons
