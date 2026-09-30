# prop/chart/confirm_blank: Leave it blank
execute unless score @s pm.dctx matches 84 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:c4/ending/blank
