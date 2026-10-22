"""Backtest explorer: every match's prediction from the saved walk-forward ledgers, for a
curated set of models.

**These are backtests, not records.** Each prediction was computed after the fact (20–22
October 2026) by the walk-forward harness: for every match, the model was fitted only on
results before that match's decision time, so it had not seen the match. None of them was
published before kickoff, so none is a genuine pre-match record (those are in
footy.model_compare). Every response says so in `kind` ("backtest") and `explanation`.

Read only from data/backtests/<run>/ledger.csv and data/backtests_euro/<run>/ledger.csv; nothing
is re-predicted. Splits (D-025): validation 2023/24 chose between models, so it flatters the
chosen ones; test 2024/25 + 2025/26 was run once per model, and only for a few.

Curated models (`CURATED`): the models of record, the four machine-learning candidates (D-028),
the best of each model-lab family, the model + market candidates (D-024, D-027) and the
baselines; for UEFA the model of record, the earlier one, the round-3 challenger and the
baseline. A purely market-derived ledger is refused (`market_only_ledger`): its probabilities
would be a de-vigged price (D-013, D-024). No price is read: the ledgers hold none.

Summary only (`PRICE_IDENTICAL`): six model + market ledgers whose match-result and over 2.5
figures are, match by match, the bookmakers' consensus with the margin removed (the tilt moves
the grid all the way onto the prices, `w_1x2` = `w_ou` = 1.0, so the base model changes nothing
there; the online learner mixes grids that were all moved onto the same prices). They agree
with each other to three decimal places on 5,254 of the 5,255 matches. Their per-match figures
would be a price in disguise, so only their summaries are sent: over the matches the filters
select, and per team (`team_summaries`), never over fewer than SUMMARY_ONLY_MIN_MATCHES
(a summary of one or two matches is close to those matches' own figures). `ml_stack_market` really
differs (its learners move the result) and keeps its per-match figures.
"""

from __future__ import annotations

import json
import math
from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd

from footy import model_catalogue as cat
from footy.modeling.hit_rates import UEFA_NAMES, wilson
from footy.reference import DIVISIONS

KIND = "backtest"
EXPLANATION = (
    "Backtest, not a record. These predictions were computed after the fact, in October "
    "2026, by replaying past seasons: for each match the model was fitted only on results "
    "from before that match, so it had not seen it. They were never published before "
    "kickoff, so they are not genuine pre-match records and are never counted with them. "
    "Validation (2023/24) was used to choose between models, which flatters the chosen ones; "
    "test (2024/25 and 2025/26) was run once, for a few models only."
)
SPLIT_NOTES = {
    "validation": "Validation 2023/24: used to choose between the models, so the best scores "
    "here are optimistic.",
    "test": "Test 2024/25 and 2025/26: run once for each tested model, after it was chosen.",
}
SPLIT_SEASONS = {"validation": ("2324",), "test": ("2425", "2526")}
SEASON_SPLIT = {s: split for split, seasons in SPLIT_SEASONS.items() for s in seasons}
GROUP_NAMES = {
    "domestic": "League games, five top leagues",
    "uefa": "UEFA competitions",
}
DOMESTIC_ORDER = ("E0", "SP1", "I1", "D1", "F1")
UEFA_ORDER = ("UCL", "UEL", "UECL", "UCLQ", "UELQ", "UECLQ")
ROOTS = {"domestic": "backtests", "uefa": "backtests_euro"}
DIVISION_COLUMN = {"domestic": "league", "uefa": "comp"}
# Per model, per match, in this order (`columns` in every response).
COLUMNS = ("p_home", "p_draw", "p_away", "p_over_2_5", "p_btts_yes", "result_right", "rps")
LEDGER_COLUMNS = ("p_H", "p_D", "p_A", "p_over_2.5", "p_btts", "correct_1x2", "rps")
PROB_DECIMALS = 3
RPS_DECIMALS = 4
MAX_LIMIT = 1000
SMALL_SAMPLE = 100

MODEL_ONLY = "model_only"
MODEL_AND_MARKET = "model_and_market"


@dataclass(frozen=True)
class Curated:
    model: str
    group: str  # "domestic" | "uefa"
    role: str  # the role when the model is not (or no longer) a model of record
    basis: str
    family: str | None
    # (split, run directory), or (split, run directory, the name the ledger was saved under)
    runs: tuple[tuple[str, ...], ...]
    note: str | None = None
    # False: summaries only, never a match's figures (PRICE_IDENTICAL).
    per_match: bool = True

    def ledger_model(self, run: tuple[str, ...]) -> str:
        return run[2] if len(run) > 2 else self.model


# Model + market ledgers whose per-match match-result and over 2.5 figures are the bookmakers'
# de-vigged consensus itself (see the module docstring): summaries only.
PRICE_IDENTICAL = frozenset(
    {
        "market_tilt_shots",
        "market_tilt_ctx",
        "market_tilt_dc",
        "market_tilt_elo",
        "market_tilt_ens",
        "ml_online_market",
    }
)
# A summary-only model's figures over a handful of matches would come close to those matches'
# own: one match's RPS is the consensus scored on its result. The API's team filter selects a
# team's season, and an opponent's share of it is just the two meetings (one, if a ledger
# misses the other), so any summary of a summary-only model over fewer matches than this is
# withheld (a team's season, the smallest the page shows, is 33 or more).
SUMMARY_ONLY_MIN_MATCHES = 10
WITHHELD_NOTE = (
    "Summary only: match by match, the match-result and over 2.5 goals figures are the "
    "bookmakers' consensus with the margin removed (the same for each such model + market "
    "forecast), which would be a price in disguise; only results over many matches are shown."
)


def _dom(model, role, basis, family, test=False, note=None) -> Curated:
    runs = [("validation", f"{model}__validation__2324")]
    if test:
        runs.append(("test", f"{model}__test__2425-2526"))
    return Curated(
        model,
        "domestic",
        role,
        basis,
        family,
        tuple(runs),
        note=note,
        per_match=model not in PRICE_IDENTICAL,
    )


CURATED: tuple[Curated, ...] = (
    # Models of record (STATUS.md, D-026, D-027).
    _dom("market_tilt_shots", cat.CHALLENGER, MODEL_AND_MARKET, "Model + market", test=True),
    _dom(
        "congestion_context_on_elo",
        cat.LAB_BEST,
        MODEL_ONLY,
        "Congestion and context",
        test=True,
    ),
    _dom("dixon_coles", cat.BASELINE, MODEL_ONLY, "Dixon-Coles (reference)", test=True),
    # Machine learning (D-028).
    _dom("ml_online_market", cat.MACHINE_LEARNING, MODEL_AND_MARKET, "Machine learning", test=True),
    _dom("ml_stack_market", cat.MACHINE_LEARNING, MODEL_AND_MARKET, "Machine learning"),
    _dom("ml_online_pure", cat.MACHINE_LEARNING, MODEL_ONLY, "Machine learning"),
    _dom("ml_stack_pure", cat.MACHINE_LEARNING, MODEL_ONLY, "Machine learning"),
    # Model + market candidates (D-024, D-027).
    _dom("market_tilt_ctx", cat.CHALLENGER, MODEL_AND_MARKET, "Model + market", test=True),
    _dom("market_tilt_dc", cat.CHALLENGER, MODEL_AND_MARKET, "Model + market"),
    _dom("market_tilt_elo", cat.CHALLENGER, MODEL_AND_MARKET, "Model + market"),
    _dom("market_tilt_ens", cat.CHALLENGER, MODEL_AND_MARKET, "Model + market"),
    # Best of each model-lab family on validation (STATUS.md, "Model lab").
    _dom("elo_goals_gd", cat.LAB_BEST, MODEL_ONLY, "Goal-based Elo"),
    _dom("ensemble_split", cat.LAB_BEST, MODEL_ONLY, "Ensembles"),
    _dom("dynamic_ratings_blend3", cat.LAB_BEST, MODEL_ONLY, "Dynamic ratings"),
    _dom(
        "bivariate_dispersion_sx_birth_time",
        cat.LAB_BEST,
        MODEL_ONLY,
        "Bivariate / dispersion",
    ),
    _dom("goal_calibration_tuned_level", cat.LAB_BEST, MODEL_ONLY, "Goal calibration"),
    _dom("shots_xg_sot_joint_rw", cat.LAB_BEST, MODEL_ONLY, "Shots as xG"),
    _dom("dc_tuning_tilt", cat.LAB_BEST, MODEL_ONLY, "Dixon-Coles tuning"),
    _dom("ml_gbm_glm_boost_dc", cat.LAB_BEST, MODEL_ONLY, "Gradient boosting"),
    # Baselines.
    _dom("baseline_frequency", cat.BASELINE, MODEL_ONLY, "Baselines", test=True),
    _dom("baseline_poisson", cat.BASELINE, MODEL_ONLY, "Baselines", test=True),
    _dom(
        "baseline_elo",
        cat.BASELINE,
        MODEL_ONLY,
        "Baselines",
        note="Match result only: no goal lines or both teams to score.",
    ),
    # UEFA (D-023, D-026): the round-3 runs, all on the same fixtures.
    Curated(
        "pan_euro_mix_mxg",
        "uefa",
        cat.CHALLENGER,
        MODEL_ONLY,
        "Pan-European",
        (
            ("validation", "pan_euro_mix_mxg__validation_round3"),
            ("test", "pan_euro_mix_mxg__test_round3"),
        ),
    ),
    Curated(
        "pan_euro_mix",
        "uefa",
        cat.EARLIER_RECORD,
        MODEL_ONLY,
        "Pan-European",
        (
            ("validation", "pan_euro_mix__validation_round3"),
            ("test", "euro_mix_0.7_0.3__test_mxg", "euro_mix_0.7_0.3"),
        ),
        note="Its test seasons were run before the model took its published name; it is the "
        "same model.",
    ),
    Curated(
        "pan_euro_mix_mxg_all_ma0_w0.8",
        "uefa",
        cat.CHALLENGER,
        MODEL_ONLY,
        "Pan-European",
        (
            ("validation", "pan_euro_mix_mxg_all_ma0_w0.8__validation_round3"),
            ("test", "pan_euro_mix_mxg_all_ma0_w0.8__test_round3"),
        ),
    ),
    Curated(
        "euro_frequency",
        "uefa",
        cat.BASELINE,
        MODEL_ONLY,
        "Baselines",
        (
            ("validation", "euro_frequency__validation_round3"),
            ("test", "euro_frequency__test_round3"),
        ),
    ),
)


def division_name(code: str) -> str:
    if code in DIVISIONS:
        return DIVISIONS[code].name
    return UEFA_NAMES.get(code, code)


def group_of_division(code: str) -> str | None:
    if code in UEFA_ORDER:
        return "uefa"
    if code in DOMESTIC_ORDER:
        return "domestic"
    return None


def market_only_ledger(manifest: Mapping) -> bool:
    """True for a ledger of bookmakers' prices alone: a market-only name, or prices used
    (books, de-vig) with no model underneath (no base, member stream or reference model)."""
    name = str(manifest.get("model") or "")
    params = manifest.get("model_params")
    params = params if isinstance(params, Mapping) else {}
    priced = any(k in params for k in ("books", "devig"))
    return cat.is_market_only(name) or (priced and not cat.names_a_model(params))


# ---------------------------------------------------------------------------------------------
# Reading the ledgers (cached per file and modification time)

_LEDGER_CACHE: dict[tuple[str, int], pd.DataFrame] = {}
_GROUP_CACHE: dict[tuple, Table] = {}
KEY = ["division", "date", "home_team", "away_team"]


def _read_ledger(path: Path, group: str) -> pd.DataFrame:
    stamp = (str(path), path.stat().st_mtime_ns)
    if stamp in _LEDGER_CACHE:
        return _LEDGER_CACHE[stamp]
    wanted = {
        DIVISION_COLUMN[group],
        "model",
        "season",
        "date",
        "stage",
        "home_team",
        "away_team",
        "home_goals",
        "away_goals",
        "status",
        *LEDGER_COLUMNS,
    }
    df = pd.read_csv(
        path,
        usecols=lambda c: c in wanted,
        dtype={"season": str, "stage": str},
        float_precision="round_trip",
    )
    df = df.rename(columns={DIVISION_COLUMN[group]: "division"})
    df = df[df.status == "predicted"].copy()
    for c in LEDGER_COLUMNS:
        if c not in df:
            df[c] = np.nan
    if "stage" not in df:
        df["stage"] = None
    for k in [k for k in _LEDGER_CACHE if k[0] == stamp[0]]:
        del _LEDGER_CACHE[k]  # an older version of the same file
    _LEDGER_CACHE[stamp] = df
    return df


@dataclass
class Run:
    model: str
    split: str
    run_id: str
    seasons: list[str]
    predicted: int
    headline: dict


@dataclass
class Table:
    """One group's matches (sorted by date) and each curated model's columns aligned to them."""

    group: str
    matches: pd.DataFrame  # division, season, split, date, stage, home/away team and goals
    values: dict[str, np.ndarray]  # model -> (n_matches, len(COLUMNS)), NaN where none
    runs: dict[str, list[Run]]  # model -> its runs
    skipped: list[dict]  # runs left out, with why


def _manifest(run_dir: Path) -> dict:
    try:
        return json.loads((run_dir / "manifest.json").read_text())
    except (OSError, ValueError):
        return {}


def load_table(data_dir: Path, group: str) -> Table:
    """Every curated model's predicted rows for the group, joined on the match (division, date,
    home and away team). Cached until a ledger changes."""
    root = data_dir / ROOTS[group]
    entries = [c for c in CURATED if c.group == group]
    files = []
    for c in entries:
        for run in c.runs:
            path = root / run[1] / "ledger.csv"
            files.append((str(path), path.stat().st_mtime_ns if path.exists() else None))
    stamp = (str(root), group, tuple(files))
    if stamp in _GROUP_CACHE:
        return _GROUP_CACHE[stamp]

    frames: dict[str, pd.DataFrame] = {}
    runs: dict[str, list[Run]] = {}
    skipped: list[dict] = []
    for c in entries:
        parts = []
        for run in c.runs:
            split, run_id = run[0], run[1]
            run_dir = root / run_id
            path = run_dir / "ledger.csv"
            manifest = _manifest(run_dir)
            expected = c.ledger_model(run)
            why = None
            if not path.exists():
                continue  # not run (most models have no test run), or not on this machine
            if manifest.get("model") != expected:
                why = f"the manifest names {manifest.get('model')!r}, not {expected!r}"
            elif market_only_ledger(manifest):
                why = "market-derived ledger (never shown)"
            if why:
                skipped.append({"model": c.model, "run_id": run_id, "reason": why})
                continue
            df = _read_ledger(path, group)
            if set(df.model.unique()) - {expected}:
                skipped.append({"model": c.model, "run_id": run_id, "reason": "mixed models"})
                continue
            df = df[df.season.isin(SPLIT_SEASONS[split])].assign(split=split)
            parts.append(df)
            h = manifest.get("headline") or {}
            runs.setdefault(c.model, []).append(
                Run(
                    c.model,
                    split,
                    run_id,
                    [str(s) for s in manifest.get("seasons") or []],
                    int(len(df)),
                    h.get("1X2") or {},
                )
            )
        if parts:
            frames[c.model] = pd.concat(parts, ignore_index=True)

    info_cols = [*KEY, "season", "split", "stage", "home_goals", "away_goals"]
    if frames:
        allrows = pd.concat([f[info_cols] for f in frames.values()], ignore_index=True)
        matches = allrows.drop_duplicates(KEY, keep="first")
    else:
        matches = pd.DataFrame(columns=info_cols)
    order = {d: i for i, d in enumerate(DOMESTIC_ORDER + UEFA_ORDER)}
    matches = (
        matches.assign(_div=matches.division.map(lambda d: order.get(d, len(order))))
        .sort_values(["date", "_div", "home_team"], kind="stable")
        .drop(columns="_div")
        .reset_index(drop=True)
    )
    index = pd.MultiIndex.from_frame(matches[KEY])
    values: dict[str, np.ndarray] = {}
    for model, f in frames.items():
        g = f.set_index(KEY)
        # A row whose final score disagrees with the first ledger's is left out, not guessed.
        goals = matches.set_index(KEY)[["home_goals", "away_goals"]].reindex(g.index)
        same = (goals.home_goals.values == g.home_goals.values) & (
            goals.away_goals.values == g.away_goals.values
        )
        g = g[same]
        values[model] = (
            g[list(LEDGER_COLUMNS)].reindex(index).to_numpy(dtype=float, na_value=np.nan)
        )
    table = Table(group, matches, values, runs, skipped)
    _GROUP_CACHE.clear()
    _GROUP_CACHE[stamp] = table
    return table


# ---------------------------------------------------------------------------------------------
# Responses


def _roles(periods: Mapping, group: str, now) -> tuple[list[str], set[str]]:
    from footy import models_of_record

    current = models_of_record.models_at(periods.get(group, []), now)
    named = {m for p in periods.get(group, []) for m in p.models}
    return current, named - set(current)


def role_for(
    c: Curated,
    current: Iterable[str],
    earlier: Iterable[str],
    shadow_entry: Mapping | None = None,
) -> str:
    """The model's role as every other page gives it (model_catalogue.role_of): model of record
    or fallback of record now, else the role shadow_models.json gives it, else an earlier model
    of record, else the explorer's own (`c.role`)."""
    return cat.role_of(c.model, current, earlier, shadow_entry, default=c.role)


def _round(v: float, n: int) -> float | None:
    return None if v is None or (isinstance(v, float) and math.isnan(v)) else round(float(v), n)


def models_body(data_dir: Path, periods: Mapping, now, shadow: Mapping | None = None) -> dict:
    """The `/api/backtest-models` body: the curated models, their splits and coverage.
    `shadow` (model_catalogue.load_shadow_models) supplies descriptions, so a model reads the
    same here as on the match pages."""
    shadow = shadow or {}
    groups, models = [], []
    for group in ("domestic", "uefa"):
        t = load_table(data_dir, group)
        g_shadow = shadow.get(group, {})
        current, earlier = _roles(periods, group, now)
        m = t.matches
        coverage = []
        if len(m):
            counts = m.groupby(["division", "season"], sort=False).size()
            order = DOMESTIC_ORDER if group == "domestic" else UEFA_ORDER
            for div in [d for d in order if d in set(m.division)]:
                for season in sorted({s for (d, s) in counts.index if d == div}):
                    coverage.append(
                        {
                            "division": div,
                            "season": season,
                            "split": SEASON_SPLIT.get(season),
                            "matches": int(counts[(div, season)]),
                        }
                    )
        divisions = list(dict.fromkeys(c["division"] for c in coverage))
        seasons = sorted({c["season"] for c in coverage})
        groups.append(
            {
                "group": group,
                "name": GROUP_NAMES[group],
                "divisions": [{"code": d, "name": division_name(d)} for d in divisions],
                "seasons": [{"season": s, "split": SEASON_SPLIT.get(s)} for s in seasons],
                "coverage": coverage,
                "skipped_runs": t.skipped,
            }
        )
        for c in [c for c in CURATED if c.group == group and c.model in t.runs]:
            models.append(
                {
                    "model": c.model,
                    "group": group,
                    "label": cat.label_of(c.model, g_shadow.get(c.model)),
                    "role": role_for(c, current, earlier, g_shadow.get(c.model)),
                    "family": c.family,
                    "basis": c.basis,
                    "per_match": c.per_match,
                    "description": cat.description_of(c.model, g_shadow.get(c.model)),
                    "note": c.note if c.per_match else WITHHELD_NOTE,
                    "splits": [
                        {
                            "split": r.split,
                            "run_id": r.run_id,
                            "seasons": r.seasons,
                            "matches": r.predicted,
                            "mean_rps": r.headline.get("rps"),
                            "results_right": r.headline.get("accuracy"),
                        }
                        for r in t.runs[c.model]
                    ],
                }
            )
    rank = {m["model"]: i for i, m in enumerate(models)}
    models.sort(
        key=lambda m: (
            m["group"] != "domestic",
            cat.ROLE_ORDER.get(m["role"], len(cat.ROLE_ORDER)),
            rank[m["model"]],
        )
    )
    return {
        "kind": KIND,
        "explanation": EXPLANATION,
        "splits": SPLIT_NOTES,
        "roles": cat.ROLE_LABELS,
        "columns": list(COLUMNS),
        "withheld_note": WITHHELD_NOTE,
        "groups": groups,
        "models": models,
    }


class BadRequest(ValueError):
    """A filter names something the explorer doesn't have."""


def matches_body(
    data_dir: Path,
    periods: Mapping,
    now,
    names: Mapping[str, str],
    media_fn,
    *,
    group: str | None = None,
    division: str | None = None,
    season: str | None = None,
    team: str | None = None,
    models: str | None = None,
    limit: int = 200,
    offset: int = 0,
    shadow: Mapping | None = None,
) -> dict:
    """The `/api/backtest-matches` body. `media_fn(names)` returns team key -> media summary.
    Raises BadRequest for an unknown division, season, group or model."""
    if division:
        g = group_of_division(division)
        if g is None:
            raise BadRequest(f"unknown division {division!r}")
        if group and group != g:
            raise BadRequest(f"{division} is not in the {group} group")
        group = g
    group = group or "domestic"
    if group not in GROUP_NAMES:
        raise BadRequest(f"unknown group {group!r}")
    if season and season not in SEASON_SPLIT:
        raise BadRequest(f"no backtest season {season!r} (2324, 2425 or 2526)")
    t = load_table(data_dir, group)
    available = [c.model for c in CURATED if c.group == group and c.model in t.values]
    if models:
        wanted = [m.strip() for m in models.split(",") if m.strip()]
        unknown = [m for m in wanted if m not in available]
        if unknown:
            raise BadRequest(f"not a curated {group} model: {', '.join(unknown)}")
        chosen = list(dict.fromkeys(wanted))
    else:
        chosen = available
    m = t.matches
    mask = np.ones(len(m), dtype=bool)
    if division:
        mask &= (m.division == division).to_numpy()
    if season:
        mask &= (m.season == season).to_numpy()
    if team:
        mask &= ((m.home_team == team) | (m.away_team == team)).to_numpy()
    # Only matches at least one chosen model predicted.
    if chosen:
        mask &= np.any([~np.isnan(t.values[x][:, 0]) for x in chosen], axis=0)
    rows = np.flatnonzero(mask)
    current, earlier = _roles(periods, group, now)
    by_name = {c.model: c for c in CURATED if c.group == group}
    g_shadow = (shadow or {}).get(group, {})

    summary = []
    for x in chosen:
        v = t.values[x][rows]
        have = ~np.isnan(v[:, 0])
        n = int(have.sum())
        right = int(np.nansum(v[have, 5])) if n else 0
        ci = wilson(right, n)
        if not by_name[x].per_match and 0 < n < SUMMARY_ONLY_MIN_MATCHES:
            right, ci, n_scored = None, None, 0  # withheld: too few to be only a summary
        else:
            n_scored = n
        summary.append(
            {
                "model": x,
                "matches": n,
                "results_right": right,
                "hit_rate": right / n if n_scored else None,
                "ci95": list(ci) if ci else None,
                "mean_rps": float(np.nanmean(v[have, 6])) if n_scored else None,
                "small_sample": n < SMALL_SAMPLE,
            }
        )

    # Summary-only models: each team's matches among those selected, never a handful
    # (SUMMARY_ONLY_MIN_MATCHES).
    team_summaries: dict[str, dict[str, list]] = {}
    selected = m.iloc[rows]
    for x in chosen:
        if by_name[x].per_match:
            continue
        v = t.values[x][rows]
        have = ~np.isnan(v[:, 0])
        per: dict[str, list] = {}
        home = selected.home_team.to_numpy()
        away = selected.away_team.to_numpy()
        for team in sorted(set(home[have]) | set(away[have])):
            sel = have & ((home == team) | (away == team))
            n = int(sel.sum())
            if n < SUMMARY_ONLY_MIN_MATCHES:
                continue
            per[str(team)] = [
                n,
                int(np.nansum(v[sel, 5])),
                _round(float(np.nanmean(v[sel, 6])), RPS_DECIMALS),
            ]
        team_summaries[x] = per

    page = rows[offset : offset + limit]
    out_matches = []
    keys: set[str] = set()
    for i in page:
        r = m.iloc[i]
        keys.update((r.home_team, r.away_team))
        cells = []
        for x in chosen:
            v = t.values[x][i]
            if math.isnan(v[0]) or not by_name[x].per_match:
                cells.append(None)
                continue
            cells.append(
                [
                    *(_round(v[j], PROB_DECIMALS) for j in range(5)),
                    None if math.isnan(v[5]) else int(v[5]),
                    _round(v[6], RPS_DECIMALS),
                ]
            )
        stage = r.stage if isinstance(r.stage, str) and r.stage else None
        out_matches.append(
            {
                "date": str(r.date),
                "division": r.division,
                "season": r.season,
                "split": r.split,
                **({"stage": stage} if group == "uefa" else {}),
                "home": r.home_team,
                "away": r.away_team,
                "home_name": names.get(r.home_team, r.home_team),
                "away_name": names.get(r.away_team, r.away_team),
                "score": [int(r.home_goals), int(r.away_goals)],
                "p": cells,
            }
        )
    named = {k: names.get(k, k) for k in sorted(keys)}
    media = media_fn(named)
    return {
        "kind": KIND,
        "explanation": EXPLANATION,
        "group": group,
        "filters": {
            "division": division,
            "season": season,
            "team": team,
            "models": models,
            "limit": limit,
            "offset": offset,
        },
        "total": int(len(rows)),
        "count": len(out_matches),
        "offset": offset,
        "columns": list(COLUMNS),
        "models": [
            {
                "model": x,
                "label": cat.label_of(x, g_shadow.get(x)),
                "role": role_for(by_name[x], current, earlier, g_shadow.get(x)),
                "basis": by_name[x].basis,
                "per_match": by_name[x].per_match,
            }
            for x in chosen
        ],
        "summary": summary,
        # Summary-only models (no per-match figures): team key -> [matches, results right,
        # mean RPS] over that team's matches among those selected (a team with fewer than
        # SUMMARY_ONLY_MIN_MATCHES of them is left out).
        "team_summaries": team_summaries,
        "teams": {k: {"name": v, "media": media.get(k)} for k, v in named.items()},
        "matches": out_matches,
    }
