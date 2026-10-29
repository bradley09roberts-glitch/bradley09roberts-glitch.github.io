# NBA 28 Oct 2026 (ET): first prospective night

Counting edition per game: the last published before tip (NBA-2026-10-28-final-20261028T1908Z).
Outcomes: SportsDataverse box scores (identical to the ESPN provisional rows used overnight).
Bookmaker comparison uses US books via The Odds API (research reference; not available to a
UK customer), the last collection before each tip, margin removed, half-point lines, players
who played. Aggregates only; no odds are stored in this repository.

## Distributions (all 251 players who played; CRPS, lower is better)

| market | N1 | A (recent average) | C (L10) |
|---|---:|---:|---:|
| points | 3.128 | 3.198 | 3.350 |
| rebounds | 1.348 | 1.384 | 1.420 |
| assists | 0.908 | 0.922 | 0.976 |
| threes | 0.581 | 0.596 | 0.623 |
| PRA | 4.031 | 4.170 | 4.395 |

N1 beat both baselines on every market, as in validation. One night.

## Model v market (575 props, 12 games)

| | log loss | Brier |
|---|---:|---:|
| market (margin removed) | **0.685** | **0.246** |
| N1 | 0.707 | 0.255 |
| A | 0.704 | 0.252 |
| C | 0.714 | 0.255 |

N1 minus market log loss +0.021 (game-bootstrap 95% interval -0.006 to +0.050): the market
was better; the interval just includes zero. By size of disagreement (model's side):

| gap | props | model said | market said | won |
|---|---:|---:|---:|---:|
| 0-3 pts | 120 | 52.1% | 50.7% | 50.8% |
| 3-6 | 92 | 54.4% | 50.0% | 46.7% |
| 6-10 | 147 | 57.5% | 49.6% | 50.3% |
| 10-15 | 94 | 62.1% | 50.0% | 60.6% |
| 15+ | 122 | 71.7% | 49.6% | 52.5% |

The large disagreements were badly overconfident: when N1 was 15+ points away from the market,
its side won about as often as the market implied, not as often as N1 said.

## Pick lists and parlays shared before tip

| list | settled | won | model said | market said | flat stakes at best US price |
|---|---:|---:|---:|---:|---:|
| 12:29 rule list (edge >= 6) | 216 | 125 (57.9%) | 62.2% | 49.4% | +26.1 units |
| 12:29 strict shortlist | 23 | 15 | 67.5% | 52.6% | +4.2 |
| 16:20 rule list | 207 | 120 (58.0%) | 62.2% | 49.4% | +25.5 |
| 16:20 strict shortlist | 19 | 11 | 65.7% | 52.3% | +1.1 |
| 19:08 rule list | 153 | 85 (55.6%) | 61.9% | 49.4% | +10.7 |
| 19:08 strict shortlist | 17 | 9 | 65.2% | 52.7% | -0.6 |

Parlays: 12:29 3-leg (Barnes, Tyson, Fears) won; 12:29 4-leg (+ Larsson) won; 16:30 3-leg
(Barnes, Larsson, Dosunmu) won; 16:30 4-leg (+ Cade) lost; 19:08 3-leg (Barnes, Larsson, Cade)
lost; the user's UK slip lost (Cade, D. Mitchell); the late Tre Johnson / Murray / Harper
parlay lost (Johnson did not play).

Caveats: one night; picks within a game and across a player's markets are correlated, so the
effective sample is far smaller than the counts; best US prices are not available in the UK.
