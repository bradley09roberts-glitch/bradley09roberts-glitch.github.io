# npc/odile/intro: "I'm looking for a surveyor. Tamsin Reed."
execute unless score @s pm.dctx matches 2 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/odile/intro_tamsin
