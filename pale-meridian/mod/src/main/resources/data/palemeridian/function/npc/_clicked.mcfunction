advancement revoke @s only palemeridian:trigger/npc_click
tag @s add pm.me
scoreboard players set #clicked pm.tmp 0
execute as @e[type=interaction,tag=pm.int,distance=..8] if data entity @s interaction if function palemeridian:npc/_is_me run function palemeridian:npc/_take
tag @s remove pm.me
execute if score #clicked pm.tmp matches 1 run return run function palemeridian:npc/noticeboard/talk
