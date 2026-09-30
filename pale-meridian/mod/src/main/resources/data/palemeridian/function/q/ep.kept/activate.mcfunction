# Quest ep.kept: Kept Safe — activate (idempotent)
execute unless score ep.kept pm.q matches 0 run return fail
scoreboard players set ep.kept pm.q 1
function palemeridian:hud/refresh
