# Fight review for the Octagon Season Ledger

Tooling that explains why each graded pick in the Octagon Season Ledger (a private claude.ai artifact) won or lost,
and what each fighter's weak spots are. It adds to the ledger page:

- a **Fight review** tab on every graded bout: the verdict (unlucky / close / outclassed for a miss; earned / close /
  fortunate for a right pick; quick finish when there's no read), who had the winner wrong (model, market or both),
  UFCStats numbers round by round, Sherdog's three scorers' round scores, a written story of the fight, the first
  takedown and first time hurt, the model's per-15-minute projection against what happened, and both fighters' weak spots;
- a **Weak spots** tab on booked bouts: where one fighter's style meets the other's weak spot;
- a **Weak spots** panel on every fighter page, from all of their UFC fights round by round, with rates pulled toward the
  division average for small samples;
- a **Review** page: misses and right picks by verdict, who underrated the winner, how the losers were beaten, a style
  check of the model and the market against each opponent style, and the takedown-mismatch test.

None of it changes the model or its record. A miss stays a miss; the review only explains it.

## Data

| Source | What | How |
|---|---|---|
| UFCStats via [Greco1899/scrape_ufc_stats](https://github.com/Greco1899/scrape_ufc_stats) | Round-by-round stats, results, judges' cards for every UFC fight | `ufc_fight_stats.csv`, `ufc_fight_results.csv`, `ufc_event_details.csv`, `ufc_fighter_tott.csv`. The fight id in the URL is the ledger's bout id. |
| Sherdog play-by-play articles ("play-by-play, results & round scoring") | Round-by-round notes and three scorers' round scores | Found by walking `sherdog.com/news/news/list/N`; robots.txt allows it. The text is used to write the stories and is not republished. |
| The ledger page's own data block | Picks, chances, market prices, projections | Read from the saved artifact HTML. |

## Pipeline

All scripts take a working folder `WORK` with `raw/`, `work/`, `sherdog/`, `packets/`, `stories/`, `build/`.

```bash
# 1. UFCStats data
for f in ufc_fight_stats ufc_fight_results ufc_event_details ufc_fighter_tott; do
  curl -sS -o $WORK/raw/$f.csv https://raw.githubusercontent.com/Greco1899/scrape_ufc_stats/main/$f.csv; done
python3 -I build_stats.py $WORK/raw $WORK/work/ufc_fights.json
# 2. the ledger's data block -> $WORK/work/ledger.json (from the saved page: the <script id="ledger-data"> block)
# 3. Sherdog: find play-by-play links (page ranges can run in parallel), fetch, parse, match to bouts
python3 -I sherdog_range.py $WORK/sherdog/range_1.json 1 700
python3 -I sherdog_fetch_match.py $WORK
# 4. stats verdicts (fits the round model on Sherdog's scored rounds), profiles
python3 -I review_core.py $WORK && python3 -I review_profiles.py $WORK
# 5. stories: packets per event, then fight-stories.workflow.js (a writer and an independent checker per event;
#    each runs validate_story.py, which checks the spec and every takedown and knockdown against UFCStats)
python3 -I make_packets.py $WORK
# 6. rerun step 4 so finish verdicts use the stories, then pack and insert into the page
python3 -I review_core.py $WORK && python3 -I review_profiles.py $WORK
python3 -I build_review_data.py $WORK 2026-11-04
python3 -I assemble.py ledger.html $WORK/work/review-data.json ui/review.js ui/review.css $WORK/build/ledger.html
node shoot.js $WORK/build/ledger.html $WORK/shots review   # render check, desktop and phone
```

## After the ledger is rebuilt

The ledger page is rebuilt elsewhere, and a rebuild replaces the whole page. To put the review back, run step 2 on the
new page, rerun steps 1, 4 and 6 (new fights get stats verdicts straight away; run step 5 for new events to add their
stories), then `assemble.py` on the new page. `assemble.py` refuses a page that already has a review block, and stops
if any of its hook points has moved. The private copies of `stories.json` and `review-data.json` are attached to the
ledger artifact as supporting files.

## How the verdicts work

- **Decisions**: unlucky (or fortunate) if it was a split or majority decision, if Sherdog's scorers had the loser
  winning, or if the round-by-round stats favoured the loser by half a round or more and Sherdog didn't back the winner.
  Clear if the judges had it by two rounds or more. Otherwise close.
- **Finishes**: completed rounds are owned by Sherdog's majority score where there is one, otherwise by the stats round
  model (60/40 or wider). The final round's lean before the finish comes from the written story; without one, the loser
  counts as ahead in that round only if they still led its numbers (finishing flurry included) at 65% or more over 90
  seconds or longer. Ahead at the finish is unlucky, level is close, behind is outclassed (or earned). A first-round
  finish with no read, or anything under a minute, is a quick finish.
- **Stats round model**: logistic regression on the round's differences in significant strikes, knockdowns, takedowns,
  control minutes, submission attempts, other strikes and reversals, fitted on about 2,400 Sherdog-scored rounds from
  2024 to 2026; about 83% cross-validated agreement with Sherdog's majority.
- **Weak spots**: flagged when a shrunk rate is in the worst fifth of the fighter's division, or a pattern repeats
  (most losses by KO, losing most fights after conceding the first takedown, a clear round 1 to round 3 drop, a losing
  record against one opponent style with four or more fights).

## What the checks found

- No style blind spot: against wrestlers, submission hunters, power and volume strikers, the model's picks won at least
  as often as it said, in line with its slight overall caution.
- A walk-forward test of takedown mismatch (a fighter who concedes takedowns facing one who lands them, both measured on
  earlier UFC fights only) added nothing to the model's price or the market's (z close to 0 for both, about 2,500 bouts).
  Weak spots describe fighters; they weren't a betting edge on their own.
