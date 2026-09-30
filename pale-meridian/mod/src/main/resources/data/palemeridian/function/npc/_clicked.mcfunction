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
execute if score #clicked pm.tmp matches 10 run return run function palemeridian:npc/foremans_log/talk
execute if score #clicked pm.tmp matches 11 run return run function palemeridian:npc/tamsin/talk
execute if score #clicked pm.tmp matches 12 run return run function palemeridian:npc/tamsin_notes/talk
execute if score #clicked pm.tmp matches 13 run return run function palemeridian:npc/echo_1/talk
execute if score #clicked pm.tmp matches 14 run return run function palemeridian:npc/echo_2/talk
execute if score #clicked pm.tmp matches 15 run return run function palemeridian:npc/echo_3/talk
execute if score #clicked pm.tmp matches 16 run return run function palemeridian:npc/echo_4/talk
execute if score #clicked pm.tmp matches 17 run return run function palemeridian:npc/echo_5/talk
execute if score #clicked pm.tmp matches 18 run return run function palemeridian:npc/echo_6/talk
execute if score #clicked pm.tmp matches 19 run return run function palemeridian:npc/echo_7/talk
execute if score #clicked pm.tmp matches 20 run return run function palemeridian:npc/echo_8/talk
execute if score #clicked pm.tmp matches 21 run return run function palemeridian:npc/echo_9/talk
execute if score #clicked pm.tmp matches 22 run return run function palemeridian:npc/echo_10/talk
execute if score #clicked pm.tmp matches 23 run return run function palemeridian:npc/echo_11/talk
execute if score #clicked pm.tmp matches 24 run return run function palemeridian:npc/relight_glassworks_0/talk
execute if score #clicked pm.tmp matches 25 run return run function palemeridian:npc/relight_glassworks_1/talk
execute if score #clicked pm.tmp matches 26 run return run function palemeridian:npc/relight_glassworks_2/talk
execute if score #clicked pm.tmp matches 27 run return run function palemeridian:npc/relight_glassworks_3/talk
execute if score #clicked pm.tmp matches 28 run return run function palemeridian:npc/hesper/talk
execute if score #clicked pm.tmp matches 29 run return run function palemeridian:npc/cradle/talk
execute if score #clicked pm.tmp matches 30 run return run function palemeridian:npc/relay_0/talk
execute if score #clicked pm.tmp matches 31 run return run function palemeridian:npc/relay_1/talk
execute if score #clicked pm.tmp matches 32 run return run function palemeridian:npc/relay_2/talk
execute if score #clicked pm.tmp matches 33 run return run function palemeridian:npc/relay_3/talk
execute if score #clicked pm.tmp matches 34 run return run function palemeridian:npc/chart_table/talk
execute if score #clicked pm.tmp matches 35 run return run function palemeridian:npc/vow/talk
