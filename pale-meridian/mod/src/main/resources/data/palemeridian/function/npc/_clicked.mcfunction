advancement revoke @s only palemeridian:trigger/npc_click
tag @s add pm.me
scoreboard players set #clicked pm.tmp 0
execute as @e[type=interaction,tag=pm.int,distance=..8] if data entity @s interaction if function palemeridian:npc/_is_me run function palemeridian:npc/_take
tag @s remove pm.me
execute if score #clicked pm.tmp matches 1 run return run function palemeridian:npc/noticeboard/talk
execute if score #clicked pm.tmp matches 2 run return run function palemeridian:npc/relight_hollin_0/talk
execute if score #clicked pm.tmp matches 3 run return run function palemeridian:npc/relight_hollin_1/talk
execute if score #clicked pm.tmp matches 4 run return run function palemeridian:npc/relight_hollin_2/talk
execute if score #clicked pm.tmp matches 5 run return run function palemeridian:npc/relight_hollin_3/talk
execute if score #clicked pm.tmp matches 6 run return run function palemeridian:npc/odile/talk
execute if score #clicked pm.tmp matches 7 run return run function palemeridian:npc/mirelle/talk
execute if score #clicked pm.tmp matches 8 run return run function palemeridian:npc/jory/talk
execute if score #clicked pm.tmp matches 9 run return run function palemeridian:npc/brannoc/talk
