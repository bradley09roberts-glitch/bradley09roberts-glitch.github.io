# Quest c1.home: A Place to Rest — activate (idempotent)
execute unless score c1.home pm.q matches 0 run return fail
scoreboard players set c1.home pm.q 1
function palemeridian:hud/refresh
