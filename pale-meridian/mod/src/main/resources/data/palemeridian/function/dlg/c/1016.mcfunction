# npc/brannoc/intro_who: "What does the orchard need?"
execute unless score @s pm.dctx matches 22 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/brannoc/intro_help
