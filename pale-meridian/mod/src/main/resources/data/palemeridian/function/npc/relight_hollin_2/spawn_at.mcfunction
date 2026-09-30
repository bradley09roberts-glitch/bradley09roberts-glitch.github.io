$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 64c21cdb-2c40-33c0-a264-ef465cb1f7dc run summon minecraft:text_display $(x) $(y) $(z) {UUID:[I;1690442971,742405056,-1570443450,1555167196],billboard:"center",text:{"text":"✦ Relight","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.relight_hollin_2"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.75f,0f],scale:[0.8f,0.8f,0.8f]}}
$tp 64c21cdb-2c40-33c0-a264-ef465cb1f7dc $(x) $(y) $(z)
$execute unless entity 233df9f9-ee67-37f5-bf44-15d37ee98ddb run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;591264249,-295225355,-1086057005,2129235419],width:1.2f,height:1.4f,response:1b,Tags:["pm.int","pm.int.relight_hollin_2"]}
scoreboard players set 233df9f9-ee67-37f5-bf44-15d37ee98ddb pm.nid 4
$tp 233df9f9-ee67-37f5-bf44-15d37ee98ddb $(x) $(y) $(z)
