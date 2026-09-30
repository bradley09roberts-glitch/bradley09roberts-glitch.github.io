# A pale miner at deepcut.fig.5
execute positioned 18.5 70 -415.5 unless entity @a[distance=..72] run return fail
execute unless entity d2b0fb54-cab4-39b4-8e67-abd8df224940 run function palemeridian:npc/echo_5/new_0
tp d2b0fb54-cab4-39b4-8e67-abd8df224940 18.5 70 -415.5 180.0 0
execute unless entity d92fb62c-50ae-35b6-90e1-8c2082bf5f2b run summon minecraft:interaction 18.5 70 -415.5 {UUID:[I;-651184596,1353594294,-1864266720,-2101387477],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_5"]}
scoreboard players set d92fb62c-50ae-35b6-90e1-8c2082bf5f2b pm.nid 17
tp d92fb62c-50ae-35b6-90e1-8c2082bf5f2b 18.5 70 -415.5
