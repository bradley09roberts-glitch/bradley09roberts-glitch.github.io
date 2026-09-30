# A pale miner at deepcut.fig.7
execute positioned 28.5 70 -411.5 unless entity @a[distance=..72] run return fail
execute unless entity adf443d0-fec4-33f8-854c-e0dd4c9bf4f4 run function palemeridian:npc/echo_7/new_0
tp adf443d0-fec4-33f8-854c-e0dd4c9bf4f4 28.5 70 -411.5 180.0 0
execute unless entity d6cd7599-6a50-396e-9676-d3fdfd49ee4c run summon minecraft:interaction 28.5 70 -411.5 {UUID:[I;-691178087,1783642478,-1770597379,-45486516],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_7"]}
scoreboard players set d6cd7599-6a50-396e-9676-d3fdfd49ee4c pm.nid 19
tp d6cd7599-6a50-396e-9676-d3fdfd49ee4c 28.5 70 -411.5
