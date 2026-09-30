# npc/odile/restored_3: "And Tamsin?"
execute unless score @s pm.dctx matches 13 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/odile/restored_4
