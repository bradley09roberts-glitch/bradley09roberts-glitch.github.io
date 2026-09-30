# npc/hesper/meet_struck: "Why?"
execute unless score @s pm.dctx matches 54 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/hesper/meet_tobin
