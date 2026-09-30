# Quest c1.home: A Place to Rest — complete (idempotent)
execute unless score c1.home pm.q matches 1 run return fail
scoreboard players set c1.home pm.q 2
advancement grant @a only palemeridian:journal/c1/home
function palemeridian:q/_advance
function palemeridian:hud/refresh
