# Quest s.eleven: The Eleven — complete (idempotent)
execute unless score s.eleven pm.q matches 1 run return fail
scoreboard players set s.eleven pm.q 2
advancement grant @a only palemeridian:journal/s/eleven
function palemeridian:keepsake/all_found
function palemeridian:q/_advance
function palemeridian:hud/refresh
