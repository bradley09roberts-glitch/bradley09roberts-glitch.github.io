# npc/brannoc/restored_2: "I'm sorry about Col."
execute unless score @s pm.dctx matches 29 run return fail
scoreboard players set @s pm.dctx 0
scoreboard players set #brannoc.told pm.world 1
function palemeridian:c2/col_token
