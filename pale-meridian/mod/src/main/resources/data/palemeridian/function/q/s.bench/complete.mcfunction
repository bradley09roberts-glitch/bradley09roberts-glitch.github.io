# Quest s.bench: Tamsin's Benchmarks — complete (idempotent)
execute unless score s.bench pm.q matches 1 run return fail
scoreboard players set s.bench pm.q 2
advancement grant @a only palemeridian:journal/s/bench
function palemeridian:bench/all
function palemeridian:q/_advance
function palemeridian:hud/refresh
