$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity ff0b8e06-750c-3632-91d8-2881310778cf run summon minecraft:text_display $(x) $(y) $(z) {UUID:[I;-16019962,1963734578,-1848104831,822573263],billboard:"center",text:{"text":"✎ Survey benchmark","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.bench_7"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.5499999999999998f,0f],scale:[0.8f,0.8f,0.8f]}}
$tp ff0b8e06-750c-3632-91d8-2881310778cf $(x) $(y) $(z)
$execute unless entity dd869471-0ab1-3db8-b54e-7d88c4542835 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-578382735,179387832,-1253147256,-1001117643],width:1.0f,height:1.2f,response:1b,Tags:["pm.int","pm.int.bench_7"]}
scoreboard players set dd869471-0ab1-3db8-b54e-7d88c4542835 pm.nid 42
$tp dd869471-0ab1-3db8-b54e-7d88c4542835 $(x) $(y) $(z)
