$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 083633d6-5416-361d-bebf-40918103071a run summon minecraft:text_display $(x) $(y) $(z) {UUID:[I;137769942,1410741789,-1094762351,-2130508006],billboard:"center",text:{"text":"✎ Survey benchmark","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.bench_1"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.5499999999999998f,0f],scale:[0.8f,0.8f,0.8f]}}
$tp 083633d6-5416-361d-bebf-40918103071a $(x) $(y) $(z)
$execute unless entity 56d0e345-bb83-38da-b2a1-06a7a31c1141 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;1456530245,-1149028134,-1298069849,-1558441663],width:1.0f,height:1.2f,response:1b,Tags:["pm.int","pm.int.bench_1"]}
scoreboard players set 56d0e345-bb83-38da-b2a1-06a7a31c1141 pm.nid 36
$tp 56d0e345-bb83-38da-b2a1-06a7a31c1141 $(x) $(y) $(z)
