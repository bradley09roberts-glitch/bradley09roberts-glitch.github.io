"""Published editions: immutable, hashed, registered, and committed to git.

An edition is written once to records/editions/<season>/W<week>/<edition_id>.json and
registered in the `editions` table (append-only). A game is part of the genuine
pre-match record only if the edition was published before that game's kickoff; games
already kicked off are listed with published_before_kickoff = false and excluded from
the prospective record. Retrospective research runs never use this module's path.
"""

from __future__ import annotations

import json
import subprocess
from datetime import datetime

import pandas as pd

from . import db
from .config import EDITIONS_DIR, LAKE_DIR, PROJECT_ROOT
from .forecast import MODEL_VERSION, MODELS
from .util import iso, london, sha256_bytes, sha256_json, utcnow


class EditionError(RuntimeError):
    pass


STATUS_NOTE = {
    "default": ("Probabilities are conditional on the player taking part (most bookmakers "
                "void a player prop if he does not play). No bookmaker prices were available "
                "to this edition, so no value claims are made."),
    "0.4.0": ("Model v0.4: as v0.3 (stacking weights frozen on 2022-2023 validation) with the "
              "official-report participation adjustment estimated on 2016-2021 instead of "
              "guessed. Every model is recorded so each has a genuine pre-match record. "
              "Probabilities are conditional on the player taking part (most bookmakers void "
              "a player prop if he does not play). No bookmaker prices were available to "
              "this edition, so no value claims are made."),
    "0.3.0": ("Model v0.3 with per-market stacking weights frozen on the 2022-2023 validation "
              "seasons (reports/EXPERIMENTS.md). Every model is recorded so each has a "
              "genuine pre-match record. Probabilities are conditional on the player taking "
              "part (most bookmakers void a player prop if he does not play). No bookmaker "
              "prices were available to this edition, so no value claims are made."),
}


def code_version() -> dict:
    def git(*a):
        return subprocess.run(["git", *a], cwd=PROJECT_ROOT, capture_output=True, text=True,
                              check=False).stdout.strip()

    dirty = git("status", "--porcelain", "--", "src", "pyproject.toml", "uv.lock")
    return {"git_commit": git("rev-parse", "HEAD"), "dirty": bool(dirty),
            "model_version": MODEL_VERSION}


def publish(con, *, season: int, week: int, kind: str, result: dict, lake: dict,
            published_at: datetime | None = None, allow_dirty: bool = False,
            notes: list[str] | None = None, headline: str = "X1",
            code: dict | None = None) -> dict:
    """`code` should be captured before the forecasts are computed (the code that actually
    ran); if omitted it is read now."""
    now = published_at or utcnow()
    cv = code or code_version()
    if cv["dirty"] and not allow_dirty:
        raise EditionError("refusing to publish from uncommitted code: commit first so the "
                           "edition names the exact code that produced it")
    games = result["games"]
    stype = games[0].season_type if games else "REG"
    edition_id = f"{season}-{stype}-W{week:02d}-{kind}-{now.strftime('%Y%m%dT%H%MZ')}"
    path = EDITIONS_DIR / str(season) / f"W{week:02d}" / f"{edition_id}.json"
    if path.exists():
        raise EditionError(f"edition {edition_id} already exists; editions are never rewritten")
    manifest = json.loads((LAKE_DIR / "build_manifest.json").read_text())
    g_rows = []
    for g in games:
        ko = pd.Timestamp(g.kickoff_utc).to_pydatetime()
        g_rows.append({"game_id": g.game_id, "kickoff_utc": iso(ko), "kickoff_london": london(ko),
                       "home": g.home.team, "away": g.away.team,
                       "published_before_kickoff": now < ko})
    inputs = {"lake_build": {"built_at": manifest["built_at"], "inputs": manifest["inputs"]},
              "params": result["params"]}
    doc = {
        "edition_id": edition_id, "kind": kind, "season": season, "week": week,
        "season_type": stype, "published_at": iso(now), "cutoff_at": iso(now),
        "published_at_london": london(now), "retrospective": False,
        "headline_model": headline, "models": MODELS, "code": cv,
        "status_note": STATUS_NOTE.get(MODEL_VERSION, STATUS_NOTE["default"]),
        "notes": notes or [], "inputs_hash": sha256_json(inputs), "inputs": inputs,
        "games": g_rows, "teams": result["teams"], "forecasts": result["forecasts"],
    }
    blob = json.dumps(doc, indent=None, separators=(",", ":"), default=str).encode()
    digest = sha256_bytes(blob)
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "xb") as f:  # exclusive create: never overwrite
        f.write(blob)
    path.with_suffix(".json.sha256").write_text(f"{digest}  {path.name}\n")
    n_ok = sum(1 for f in result["forecasts"] if f["status"] == "ok")
    with db.tx(con):
        con.execute(
            "INSERT INTO editions(edition_id, season, week, season_type, kind, cutoff_at,"
            " published_at, model_id, code_version, inputs_hash, n_forecasts, file_path,"
            " file_sha256, retrospective) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
            (edition_id, season, week, stype, kind, iso(now), iso(now), ",".join(MODELS),
             cv["git_commit"] + ("+dirty" if cv["dirty"] else ""), doc["inputs_hash"], n_ok,
             str(path.relative_to(PROJECT_ROOT)), digest))
        con.executemany(
            "INSERT INTO edition_games(edition_id, game_id, kickoff_at, published_before_kickoff)"
            " VALUES (?,?,?,?)",
            [(edition_id, r["game_id"], r["kickoff_utc"], int(r["published_before_kickoff"]))
             for r in g_rows])
    return {"edition_id": edition_id, "path": str(path), "sha256": digest, "forecasts_ok": n_ok,
            "games": len(g_rows),
            "before_kickoff": sum(r["published_before_kickoff"] for r in g_rows)}


def verify(path) -> bool:
    """Recompute an edition file's hash against its sidecar."""
    from pathlib import Path

    p = Path(path)
    want = p.with_suffix(".json.sha256").read_text().split()[0]
    return sha256_bytes(p.read_bytes()) == want
