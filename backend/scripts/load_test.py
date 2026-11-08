#!/usr/bin/env python3
"""
Rough load test for the sync endpoint: many phones each sending athletes with all ten tests.

    python backend/scripts/load_test.py --url http://localhost:8000 --key DEVICE_KEY \
        --phones 200 --athletes 50 --workers 16

Reports athletes per second and latency percentiles. Run it against the PostgreSQL stack to learn anything about
production; against SQLite it only shows the floor.
"""

import argparse
import concurrent.futures as cf
import statistics
import time
import uuid

import httpx

TESTS = [("height", "cm"), ("weight", "kg"), ("sit_and_reach", "cm"), ("vertical_jump", "cm"), ("broad_jump", "cm"),
         ("medicine_ball_throw", "cm"), ("sprint_30m", "s"), ("shuttle_run", "s"), ("sit_ups", "reps"), ("endurance_run", "s")]


def make_batch(phone: int, athletes: int) -> dict:
    dev = f"load-{phone:06d}-{uuid.uuid4().hex[:6]}"
    ath, res = [], []
    for i in range(athletes):
        lid = str(1700000000000 + i)
        ath.append({"local_id": lid, "name": f"Load Test {phone}-{i}", "age_years": 9 + (i % 12), "gender": "FMO"[i % 3], "school": "Load School",
                    "id_type": "LOCAL", "id_number": "", "assessment_date": "2026-11-09",
                    "consent": {"version": 1, "signed_at": "2026-11-09T10:00:00Z", "signer_name": "Load Signer", "relationship": "parent" if (9 + i % 12) < 18 else "self",
                                "minor": (9 + i % 12) < 18, "items": {"assess": True, "video": True, "data": True, "parent_height": False}, "assessor": "Load", "signature_png_base64": None}})
        for k, (t, u) in enumerate(TESTS):
            res.append({"athlete_local_id": lid, "test_key": t, "attempts": [
                {"attempt_id": f"{lid}:{t}:{n}", "attempt_no": n, "valid": True, "value": 10.0 + k + n, "unit": u, "confidence": "GREEN", "method": "load", "audit": []} for n in (1, 2)]})
    return {"device_id": dev, "assessor": "Load", "app_version": "load", "sent_at": "2026-11-09T10:05:00Z", "athletes": ath, "results": res}


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--url", default="http://localhost:8000")
    p.add_argument("--key", required=True)
    p.add_argument("--phones", type=int, default=100)
    p.add_argument("--athletes", type=int, default=20)
    p.add_argument("--workers", type=int, default=8)
    a = p.parse_args()
    lat: list[float] = []
    bad = 0

    def one(phone: int) -> None:
        nonlocal bad
        body = make_batch(phone, a.athletes)
        t0 = time.perf_counter()
        r = httpx.post(f"{a.url}/v1/sync", json=body, headers={"X-API-Key": a.key}, timeout=120)
        lat.append(time.perf_counter() - t0)
        if r.status_code != 200 or r.json()["accepted_athletes"] != a.athletes:
            bad += 1

    t0 = time.perf_counter()
    with cf.ThreadPoolExecutor(a.workers) as ex:
        list(ex.map(one, range(a.phones)))
    dur = time.perf_counter() - t0
    total = a.phones * a.athletes
    lat.sort()
    print(f"{total} athletes ({total * 20} attempts) from {a.phones} phones in {dur:.1f} s = {total / dur:.0f} athletes/s; "
          f"batch latency p50 {statistics.median(lat):.2f} s, p95 {lat[int(len(lat) * .95) - 1]:.2f} s, failures {bad}")


if __name__ == "__main__":
    main()
