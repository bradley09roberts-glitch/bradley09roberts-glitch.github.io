# npc/tamsin/remind: Show her the theodolite
execute unless score @s pm.dctx matches 33 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:c3/show/theodolite
