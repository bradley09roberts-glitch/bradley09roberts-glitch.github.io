# Quest s.fen: The Drowned Chapel — activate (idempotent)
execute unless score s.fen pm.q matches 0 run return fail
scoreboard players set s.fen pm.q 1
function palemeridian:hud/refresh
