# npc/brannoc/restored: "What happened in the Deepcut?"
execute unless score @s pm.dctx matches 28 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/brannoc/restored_2
