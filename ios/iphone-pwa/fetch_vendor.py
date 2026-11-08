#!/usr/bin/env python3
"""
Download the pose-AI runtime and models into vendor/ so the app works with no internet.

Run once, while online, from anywhere:

    python3 ios/iphone-pwa/fetch_vendor.py

The files are about 34 MB in total and are not committed (see .gitignore). The app uses them from
vendor/mediapipe/ when present and falls back to the public CDN otherwise. The service worker (sw.js)
then keeps them on the phone, so after the first online visit the pose tests need no network.

MediaPipe Tasks Vision and the pose models are Apache 2.0.
"""

from __future__ import annotations

import os
import sys
import urllib.request

MP_VERSION = "0.10.14"  # keep in step with MP_VERSION in index.html
NPM = f"https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@{MP_VERSION}"
MODELS = "https://storage.googleapis.com/mediapipe-models/pose_landmarker"
HERE = os.path.dirname(os.path.abspath(__file__))
DEST = os.path.join(HERE, "vendor", "mediapipe")

FILES = [
    (f"{NPM}/vision_bundle.mjs", "vision_bundle.mjs"),
    (f"{NPM}/wasm/vision_wasm_internal.js", "wasm/vision_wasm_internal.js"),
    (f"{NPM}/wasm/vision_wasm_internal.wasm", "wasm/vision_wasm_internal.wasm"),
    (f"{NPM}/wasm/vision_wasm_nosimd_internal.js", "wasm/vision_wasm_nosimd_internal.js"),
    (f"{NPM}/wasm/vision_wasm_nosimd_internal.wasm", "wasm/vision_wasm_nosimd_internal.wasm"),
    (f"{MODELS}/pose_landmarker_full/float16/latest/pose_landmarker_full.task", "pose_landmarker_full.task"),
    (f"{MODELS}/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task", "pose_landmarker_lite.task"),
]


def fetch(url: str, path: str, force: bool) -> str:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path) and os.path.getsize(path) > 0 and not force:
        return "kept"
    tmp = path + ".part"
    with urllib.request.urlopen(url, timeout=60) as r, open(tmp, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
    if os.path.getsize(tmp) == 0:
        os.remove(tmp)
        raise RuntimeError("empty download")
    os.replace(tmp, path)
    return "downloaded"


def main() -> int:
    force = "--force" in sys.argv
    failed = 0
    for url, rel in FILES:
        path = os.path.join(DEST, rel)
        try:
            state = fetch(url, path, force)
            print(f"  {state:<10} {rel}  ({os.path.getsize(path) / 1048576:.1f} MB)")
        except Exception as e:  # noqa: BLE001 - report every failure, keep going
            failed += 1
            print(f"  FAILED     {rel}: {e}")
    print("\nDone." if not failed else f"\n{failed} file(s) failed. Check the connection and run again.")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
