"""
De-identification gateway.

What leaves the identified zone for analysis, and how.

* A pseudonym replaces the person: HMAC-SHA256 under a server-side secret, truncated. Where the athlete has a
  programme ID (APAAR, Aadhaar, NSRS) the pseudonym is derived from it, so the same athlete seen from two phones is
  one subject; otherwise it is derived from the phone and the app's local id.
* Name, school, ID number, signer, assessor, video keys, free-text methods and the exact date are never copied.
* Age is kept as years and as a band; the date becomes a month.
* Parental height travels only when the optional consent for it was given.

Re-identification needs the secret, which never sits in the analytics zone. Changing the secret re-keys every
pseudonym, so it must stay fixed once data exists.
"""

from __future__ import annotations

import hashlib
import hmac
import re

# Results where the lowest value is the best (times), and where the latest valid attempt counts (height).
LOWER_IS_BETTER = {"sprint_30m", "shuttle_run", "endurance_run"}
LATEST_VALID = {"height"}


def _norm(v: str) -> str:
    return re.sub(r"[^a-z0-9]", "", v.lower())


def pseudonym(secret: str, device_id: str, local_id: str, id_type: str, id_number: str) -> str:
    if secret == "":
        raise RuntimeError("BFA_PSEUDONYM_SECRET is not set")
    if id_type != "LOCAL" and _norm(id_number):
        basis = f"id:{id_type}:{_norm(id_number)}"
    else:
        basis = f"dev:{device_id}:{local_id}"
    digest = hmac.new(secret.encode(), basis.encode(), hashlib.sha256).hexdigest()
    return "S-" + digest[:16].upper()


def age_band(age: int) -> str:
    return "5-8" if age <= 8 else "9-11" if age <= 11 else "12-14" if age <= 14 else "15-17" if age <= 17 else "18+"


def best_attempt(test_key: str, attempts: list[dict]) -> dict | None:
    """The attempt that counts for the test, from the valid ones; None when none is valid."""
    valid = [a for a in attempts if a["valid"] and a["value"] is not None]
    if not valid:
        return None
    if test_key in LATEST_VALID:
        return max(valid, key=lambda a: a["attempt_no"])
    if test_key in LOWER_IS_BETTER:
        return min(valid, key=lambda a: a["value"])
    return max(valid, key=lambda a: a["value"])


def deidentify_subject(pseud: str, athlete, consent_items) -> dict:
    """athlete: AthleteIn-like. Returns only the fields allowed in the de-identified zone."""
    share_parents = bool(consent_items.parent_height)
    return {
        "pseudonym": pseud,
        "age_years": athlete.age_years,
        "age_band": age_band(athlete.age_years),
        "gender": athlete.gender,
        "assessment_month": athlete.assessment_date.strftime("%Y-%m"),
        "mother_cm": athlete.mother_cm if share_parents else None,
        "father_cm": athlete.father_cm if share_parents else None,
    }
