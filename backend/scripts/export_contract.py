#!/usr/bin/env python3
"""Regenerate openapi.json and schema.sql from the code, so the documents never drift from it.

    python backend/scripts/export_contract.py
"""

import json
import os
import sys

os.environ["BFA_AUTOSTART"] = "0"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)

from sqlalchemy.dialects import postgresql  # noqa: E402
from sqlalchemy.schema import CreateIndex, CreateTable  # noqa: E402

from app.config import Settings  # noqa: E402
from app.main import create_app  # noqa: E402
from app.models import Base  # noqa: E402


def main() -> None:
    app = create_app(Settings(database_url="sqlite:///:memory:"))
    with open(os.path.join(ROOT, "openapi.json"), "w", encoding="utf-8") as f:
        json.dump(app.openapi(), f, indent=2, ensure_ascii=False)
        f.write("\n")
    d = postgresql.dialect()
    out = ["-- PostgreSQL schema, generated from app/models.py by scripts/export_contract.py. Do not edit by hand.",
           "-- Identified zone: devices, athletes, consents, attempts, results, media, sync_log.",
           "-- De-identified zone: deid_subjects, deid_results. See README.md for giving each zone its own schema and role.", ""]
    for table in Base.metadata.sorted_tables:
        out.append(str(CreateTable(table).compile(dialect=d)).strip() + ";")
        for ix in sorted(table.indexes, key=lambda i: i.name):
            out.append(str(CreateIndex(ix).compile(dialect=d)).strip() + ";")
        out.append("")
    with open(os.path.join(ROOT, "schema.sql"), "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print("wrote openapi.json and schema.sql")


if __name__ == "__main__":
    main()
