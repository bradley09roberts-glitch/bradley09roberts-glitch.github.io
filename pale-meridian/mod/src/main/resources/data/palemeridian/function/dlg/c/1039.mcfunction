# prop/chart/confirm_true: Write the names
execute unless score @s pm.dctx matches 83 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:c4/ending/true
