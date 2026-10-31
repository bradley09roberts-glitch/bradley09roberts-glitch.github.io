# NBA 30 Oct 2026 (ET): third prospective night, 10 games

Counting edition: NBA-2026-10-30-final-20261030T2201Z (nba-0.2, ESPN injury reports at publish).
Lines: US books via The Odds API, one collection at 14:30 UTC (no credits left for a later one;
research reference, not available to a UK customer), margin removed, eligible props only (plays
>= 85%, no injury flag, >= 3 books, half-point line). Outcomes: SportsDataverse box scores.
Aggregates only; no odds are stored in this repository.

## Model v market (461 eligible props)

| gap (model's side) | props | model said | market said | won |
|---|---:|---:|---:|---:|
| 0-3 pts | 116 | 51.7% | 49.9% | 52.6% |
| 3-6 | 105 | 54.7% | 50.3% | 54.3% |
| 6-10 | 114 | 58.4% | 50.8% | 55.3% |
| 10-15 | 65 | 62.8% | 50.7% | 47.7% |
| 15+ | 61 | 68.9% | 49.8% | 55.7% |

Log loss: market 0.686, model 0.695 (the market better for the third night running). The 10-15
band, which D-030 kept for picks after two nights, won 47.7%; over three nights it is 101 of 177
(57%), 6-10 157 of 297 (53%), 15+ 106 of 203 (52%). Reviewed after about ten nights (D-030).

## Picks and shared bets

- Published pick list at 22:01 (10-15 band, teams with a fresh starter absence skipped): 19-17,
  -0.2 units at the best US price.
- Picks shared in the afternoon (9): 6-3 at their lines (Maxey's line later moved; Giannis and
  Edgecombe left the list during the evening and would have won).
- Parlays: 3-leg (Thompson, Flagg, George) and 4-leg (+ Dunn) lost: Thompson 11 v 10.5, Flagg 9 v
  6.5. 2am singles: DeRozan over 2.5 assists won (5), Curry under 24.5 points lost (31).

## nba-0.2 on its first live night

Against the nba-0.1 early edition for the 229 players who played: CRPS points +1.8%, rebounds
+1.4%, assists +1.5%, PRA +2.4% (worse); minutes error 4.88 v 4.74. Players shifted up by the
lineup adjustment played what nba-0.1 expected. Live editions are back on nba-0.1 (D-037).
