# A pale miner at deepcut.fig.3
execute positioned 20.5 70 -411.5 unless entity @a[distance=..72] run return fail
execute unless entity ed011aa7-2737-37ee-a9ce-c5043b2a0afc run function palemeridian:npc/echo_3/new_0
tp ed011aa7-2737-37ee-a9ce-c5043b2a0afc 20.5 70 -411.5 180.0 0
execute unless entity 04afae82-73cb-34cb-9c76-4cc3dbbe75d5 run summon minecraft:interaction 20.5 70 -411.5 {UUID:[I;78622338,1942697163,-1669968701,-608274987],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_3"]}
scoreboard players set 04afae82-73cb-34cb-9c76-4cc3dbbe75d5 pm.nid 15
tp 04afae82-73cb-34cb-9c76-4cc3dbbe75d5 20.5 70 -411.5
