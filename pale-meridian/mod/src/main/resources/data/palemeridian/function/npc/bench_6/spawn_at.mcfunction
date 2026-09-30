$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity ef91c60b-11f3-3d9c-b30f-7b3319845ddd run summon minecraft:text_display $(x) $(y) $(z) {UUID:[I;-275659253,301153692,-1290831053,428105181],billboard:"center",text:{"text":"✎ Survey benchmark","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.bench_6"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.5499999999999998f,0f],scale:[0.8f,0.8f,0.8f]}}
$tp ef91c60b-11f3-3d9c-b30f-7b3319845ddd $(x) $(y) $(z)
$execute unless entity 30fe8b89-9d62-3322-bcf4-ef896a0b9802 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;821988233,-1654508766,-1124798583,1779144706],width:1.0f,height:1.2f,response:1b,Tags:["pm.int","pm.int.bench_6"]}
scoreboard players set 30fe8b89-9d62-3322-bcf4-ef896a0b9802 pm.nid 41
$tp 30fe8b89-9d62-3322-bcf4-ef896a0b9802 $(x) $(y) $(z)
