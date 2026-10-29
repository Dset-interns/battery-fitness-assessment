#!/usr/bin/env python3
"""
Battery Fitness — algorithm lab CLI.

Tune analyzers against real clips here before porting thresholds to Kotlin.
A Python iteration is seconds; an on-device iteration is rebuild-deploy-record.

Usage
-----
  ./.venv/bin/python run.py situps  clips/situps.mp4 --age 14
  ./.venv/bin/python run.py jump    clips/jump.mp4
  ./.venv/bin/python run.py height  clips/height.mp4 --ref-px 412 --ref-cm 100
  ./.venv/bin/python run.py probe   clips/anything.mp4

DATA PROTECTION (Concept Note §7.1 / constraint C1)
---------------------------------------------------
Clips live in lab/clips/, which is git-ignored. Never commit footage, never
upload it, never process it on a cloud runner. Everything here runs locally.
"""

from __future__ import annotations

import argparse
import os
import sys

from analyzers import height_scale, jump_flight, sit_and_reach, situp_counter
from analyzers.pose import probe


def _require(path: str) -> str:
    if not os.path.exists(path):
        sys.exit(f"error: no such clip: {path}\n"
                 f"Put recordings in lab/clips/ (git-ignored).")
    return path


def main() -> int:
    ap = argparse.ArgumentParser(description="Battery Fitness algorithm lab")
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("probe", help="report clip resolution / fps / duration")
    p.add_argument("clip")

    p = sub.add_parser("situps", help="Test 9 — count valid sit-ups")
    p.add_argument("clip")
    p.add_argument("--age", type=int, default=14,
                   help="selects the Handbook window: <12 -> 30s, else 45s")

    p = sub.add_parser("jump", help="Test 4 — vertical jump via flight time")
    p.add_argument("clip")

    p = sub.add_parser("reach", help="Test 3 — sit and reach")
    p.add_argument("clip")
    p.add_argument("--ref-px", type=float,
                   help="pixel length of a known span visible in frame")
    p.add_argument("--ref-cm", type=float,
                   help="real-world length of that span, in cm")

    p = sub.add_parser("height", help="Test 1 — standing height")
    p.add_argument("clip")
    p.add_argument("--ref-px", type=float,
                   help="pixel length of a known span visible in frame")
    p.add_argument("--ref-cm", type=float,
                   help="real-world length of that span, in cm")

    a = ap.parse_args()
    clip = _require(a.clip)

    if a.cmd == "probe":
        i = probe(clip)
        print(f"\n  {os.path.basename(clip)}")
        print(f"  resolution : {i.width}x{i.height}")
        print(f"  frame rate : {i.fps:.2f} fps")
        print(f"  frames     : {i.frame_count}")
        print(f"  duration   : {i.duration:.2f} s")
        # Concept Note §6.3 minimum quality gate
        ok_res = min(i.width, i.height) >= 720
        print(f"  720p gate  : {'PASS' if ok_res else 'FAIL — below 720p'}")
        print(f"  30fps gate : {'PASS' if i.fps >= 29 else 'FAIL — below 30 fps'}\n")
        return 0

    if a.cmd == "situps":
        print(situp_counter.analyze(clip, age=a.age).report())
    elif a.cmd == "jump":
        print(jump_flight.analyze(clip).report())
    elif a.cmd == "reach":
        print(sit_and_reach.analyze(clip, ref_px=a.ref_px, ref_cm=a.ref_cm).report())
    elif a.cmd == "height":
        print(height_scale.analyze(clip, ref_px=a.ref_px, ref_cm=a.ref_cm).report())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
