# Over gaps checked against our own forecast errors (descriptive, 30 Oct 2026)

Reference over gaps from the user's separate research board, tested against this project's
models: NFL X1 v0.4.2 on the 2022-2023 validation seasons, NBA N1 (nba-0.1) on 2024-2025. For a
gap g the line is the highest half-point at least g below our projection (forecast mean); the
over wins when the actual beats it. Players who took part, projections in the range where props
are usually offered. Script `gap_check.py`, numbers `gap_check.json`.

**This is the optimistic case.** The line here is built from our own projection, so it carries
no information of its own. A bookmaker's line does; against real lines the over would win less
often at the same gap (on the first two NBA nights, model-v-market gaps of 15+ points won 51%).
It says how big a gap our errors need, not that any gap beats a bookmaker.

| NFL market | Reference gap | Over won at that gap (95% CI) | Gap our errors need for 60% |
|---|---:|---:|---:|
| QB passing yards | +25 | 63% (60-67) | +19.5 |
| QB pass attempts | +3 | 67% (64-70) | +1.5 |
| QB completions | +2.5 | 73% (69-76) | +1 |
| RB rushing yards | +7.5 | 57% (55-60) | +9.5 |
| QB rushing yards | +5 | 47% (44-50) | +8.5 |
| RB rushing attempts | +2 | 68% (66-70) | +1 |
| WR/TE receiving yards | +7.5 | 50% (49-52) | +15 |
| RB receiving yards | +2.5 | 44% (41-46) | +9.5 |
| WR/TE receptions | +1 | 73% (72-75) | +0.5 |
| RB receptions | none | | +0.5 |
| QB rushing attempts | none | | +0.5 |

Yardage is skewed (a few long plays), so our mean sits above the typical outcome: at a gap of
zero, WR/TE receiving yards overs won 42%, RB receiving yards 38%, QB rushing yards 36%. A
projection-minus-line gap is therefore a poor trigger for yardage; the forecast distribution's
own over probability already accounts for the skew, which is what the pick rules use.

NBA (no reference gaps exist; these are what our errors imply, not rules): 60% needs about +2
for points and PRA, +0.5 for rebounds, assists and threes (half-point rounding below small
projections already gives a margin there).
