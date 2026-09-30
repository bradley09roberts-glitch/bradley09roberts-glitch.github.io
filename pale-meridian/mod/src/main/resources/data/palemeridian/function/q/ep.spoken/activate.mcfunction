# Quest ep.spoken: Every Name, Spoken — activate (idempotent)
execute unless score ep.spoken pm.q matches 0 run return fail
scoreboard players set ep.spoken pm.q 1
function palemeridian:hud/refresh
