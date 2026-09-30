# Quest c4.causeway: The Broken Span — activate (idempotent)
execute unless score c4.causeway pm.q matches 0 run return fail
scoreboard players set c4.causeway pm.q 1
function palemeridian:hud/refresh
