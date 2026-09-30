$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 4209f702-33b7-339c-a454-0a2faac9ef53 run summon minecraft:text_display $(x) $(y) $(z) {UUID:[I;1107949314,867644316,-1537996241,-1429606573],billboard:"center",text:{"text":"✎ Foreman's log","color":"white"},background:1073741824,Tags:["pm.label","pm.label.foremans_log"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.75f,0f],scale:[0.8f,0.8f,0.8f]}}
$tp 4209f702-33b7-339c-a454-0a2faac9ef53 $(x) $(y) $(z)
$execute unless entity 5ac82af6-c102-329f-b278-3b625cb2dbdf run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;1523067638,-1056820577,-1300743326,1555225567],width:1.0f,height:1.4f,response:1b,Tags:["pm.int","pm.int.foremans_log"]}
scoreboard players set 5ac82af6-c102-329f-b278-3b625cb2dbdf pm.nid 10
$tp 5ac82af6-c102-329f-b278-3b625cb2dbdf $(x) $(y) $(z)
