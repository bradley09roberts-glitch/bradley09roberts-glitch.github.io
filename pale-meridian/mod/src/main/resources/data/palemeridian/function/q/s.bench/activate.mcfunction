# Quest s.bench: Tamsin's Benchmarks — activate (idempotent)
execute unless score s.bench pm.q matches 0 run return fail
scoreboard players set s.bench pm.q 1
execute unless score s.bench pm.qp matches 0.. run scoreboard players set s.bench pm.qp 0
function palemeridian:hud/refresh
