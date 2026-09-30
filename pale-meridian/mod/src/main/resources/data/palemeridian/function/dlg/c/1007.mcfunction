# npc/odile/intro_help: "I'll listen."
execute unless score @s pm.dctx matches 6 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:q/c1.odile/complete
