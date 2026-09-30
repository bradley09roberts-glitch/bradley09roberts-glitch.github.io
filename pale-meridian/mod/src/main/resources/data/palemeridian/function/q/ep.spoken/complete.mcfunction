# Quest ep.spoken: Every Name, Spoken — complete (idempotent)
execute unless score ep.spoken pm.q matches 1 run return fail
scoreboard players set ep.spoken pm.q 2
advancement grant @a only palemeridian:journal/ep/spoken
function palemeridian:q/_advance
function palemeridian:hud/refresh
