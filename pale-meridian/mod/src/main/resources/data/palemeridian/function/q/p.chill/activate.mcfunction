# Quest p.chill: Cold Without Light — activate (idempotent)
execute unless score p.chill pm.q matches 0 run return fail
scoreboard players set p.chill pm.q 1
function palemeridian:hud/refresh
