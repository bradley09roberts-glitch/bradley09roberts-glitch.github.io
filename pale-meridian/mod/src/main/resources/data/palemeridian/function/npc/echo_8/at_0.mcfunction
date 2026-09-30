# A pale miner at deepcut.fig.8
execute positioned 30.5 70 -415.5 unless entity @a[distance=..72] run return fail
execute unless entity d15c7dc9-ad9e-34f5-b019-9b855c29ec1a run function palemeridian:npc/echo_8/new_0
tp d15c7dc9-ad9e-34f5-b019-9b855c29ec1a 30.5 70 -415.5 180.0 0
execute unless entity d3aa9423-a1b5-3296-ba10-bc2bb0127581 run summon minecraft:interaction 30.5 70 -415.5 {UUID:[I;-743795677,-1581960554,-1173308373,-1340967551],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_8"]}
scoreboard players set d3aa9423-a1b5-3296-ba10-bc2bb0127581 pm.nid 20
tp d3aa9423-a1b5-3296-ba10-bc2bb0127581 30.5 70 -415.5
