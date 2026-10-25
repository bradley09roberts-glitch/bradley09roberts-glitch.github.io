# How much a defensive difference carries over

Regular seasons; K fitted on 2012-2021, everything else scored on 2022-2025 (out of sample). Cutoff weeks 5, 8, 11, 14: the difference from games before the cutoff (opponents also measured before it, as in the app) against the difference over the rest of that season. Code c503aad.

* **Carries over after 6 games** = 6/(6+K): the share of an observed difference expected to persist for the rest of the season.
* **Skill** = how much better the shrunk difference predicts the rest of the season than assuming every defence is average (1 - MSE ratio). **Raw** = the same for the unshrunk difference; negative means taking early numbers at face value is worse than ignoring them.
* **Labels held** = of 'likely strong/weak' labels at a cutoff, the share whose remaining games were on the same side of average; **expected** = what a calibrated label should achieve given the noise in those remaining games.

| Measure | Area | K | Carries over after 6 games | Week-8 correlation | Skill | Raw | Labels | Labels held | Expected |
|---|---|---|---|---|---|---|---|---|---|
| Points allowed per game | Overall | 16 | 27% | 0.22 | 0.068 | -0.64 | 121 | 64% | 72% |
| Yards per play allowed | Overall | 14 | 30% | 0.38 | 0.142 | -0.46 | 139 | 80% | 74% |
| Net yards per dropback | Pass defence | 18 | 25% | 0.33 | 0.103 | -0.53 | 96 | 68% | 71% |
| Completion rate allowed | Pass defence | 14 | 30% | 0.29 | 0.097 | -0.64 | 148 | 73% | 72% |
| Passing yards allowed per game | Pass defence | 28 | 18% | 0.26 | 0.079 | -0.68 | 73 | 74% | 68% |
| Interception rate | Pass defence | 48 | 11% | 0.20 | 0.032 | -0.88 | 18 | 72% | 63% |
| Sack rate (per dropback) | Pass rush | 52 | 10% | 0.12 | 0.014 | -0.93 | 40 | 55% | 62% |
| Yards per designed run (non-QB) | Run defence | 24 | 20% | 0.21 | 0.056 | -0.80 | 81 | 74% | 68% |
| Runs of 10+ yards | Run defence | 28 | 18% | 0.25 | 0.064 | -0.69 | 82 | 68% | 67% |
| Non-QB rushing yards per game | Run defence | 16 | 27% | 0.25 | 0.095 | -0.53 | 134 | 72% | 72% |
| QB rushing yards per game | QB runs | 60 | 9% | 0.14 | 0.036 | -0.76 | 34 | 68% | 62% |
| Yards per target to WRs | v wide receivers | 26 | 19% | 0.23 | 0.038 | -0.69 | 41 | 66% | 67% |
| Catch rate allowed to WRs | v wide receivers | 20 | 23% | 0.21 | 0.039 | -0.80 | 86 | 62% | 69% |
| Receiving yards to WRs per game | v wide receivers | 38 | 14% | 0.22 | 0.050 | -0.50 | 33 | 79% | 64% |
| Targets to WRs per game | v wide receivers | 26 | 19% | 0.22 | 0.060 | -0.85 | 78 | 71% | 68% |
| Yards per target to TEs | v tight ends | 320 | 2% | 0.13 | 0.004 | -1.22 | 0 | - | - |
| Catch rate allowed to TEs | v tight ends | 100 | 6% | -0.01 | -0.017 | -1.83 | 2 | 50% | 59% |
| Receiving yards to TEs per game | v tight ends | 80 | 7% | 0.28 | 0.032 | -0.79 | 6 | 50% | 60% |
| Targets to TEs per game | v tight ends | 60 | 9% | 0.20 | 0.029 | -0.91 | 13 | 62% | 62% |
| Yards per target to RBs | v running backs (receiving) | 34 | 15% | -0.09 | -0.026 | -1.22 | 22 | 59% | 65% |
| Catch rate allowed to RBs | v running backs (receiving) | 180 | 3% | 0.13 | 0.011 | -0.80 | 0 | - | - |
| Receiving yards to RBs per game | v running backs (receiving) | 32 | 16% | 0.08 | 0.013 | -1.04 | 31 | 48% | 65% |
| Targets to RBs per game | v running backs (receiving) | 26 | 19% | 0.27 | 0.049 | -0.70 | 59 | 63% | 67% |
