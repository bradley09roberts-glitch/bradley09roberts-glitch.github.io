$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity acd33982-82b8-3390-81c6-87889f298df9 run summon minecraft:text_display $(x) $(y) $(z) {UUID:[I;-1395443326,-2101857392,-2117695608,-1624666631],billboard:"center",text:{"text":"✦ Relight","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.relight_hollin_0"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.75f,0f],scale:[0.8f,0.8f,0.8f]}}
$tp acd33982-82b8-3390-81c6-87889f298df9 $(x) $(y) $(z)
$execute unless entity b13449a5-0db5-305a-ab0b-06d0ed994d8e run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-1321973339,229978202,-1425340720,-308720242],width:1.2f,height:1.4f,response:1b,Tags:["pm.int","pm.int.relight_hollin_0"]}
scoreboard players set b13449a5-0db5-305a-ab0b-06d0ed994d8e pm.nid 2
$tp b13449a5-0db5-305a-ab0b-06d0ed994d8e $(x) $(y) $(z)
