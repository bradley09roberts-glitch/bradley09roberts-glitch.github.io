# STATUS

_Last updated: 2026-10-22, 22:45 UK (after round A of the audit fixes). Every session starts
from this file; scheduled check-ins follow its "Check-in runbook"._

## Standing rules

- **Private repository (D-030).** The project lives in the private repository
  `bradley09roberts-glitch/football-model`, branch `main`. In this working copy `origin` is that
  repository and `public` is the FightBase site: never push football work to `public`.

- **Records are immutable.** Genuine pre-match records are append-only: never edit or delete
  a line of `records/predictions/*.jsonl` or a `predictions` row. A correction is a new record.
- **Prices stay private.** Never show, commit or quote a football-data price (D-013); records
  keep only the de-vigged consensus probabilities (D-024).
- **No UEFA.com.** Never fetch uefa.com or use its content (D-023).
- **Wikimedia refuses this machine** (HTTP 403 since 18:49 UTC on 21 Oct): at most one try per
  check-in, never a loop.
- **Back up before migrating.** Copy `data/footy.sqlite3*` before `uv run footy migrate`.
  `fd-update`, `euro-update` and `serve` still migrate on their own (audit M5, open), so start
  every check-in with `footy doctor` (runbook).
- **Commit only what a check-in writes** (records files, a `comparisons/results_*.json` file,
  STATUS.md): other work can be uncommitted in the tree.
- **No betting advice in chat (audit D4).** No picks, no "value" or "fair odds" thresholds,
  no urgency ("now or never"). If a bet comes up, cite the market benchmark: over the two test
  seasons no model here beat the closing price. The league model of record is the bookmakers'
  consensus for the match result and over/under 2.5 (1X2 RPS +0.0006 against the closing
  price, O/U 2.5 log loss +0.0035, significantly worse); the best pure model is further off
  (+0.0048 and +0.0120); the UEFA model has no price input and no price benchmark. Forecasts
  may be shown as information, never as a recommendation. If staking comes up, mention the
  safer-gambling tools: deposit limits at the bookmaker, GamStop (self-exclusion), GamCare and
  the National Gambling Helpline (0808 8020 133).

## Where things stand

On 21 Oct the owner asked for as much as possible overnight, with the Champions League first
and "whatever has the best accuracy". M2 to M6 were built in that push and M7 is running;
none of it has had the owner's review yet.

| Milestone | State |
| --- | --- |
| M0 Setup | Done, reviewed |
| M1 Data | Done; extended on 21 Oct (European leagues, repeat meetings, prices for 26 divisions) |
| M2 Baselines | Done: walk-forward harness, ledgers, leakage tests, frequency/Elo/Poisson baselines |
| M3 Score model | Done: Dixon-Coles, then a model lab of 8 families (finished 21 Oct; see "Model lab") |
| M4 Five leagues | Done: every model is run and selected on the five leagues pooled |
| M5 Profit simulation | **Partly done** (corrected 22 Oct, audit M20): `footy final-report` ran it only on the model + market model of record, whose figures are the bookmakers' prices, so it found nothing to bet (0 validation bets in all 72 threshold strategies, 1 test bet, withheld; `frozen_on_validation` is empty). Pending: a pure-model run (`congestion_context_on_elo`, `dixon_coles`) frozen on validation with its closing-line value, a confusion matrix, and the equity curve on the site; choose the publication policy first (known problem 5) |
| M6 Website core | Done: fixtures, match, past, performance, lab, data, status pages; QA in `docs/m6/qa.md` |
| M7 Live operations | Running on one-off check-ins that end on Tue 27 Oct (see "Scheduled check-ins") |

On 21–22 Oct the owner asked for machine learning, detailed stats for every team in the league
and in Europe (and cups later), a less plain design and pictures for teams. All four are built
(see "Website" and "Machine learning" below); none of it has had the owner's review yet.

## Audit of 22 Oct and round A of the fixes

The audit ([docs/audit/2026-10-22-audit.md](docs/audit/2026-10-22-audit.md)) found no leakage:
all 1,089 records were made before kick-off and replay bit for bit, and the headline numbers
recompute exactly. Its findings are about operations and how results are described. Round A
(22 Oct evening) fixed these, each tested on copies of the database; no migration was added,
and nothing was written to the real database or the records:

- **La Liga placeholder dates (M1).** openfootball lists all ten matchday 10 games on Sun 25
  Oct with no times. In leagues where a pairing meets once a season:
  - the latest stored `fixtures.csv` gives a fixture's date and kick-off (a firm, earlier
    kick-off in the schedule stands), and prices join by pairing within the season, so
    Friday's file prices these games;
  - a fixture is the pairing in the season, whatever its date: a re-dated game is never
    recorded twice, and a new model's record joins the first record's `fixture_ref` but keeps
    the real date and kick-off; settlement already ignored the date;
  - a fixture without a kick-off time closes at 00:00 UK on the first day of its round. For
    the 25 Oct placeholders that was 23:00 UTC on 22 Oct: from then on they get new records
    only once `fixtures.csv` dates them, so a game missing from Friday's file, or listed
    without a time, gets none (all 14 pure models already have theirs);
  - `settle` lists any record made after kick-off (`made_after_kickoff`; none so far).
- **Rebuilding the database (H4).** New `uv run footy import-records [--dir]` loads every
  records line (sha256 checked first, ids kept), then settles. Publishing refuses while the
  records files hold an id or `fixture_ref` the database lacks. Checked: all 1,089 lines verify,
  and a fresh database rebuilt from them matches the current rows (creation times to the
  second, as the lines store them).
- **Duplicate settlements (L8).** `settle` takes the database's write lock (SQLite only), and
  the readers take the latest settlement. The unique indexes need a migration (not added).
- **UEFA loader (H5, M14).** An unreadable Wikipedia box, a missing page or a failed rebuild
  makes the job `partial` and deletes nothing, also when the CSV is loaded later without a
  rebuild. A row with a result is deleted only with a `data_corrections` entry. Walkovers
  and awarded results are stored as `awarded` (kept out of the models), abandoned or
  postponed matches as `postponed`, without a score. Extra time is inferred from goal minutes,
  penalties or the summary's a.e.t.; a tie whose legs contradict that is held with no score.
  The integrity checks run at the end of every `euro-update`.
- **Wikipedia failures and a second results path (M3).** Every failed request is logged with
  its status and the start of its body; the fetch stops at the first 403 or 429; the job is
  `partial` and `euro-update` exits 1. Jobs still "running" after 6 hours are marked
  `interrupted` (job 280 at the next `euro-update`). New `uv run footy euro-import-results
  FILE [--dry-run] [--correct]` enters UEFA results from news reports, two independent
  publishers each (D-029). On a copy, the 21 Oct file settled all 18 Champions League records.
- **UEFA.com (M16).** Nothing reads UEFA.com any more. Union Saint-Gilloise v Hapoel Be'er
  Sheva's Düsseldorf venue now comes from German Wikipedia
  (`src/footy/europe/venue_overrides.csv`); no model input changed.
- **Website (H2, H3, M2, M3, L15, L16).** See "Website".
- **Documents.** STATUS, README, `docs/` and dated corrections appended to DECISIONS.md
  (audit section 5, L1, L2, L4, L6, L9, L19, L21, M19, M20, M22).

**Left over from round A:**

- the unique indexes on settlements and genuine predictions (a migration), and a write lock
  for PostgreSQL;
- `supersedes_id` and `reason` are not written to the records lines (none of the 1,089 has
  them), so an import can't restore them;
- `made_after_kickoff` is reported by `settle`, not stored; the site doesn't show the
  `recorded_match_date` / `recorded_kickoff_utc` fields; a record without a kick-off still
  uses 00:00 UK on its own date to pick the model-of-record period;
- the data fingerprint ignores manual UEFA results and the venue override (add `"manual"` to
  `DATA_SOURCES`, `src/footy/modeling/ledger.py:23`); team pages show the raw word "manual"
  as a source (`SOURCE_NAME` in `frontend/src/components/TeamStats.tsx`);
- no stale-results guard in `predict_euro`; a settlement made from a manual score is not
  corrected if Wikipedia later disagrees (M7; the disagreement is flagged as an error); only
  ties found inconsistent while parsing are held from settlement;
- `euro-update --fetch-uefa` still exists (disabled: it fails the job);
- the phone copy still reaches the host's 64 MB around early December (H3; see "Website");
- the Data page's failing notice keeps a one-off research fetch ("Other web pages
  (research)", one HTTP 500) for good;
- the match page's "In brief" still says model + market expected goals are "before the
  bookmakers' prices were blended in" (M12);
- code comments and site text that this documents round left alone (no code changes): see
  the end of "Known problems".

Still open from the audit: H1 (recurring check-ins, to be scheduled from the runbook below),
M4 (the repository is public: an owner decision), M5, M6 (no off-site copy of `data/`), M7 to
M13, M15, M17, M18, M21, M22, most Low items, and section 3's design points.

## Models of record (D-025, D-026, D-027)

`src/footy/reference/primary_models.json` holds them as periods with start times.

**How they were chosen** (corrected 22 Oct, audit L1). Each was the lowest D-025 composite on
validation 2023/24 among the candidates of its round, and was then run on test 2024/25 +
2025/26. Later candidates scored lower on validation (`market_tilt_shots` is 6th of the 7 price
candidates: `market_tilt_ctx` 0.94412, `_elo` 0.94434, `_ens` 0.94436, `ml_online_market`
0.94455, `ml_stack_market` 0.94484, `market_tilt_shots` 0.94488, `market_tilt_dc` 0.94516).
None displaced it, because under D-027 a challenger must also be at least as good on test,
and the two that were tested weren't. So the test seasons were consulted several times
(domestic test runs at 06:24 and 10:05 UTC on 21 Oct and 00:18 on 22 Oct; UEFA at 01:58,
04:17 and 05:43 on 21 Oct), and the test figures below helped choose: they are not untouched
estimates.

| Group | Model of record (in order) | Test composite (used for selection) | Against |
| --- | --- | --- | --- |
| League games, five top leagues and the Championship | `market_tilt_shots` where the fixture has prices, else `congestion_context_on_elo`, else `dixon_coles` (from 21 Oct, 10:52 UTC) | 0.9555 / 0.9661 | `dixon_coles` 0.9693 |
| UEFA competitions | `pan_euro_mix_mxg` (from 21 Oct, 04:30 UTC) | 0.9666 | earlier `pan_euro_mix` 0.9686 |

- **`market_tilt_shots`** moves the shots model's score grid fully onto football-data's
  pre-closing Bet365 and market-average prices (D-024,
  [docs/research/market-blend.md](docs/research/market-blend.md)). Its match-result and over/under
  2.5 figures are therefore the bookmakers' de-vigged consensus (equal to within 4.4e-13 on
  every test fixture); the model shapes only O/U 1.5, O/U 3.5 and BTTS. It beats each of the
  three pure models that were tested (`congestion_context_on_elo`, `dixon_coles`,
  `baseline_poisson`) in all ten league-season cells. It does not beat the closing price: 1X2
  RPS +0.0006 (95% CI −0.0002 to +0.0013), O/U 2.5 log loss +0.0035 (+0.0011 to +0.0058).
  Its 53.4% of results right is what backing the bookmakers' favourite gives (53.5% on the
  same games). Report: [docs/results/domestic.md](docs/results/domestic.md) (21 Oct, before
  round 4). A blend on the lab's best base (`market_tilt_ctx`) won validation but not test, so
  it was not adopted; the two differ only in the shape markets, by noise (+0.00045, −0.00014
  to +0.00104).
- **`congestion_context_on_elo`** is an Elo model on goals and shots on target, with red
  cards, fixture congestion and context effects (corrected 22 Oct, audit L19: not "goal-based
  Elo"). It is the pure fallback for fixtures without prices. Its base `elo_goals_gd` was tuned
  on 2022/23 and 2023/24, so its validation margin is flattering. On test it beats Dixon-Coles
  pooled within every league and within each season (barely in Ligue 1: 0.97478 against
  0.97489): RPS −0.0019 (−0.0029 to −0.0008), significant; composite −0.0032 (−0.0068 to
  +0.0003), not. League by season its composite is worse in 3 of the 10 cells (D1, F1 and SP1
  2024/25). Until mid-season its context features are inactive. Its 12 Championship (E1)
  records of record are outside the tested scope: E1 has no validation or test ledger (L5).
- **`pan_euro_mix_mxg`** is the pan-European Dixon-Coles + Elo mixture, with top-five domestic
  matches fitted partly to the expected goals their closing prices implied. Its market-xG
  scope is pinned (`top5_exact`), so it reproduces its published records exactly. It was
  picked from about 30 configurations over six rounds on the same 364 validation fixtures,
  and the first round's search used 2023/24 (corrected 22 Oct, audit L2: D-027 said nine). It
  has no price input and no closing-price benchmark, and it under-predicts Champions League
  goals (M13).

## Published predictions

From the records files (`records/predictions/`), 22 Oct 22:40 UK: **1,089 genuine pre-match
records**, 225 in `2026-10-21.jsonl` and 864 in `2026-10-22.jsonl`; the database holds the same.

| Model | Role | Records | Fixtures |
| --- | --- | --- | --- |
| `pan_euro_mix_mxg` | UEFA model of record | 45 | UCL 21 Oct (9), UEL and UECL 22 Oct (36) |
| `pan_euro_mix` | earlier UEFA model of record | 45 | the same fixtures, published at 01:58 UTC on 21 Oct, before the repeat-meetings fix (known problem 9) |
| `pan_euro_mix_mxg_all_ma0`, `_ma0.25`, `_ma0_w0.8`, `euro_frequency` | UEFA shadows | 36 each | UEL and UECL 22 Oct |
| `congestion_context_on_elo` | fallback of record | 69 | league games 21–28 Oct (E0 10, SP1 11, I1 18, D1 9, F1 9, E1 12) |
| `dixon_coles` | reference, last-resort fallback | 64 | the same games except the five I1 games of 28 Oct (fitted on all history, known problem 9; none since, audit L6) |
| `market_tilt_shots`, `market_tilt_dc` | model + market | 1 each | Levante v Athletic Club, 21 Oct |
| 12 league shadows without prices (`docs/shadow-records.md`) | shadows | 60 each | league games 23–25 Oct (E0 10, SP1 10, I1 10, D1 9, F1 9, E1 12) |

140 of them (14 models × the ten La Liga matchday 10 games) carry the placeholder date 25 Oct
and no kick-off (M1, fixed: they settle against the real match).

**Settled: 4 records**, Levante 3-1 Athletic Club (football-data, 22 Oct 07:32 UTC; matches
the news-sourced score). **Waiting:** the nine Champions League games of 21 Oct (18 records)
and the 36 Europa League and Conference League games of 22 Oct (216 records), because
Wikipedia refuses this machine. The Champions League scores are in
`comparisons/results_2026-10-21.json` (three to five publishers each) and can go in now with
`euro-import-results` (runbook, "UEFA nights").

**The ChatGPT comparison** (`comparisons/scored_2026-10-21.json`, `comparisons/score.py`): the
owner's ChatGPT app got 7 of 10 results right against our 5, RPS 0.209 against 0.240. That is
not like for like: its forecasts arrived at about 19:55 UK, after three of the games had
kicked off (17:45 and 19:00 UK). On the seven games that kicked off later it is 5 of 7 each,
with mean RPS 0.169 for ChatGPT and 0.189 for us; over all ten our BTTS log loss was better
(0.681 against 0.699). Ten games say nothing yet (about 290 would be needed to detect 0.01 RPS).

## Scheduled check-ins

All one-off tasks, firing into this session (22 Oct 22:45 UK). UK clocks go back on Sun 25
Oct; from then on UK time is UTC.

| When (UTC) | What |
| --- | --- |
| Thu 22 Oct 22:20 | Europa League and Conference League results; manual import if Wikipedia refuses (`trig_01PcdHKRQ1uKy55Tnn2yy1G7`) |
| Fri 23 Oct 09:00 | Commit round A of the audit fixes; replace the one-off check-ins with recurring ones pointing at the runbook (`trig_012qxAV4WunYDArsyC43E2ts`) |
| Fri 23 Oct 12:20 | Weekend prices and records (`trig_01FdMDVnBavFFhfa5ENak5Ee`); its prompt aims at 19:00 UTC, but the first kick-off is 18:30 UTC (Stuttgart v Gladbach; L10) |
| Mon 26 Oct 08:10 | Settle the weekend's league games (`trig_01XVY6ofksYqHeaeNdMcVre2`) |
| Tue 27 Oct 11:50 | Midweek prices and records (`trig_01JWasTHY8MisPAX1uSWEhBX`) |

**Last scheduled check-in: Tue 27 Oct 11:50 UTC. Nothing covers the 27–30 Oct settlements,
the 31 Oct – 2 Nov weekend or Champions League matchday 4 (3–4 Nov)** (audit H1). Keep this
table current.

## Check-in runbook

The exact commands for each kind of check-in, in order. They mirror the server kit's jobs
(`deploy/jobs/*.sh`, [docs/server.md](docs/server.md)); once the server runs, this container
stops publishing (records must never be published from two machines). Every publishing
command is idempotent: a fixture a model has already recorded is skipped, so a rerun publishes
only what is new. A command that fails is reported in the summary, not retried in a loop.
League divisions: `E0,SP1,I1,D1,F1,E1`.

### Every check-in: start

```bash
cd /home/user/bradley09roberts-glitch.github.io/football
git status --short          # others' work may be uncommitted: commit only your own files
uv run footy doctor --quiet # read-only health check; its server-timer lines don't apply here
pgrep -fa "^/bin/bash data/logs/capture_loop" || (nohup data/logs/capture_loop.sh > /dev/null 2>&1 &)
```

- If the doctor says the schema is behind: back up, then migrate, and say so in the summary:
  `B=data/backups/$(date -u +%Y%m%dT%H%MZ); mkdir -p "$B" && cp data/footy.sqlite3* "$B"/ && uv run footy migrate`.
- If a publishing command refuses because the records files hold records the database lacks:
  `uv run footy import-records` (hashes checked, ids kept, then settles), then carry on.

### Every check-in: end

1. Commit and push at once what the check-in wrote: `git add records/predictions` (plus any
   `comparisons/results_<date>.json`), `git commit`, `git push origin main` (the private
   repository, D-030; never push football work to the `public` remote).
2. Refresh the phone copy ("Publishing the phone copy") when anything was published or settled.
3. Update "Published predictions" and "Scheduled check-ins" above.
4. Check that a check-in is scheduled at least 7 days ahead; if not, say so first.
5. Summary: what was published and settled, and the record of record so far. No betting
   advice (standing rules).

### UEFA nights (Tue, Wed and Thu after European games, 23:30 UK)

1. `uv run footy euro-update --fetch-wikipedia --no-rebuild`: one try. It stops at the first
   refusal, loads the stored pages, and exits 1 if a fetch failed.
2. If it exited 0 (no fetch `error` or `skipped`): `uv run footy euro-update` (rebuild from the
   fresh pages), then go to step 5.
3. Otherwise enter the night's results by hand (D-029,
   [docs/europe/uefa-matches.md](docs/europe/uefa-matches.md)). For each game with published
   records (the doctor lists them under "settlements"), read the final score after 90 minutes
   plus stoppage time in at least two independent publishers' reports (different names and
   web domains; never UEFA.com). Write them to `comparisons/results_<date>.json` in the format
   of `comparisons/results_2026-10-21.json` (`fixture_ref`, `home_goals`, `away_goals`,
   `sources` with `publisher` and `url`).
4. `uv run footy euro-import-results comparisons/results_<date>.json --dry-run`. Every UEFA
   entry should be `applied` (league games are `skipped`); fix or drop a refused entry. Then
   run it again without `--dry-run`. Only games that kicked off at least two hours earlier are
   accepted. Use `--correct` only after checking both scores.
5. `uv run footy settle`, and check the count against the records waiting.
6. End.

**Done 22 Oct, 23:40 UK:** Wikipedia refused again (HTTP 403). `comparisons/results_2026-10-21.json`
(9 UCL games) and `comparisons/results_2026-10-22.json` (36 UEL/UECL games, three to five
independent publishers each) were imported with `euro-import-results` and settled: 18 + 216
records. Every UEFA record published so far is settled.

### Monday settle (08:10 UK)

1. `uv run footy fd-update` (every division: the weekend's results).
2. Refresh the league schedules:
   `for d in /home/user/openfootball/*/; do git -C "$d" pull --ff-only --quiet || echo "pull failed: $d"; done`
3. UEFA results as on a UEFA night, steps 1 to 4 (anything still waiting).
4. `uv run footy settle`
5. Report the record of record per market (`uv run footy doctor --summary`, and the Performance
   page of the refreshed phone copy).
6. End. On Mondays this can be one check-in with the daily run (daily steps, then step 3).

### Tuesday and Friday prices (12:40 UK; again at 14:20 and 15:20 UK if the file isn't in)

N is 3 on Tuesday (the midweek games to Friday) and 4 on Friday (the weekend to Tuesday).

1. `uv run footy fd-fixtures` (at 14:20 and 15:20: `uv run footy fd-fixtures --force`, because
   the 6-hour refresh limit counts from the first download, L11). Go on only if its
   `published_at` is today (UK). If not, the next try is at 14:20, then 15:20 UK; after 15:20
   stop: every fixture keeps its fallback record.
2. `uv run footy settle`
3. `uv run footy fd-update --scope core`
4. Refresh the league schedules (Monday step 2).
5. Fallback records for fixtures without one:
   `uv run footy predict-domestic --model congestion_context_on_elo --divisions E0,SP1,I1,D1,F1,E1 --days N`
6. The reference model's own records (audit L6; `publish-shadows` won't publish a model of
   record): `uv run footy predict-domestic --model dixon_coles --divisions E0,SP1,I1,D1,F1,E1 --days N`
7. Dry run: `uv run footy predict-domestic --model market_tilt_shots --divisions E0,SP1,I1,D1,F1,E1 --days N --dry-run`.
   Most fixtures should show `market_used: true`; look into any that don't (team names, a
   date the file and the schedule disagree on, refused prices). Note the earliest kick-off.
8. Before that kick-off (Fri 23 Oct: 18:30 UTC, 19:30 UK):
   `uv run footy predict-domestic --model market_tilt_shots --divisions E0,SP1,I1,D1,F1,E1 --days N`
   (it publishes only fixtures with usable prices). Commit and push the records at once.
9. Shadow models: `uv run footy publish-shadows --group domestic --days N` (20 to 30
   minutes; the six price models publish their priced fixtures, the rest only what is new). If
   it exits 1 or is cut short, run it once more. Commit and push.
10. `uv run footy settle`
11. End.

On Fri 23 Oct, La Liga matchday 10 takes its dates and times from that day's `fixtures.csv`
(M1): check that the dry run lists all ten SP1 games with a kick-off and `market_used: true`.
The dry run names each re-dated game's reference with its new date, but the records keep the
25 Oct `fixture_ref` of the game's first record (expected; the real date and kick-off are
saved in the record). A game missing from the file, or listed there without a time, gets no
new records.

### Daily fallback publishing (every day, 08:00 UK)

1. `uv run footy fd-update`
2. Refresh the league schedules (Monday step 2).
3. `uv run footy settle`
4. `uv run footy predict-domestic --model congestion_context_on_elo --divisions E0,SP1,I1,D1,F1,E1 --days 7`
5. `uv run footy predict-domestic --model dixon_coles --divisions E0,SP1,I1,D1,F1,E1 --days 7`
6. `uv run footy predict-euro --model pan_euro_mix_mxg --days 3`
7. Commit and push the records (the records of record go out before the long shadow run).
8. The shadow models without prices (the price models wait for Tuesday and Friday):

   ```bash
   uv run footy publish-shadows --group all --models ml_online_pure,ml_stack_pure,shots_xg_sot_joint_rw,elo_goals_gd,ensemble_split,dynamic_ratings_blend3,bivariate_dispersion_sx_birth_time,goal_calibration_tuned_level,ml_gbm_glm_boost_dc,dc_tuning_tilt,baseline_poisson,baseline_frequency,pan_euro_mix,pan_euro_mix_mxg_all_ma0,pan_euro_mix_mxg_all_ma0.25,pan_euro_mix_mxg_all_ma0_w0.8,euro_frequency
   ```

   The list is `SHADOWS_PURE` from `uv run python deploy/jobs/plan.py` (read it again after
   `shadow_models.json` or `primary_models.json` changes). 20 to 30 minutes; run it once more
   if it exits 1 or is cut short.
9. Commit and push.
10. End.

## Machine learning (D-028, proposed: no switch)

Four learners that learn from the other models' out-of-sample track record and keep learning
after every settled match (`ml_online_*`: Hedge mixture weights over score grids; `ml_stack_*`:
ridge logistic meta-models tilted back onto one grid). Their own settings were tuned on
2021/22–2022/23 only, but some members' settings were not: `congestion_context_on_elo`'s base
was tuned on 2022/23 and 2023/24, and the GLM booster's builder also ran about 47
configurations on 2023/24, so validation flatters learners that trust them (test is clean).
`ml_online_market` beat `market_tilt_shots` on validation (0.94455 against 0.94488) but not on
its one test run (0.95574 against 0.95546), so nothing switched; the pure learners lost
validation to `congestion_context_on_elo`. Audited: no leakage in any reported run. Live, a
learner rebuilds its state at each run, so its records differ by 0.01 to 0.02 from the
procedure that was validated (M9, open). Details:
[docs/research/machine-learning.md](docs/research/machine-learning.md); the Lab's "Machine
learning" tab shows the learning curves.

## Website (22 Oct)

- **Redesign:** matchday-programme look (chalk light theme, floodlit dark theme), team badges,
  pitch heroes, score heat map, bottom tab bar on phones.
- **Teams:** `#/teams` (every club with a match this season) and `#/team/<key>`: record, form,
  per-match stats for and against, half-time turnarounds, home/away, league / Europe / cups
  splits, a descriptive all-competition Elo, upcoming fixtures with our predictions, and the
  full match log with every stat (`src/footy/teams.py`, [docs/teams.md](docs/teams.md);
  CLI `uv run footy team-stats <team> [--csv OUT]`). UEFA matches carry scores only (the
  permitted sources have no shots or cards); cups appear once a cup source is added.
- **Match page:** "Form and stats" (both sides' form, averages, head-to-head, match stats).
- **Team pictures** ([docs/research/team-media.md](docs/research/team-media.md)): 705 of 717
  recent clubs mapped to Wikidata, which names a crest file for 207 of them and a venue photo
  for 624. Licences are checked only at download (CC0, public domain, CC BY and CC BY-SA are
  kept). **Nothing is downloaded yet** (Wikimedia refuses this machine), so every club shows a
  badge drawn from its Wikidata colours. Run `uv run footy team-media --download` once a
  `--probe` answers `"ok": true`.
- **Every model's predictions** ([docs/model-comparison.md](docs/model-comparison.md)).
  Genuine pre-match records and backtests are never mixed, and every backtest view says
  "Backtest":
  - **Match page, "Every model":** each model's own genuine pre-match record for the fixture,
    ticks or crosses and the RPS once settled. Where a model + market model's base saved no
    record, the page names the match's pure record of record as "Model alone" (L16).
  - **Fixtures:** each card shows the record of record (and its model + market partner) and
    "also predicted by N other models · Compare every model".
  - **Past (H2):** one card per match by default, the first edition of its record of record
    (the rule `/api/performance` uses, so the tallies agree); an "All models" switch lists
    every model's card, the counted one first. Market-only records are never listed.
  - **Performance, "Model leaderboard (live)":** every model with settled records, ranked by
    mean RPS. It ranks models on different sets of matches and on very few games (M11, open).
  - **Lab, "Explorer" tab:** the saved walk-forward backtests match by match. The six
    price-identical model + market models (`market_tilt_shots`, `_ctx`, `_dc`, `_elo`, `_ens`,
    `ml_online_market`) are **summary only**: match by match their match-result and over 2.5
    figures are the bookmakers' consensus. The Lab's ledger download now refuses them too (L15).
  - **Honest labels (M2):** where a record's match result or over/under 2.5 equals the saved
    consensus, the site says "Bookmakers' consensus (margins removed)" with a "Bookies'
    favourite" tag, and adds a "Model alone" line. Hit rates has an "Against the bookmakers"
    table (model 53.4% against the favourite's 53.5%). Fixtures and Performance say "Over two
    test seasons no model beat the closing price."
  - The model + market record's xG box claims "pure model's expected goals" only where they
    equal the base model's own record; the "In brief" text still claims it (M12, open).
- **Data page (M3):** "Downloads by source", with a "Downloads failing" notice (on 22 Oct:
  Wikipedia, 49 failures since 21 Oct, HTTP 403). "No failed or partial runs" shows only when
  every source's latest download worked.
- **Snapshot** ([docs/snapshot.md](docs/snapshot.md)): format `footy-api-snapshot/4`, a page
  plus JSON shards. Every model's match pages are kept only for fixtures from 10 days back
  (`FULL_PAGE_DAYS`); older fixtures keep their record-of-record pages. On a copy on 22 Oct
  (21:13 UK): 40.46 MB in 54 files. The export warns (`WARNING:` lines) when the site nears
  the host's limits. With settled records the projection reaches 64 MB around early December:
  shortening the window, trimming the team pages (22.6 MB) or publishing in batches is still to
  decide (H3).

## Publishing the phone copy

The owner's phone copy is the private artifact https://claude.ai/artifact/FZeCvYx9mG2fDmrhPZ2Gyf.
With P = /tmp/claude-0/-home-user/2e2a3e27-112f-5b7b-9aa0-b88dd615f6f7/scratchpad/app_snapshot:

1. `make snapshot OUT=$P/site PAGE=$P/artifact_new/football-model.html`. Read any `WARNING:`
   line it prints (size or file count near the host's limits) and report it.
2. Publish with the Artifact tool: `file_path` = `$P/artifact_new/football-model.html`, `url` = the
   artifact, `root` = `$P/artifact_new`, `files` = every other file in that folder (`ls` it:
   snapshot.json and every snapshot-*.json, plus media/ if pictures were downloaded). Files
   left out keep their old contents, so always pass every shard.

The second owner page, the Overnight report (https://claude.ai/artifact/9DaBxi1B6NCC4mpsxJg2UJ),
is stale and never says so (M21): retire it or rebuild it, and never republish it from the
local `index.html`, which is older than its live version 3.

## Data (21 Oct 2026)

- 64,693 matches in 26 divisions (the core ten plus 16 European leagues, D-022), 2018/19 to
  date; 181 excluded from models (play-offs, bare and awarded results).
- 1,473,193 prices: closing 1X2 for every division, O/U 2.5 where football-data has it,
  pre-closing prices for the main-layout files.
- 4,537 UEFA matches in `euro_matches` (4,234 played, 297 scheduled, 4 awarded, 2 cancelled),
  from openfootball and Wikipedia (D-023), and from 22 Oct results entered by hand from news
  reports (source `manual`, D-029; none in the real database yet). UEFA.com is not read (terms
  §6.2); until 22 Oct its stored pages set one neutral-venue flag, now taken from German
  Wikipedia (M16).
- Repeat meetings in multi-round leagues are stored since migration 0005 (4,815 recovered).
- League schedules: openfootball clones in `/home/user/openfootball` (outside the repository;
  CC0), refreshed by the check-ins.
- Coverage report: [docs/coverage/coverage.md](docs/coverage/coverage.md). Its European
  section still describes prices as top-five only (to regenerate).

## Model lab (finished 21 Oct; every family audited, no data leakage found)

| Family | Best candidate | Validation composite | Tuned on 2023/24 too? |
| --- | --- | --- | --- |
| Congestion and context | `congestion_context_on_elo` | 0.9517 (test 0.9661) | Yes, through its base `elo_goals_gd` |
| Elo on goals and shots on target | `elo_goals_gd` | 0.9528 | Yes: search on 2022/23 + 2023/24 |
| Ensembles | `ensemble_split` | 0.9529 | Partly: weights on 2022/23, three members tuned on 2023/24 |
| Dynamic ratings | `dynamic_ratings_blend3` | 0.9540 | Yes: search on 2021/22 to 2023/24 |
| Bivariate / dispersion | `bivariate_dispersion_sx_birth_time` | 0.9544 | Yes: the family's search was scored on 2023/24 as well |
| Goal calibration | `goal_calibration_tuned_level` | 0.9548 | Through its base `dc_tuning_tilt`; its own settings on 2021/22–2022/23 |
| Shots as xG | `shots_xg_sot_joint_rw` | 0.9552 | No (2022/23) |
| Dixon-Coles tuning | `dc_tuning_tilt` | 0.9560 | Yes: about 90 configurations on 2022/23 + 2023/24 |
| Gradient boosting | `ml_gbm_glm_boost_dc` | 0.9564 | Partly: about 47 configurations also run on 2023/24 |
| Dixon-Coles (reference) | `dixon_coles` | 0.9598 (test 0.9693) | No tuning |

Corrected 22 Oct (audit M19): the lab brief allowed tuning on validation, and D-025's "seasons
before 2023/24 only" was written (03:43 UTC, 21 Oct) after most of this tuning, so this is a
disclosure gap, not a breach, and not data leakage (no forecast saw a later result). But the
validation composites of the affected rows are flattered by selection: Dixon-Coles tuning's
gain over Dixon-Coles is −0.0038 on 2023/24 and −0.0016 to −0.0029 on the unused 2021/22,
enough to reorder rows about 0.001 apart. 59 models have validation ledgers on the same 1,752
fixtures, plus the search runs behind them, so the best validation scores are optimistic; the
test runs are the evidence (D-027). The models of record's own settings are unaffected
(`market_tilt_shots`: the shots model and the tilt weights were chosen on 2022/23); the
fallback's base is affected (above).

## Resume commands

```bash
cd football
uv sync
uv run footy doctor --quiet               # health check (read-only)
uv run footy fd-update                    # results and prices (polite, cached; --scope core|europe)
uv run footy euro-update --fetch-wikipedia --no-rebuild   # UEFA results (one try)
uv run footy euro-import-results FILE --dry-run           # UEFA results from news reports (D-029)
uv run footy fd-fixtures                  # this week's prices (Tue/Fri), D-024
uv run footy predict-domestic --model market_tilt_shots --divisions E0,SP1,I1,D1,F1,E1 --days 4 --dry-run
uv run footy predict-euro --model pan_euro_mix_mxg --days 2 --dry-run
uv run footy publish-shadows --group all --dry-run   # every shadow model (docs/shadow-records.md)
uv run footy settle
uv run footy import-records               # a new or rebuilt database: the records back, ids kept
uv run footy final-report --selected <model> --candidates <a,b>
uv run footy set-model-of-record --group domestic --models <a,b> --from now
make check
uv run footy serve; cd frontend && npm ci && npm run dev
```

`data/` (database, raw store, ledgers, reports) is git-ignored and exists only on this disk
(audit M6). After a loss, `fd-update` (which creates the schema) and `euro-update` rebuild the
match data; `uv run footy import-records` brings the predictions back from
`records/predictions/` (ids kept, hashes checked) and settles them again; publishing refuses
until it has. Nothing rebuilds the job and fetch logs, the odds snapshots, the stored
`fixtures.csv` revisions or `data/reports/` (`uefa_selection.json`, `ml_selection.json` and
`ml_learning_curve.json` have no generator in the repository); the ledgers come back only by
re-running `lab-validate` and `final-report` (about a day of computing). The capture loop
(`data/logs/capture_loop.sh`, every 5 minutes) runs only while this container lives.

## Known problems and open dependencies

1. **Always-on host** (D-009): publishing, settling and odds capture run from this session's
   check-ins and a loop inside the container. When the container is reclaimed they stop
   until a session resumes them. The server kit (`deploy/`, [docs/server.md](docs/server.md))
   is built and rehearsed but not yet run on a real server; the owner creates the server.
2. **Odds capture** (Sportmonks free plan: Scottish Premiership and Danish Superliga only). It
   has stored one capture, 88 quotes for Randers v Brøndby (21 Oct, 5.4 h late; corrected 22
   Oct: not "no snapshots"). Misses are not recorded once kick-off is an hour past, every
   snapshot is stamped with its run's start, and its fixtures can't yet be joined to our teams
   (M18). The loop was not running at 22:40 UK on 22 Oct. The live prices for the five leagues
   come from football-data's `fixtures.csv` instead (D-024).
3. **UEFA.com raw files** (D-023): 147 pages fetched before the terms were checked sit in the
   local raw store, never committed; since 22 Oct nothing reads them (M16). The owner decides
   whether to purge them.
4. **No lineups or injuries**: only early editions exist. Records are made on the run day, up
   to 7 days before kick-off, not on the fixture's decision day (M10, open).
5. **Profit reports**: results with fewer than 20 bets are withheld and breakdowns aggregate,
   but two published results that differ by a few bets could still be subtracted to recover a
   price. Nothing saved is exposed (the model of record makes about one bet in two seasons);
   choose a publication policy before reporting a model that bets often.
6. **Migrations**: `capture-odds`, `fd-fixtures`, `import-records` and `euro-import-results`
   refuse an out-of-date database, but `fd-update`, `euro-update` and `serve` still migrate on
   their own (M5, open), and the check-ins run the first two. Back up `data/` and run
   `uv run footy migrate` after pulling a new migration, before any check-in command. Round A
   added no migration.
7. **The fixed rho** for 1X2-only market-xG solves is a median over all seasons (a small
   look-ahead, measured as negligible; the UEFA model of record doesn't use it).
8. **Data fingerprint** (`ledger.data_revision`) covers parser versions, source revisions and
   row counts, but not Wikipedia parser changes, applied corrections, reference CSVs (such as
   the venue override) or manual UEFA results.
9. **Records that differ from the tested procedure.** The 64 `dixon_coles` records and the two
   Levante model + market records were fitted on all history, not the tested 3-year window
   (D-027). The 45 `pan_euro_mix` records were published at 01:58 UTC on 21 Oct, before the
   4,815 repeat meetings were imported at 03:14: replayed with them, 1X2 moves by up to 0.0506
   (Braga v Gent), O/U 3.5 by up to 0.0485 (median 0.0047), and two picks flip; the data
   fingerprint didn't change (corrected 22 Oct, audit L21). The machine-learning records are
   rebuilt at each run (M9). All stay as published.
10. **Wikimedia refuses this machine.** en.wikipedia.org has answered with HTTP 403 since 18:49
    UTC on 21 Oct (49 failures in 7 sweeps up to 07:31 UTC on 22 Oct; the last success was at
    02:20 UTC on 21 Oct). Corrected 22 Oct: not a 429 rate limit from about 22:00. A curl with
    the same User-Agent got 429 at 07:31:57, so the refusal is specific to the client. Commons
    and upload.wikimedia.org refuse too, so team pictures can't be downloaded; only the
    Wikidata Query Service works. Until it answers, UEFA results come from `euro-import-results`.
11. **Container restarts** happen every hour or two: they kill the capture loop and any
    running workflow (work on disk survives; commit checkpoints often).
12. **Price shadow models publish as planned (decided 22 Oct):** the six price shadow models
    (`market_tilt_ctx`, `_dc`, `_elo`, `_ens`, `ml_online_market`, `ml_stack_market`) publish
    once each collection day's prices arrive. All but `ml_stack_market` tilt fully onto the
    prices, so their match-result and over 2.5 figures equal the de-vigged consensus the model
    of record's own page already shows (D-024); their other markets (O/U 1.5 and 3.5, BTTS) are
    what they add. The backtest explorer and the ledger download withhold, match by match, the
    six price-identical models: `market_tilt_shots`, `_ctx`, `_dc`, `_elo`, `_ens` and
    `ml_online_market` (corrected 22 Oct: this item named `ml_stack_market` in place of
    `market_tilt_shots`; `ml_stack_market` moves the match result a little, median 0.012). The
    repository that holds the records is public (M4): the consensus figures of every priced
    fixture accumulate there. That is the owner's decision.
13. **Disk:** 3.6 GB free on 22 Oct (22:40 UK); the session scratchpad holds 25 GB, mostly
    database copies. Clear old copies before long runs.
14. **Evidence that can't be regenerated** (M22): the paired intervals in D-027 and D-028 came
    from scratch scripts (the audit reproduced them exactly with its own code); there is no
    ledger for the Championship 2023/24 figure in D-027; `euro_eval` has no CLI command; the
    backtest manifests record only HEAD (the 06:24 test run was on a "WIP checkpoint" commit),
    and records name the commit at HEAD when written, not the code that ran (L7).
15. **Code comments and site text still to correct** (the documents round changed no code):
    `src/footy/live.py:138-148` ("the model in use is the one that was validated": not for the
    eight path-dependent models, M9); `src/footy/capture.py:10-11` (misses are recorded: not
    after kick-off + 1 h, M18); `src/footy/db.py:91-94` (unattended jobs never migrate:
    `fd-update`, `euro-update` and `serve` do, M5); `src/footy/modeling/lab/goal_calibration.py:47`
    (every setting on the inner seasons: not its base, M19); `src/footy/model_catalogue.py:71-74`
    and the `elo_goals_gd` entry in `reference/shadow_models.json` ("goal-based Elo": also
    shots on target and red cards, L19); `src/footy/ml_decision.py:29-35` (L14); and the server
    kit's daily job publishes no `dixon_coles` records (L6; the runbook does).

## Said to the owner in chat, and since corrected

For the next summary to the owner (audit section 5 and M2):

- **"Within a hair of the closing prices"** is true only of the league model of record, which
  is the bookmakers' consensus: 1X2 RPS +0.0006 (−0.0002 to +0.0013), O/U 2.5 log loss
  +0.0035 (+0.0011 to +0.0058, significantly worse). The best pure model is +0.0048 (+0.0030
  to +0.0066) and +0.0120; Dixon-Coles +0.0067 and +0.0127. The UEFA model has no benchmark.
- **"53.4% of match results right"** is league games only, and the same as backing the
  bookmakers' favourite (53.5% on the same games). The pure fallback scores 52.5%, the UEFA
  model 55.5%.
- **"The ChatGPT app beat us, 7 against 5"**: three games were under way when its forecasts
  arrived; like for like it is 5 of 7 each (RPS 0.169 against our 0.189).
- **"206 crests and 620 venue photos, freely licensed"**: Wikidata names 207 crest files and
  624 venue photos; their licences are confirmed only at download.
- **"Five of six suggested bets landed"**: the model expected 4.46 of 6, and neither the
  suggestions nor the prices were recorded. Picking bets in chat was a mistake (standing rules).
- **"Wikipedia is rate-limiting us (HTTP 429)"**: it is refusing this machine (HTTP 403) since
  18:49 UTC on 21 Oct.
- **"The odds capture has stored nothing"**: it stored one capture of 88 quotes.
- **"UEFA.com is not used"**: until 22 Oct its stored pages set one neutral-venue flag; German
  Wikipedia does now.
