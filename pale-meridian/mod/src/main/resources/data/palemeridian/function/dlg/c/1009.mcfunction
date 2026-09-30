# npc/odile/restored_2: "What happened forty years ago?"
execute unless score @s pm.dctx matches 12 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/odile/restored_3
