# Dark lamp at surge.glassworks.relight0
execute positioned 16.5 70.8 -303.5 unless entity @a[distance=..72] run return fail
execute unless entity 951d1be9-ae92-33e5-996d-63c0fcb4812c run summon minecraft:text_display 16.5 70.8 -303.5 {UUID:[I;-1793254423,-1366150171,-1720884288,-55279316],billboard:"center",text:{"text":"✦ Relight","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.relight_glassworks_0"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.75f,0f],scale:[0.8f,0.8f,0.8f]}}
tp 951d1be9-ae92-33e5-996d-63c0fcb4812c 16.5 70.8 -303.5
execute unless entity 8e07d7a1-6b58-384d-b53b-badcad75b9b7 run summon minecraft:interaction 16.5 70.8 -303.5 {UUID:[I;-1912088671,1800943693,-1254376740,-1384793673],width:1.2f,height:1.4f,response:1b,Tags:["pm.int","pm.int.relight_glassworks_0"]}
scoreboard players set 8e07d7a1-6b58-384d-b53b-badcad75b9b7 pm.nid 24
tp 8e07d7a1-6b58-384d-b53b-badcad75b9b7 16.5 70.8 -303.5
