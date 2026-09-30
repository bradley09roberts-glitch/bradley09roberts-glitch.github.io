# npc/brannoc/intro_help: "I'll go."
execute unless score @s pm.dctx matches 24 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:q/c2.brannoc/complete
