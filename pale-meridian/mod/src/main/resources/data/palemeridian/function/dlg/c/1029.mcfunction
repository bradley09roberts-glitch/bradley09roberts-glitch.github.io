# npc/hesper/meet: "Tamsin says you struck the Deepcut from the Chart."
execute unless score @s pm.dctx matches 52 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/hesper/meet_struck
