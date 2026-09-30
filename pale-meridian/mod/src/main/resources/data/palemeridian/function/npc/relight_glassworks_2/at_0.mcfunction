# Dark lamp at surge.glassworks.relight2
execute positioned 16.5 70.8 -287.5 unless entity @a[distance=..72] run return fail
execute unless entity 2cd33d47-3496-3c02-9566-27df5b491ce7 run summon minecraft:text_display 16.5 70.8 -287.5 {UUID:[I;752041287,882260994,-1788467233,1531518183],billboard:"center",text:{"text":"✦ Relight","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.relight_glassworks_2"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.75f,0f],scale:[0.8f,0.8f,0.8f]}}
tp 2cd33d47-3496-3c02-9566-27df5b491ce7 16.5 70.8 -287.5
execute unless entity 936b6928-6d1d-39d0-8f9b-0b949132730e run summon minecraft:interaction 16.5 70.8 -287.5 {UUID:[I;-1821677272,1830631888,-1885664364,-1858964722],width:1.2f,height:1.4f,response:1b,Tags:["pm.int","pm.int.relight_glassworks_2"]}
scoreboard players set 936b6928-6d1d-39d0-8f9b-0b949132730e pm.nid 26
tp 936b6928-6d1d-39d0-8f9b-0b949132730e 16.5 70.8 -287.5
