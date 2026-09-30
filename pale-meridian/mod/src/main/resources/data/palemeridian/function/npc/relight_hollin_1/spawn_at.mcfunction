$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity bfd791df-872e-3493-b81a-c2907f7c8ab6 run summon minecraft:text_display $(x) $(y) $(z) {UUID:[I;-1076391457,-2027015021,-1206205808,2138868406],billboard:"center",text:{"text":"✦ Relight","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.relight_hollin_1"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.75f,0f],scale:[0.8f,0.8f,0.8f]}}
$tp bfd791df-872e-3493-b81a-c2907f7c8ab6 $(x) $(y) $(z)
$execute unless entity 48a204a8-e1f5-3d31-b5c8-ea6b9a1d6e08 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;1218577576,-504021711,-1245123989,-1709347320],width:1.2f,height:1.4f,response:1b,Tags:["pm.int","pm.int.relight_hollin_1"]}
scoreboard players set 48a204a8-e1f5-3d31-b5c8-ea6b9a1d6e08 pm.nid 3
$tp 48a204a8-e1f5-3d31-b5c8-ea6b9a1d6e08 $(x) $(y) $(z)
