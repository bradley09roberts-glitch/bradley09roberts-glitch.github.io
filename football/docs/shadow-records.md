# Shadow records

_Written 22 October 2026. Code: `src/footy/shadows.py`, `src/footy/live.py`; list:
`src/footy/reference/shadow_models.json`; CLI: `uv run footy publish-shadows`._

The owner wants to see every model's own forecasts for real upcoming games, not only the model
of record's. A **shadow record** is a genuine pre-match record published under another
model's own name, by the same code and rules as the model of record's, so each model builds a
live track record that can be settled and compared like the backtests. Nothing about a shadow
record makes it the headline forecast: the model of record and its fallbacks stay as
`primary_models.json` says (D-026, D-027).

## Which models

`reference/shadow_models.json` lists them per group, each with a role and a one-line
description for the website (`footy.model_catalogue` reads the same file).

| Group | Role | Models |
| --- | --- | --- |
| League games | `machine_learning` (D-028) | `ml_online_market`, `ml_stack_market` (with prices), `ml_online_pure`, `ml_stack_pure` (without) |
| League games | `challenger` | the best of each model-lab family: `shots_xg_sot_joint_rw`, `elo_goals_gd`, `ensemble_split`, `dynamic_ratings_blend3`, `bivariate_dispersion_sx_birth_time`, `goal_calibration_tuned_level`, `ml_gbm_glm_boost_dc`, `dc_tuning_tilt`; the other model + market candidates of round 4: `market_tilt_ctx`, `market_tilt_dc`, `market_tilt_elo`, `market_tilt_ens` |
| League games | `baseline` | `baseline_poisson`, `baseline_frequency` |
| UEFA | `earlier_record` | `pan_euro_mix` (the UEFA model of record until 21 Oct 2026) |
| UEFA | `challenger` (D-026) | `pan_euro_mix_mxg_all_ma0`, `pan_euro_mix_mxg_all_ma0.25`, `pan_euro_mix_mxg_all_ma0_w0.8` |
| UEFA | `baseline` | `euro_frequency` |

Not listed, because they already publish through the models-of-record jobs:
`market_tilt_shots`, `congestion_context_on_elo` and `dixon_coles` (league games) and
`pan_euro_mix_mxg` (UEFA). The pan-European Dixon-Coles and Elo parts of the UEFA mixture are
not registered as models on their own, so they have no shadow records.

To add a model, append an entry (`model`, `role`, `description`, optionally `decision` for the
DECISIONS.md entry its notes cite, and `label` for the website). The name must be registered
(`footy.modeling.registry` for league games, `footy.modeling.euro_registry` for UEFA) and
must not be a model of record from now on; `publish-shadows` reports and skips one that is.

## Running it

```bash
uv run footy publish-shadows --group all --dry-run          # what each model would publish
uv run footy publish-shadows --group uefa --days 1          # today's and tomorrow's UEFA games
uv run footy publish-shadows --group domestic --days 4      # the league games of the next 4 days
uv run footy publish-shadows --models ml_online_pure,ml_stack_pure --days 4
```

Options: `--group domestic|uefa|all` (default all; UEFA runs first, it takes about a minute
and its games are usually the sooner ones), `--days N` (default 7 for league games and
3 for UEFA, as `predict-domestic` and `predict-euro`), `--divisions` (default
E0,SP1,I1,D1,F1,E1), `--comps` (default UCL,UEL,UECL), `--models a,b` (only these listed
models), `--dry-run`, `--json FILE` (every model's outcome, its records or dry-run forecasts,
and each skipped fixture with the reason).

Commit the code before a real run: each record's `model_version` names the git commit
(`code:<hash>`), not uncommitted changes.

It prints one line per model as soon as that model is done, then a total, and exits with 1 if
any model (or any league of a model) failed. The run is logged in `job_runs` (job `predict`, detail `kind: shadows`).

```text
uefa     pan_euro_mix                         earlier_record   fixtures  36  would publish   0  skipped  36      0.5s  (36 already recorded for this model)
uefa     pan_euro_mix_mxg_all_ma0             challenger       fixtures  36  would publish  36  skipped   0     48.0s
domestic ml_online_market                     machine_learning fixtures  60  would publish   0  skipped  60     97.4s  (60 no usable prices (a model + market forecast publishes only with prices))
domestic ml_online_pure                       machine_learning fixtures  60  would publish  60  skipped   0    170.7s
```

Run it after the models of record have published (`predict-domestic`, `predict-euro`), at the
same check-ins: a fixture without a kick-off time gets shadow records only once it has a
record of record (see below). It is safe to repeat: a fixture already recorded for a model is
skipped before the model is fitted, so a second run publishes only what is new.

## Rules (the same as the models of record)

Each model goes through `footy.live.predict_domestic` or `predict_euro`, the functions behind
`footy predict-domestic --model <name>` and `footy predict-euro --model <name>`, with only the
shadow's notes and the skips below added:

- **Decision time and history.** League models are fitted per league (with its second tier) on
  the results final before 00:00 UK today, from the backtests' three-year window
  (`walkforward.training_history`). UEFA models are fitted on every domestic and UEFA result
  before today.
- **Prices.** A model + market forecast (`market_tilt_*`, `ml_online_market`,
  `ml_stack_market`) uses football-data's pre-closing prices only under `MarketTilt`'s rules
  (quoted by the run, before kickoff, from a file published on the fixture's own collection
  day, D-012, D-024), and publishes **only** the fixtures with usable prices. A fixture
  without them is skipped ("no usable prices") and left for a later run once the prices are
  out (Friday for weekend games, Tuesday for midweek ones). A record keeps the consensus
  probabilities it used, never a price (D-013).
- **Before kickoff.** A fixture past its pre-match deadline (its kickoff, or 00:00 UK on its
  day when no kickoff time is known) is skipped. A live run reads the clock again once the
  forecasts are made, leaves out any fixture that kicked off while the model was fitting,
  and stamps each record with the time it is written.
- **Never twice.** A fixture this model has already recorded (same fixture, edition and record
  type) is skipped before fitting; `publish` would refuse a duplicate anyway. `publish` takes
  the database's write lock before it checks, so two runs at once (an overlapping check-in)
  still record a fixture once.
- **One failure doesn't stop the rest.** A model that raises is reported (`FAILED`, with the
  error) and the next model runs. League games run one league at a time, so a league that
  fails (a schedule that can't be read, say) costs only its own fixtures; the line shows what
  was published and `FAILED: <league>: <error>`. The command exits with 1 either way.
- **Killed part-way.** The container can restart at any moment. Each league's records are
  saved as one transaction, and `publish` writes a pending note
  (`records/predictions/.publish-pending-*.json`, git-ignored) before it appends the lines
  and deletes it after the commit. The next publishing command (`publish-shadows`,
  `predict-domestic`, `predict-euro`) first finishes any note it finds: rows already
  committed are left as they are; otherwise the lines are completed in the records file and
  the rows saved from them exactly as written. So the records file and the table always end
  up holding the same records, none twice, and a rerun publishes only what is still missing.

Each record's `notes` say what it is, e.g.
"Shadow record (machine-learning challenger, D-028); not the model of record." followed by the
model's description. The roles cite D-028 (machine learning), D-027 (challengers), D-025
(baselines) and D-026 (earlier models of record), unless an entry names its own decision.

## Why a shadow record can't become the record of record

- The record of record for a fixture is the first model, in the `primary_models.json` period
  that applies, with a genuine record for it (`RecordsOfRecord`). Shadow models are not in the
  current or any later period: `publish-shadows` refuses one that is, and never writes
  `primary_models.json`.
- The period that applies is decided by the earliest time among a fixture's genuine records:
  its kickoff, or 00:00 UK on its date for a record without a kickoff time. A shadow record
  without a kickoff time could therefore move a fixture into an earlier period. So a shadow
  record waits for the kickoff time ("kick-off time not known yet") unless the fixture
  already has a record without one, in which case it is dated from 00:00 UK already (the
  Spanish Sunday games, whose times openfootball doesn't have yet, are the usual case).
- What remains: a shadow record made with a kickoff that was later moved (brought forward
  before the model of record's record, or moved back after it) carries that time, as any
  genuine record does. It could change a fixture's period only if a model-of-record switch
  fell between the two kickoff times.

`tests/test_shadows.py` checks the duplicate guard, the kicked-off and late skips, the price
gating, failure isolation (per model and per league), the notes, the kickoff-time rule, and
that the record of record and `primary_models.json` are unchanged after shadow records are
published. `tests/test_publish.py` kills a publish at each step (before the lines, part-way
through them, before the commit, after it) and checks the rerun, and races two publishers.

## Run times (dry runs on the real data, 22 Oct 2026)

Fit and forecast, per model, for the dry runs of 22 Oct: UEFA, 36 Europa League and
Conference League games; league games, the 60 fixtures of 22–26 Oct in six divisions. Times
vary with what else the machine is doing: the same model has taken from half to twice these.

| League games | Seconds | | UEFA | Seconds |
| --- | ---: | --- | --- | ---: |
| `ml_online_market` | 97 | | `pan_euro_mix` | 0.5 (all recorded) |
| `ml_stack_market` | 143 | | `pan_euro_mix_mxg_all_ma0` | 48 |
| `ml_online_pure` | 171 (78 to 332) | | `pan_euro_mix_mxg_all_ma0.25` | 2 |
| `ml_stack_pure` | 144 | | `pan_euro_mix_mxg_all_ma0_w0.8` | 2 |
| `shots_xg_sot_joint_rw` | 1 | | `euro_frequency` | 0.5 |
| `elo_goals_gd` | 1 | | | |
| `ensemble_split` | 51 | | | |
| `dynamic_ratings_blend3` | 84 | | | |
| `bivariate_dispersion_sx_birth_time` | 261 | | | |
| `goal_calibration_tuned_level` | 15 | | | |
| `ml_gbm_glm_boost_dc` | 10 | | | |
| `dc_tuning_tilt` | 1 | | | |
| `market_tilt_ctx`, `_dc`, `_elo` | 1–4 | | | |
| `market_tilt_ens` | 107 | | | |
| `baseline_poisson`, `baseline_frequency` | 5, 1 | | | |
| **All league models** | **about 18 minutes** | | **All UEFA models** | **about 1 minute** |

- **Every model is well under ten minutes**, so nothing was cached or changed.
- **Why the machine-learning learners take longest.** They learn from a stream of their
  members' out-of-sample forecasts, which isn't kept between runs: each run rebuilds it by
  walking forward through the last year (two years for `ml_stack_*`) of the league's decision
  days, fitting every member at each. For the Premier League `ml_online_pure` makes 62
  decision batches × 4 members (about 74 s; most of it the Dixon-Coles, Elo-with-context and
  shots fits). `bivariate_dispersion_sx_birth_time` fits its score shape by maximum
  likelihood on top of the shots model; the first UEFA all-league model loads every league's
  price-implied expected goals once, which the next two reuse.
- **Possible savings, not needed now.** A Championship run repeats the Premier League's fit
  (same history and cutoff), about a sixth of each model's time. A model + market forecast is
  fitted before it finds out a fixture has no prices, so until Friday's prices are out about
  six minutes of each run go on forecasts that are not published.
- **Repeat runs are cheap.** A model with nothing new to forecast is not fitted, so after the
  first run of the week a run costs only the models with new fixtures (or, for the model +
  market ones, fixtures still waiting for prices).
