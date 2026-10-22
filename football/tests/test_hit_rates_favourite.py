"""The bookmakers' favourite beside each league row on /api/hit-rates (audit 22 Oct 2026, M2).

SYNTHETIC TEST DATA: fictional teams, made-up ledgers, results and prices. Nothing here is a
real price, prediction or result.
"""

import json

import pandas as pd
import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session

from fdfixtures import REFERENCE
from footy.ingest.fd_import import sync_reference
from footy.modeling import hit_rates as hr
from footy.snapshot import find_prices

SOURCE = hr.Source(
    "domestic",
    "synthetic_model",
    hr.MODEL_AND_MARKET,
    "synthetic_model__test__2425",
    "synthetic_base",
    "synthetic_base__test__2425",
    "backtests",
    "league",
)
# (match id, home goals, away goals, SYNTHETIC Avg pre-closing prices H/D/A, over/under 2.5)
GAMES = [
    (1, 2, 0, (1.5, 4.0, 6.0), (1.8, 2.0)),  # favourite H: right; over 2.5 favourite: wrong
    (2, 0, 1, (1.7, 3.8, 4.5), (1.9, 1.9)),  # favourite H: wrong; over/under level: no favourite
    (3, 1, 1, (2.9, 3.0, 2.6), (2.1, 1.7)),  # favourite A: wrong; under: right
    (4, 3, 1, (2.0, 3.4, 3.6), None),  # favourite H: right; no over/under prices
]


def ledger(model: str) -> pd.DataFrame:
    return pd.DataFrame(
        [
            {
                "model": model,
                "league": "E0",
                "match_id": mid,
                "season": "2425",
                "date": "2024-09-01",
                "home_team": f"syn-home-{mid}",
                "away_team": f"syn-away-{mid}",
                "home_goals": hg,
                "away_goals": ag,
                "status": "predicted",
                "p_H": 0.5,
                "p_D": 0.3,
                "p_A": 0.2,
                "p_over_2.5": 0.5,
                "p_btts": 0.5,
                **{col: 1 for col in hr.MARKETS.values()},
            }
            for mid, hg, ag, _, _ in GAMES
        ]
    )


@pytest.fixture
def runs(settings, migrated_engine, monkeypatch):
    monkeypatch.setattr(hr, "SOURCES", (SOURCE,))
    root = settings.data_dir / "backtests"
    for run_id, model in (
        (SOURCE.run_id, "synthetic_model"),
        (SOURCE.baseline_run_id, "synthetic_base"),
    ):
        d = root / run_id
        d.mkdir(parents=True)
        (d / "manifest.json").write_text(json.dumps({"model": model, "split": "test"}))
        ledger(model).to_csv(d / "ledger.csv", index=False)
    with Session(migrated_engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()
    pairs = {
        1: ("eng-testtown", "eng-sampleton"),
        2: ("eng-sampleton", "eng-mockford"),
        3: ("eng-mockford", "eng-testtown"),
        4: ("eng-sampleton", "eng-testtown"),
    }
    with migrated_engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-01', 'p')"
            )
        )
        for mid, hg, ag, x, ou in GAMES:
            conn.execute(
                text(
                    "INSERT INTO matches (id, division, season, match_date, home_team, "
                    "away_team, home_goals, away_goals, result, source_row, first_raw_file_id, "
                    "last_raw_file_id) VALUES (:id, 'E0', '2425', '2024-09-01', :h, :a, :hg, "
                    ":ag, 'H', 1, 1, 1)"
                ),
                {"id": mid, "h": pairs[mid][0], "a": pairs[mid][1], "hg": hg, "ag": ag},
            )
            prices = [("1X2", s, p) for s, p in zip("HDA", x, strict=True)]
            if ou:
                prices += [("OU_2.5", "O", ou[0]), ("OU_2.5", "U", ou[1])]
            for market, sel, price in prices:
                conn.execute(
                    text(
                        "INSERT INTO fd_odds (match_id, bookmaker, market_key, selection, phase, "
                        "price, quote_time_basis, source_column, raw_file_id) VALUES (:m, 'Avg', "
                        ":k, :s, 'pre_closing', :p, 'synthetic', 'SYN', 1)"
                    ),
                    {"m": mid, "k": market, "s": sel, "p": price},
                )


def test_league_rows_carry_the_bookmakers_favourite(client, runs):
    body = client.get("/api/hit-rates").json()
    (table,) = body["backtest"]["tables"]
    assert table["favourite_note"]
    all_row = next(r for r in table["rows"] if r["code"] == "ALL")
    fav = all_row["markets"]["1X2"]["favourite"]
    assert (fav["n"], fav["correct"]) == (4, 2)
    ou = all_row["markets"]["OU_2.5"]["favourite"]
    assert (ou["n"], ou["correct"]) == (2, 1)  # one level pair and one without prices left out
    assert "favourite" not in all_row["markets"]["BTTS"]
    # Only hit rates go out: no price, and nothing that looks like one.
    assert find_prices({"bodies": [body]}) == []
    assert "1.5" not in json.dumps(all_row["markets"]["1X2"]["favourite"])
