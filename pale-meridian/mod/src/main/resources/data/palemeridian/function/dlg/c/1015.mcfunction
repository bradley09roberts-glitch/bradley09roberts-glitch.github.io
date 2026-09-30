# npc/brannoc/intro: "I lit Hollin's lamp. I can help here too."
execute unless score @s pm.dctx matches 21 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/brannoc/intro_help
