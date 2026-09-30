# npc/brannoc/intro: "Who are you?"
execute unless score @s pm.dctx matches 21 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/brannoc/intro_who
