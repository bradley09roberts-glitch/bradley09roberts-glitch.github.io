$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 506da443-1b64-365a-8fde-05674d58e0f4 run summon minecraft:text_display $(x) $(y) $(z) {UUID:[I;1349362755,459552346,-1881275033,1297670388],billboard:"center",text:{"text":"✎ Tamsin's notes","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.tamsin_notes"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.15f,0f],scale:[0.8f,0.8f,0.8f]}}
$tp 506da443-1b64-365a-8fde-05674d58e0f4 $(x) $(y) $(z)
$execute unless entity 676a1784-d2d6-3877-bee7-c5db4946268d run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;1735006084,-757712777,-1092106789,1229334157],width:1.0f,height:0.8f,response:1b,Tags:["pm.int","pm.int.tamsin_notes"]}
scoreboard players set 676a1784-d2d6-3877-bee7-c5db4946268d pm.nid 12
$tp 676a1784-d2d6-3877-bee7-c5db4946268d $(x) $(y) $(z)
