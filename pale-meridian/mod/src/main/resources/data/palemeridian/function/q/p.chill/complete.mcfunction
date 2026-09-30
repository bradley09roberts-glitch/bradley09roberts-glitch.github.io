# Quest p.chill: Cold Without Light — complete (idempotent)
execute unless score p.chill pm.q matches 1 run return fail
scoreboard players set p.chill pm.q 2
advancement grant @a only palemeridian:journal/p/chill
function palemeridian:q/_advance
function palemeridian:hud/refresh
