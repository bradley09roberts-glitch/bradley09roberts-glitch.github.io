"""What the website says about each model: a friendly label, a role and a one-line description.

Used by the model comparison pages (footy.model_compare: every model's genuine pre-match
records and the live leaderboard; footy.backtest_explorer: the saved walk-forward ledgers).

Roles:

- `model_of_record`: named in the period of reference/primary_models.json that applies
  (D-026). On a match, only the fixture's record of record has it; another listed model with a
  record for that match is a `fallback_of_record` there.
- otherwise the role given in reference/shadow_models.json (written by `footy publish-shadows`:
  `machine_learning`, `challenger`, `baseline` or `earlier_record`), read leniently because the
  file may be missing or still being written;
- otherwise `earlier_record` for a model of record of an earlier period, else `other`.

The backtest explorer adds `lab_best` (the best candidate of a model-lab family on validation).

Labels and descriptions below are taken from STATUS.md and DECISIONS.md (D-024 to D-028);
shadow_models.json's own label or description wins when it gives one. A model that appears in
neither gets its name made readable and no description: nothing is made up.

A purely market-derived model (a de-vigged bookmakers' consensus on its own) is never shown:
its probabilities are a price in disguise (D-013, D-024). `is_market_only` decides.
"""

from __future__ import annotations

import json
from collections.abc import Iterable, Mapping
from pathlib import Path

from footy.reference import REFERENCE_DIR

SHADOW_MODELS_PATH = REFERENCE_DIR / "shadow_models.json"
GROUPS = ("domestic", "uefa")

MODEL_OF_RECORD = "model_of_record"
FALLBACK_OF_RECORD = "fallback_of_record"
MACHINE_LEARNING = "machine_learning"
CHALLENGER = "challenger"
BASELINE = "baseline"
EARLIER_RECORD = "earlier_record"
LAB_BEST = "lab_best"
OTHER = "other"
SHADOW_ROLES = (MACHINE_LEARNING, CHALLENGER, BASELINE, EARLIER_RECORD)
# Display order and names. The API sends ROLE_LABELS with every model list.
ROLE_LABELS = {
    MODEL_OF_RECORD: "Model of record",
    FALLBACK_OF_RECORD: "Model of record (fallback)",
    CHALLENGER: "Challenger",
    MACHINE_LEARNING: "Machine learning",
    LAB_BEST: "Best of a model-lab family",
    EARLIER_RECORD: "Earlier model of record",
    BASELINE: "Baseline",
    OTHER: "Other",
}
ROLE_ORDER = {role: i for i, role in enumerate(ROLE_LABELS)}


# name -> (label, description). Descriptions say what the model is, in one line.
CATALOGUE: dict[str, tuple[str, str]] = {
    # Domestic models of record (D-026, D-027).
    "market_tilt_shots": (
        "Shots model + market",
        "The shots-as-xG model's score grid moved onto the bookmakers' pre-closing consensus "
        "(Bet365 and the market average) for the match result and over/under 2.5.",
    ),
    "congestion_context_on_elo": (
        "Elo with congestion and context",
        "Goal-based Elo ratings with fixture-congestion and context effects; the pure model "
        "used where a match has no prices.",
    ),
    "dixon_coles": (
        "Dixon-Coles",
        "The classic Dixon-Coles team-strength model: time-weighted Poisson goals with a "
        "low-score correction. The reference every model is compared with.",
    ),
    # Model + market candidates (D-024, D-027).
    "market_tilt_dc": (
        "Dixon-Coles + market",
        "Dixon-Coles's score grid moved onto the bookmakers' pre-closing consensus for the "
        "match result and over/under 2.5.",
    ),
    "market_tilt_ctx": (
        "Elo with congestion + market",
        "The congestion-and-context Elo model's score grid moved onto the bookmakers' "
        "pre-closing consensus.",
    ),
    "market_tilt_elo": (
        "Goal Elo + market",
        "The goal-difference Elo model's score grid moved onto the bookmakers' pre-closing "
        "consensus.",
    ),
    "market_tilt_ens": (
        "Ensemble + market",
        "The lab's ensemble's score grid moved onto the bookmakers' pre-closing consensus.",
    ),
    # Machine learning (D-028).
    "ml_online_market": (
        "Online learner (with prices)",
        "Learns mixture weights over the other models' score grids and updates them after "
        "every settled match; uses the pre-closing prices as the market-tilted models do.",
    ),
    "ml_online_pure": (
        "Online learner (no prices)",
        "Learns mixture weights over the pure models' score grids and updates them after "
        "every settled match; no prices.",
    ),
    "ml_stack_market": (
        "Stacked learner (with prices)",
        "A ridge logistic meta-model that corrects a reference forecast from the other "
        "models' forecasts; uses the pre-closing prices.",
    ),
    "ml_stack_pure": (
        "Stacked learner (no prices)",
        "A ridge logistic meta-model that corrects a reference forecast from the pure "
        "models' forecasts; no prices.",
    ),
    # Best of each model-lab family (STATUS.md, "Model lab").
    "elo_goals_gd": (
        "Goal-difference Elo",
        "Elo ratings in which every goal of the margin counts, mapped to a score grid.",
    ),
    "ensemble_split": (
        "Ensemble (split pool)",
        "Pools several lab models: one mixture decides the match result, another the "
        "number of goals.",
    ),
    "dynamic_ratings_blend3": (
        "Dynamic ratings",
        "Team ratings that move match by match: two Kalman-filter rating systems (one also "
        "reading shots) blended with pi-ratings.",
    ),
    "bivariate_dispersion_sx_birth_time": (
        "Bivariate score shape",
        "The shots model's expected goals with a score-dependent scoring process: a side "
        "that leads or trails scores at a different rate as the match goes on.",
    ),
    "goal_calibration_tuned_level": (
        "Calibrated Dixon-Coles",
        "Tuned Dixon-Coles with each league's recent scoring level corrected out of sample.",
    ),
    "shots_xg_sot_joint_rw": (
        "Shots as xG",
        "One fit over shots on target and goals, using shots as a stand-in for expected "
        "goals, with each league's level free to drift from season to season.",
    ),
    "dc_tuning_tilt": (
        "Tuned Dixon-Coles",
        "Dixon-Coles with separate strength and openness ratings and its own time decays, "
        "tilted for both teams to score.",
    ),
    "ml_gbm_glm_boost_dc": (
        "Gradient boosting",
        "A Poisson model on engineered team features with a few boosted trees on its "
        "residuals, mixed with Dixon-Coles.",
    ),
    # Baselines.
    "baseline_frequency": (
        "Frequency baseline",
        "Each league's share of home wins, draws, goal lines and both teams scoring over the "
        "previous two years. Every composite score is measured against it.",
    ),
    "baseline_poisson": (
        "Poisson baseline",
        "Plain Poisson goals from team attack and defence ratings, without time decay or the "
        "low-score correction.",
    ),
    "baseline_elo": (
        "Elo baseline",
        "Elo ratings per league with a home advantage and a fitted draw model; match result only.",
    ),
    # UEFA (D-023, D-026).
    "pan_euro_mix_mxg": (
        "Pan-European mix (market xG)",
        "A 70/30 mix of pan-European Dixon-Coles and Elo; the Dixon-Coles part is fitted "
        "partly to the expected goals top-five league prices implied.",
    ),
    "pan_euro_mix": (
        "Pan-European mix",
        "A 70/30 mix of pan-European Dixon-Coles and Elo, fitted on goals only. The UEFA "
        "model of record before 21 October 2026.",
    ),
    "pan_euro_mix_mxg_all_ma0_w0.8": (
        "Pan-European mix (all-league market xG)",
        "The pan-European mix trained on every league's market expected goals; it led on "
        "validation but not on test.",
    ),
    "euro_frequency": (
        "Frequency baseline",
        "The recent share of home wins, draws, goal lines and both teams scoring in UEFA "
        "matches. Every composite score is measured against it.",
    ),
}

# Names that are a bookmakers' consensus on their own (never shown).
MARKET_ONLY_NAMES = frozenset(
    {"market", "market_consensus", "market_closing", "closing_market", "bookmakers", "closing"}
)


def readable(name: str) -> str:
    """A model name made readable when the catalogue has no label: "elo_goals_wfe" ->
    "Elo goals wfe"."""
    text = name.replace("_", " ").strip()
    return text[:1].upper() + text[1:] if text else name


def is_market_only(name: str, features: Mapping | None = None) -> bool:
    """True for a forecast made of bookmakers' prices alone: a market-only name, a record that
    says so (`market_only`), or one that used prices without a base model to move."""
    if name in MARKET_ONLY_NAMES or name.startswith(("market_only", "market_consensus")):
        return True
    f = features or {}
    if f.get("market_only") is True:
        return True
    params = f.get("model_params")
    used = isinstance(f.get("market_prices_used"), dict)
    return used and not (isinstance(params, dict) and params.get("base"))


# ---------------------------------------------------------------------------------------------
# reference/shadow_models.json


def _entries(value: object) -> Iterable[tuple[str, dict]]:
    """(model, entry) pairs from a group's value: a list of objects naming their model, or
    an object keyed by model name (an entry may be a plain role string)."""
    if isinstance(value, dict) and isinstance(value.get("models"), list | dict):
        value = value["models"]
    if isinstance(value, dict):
        for name, e in value.items():
            if isinstance(name, str) and not name.startswith("_"):
                yield name, e if isinstance(e, dict) else {"role": e}
    elif isinstance(value, list):
        for e in value:
            if isinstance(e, dict):
                name = e.get("model") or e.get("name") or e.get("model_name")
                if isinstance(name, str) and name:
                    yield name, e


def load_shadow_models(path: Path | None = None) -> dict[str, dict[str, dict]]:
    """group -> model -> {"role", "label", "description"} from shadow_models.json. A missing,
    unreadable or half-written file reads as empty (the pages fall back to the model of
    record and "other"); an unknown role reads as "other"."""
    path = path or SHADOW_MODELS_PATH
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}
    if not isinstance(data, dict):
        return {}
    out: dict[str, dict[str, dict]] = {}
    for group, value in data.items():
        if group not in GROUPS:
            continue
        for name, e in _entries(value):
            role = e.get("role")
            out.setdefault(group, {})[name] = {
                "role": role if role in SHADOW_ROLES else OTHER,
                "label": e.get("label") if isinstance(e.get("label"), str) else None,
                "description": e.get("description")
                if isinstance(e.get("description"), str)
                else None,
            }
    return out


# ---------------------------------------------------------------------------------------------
# One model's display facts


def label_of(name: str, shadow_entry: Mapping | None = None) -> str:
    if shadow_entry and shadow_entry.get("label"):
        return str(shadow_entry["label"])
    return CATALOGUE[name][0] if name in CATALOGUE else readable(name)


def description_of(name: str, shadow_entry: Mapping | None = None) -> str | None:
    if shadow_entry and shadow_entry.get("description"):
        return str(shadow_entry["description"])
    return CATALOGUE[name][1] if name in CATALOGUE else None


def role_of(
    name: str,
    current: Iterable[str],
    earlier: Iterable[str],
    shadow_entry: Mapping | None = None,
) -> str:
    """A model's standing role in a group: `current` is the list of models of record in force
    now, `earlier` every model named in an earlier period."""
    if name in set(current):
        return MODEL_OF_RECORD
    if shadow_entry and shadow_entry.get("role") in SHADOW_ROLES:
        return str(shadow_entry["role"])
    if name in set(earlier):
        return EARLIER_RECORD
    return OTHER
