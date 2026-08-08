"""
Unit tests for the sit-up state machine, driven by synthetic signal series.

Why synthetic: the state machine is a pure function of scalar signals, so it can
be verified exactly — known rep count in, known rep count out — without any
footage. That matters here because Concept Note §7.1 forbids athlete video
leaving our control, and because these tests must run before any subject has
been recorded.

Real-footage validation is a separate, later step (Submission §6) and does not
replace this. These tests prove the RULES are implemented; footage proves the
POSE ESTIMATION holds up.

Run:  ./.venv/bin/python -m pytest test_situp_logic.py -v
  or: ./.venv/bin/python test_situp_logic.py
"""

from __future__ import annotations

import math

from analyzers.situp_counter import (
    ELBOW_KNEE_TOUCH, WRIST_EAR_MAX, count_from_signals,
)

FPS = 30.0
SHOULDER_W = 0.15

# Postures, expressed in the same units the real extractor produces.
HIP_DOWN, HIP_UP = 130.0, 60.0        # degrees at the hip
EK_DOWN, EK_UP = 1.50, 0.40           # elbow->knee, in femur lengths
ELBOW_FLOOR, ELBOW_TOP = 0.80, 0.50   # normalised image y (grows downward)
WRIST_EAR_OK = 0.60


def _lerp(a: float, b: float, u: float) -> float:
    return a + (b - a) * u


def make_rep(t0: float,
             *,
             frames_up: int = 12,
             frames_down: int = 12,
             hold: int = 2,
             elbow_return: float = ELBOW_FLOOR,
             wrist_ear_peak: float = WRIST_EAR_OK,
             pushoff: bool = False) -> list[dict]:
    """Generate one sit-up cycle: supine -> curled -> supine."""
    out: list[dict] = []
    t = t0

    def emit(hip, ek, elbow_y, wrist_ear):
        nonlocal t
        out.append({
            "t": t, "hip_angle": hip, "elbow_knee": ek,
            "wrist_ear": wrist_ear, "elbow_y": elbow_y,
            "shoulder_y": 0.7, "shoulder_w": SHOULDER_W,
        })
        t += 1.0 / FPS

    # rise
    for i in range(frames_up):
        u = (i + 1) / frames_up
        elbow_y = _lerp(ELBOW_FLOOR, ELBOW_TOP, u)
        if pushoff and frames_up // 3 <= i <= frames_up // 3 + 2:
            # elbow drives back down toward the floor mid-rise
            elbow_y += 0.06
        emit(_lerp(HIP_DOWN, HIP_UP, u),
             _lerp(EK_DOWN, EK_UP, u),
             elbow_y,
             _lerp(WRIST_EAR_OK, wrist_ear_peak, u))
    # hold at the top
    for _ in range(hold):
        emit(HIP_UP, EK_UP, ELBOW_TOP, wrist_ear_peak)
    # return
    for i in range(frames_down):
        u = (i + 1) / frames_down
        emit(_lerp(HIP_UP, HIP_DOWN, u),
             _lerp(EK_UP, EK_DOWN, u),
             _lerp(ELBOW_TOP, elbow_return, u),
             _lerp(wrist_ear_peak, WRIST_EAR_OK, u))
    # settle supine so the DOWN state is unambiguous
    for _ in range(3):
        emit(HIP_DOWN, EK_DOWN, elbow_return, WRIST_EAR_OK)
    return out


def build(n: int, **kw) -> list[dict]:
    sig: list[dict] = []
    t = 0.0
    for _ in range(n):
        rep = make_rep(t, **kw)
        sig += rep
        t = rep[-1]["t"] + 1.0 / FPS
    return sig


# ---------------------------------------------------------------------------

def test_counts_clean_reps():
    r = count_from_signals(build(8), window=45, fps=FPS, detection_rate=1.0)
    assert r.total_attempts == 8, f"expected 8 attempts, got {r.total_attempts}"
    assert r.valid_count == 8, (
        f"expected 8 valid, got {r.valid_count}: "
        f"{[(x.number, x.foul_rule, x.foul_detail) for x in r.reps if not x.valid]}"
    )


def test_rule_3a_hands_leave_neck():
    r = count_from_signals(build(4, wrist_ear_peak=WRIST_EAR_MAX + 0.5),
                           window=45, fps=FPS, detection_rate=1.0)
    assert r.total_attempts == 4
    assert r.valid_count == 0, "hands leaving the neck must nullify every rep"
    assert all(x.foul_rule == "3a" for x in r.reps), \
        [x.foul_rule for x in r.reps]


def test_rule_3b_elbow_pushoff():
    r = count_from_signals(build(4, pushoff=True),
                           window=45, fps=FPS, detection_rate=1.0)
    assert r.total_attempts == 4
    assert r.valid_count == 0, "pushing off the floor must nullify the rep"
    assert all(x.foul_rule == "3b" for x in r.reps), \
        [x.foul_rule for x in r.reps]


def test_rule_3c_elbows_not_returned_flat():
    # Return the elbows only part-way to the floor.
    r = count_from_signals(build(4, elbow_return=ELBOW_FLOOR - 0.12),
                           window=45, fps=FPS, detection_rate=1.0)
    assert r.total_attempts == 4
    assert r.valid_count == 0, "not returning elbows flat must nullify the rep"
    assert all(x.foul_rule == "3c" for x in r.reps), \
        [x.foul_rule for x in r.reps]


def test_window_cutoff_u12_is_30s():
    """A U-12 athlete's count must stop at 30 s even if the clip runs longer."""
    sig = build(40)                      # ~40 reps over ~40 s
    full = count_from_signals(sig, window=45, fps=FPS, detection_rate=1.0)
    u12 = count_from_signals(sig, window=30, fps=FPS, detection_rate=1.0)
    assert u12.total_attempts < full.total_attempts, \
        "30 s window must count fewer reps than 45 s on the same clip"
    assert all(x.t_end <= 30.0 for x in u12.reps)


def test_low_detection_rate_downgrades_confidence():
    good = count_from_signals(build(5), window=45, fps=FPS, detection_rate=1.0)
    poor = count_from_signals(build(5), window=45, fps=FPS, detection_rate=0.55)
    assert good.confidence == "GREEN"
    assert poor.confidence in ("AMBER", "RED")
    assert poor.confidence_reasons, "a downgraded band must explain itself"


def test_insufficient_data_is_red_not_zero():
    r = count_from_signals([], window=45, fps=FPS, detection_rate=0.0)
    assert r.confidence == "RED"
    assert r.valid_count == 0
    assert r.confidence_reasons


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    failed = 0
    for t in tests:
        try:
            t()
            print(f"  PASS  {t.__name__}")
        except AssertionError as e:
            failed += 1
            print(f"  FAIL  {t.__name__}\n        {e}")
        except Exception as e:                       # noqa: BLE001
            failed += 1
            print(f"  ERROR {t.__name__}\n        {type(e).__name__}: {e}")
    print(f"\n{len(tests) - failed}/{len(tests)} passed")
    raise SystemExit(1 if failed else 0)
