# npc/brannoc/intro: "I'm looking for Tamsin Reed."
execute unless score @s pm.dctx matches 21 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/brannoc/intro_tamsin
