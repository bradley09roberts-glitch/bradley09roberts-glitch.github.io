$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 62acdf89-0665-340a-84dc-7a2a961f246f run summon minecraft:text_display $(x) $(y) $(z) {UUID:[I;1655496585,107295754,-2065925590,-1776343953],billboard:"center",text:{"text":"✦ Relight","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.relight_glassworks_3"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.75f,0f],scale:[0.8f,0.8f,0.8f]}}
$tp 62acdf89-0665-340a-84dc-7a2a961f246f $(x) $(y) $(z)
$execute unless entity f660e209-eaec-3fa8-adf5-1de4a16559fd run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-161422839,-353615960,-1376444956,-1587193347],width:1.2f,height:1.4f,response:1b,Tags:["pm.int","pm.int.relight_glassworks_3"]}
scoreboard players set f660e209-eaec-3fa8-adf5-1de4a16559fd pm.nid 27
$tp f660e209-eaec-3fa8-adf5-1de4a16559fd $(x) $(y) $(z)
