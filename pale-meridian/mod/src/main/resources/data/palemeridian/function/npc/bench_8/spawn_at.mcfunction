$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 3b94a968-2814-3cd4-8dd2-2c61b99e7f24 run summon minecraft:text_display $(x) $(y) $(z) {UUID:[I;999598440,672414932,-1915605919,-1180795100],billboard:"center",text:{"text":"✎ Survey benchmark","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.bench_8"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.5499999999999998f,0f],scale:[0.8f,0.8f,0.8f]}}
$tp 3b94a968-2814-3cd4-8dd2-2c61b99e7f24 $(x) $(y) $(z)
$execute unless entity 0318802b-45cb-377f-bc01-80a03c7a97cf run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;51937323,1170945919,-1140752224,1014667215],width:1.0f,height:1.2f,response:1b,Tags:["pm.int","pm.int.bench_8"]}
scoreboard players set 0318802b-45cb-377f-bc01-80a03c7a97cf pm.nid 43
$tp 0318802b-45cb-377f-bc01-80a03c7a97cf $(x) $(y) $(z)
