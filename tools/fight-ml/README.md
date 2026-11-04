# Fight ML: learning from every UFC fight since 1994

Machine learning that goes through every UFC fight on UFCStats, from UFC 1 (1994-03-11) onward, one card at a time. It
predicts each card from what happened before it, then learns from the results before the next card. The inputs are
UFCStats round-by-round stats and Sherdog's round-by-round play-by-play from each fighter's earlier fights. It is scored
against the Octagon Season Ledger's model (v4) and the de-vigged closing line, using the model-gate rules. The results
are on a private claude.ai dashboard ("Fight ML Lab").

Odds are never an input. The prediction model in the ledger is not changed by any of this.

## Learners

| Name | What it is |
|---|---|
| Elo only | Elo ratings (K = 64), no fitting |
| Online learner | Logistic regression updated after every card by the gradient of the log score: confident and right earns most, confident and wrong costs most |
| RL agent | Reinforcement learning as a contextual bandit: it picks a fighter (sampling in proportion to its belief, so it explores), gets +1 if that fighter wins and −1 if not, and updates by policy gradient (REINFORCE). Its chances are averaged over 5 runs. "RL agent, no exploration" is a separate agent trained the same way but always taking its top pick |
| Yearly logistic | L2 logistic refitted at the start of each year on every earlier fight |
| Boosted trees | Gradient boosting refitted at the start of each year |
| ML blend | Average log-odds of the online learner, yearly logistic and boosted trees |

Every row is fighter A minus fighter B and no model has an intercept, so P(A) = 1 − P(B). This stops the models from
learning UFCStats' habit of listing the winner first (64% of fights).

## Features (each from fights before the one predicted)

- **Elo, Glicko-1 and a margin-of-victory Elo.** The last two score each result 30% by the outcome (a finish is a full
  win, a decision counts by the judges' margin) and 70% by the stats round share.
- **Record:** experience, win rate, finishes, KO and submission losses, opponent quality, streak, layoff, and a luck
  adjustment from earlier fight reviews.
- **UFCStats rates per minute, all time and recency-weighted:** strikes landed and absorbed, accuracy and defence,
  takedowns and takedown defence, control, knockdowns, submission attempts, body, leg and ground strikes absorbed, rounds
  won on the stats.
- **Physicals:** age, height, reach, stance.
- **Sherdog play-by-play:** share of rounds Sherdog's scorers gave the fighter, 10-8s, how often they were hurt or hurt
  the opponent, fatigue. These come from 5,318 UFC fights covered in 2016–26.
- **Written fight stories** (where they exist): of the fighter's earlier fights with a story, the share that were losses
  from takedowns or the bottom game, from getting hurt standing, from one shot, or unlucky.
- **Style interactions:** one fighter's takedowns against the other's takedown defence, and so on.

Stats are pulled toward running population means for small samples, and a debutant's UFCStats rates start at those means. Layoff, Sherdog and story features stay blank when there is no data. Two UFCStats fighters share each of 9 names; each bout goes to the namesake whose listed weight is nearest the bout's division. Fighters spelled differently across the UFCStats files (Jr./III, hyphens, nicknames, typos) are joined by a short alias list and a suffix-blind match.

## How it was kept honest

- Every setting was chosen on 2014–18. Development copies ran with `FIREWALL=1`, which hides every 2019+ result from the
  code. That covers learning rates, tree settings, rating parameters, shrinkage and which improvements to keep.
  Caveat: an earlier version, before the audit, had already been scored on 2019–26 several times, including a
  with-and-without-Sherdog test. No choice since used those scores, and the final model was then scored on 2019–26.
- The stats round scorer (which rounds a fighter won on the numbers) was fitted on 2016–18 Sherdog rounds only.
- An independent audit had three reviewers, and a skeptic checked each finding. It found one leak. Whether UFCStats has a
  fighter's reach, height, stance or birth date depends on how long their career later lasted: blanks mark fighters who
  were cut. A coverage flag had become the strongest feature, and it inflated 2014–18 validation (blend log loss 0.620
  against 0.658 honest).
  - The flag is gone, and blanks are filled with running typical values (averages of fighters already seen), which the
    models can't tell from real ones. In 2014–18, the fighter with filled-in physicals won 6% of the time, and the blend
    gave them 45%, so it can't see the fill-ins.
- **Other audit fixes:**
  - the close is de-vigged additively (Shin for two-way markets), which reproduces the ledger's own gate close log loss
    of 0.5926;
  - comparisons with the close, the calibration chart included, use the same priced bouts;
  - no close-as-feature blend: the information test is labelled a diagnostic and compared against the close alone;
  - one driver script, with hash checks so the evaluation can't read stale predictions;
  - boosting early stopping off;
  - a second, independent verification found smaller issues, also fixed: fill-in values that used later fights,
    namesakes split by weight-class string, the same fighter spelled two ways, 1990s fights adding minutes with no stats,
    and two RL agents shown under one name.

## Results (2019–26 ledger bouts; the close only on bouts it priced)

| | Picked, all 3,958 bouts | Picked, 2,497 priced | Log loss, priced |
|---|---|---|---|
| ML blend | 62.6% | 62.2% | 0.6438 |
| RL agent | 61.8% | 62.2% | 1.7078 |
| v4 (ledger) | 63.7% | 64.1% | 0.6384 |
| Closing line | — | 66.5% | 0.6061 |

- **2025–26 season:** on all 988 bouts, ML blend 65.1%, yearly logistic 65.8%, RL agent 65.2%, v4 66.7%. On the 378
  priced bouts: ML blend 64.3%, yearly logistic 65.3%, RL agent 67.2%, v4 66.1%, the close 66.1%. That is 378 bouts, so
  a few points either way is noise, and the log losses still put the close first.
- **Gate:** every model fails against the close, v4 included, in every period. The 95% interval of (close log loss −
  model log loss) for the blend is [−0.048, −0.027] on 2019–26.
- **The ±1 reward:** the RL agent picks winners about as well as the supervised learners. Its own exploring picks were
  right 60.9% of the time in 2019–26. But 53% of its 2019–26 chances were below 2% or above 98%: a ±1 reward teaches who
  to pick, not how likely they are to win. Its log loss (1.71) is well over twice the others'.
- **Sherdog play-by-play and fight stories add nothing measurable:**
  - 2014–18 blend log loss 0.6461 with them, 0.6463 without;
  - 2019+ 0.6439 with, 0.6438 without.
  - The ratings group is what helped: 0.6510 → 0.6461 on 2014–18 and 0.6467 → 0.6439 from 2019.
- **Combining with v4:** combining a learner with v4 (weights fitted on earlier bouts only) changes log loss by about
  0.001 or less (Elo makes it 0.001 worse), and every interval includes zero.

## Running it

Paths are relative to a working folder `S` laid out as in `tools/fight-review/`. It needs `work/ufc_fights.json`,
`work/ledger.json`, `raw/ufc_fighter_tott.csv`, `sherdog/` and `stories/`, plus `scripts/` holding the fight-review
scripts (`sherdog_parse.py`, `stories_load.py`).

```bash
python3 -I sherdog_all.py $S            # Sherdog play-by-play for every UFC fight it covers -> sherdog/all_fights.json
FIREWALL=1 ./run_all.sh $S $S/ml_dev     # development: 2019+ results hidden
devtools/dev_runs.sh $S $S/dev           # the 2014-18 comparisons that judged each improvement (2019+ hidden)
./run_all.sh $S $S/ml_run                # final: features, learners, ablations, evaluation (about 7 minutes)
python3 -I export.py $S $S/ml_run $S/dash_data     # the dashboard's data files, predictions included
python3 -I report_tables.py $S/ml_run $S/dash_data $S/ml/learn_all.json $S/dev/dev_base $S/dev/dev $S/dev/dev_s1
python3 -I dashboard/make_docs.py $S/dash_data dashboard/index.html blobs.json $S/dash_docs   # the page's store documents
```

`report_tables.py` builds the improvements and checklist tables; its third argument is `learn_all.json` from the run
before the audit fix (the leaky validation score it reports). `features.py` takes `DEBUT_PRIORS=0 RATINGS=0 SHRINK=1`
for the original features, and `devtools/learn_old.py` is the original learners.
`phys_check.py` measures how missing physical data depends on career length. `dashboard/index.html` is the dashboard
page. To publish, upload each CSV in `dash_data` to the dashboard, list each dataset id with its uploaded asset id in
`blobs.json`, run `make_docs.py`, and write the documents in `dash_docs/writes.json` to the dashboard's store.
