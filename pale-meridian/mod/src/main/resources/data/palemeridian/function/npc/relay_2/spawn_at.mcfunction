$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity d5478c38-3082-31c5-9179-9ca6bcecd868 run summon minecraft:text_display $(x) $(y) $(z) {UUID:[I;-716731336,813838789,-1854301018,-1125328792],billboard:"center",text:{"text":"✦ Relight","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.relay_2"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,2.75f,0f],scale:[0.8f,0.8f,0.8f]}}
$tp d5478c38-3082-31c5-9179-9ca6bcecd868 $(x) $(y) $(z)
$execute unless entity f0a5d034-0443-3770-8e52-e7d88bce6913 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-257568716,71513968,-1907169320,-1949406957],width:1.2f,height:2.4f,response:1b,Tags:["pm.int","pm.int.relay_2"]}
scoreboard players set f0a5d034-0443-3770-8e52-e7d88bce6913 pm.nid 32
$tp f0a5d034-0443-3770-8e52-e7d88bce6913 $(x) $(y) $(z)
