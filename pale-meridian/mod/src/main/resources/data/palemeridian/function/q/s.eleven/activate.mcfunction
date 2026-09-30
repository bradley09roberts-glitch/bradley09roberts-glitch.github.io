# Quest s.eleven: The Eleven — activate (idempotent)
execute unless score s.eleven pm.q matches 0 run return fail
scoreboard players set s.eleven pm.q 1
execute unless score s.eleven pm.qp matches 0.. run scoreboard players set s.eleven pm.qp 0
function palemeridian:hud/refresh
