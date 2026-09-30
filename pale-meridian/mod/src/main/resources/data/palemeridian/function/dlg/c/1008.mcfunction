# npc/odile/restored: (Tell her)
execute unless score @s pm.dctx matches 11 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/odile/restored_2
