# Every model's predictions

_Written 22 October 2026. Code: `src/footy/model_compare.py` (genuine records),
`src/footy/backtest_explorer.py` (backtests), `src/footy/model_catalogue.py` (labels, roles,
descriptions), `src/footy/api/models_routes.py` (the endpoints). Tests:
`tests/test_api_models.py`, `tests/test_snapshot.py`._

The owner asked to see all the predictions for the models, not only the backtest scores. Two
kinds of prediction exist, and they are never mixed in one response:

| `kind` | What it is | Endpoints |
| --- | --- | --- |
| `genuine_prematch` | Saved before kickoff and never changed (D-026). Every model with a live record: the models of record, their fallbacks and the shadow models (`footy publish-shadows`, `reference/shadow_models.json`). | `/api/fixtures/{fixture_ref}/models`, `/api/model-leaderboard` |
| `backtest` | Computed after the fact, in October 2026, by the walk-forward harness: for each match the model was fitted only on results from before it, so it had not seen it. Never published before kickoff, so never a record. | `/api/backtest-models`, `/api/backtest-matches` |

Every response carries `kind` and a plain `explanation`. A page must show the explanation (or
its sense) wherever it shows the numbers.

## Rules

- **No prices.** Nothing here reads a bookmaker's price. A forecast made of prices alone (a
  de-vigged consensus) is never shown, live or backtest: `model_catalogue.is_market_only`
  (a market-only name, a record marked `market_only`, or prices used with no model named in
  `model_params`) and `backtest_explorer.market_only_ledger` (a ledger whose manifest has books
  or a de-vig method and no model named) keep them out. Both read the same rule,
  `model_catalogue.names_a_model`: a tilt's `base`, a learner's member `stream` or `reference`
  forecast, or an ensemble's `members` (the learners name no `base`, so a `base`-only test hid
  their live records). Model + market forecasts (`basis: "model_and_market"`) are model output
  and are shown, marked.
- **No consensus in disguise in the explorer** (decided 22 October 2026, after QA). Six model +
  market ledgers are, match by match, the bookmakers' consensus with the margin removed: the
  tilt moves the grid all the way onto the prices (`w_1x2` = `w_ou` = 1.0), so the base model
  changes nothing in the match result and over 2.5, and `ml_online_market` mixes grids that
  were all moved onto the same prices. `market_tilt_shots`, `market_tilt_ctx`,
  `market_tilt_dc`, `market_tilt_elo`, `market_tilt_ens` and `ml_online_market` agree with
  each other to three decimal places on 5,254 of 5,255 matches (validation and test). Their
  per-match figures would be football-data's prices in disguise for every past match, so the
  explorer sends only their summaries (`backtest_explorer.PRICE_IDENTICAL`, `per_match: false`):
  over the selected matches, and per team (`team_summaries`), never one match and never
  fewer than 10 (`SUMMARY_ONLY_MIN_MATCHES`: with the API's team filter an opponent's share is
  just the two meetings, whose mean RPS is close to the consensus itself; the page's own
  requests, a whole division and season, give each team 33 or more).
  `ml_stack_market` really differs (0 to 3 matches identical) and keeps its figures. The
  snapshot export refuses a backtest page where a summary-only model has a cell, or where two
  model + market models share their match-result figures on most matches
  (`snapshot.consensus_cells`).
- **Live, the same six** publish shadow records from Friday 23 October (the model of record
  already does). Each record's match-result and over 2.5 figures will be that same consensus,
  which the model of record's own page already shows by design (D-024: a model + market record
  shows the consensus probabilities it used). The every-model list shows each record as
  published and says when several model + market records share their figures. Whether these
  shadows should publish at all, or show only BTTS and expected goals, is the owner's call
  (STATUS.md, open questions).
- **Nothing made up.** Every number comes from a saved record, its settlement or a saved
  ledger. Labels and descriptions come from `reference/shadow_models.json` when it gives them,
  else from the catalogue in `model_catalogue.py` (taken from STATUS.md and DECISIONS.md), else
  the model's name made readable with no description.
- **Times** are ISO 8601 UTC with `+00:00` (D-005); the site shows them in UK time.

## Roles

`roles` in every response maps each role to its display name.

| Role | Meaning |
| --- | --- |
| `model_of_record` | On one match: the record of record. Away from a match (leaderboard, explorer, a record's page): the first model of the period of `primary_models.json` in force (D-026). |
| `fallback_of_record` | On one match: another model listed in that match's period, with a record for it, but not the record of record. Away from a match: the other models of the period in force, in order (`model_catalogue.role_of`), so a model has the same tag on every page. |
| `challenger`, `machine_learning`, `baseline`, `earlier_record` | The role `shadow_models.json` gives the model (`earlier_record` also for a model of an earlier period that the file doesn't list). The explorer takes these too. |
| `lab_best` | Backtest explorer only, for a model `shadow_models.json` doesn't list: the best candidate of a model-lab family on validation. |
| `other` | Anything else (for example when `shadow_models.json` is missing or half-written). |

## Records on the fixtures list and on a record's own page

`/api/fixtures` carries, per fixture, only the record of record and the record with the other
basis built with it (its model + market partner, or for a model + market record its pure
model's own; `routes.counterpart`), each the latest edition of its model, plus
`models_for_fixture`: how many models have a genuine pre-match record for the fixture
(market-only records are never counted or carried). Every other model's record is on the match
page's every-model list. With fourteen models a fixture, carrying them all made the 30-day
response 2.4 MB, the phone's first load.

Every card carries `model_label` (the display name). A record's own page
(`/api/predictions/{id}`) also carries `model_role` (its role on the fixture, as the every-model
list gives it; `model_compare.fixture_role`) and `record_of_record` (`model`, `label` and the id
of its latest genuine pre-match edition; null for a retrospective record), so its top says whose
forecast it is and links to the record of record.

## `GET /api/fixtures/{fixture_ref}/models`

A separate endpoint rather than a block on `/api/predictions/{id}`: it is per fixture, and a
fixture has one record per model, each with its own match page. The match page asks for it
with its `fixture_ref`, URL-encoded (`E0:2627:2026-10-24:eng-arsenal:eng-chelsea` →
`E0%3A2627%3A2026-10-24%3Aeng-arsenal%3Aeng-chelsea`). 404 when the fixture has no genuine
pre-match record.

One row per model with a genuine pre-match record: its first edition, later editions listed.
The record of record first, then the other listed models of record in their order, then by
role (challenger, machine learning, earlier model of record, baseline, other), then by
publication time.

```json
{
  "kind": "genuine_prematch",
  "explanation": "Every model's genuine pre-match record for this match: …",
  "fixture_ref": "SP1:2627:2026-10-21:esp-levante:esp-ath-bilbao",
  "competition": "SP1", "competition_name": "La Liga", "group": "domestic",
  "season": "2627", "match_date": "2026-10-21", "kickoff_utc": "2026-10-21T18:00:00+00:00",
  "home_team": "esp-levante", "home_name": "Levante", "home_media": {"crest_url": null, "badge": {…}},
  "away_team": "esp-ath-bilbao", "away_name": "Ath Bilbao", "away_media": {…},
  "record_of_record": "market_tilt_shots",
  "result": {"home_goals": 3, "away_goals": 1, "settled_at_utc": "2026-10-22T07:32:19.057066+00:00"},
  "roles": {"model_of_record": "Model of record", …},
  "models": [
    {
      "model": "market_tilt_shots", "label": "Shots model + market", "role": "model_of_record",
      "description": "…", "basis": "model_and_market", "is_record_of_record": true,
      "prediction_id": 156, "edition": "early", "model_version": "…",
      "published_at_utc": "2026-10-21T07:00:29.809004+00:00", "later_editions": [],
      "probabilities": {
        "1X2": {"H": 0.276, "D": 0.278, "A": 0.446},
        "OU_1.5": {"O": 0.746, "U": 0.254}, "OU_2.5": {…}, "OU_3.5": {…},
        "BTTS": {"Y": 0.545, "N": 0.455},
        "expected_goals": {"home": 1.18, "away": 1.46},
        "top_score": {"score": "1-1", "p": 0.134}
      },
      "expected_goals_before_prices": false,
      "settlement": {
        "markets": {"1X2": {"pick": "A", "actual": "H", "correct": false, "p_actual": 0.276}, …},
        "rps": 0.3613,
        "log_loss": {"1X2": 1.287, "OU_1.5": 0.293, "OU_2.5": 0.718, "OU_3.5": 1.286, "BTTS": 0.607}
      }
    },
    {"model": "congestion_context_on_elo", "role": "fallback_of_record", "prediction_id": 167, …},
    {"model": "dixon_coles", "role": "fallback_of_record", "prediction_id": 56, …},
    {"model": "market_tilt_dc", "role": "challenger", …}
  ]
}
```

(Levante v Athletic Club, 21 October 2026, the first settled league match; probabilities are
sent at full precision, trimmed here.) `settlement` is null until the match
is settled (and for a void match). `expected_goals_before_prices` is true only for a model +
market record whose expected goals equal its base model's own record for the fixture (the
record the match page compares with, `counterpart`): the prices then moved its score grid, not
these averages. Since 21 October 2026 (05:23 UTC) a model + market record takes its expected
goals from the tilted grid (`footy.modeling.lab.market_blend`), and with no base record to
compare nothing is claimed, so it is false for most (market_tilt_dc's 03:21 record for
Levante v Athletic Club is the exception: the same as dixon_coles's).

## `GET /api/model-leaderboard?group=domestic|uefa`

Every model with settled genuine pre-match records in the group, each match once per model
(its first edition), sorted by mean RPS (best first). Models still waiting for their first
result are under `waiting`. `record` is the headline record (each match's record of record,
as `/api/performance` counts it).

```json
{
  "kind": "genuine_prematch", "group": "domestic", "group_name": "League games",
  "explanation": "Genuine pre-match records only: …",
  "small_sample_below": 100,
  "models_of_record_now": ["market_tilt_shots", "congestion_context_on_elo", "dixon_coles"],
  "last_settled_at_utc": "2026-10-22T07:32:19.057066+00:00",
  "record": {"label": "Model of record", "published": 69, "settled": 1,
             "results_right": {"n": 1, "correct": 0, "hit_rate": 0.0, "ci95": [0.0, 0.793]},
             "mean_rps": 0.3613, "mean_log_loss": {"OU_2.5": 0.718, "BTTS": 0.607},
             "small_sample": true, "description": "…"},
  "models": [
    {"model": "dixon_coles", "label": "Dixon-Coles", "role": "fallback_of_record",
     "basis": "model_only", "description": "…",
     "published": 64, "settled": 1, "pending": 63, "void": 0, "records_of_record": 0,
     "results_right": {"n": 1, "correct": 1, "hit_rate": 1.0, "ci95": [0.207, 1.0]},
     "mean_rps": 0.2628, "mean_log_loss": {"OU_2.5": 0.765, "BTTS": 0.656},
     "small_sample": true,
     "vs_record": {"n": 1, "mean_rps_difference": -0.0985, "better": 1, "worse": 0,
                   "level": 0, "small_sample": true}}
  ],
  "waiting": [{"model": "ml_online_market", "label": "…", "role": "machine_learning", "published": 60, …}]
}
```

- `results_right`: how often the match result pick (home, draw or away, draws included) was
  right, with a 95% Wilson interval (`footy.modeling.hit_rates.wilson`).
- `mean_rps` (1X2) and `mean_log_loss` (O/U 2.5 and BTTS, from the probability the record gave
  to what happened): lower is better.
- `vs_record`: on the matches where both this model and the match's record of record have a
  settled record (not counting matches where this model *is* the record of record): how many,
  the mean of (this model's RPS − the record's), negative meaning this model did better, and
  on how many it was better, worse or level (differences under 1e-9 are level).
- `small_sample`: fewer than 100 settled matches. Say so next to the numbers.

## `GET /api/backtest-models`

The explorer's curated models (`backtest_explorer.CURATED`), with the splits each was run on,
and what each group's ledgers cover (`coverage`: division, season, split, matches). Runs refused
(a market-derived ledger, or a manifest naming another model) are in `skipped_runs`.

```json
{
  "kind": "backtest",
  "explanation": "Backtest, not a record. …",
  "splits": {"validation": "Validation 2023/24: …", "test": "Test 2024/25 and 2025/26: …"},
  "roles": {…},
  "columns": ["p_home", "p_draw", "p_away", "p_over_2_5", "p_btts_yes", "result_right", "rps"],
  "groups": [
    {"group": "domestic", "name": "League games, five top leagues",
     "divisions": [{"code": "E0", "name": "Premier League"}, …],
     "seasons": [{"season": "2324", "split": "validation"}, {"season": "2425", "split": "test"}, …],
     "coverage": [{"division": "E0", "season": "2324", "split": "validation", "matches": 380}, …],
     "skipped_runs": []},
    {"group": "uefa", …}
  ],
  "models": [
    {"model": "market_tilt_shots", "group": "domestic", "label": "Shots model + market",
     "role": "model_of_record", "family": "Model + market", "basis": "model_and_market",
     "per_match": false, "description": "…", "note": "Summary only: match by match, …",
     "splits": [
       {"split": "validation", "run_id": "market_tilt_shots__validation__2324",
        "seasons": ["2324"], "matches": 1752, "mean_rps": 0.1875, "results_right": 0.538},
       {"split": "test", "run_id": "market_tilt_shots__test__2425-2526",
        "seasons": ["2425", "2526"], "matches": 3503, "mean_rps": 0.1959, "results_right": 0.534}]}
  ]
}
```

### The curated models (22 October 2026)

League games (five top leagues pooled; validation 2023/24 for all, test 2024/25 + 2025/26 where
marked):

| Model | Role | Test run |
| --- | --- | --- |
| `market_tilt_shots` (summary only), `congestion_context_on_elo`, `dixon_coles` | Model of record, then its fallbacks | yes |
| `ml_online_market` (summary only) | Machine learning (with prices) | yes |
| `ml_stack_market`, `ml_online_pure`, `ml_stack_pure` | Machine learning | no |
| `market_tilt_ctx` (summary only) | Challenger, model + market | yes |
| `market_tilt_dc`, `market_tilt_elo`, `market_tilt_ens` (summary only) | Challengers, model + market | no |
| `elo_goals_gd`, `ensemble_split`, `dynamic_ratings_blend3`, `bivariate_dispersion_sx_birth_time`, `goal_calibration_tuned_level`, `shots_xg_sot_joint_rw`, `dc_tuning_tilt`, `ml_gbm_glm_boost_dc` | Best of each model-lab family | no |
| `baseline_frequency`, `baseline_poisson` | Baselines | yes |
| `baseline_elo` | Baseline (match result only: no goal lines, no BTTS) | no |

UEFA (all three competitions and qualifiers; the round-3 runs, same fixtures):

| Model | Role | Runs |
| --- | --- | --- |
| `pan_euro_mix_mxg` | Model of record | validation, test |
| `pan_euro_mix` | Earlier model of record | validation, test (the test run is saved under its lab name `euro_mix_0.7_0.3`, the configuration published as `pan_euro_mix`: `data/reports/uefa_selection.json`; the page says only that it is the same model) |
| `pan_euro_mix_mxg_all_ma0_w0.8` | Challenger (led validation, not test) | validation, test |
| `euro_frequency` | Baseline | validation, test |

The custom-season ledgers (2021/22–2022/23, used for tuning) are not shown: they are not
out-of-sample in the D-025 sense.

## `GET /api/backtest-matches?division=&season=&team=&models=&group=&limit=&offset=`

One row per match, in date order, with each chosen model's cell (`columns`): home, draw and
away probabilities, over 2.5 and both-teams-to-score probabilities (three decimal places), the
match result pick right (1/0) and the match's RPS (four decimal places). A model that did not
predict a match has `null`, and so does a summary-only model (`per_match: false` in `models`)
on every match; a match no chosen model predicted is left out. `summary` covers every match
the filters select (not just the page): matches, results right with a 95% Wilson interval,
mean RPS (from the full-precision ledger values), small-sample flag. `team_summaries` gives
each chosen summary-only model's `[matches, results right, mean RPS]` per team over the
selected matches, so the page can show one team's summary without any match's figures; a
team with fewer than 10 such matches is left out, and a summary-only model's `summary` over
fewer than 10 matches gives only `matches` (`SUMMARY_ONLY_MIN_MATCHES`).
`teams` gives each team on the page its name and media summary.

- `division`: E0, SP1, I1, D1, F1, or UCL, UEL, UECL, UCLQ, UELQ, UECLQ (it sets the group);
  without one, `group` (default `domestic`).
- `season`: 2324 (validation), 2425 or 2526 (test).
- `team`: a team key, home or away. `models`: comma-separated curated names of the group
  (default all, in the curated order). `limit` 1–1000 (default 200), `offset` from 0.
- 400 for an unknown division, season, group or model.

```json
{
  "kind": "backtest", "explanation": "Backtest, not a record. …", "group": "domestic",
  "filters": {"division": "E0", "season": "2324", "team": null, "models": null, "limit": 1000, "offset": 0},
  "total": 380, "count": 380, "offset": 0,
  "columns": ["p_home", "p_draw", "p_away", "p_over_2_5", "p_btts_yes", "result_right", "rps"],
  "models": [{"model": "market_tilt_shots", "label": "Shots model + market", "role": "model_of_record", "basis": "model_and_market"}, …],
  "summary": [{"model": "market_tilt_shots", "matches": 380, "results_right": 224, "hit_rate": 0.589,
               "ci95": [0.539, 0.638], "mean_rps": 0.1836, "small_sample": false}, …],
  "teams": {"eng-burnley": {"name": "Burnley", "media": {"crest_url": null, "badge": {…}}}, …},
  "matches": [
    {"date": "2023-08-11", "division": "E0", "season": "2324", "split": "validation",
     "home": "eng-burnley", "away": "eng-man-city", "home_name": "Burnley", "away_name": "Man City",
     "score": [0, 3],
     "p": [[0.105, 0.17, 0.725, 0.577, 0.467, 1, 0.0432], [0.126, 0.184, 0.69, 0.625, 0.538, 1, 0.0562], …]}
  ]
}
```

UEFA rows also carry `stage` as the sources write it (for example "League MD3"); the page
shows it in plain words ("Matchday 3", `stageLabel` in `frontend/src/format.ts`).

## What the frontend asks for

These exact requests are the ones the static snapshot holds (docs/snapshot.md); a page that
asks for anything else in snapshot mode gets "Not in this snapshot".

| Page | Request |
| --- | --- |
| Match page, "Every model" | `/api/fixtures/{encodeURIComponent(fixture_ref)}/models` |
| Leaderboard | `/api/model-leaderboard?group=domestic`, `/api/model-leaderboard?group=uefa` |
| Backtest explorer, models and choices | `/api/backtest-models` |
| Backtest explorer, one division and season | `/api/backtest-matches?division=<code>&season=<season>&limit=1000` for each `coverage` entry of `/api/backtest-models` |

The explorer filters by team and by model itself, from the division-season response (each
cell has `result_right` and `rps`, so a filtered summary is a count and a mean), rather than
asking for `team=` or `models=`.
