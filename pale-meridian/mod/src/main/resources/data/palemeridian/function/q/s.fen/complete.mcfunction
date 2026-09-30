# Quest s.fen: The Drowned Chapel — complete (idempotent)
execute unless score s.fen pm.q matches 1 run return fail
scoreboard players set s.fen pm.q 2
advancement grant @a only palemeridian:journal/s/fen
function palemeridian:q/_advance
function palemeridian:hud/refresh
