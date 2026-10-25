# Manual reconciliation (25 October 2026)

28 scored rows of the test ledger (`reports/backtests/exp06-TEST-frozen-v0.3`, model X),
four per core market, drawn at random, each traced back to source:

* the ledger's `actual` value,
* the raw nflverse `stats_player_week` file stored in the raw store (by SHA-256 prefix), and
* an independent count from the raw play-by-play file (completions to the receiver, runs
  and kneels by the rusher, passes and spikes by the passer excluding sacks and scrambles,
  two-point tries excluded).

Result: **28 of 28 agree in all three places** (`reports/reconciliation_sample.csv`).
The sample includes genuine zeros (players who took the field but recorded nothing), which
are scored as 0 only because on-field data shows they played; non-participants are never
scored. Aggregate reconciliation over every 2016-2025 team-game is in
`tests/test_integration.py` (attempts, completions and carries match play-by-play in more
than 99.5% of team-games, overtime included).
