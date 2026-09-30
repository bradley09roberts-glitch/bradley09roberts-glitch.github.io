# prop/chart/choice: Leave it blank
execute unless score @s pm.dctx matches 82 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/prop/chart/confirm_blank
