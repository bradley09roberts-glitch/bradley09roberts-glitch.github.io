# A pale miner at deepcut.fig.1
execute positioned 16.5 70 -411.5 unless entity @a[distance=..72] run return fail
execute unless entity 6187e44a-275b-326d-958b-679508ea6556 run function palemeridian:npc/echo_1/new_0
tp 6187e44a-275b-326d-958b-679508ea6556 16.5 70 -411.5 180.0 0
execute unless entity 0dd87792-5868-3ed6-a83a-c03ab50cd356 run summon minecraft:interaction 16.5 70 -411.5 {UUID:[I;232290194,1483226838,-1472544710,-1257450666],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_1"]}
scoreboard players set 0dd87792-5868-3ed6-a83a-c03ab50cd356 pm.nid 13
tp 0dd87792-5868-3ed6-a83a-c03ab50cd356 16.5 70 -411.5
