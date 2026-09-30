# Relay lamp at meridian.relay0
execute positioned -15.5 96 -22.5 unless entity @a[distance=..72] run return fail
execute unless entity fcf9a224-b004-33e4-b21c-20fa0e3fc535 run summon minecraft:text_display -15.5 96 -22.5 {UUID:[I;-50748892,-1341901852,-1306779398,239060277],billboard:"center",text:{"text":"✦ Relight","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.relay_0"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,2.75f,0f],scale:[0.8f,0.8f,0.8f]}}
tp fcf9a224-b004-33e4-b21c-20fa0e3fc535 -15.5 96 -22.5
execute unless entity e21762a8-58b5-39c0-bfdb-6a3c6e326162 run summon minecraft:interaction -15.5 96 -22.5 {UUID:[I;-501783896,1488271808,-1076139460,1848795490],width:1.2f,height:2.4f,response:1b,Tags:["pm.int","pm.int.relay_0"]}
scoreboard players set e21762a8-58b5-39c0-bfdb-6a3c6e326162 pm.nid 30
tp e21762a8-58b5-39c0-bfdb-6a3c6e326162 -15.5 96 -22.5
