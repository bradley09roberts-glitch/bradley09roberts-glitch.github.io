# Quest ep.kept: Kept Safe — complete (idempotent)
execute unless score ep.kept pm.q matches 1 run return fail
scoreboard players set ep.kept pm.q 2
advancement grant @a only palemeridian:journal/ep/kept
function palemeridian:q/_advance
function palemeridian:hud/refresh
