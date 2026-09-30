# The Long Chart at meridian.chart_table
execute positioned 2.5 68 -20.5 unless entity @a[distance=..72] run return fail
execute unless entity 97c8c190-cae7-33a9-a83f-35e2584b2916 run summon minecraft:text_display 2.5 68 -20.5 {UUID:[I;-1748450928,-890817623,-1472252446,1481320726],billboard:"center",text:{"text":"✎ The Long Chart","color":"white"},background:1073741824,Tags:["pm.label","pm.label.chart_table"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.75f,0f],scale:[0.8f,0.8f,0.8f]}}
tp 97c8c190-cae7-33a9-a83f-35e2584b2916 2.5 68 -20.5
execute unless entity 6e610fde-b020-3b04-a920-8f08d207881b run summon minecraft:interaction 2.5 68 -20.5 {UUID:[I;1851854814,-1340065020,-1457484024,-771258341],width:1.2f,height:1.4f,response:1b,Tags:["pm.int","pm.int.chart_table"]}
scoreboard players set 6e610fde-b020-3b04-a920-8f08d207881b pm.nid 34
tp 6e610fde-b020-3b04-a920-8f08d207881b 2.5 68 -20.5
