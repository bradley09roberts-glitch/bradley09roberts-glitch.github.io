# Relay lamp at meridian.relay3
execute positioned -24.5 96 -13.5 unless entity @a[distance=..72] run return fail
execute unless entity ec451c79-5323-3d7d-99cb-2c42f4db6549 run summon minecraft:text_display -24.5 96 -13.5 {UUID:[I;-331015047,1394818429,-1714738110,-186948279],billboard:"center",text:{"text":"✦ Relight","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.relay_3"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,2.75f,0f],scale:[0.8f,0.8f,0.8f]}}
tp ec451c79-5323-3d7d-99cb-2c42f4db6549 -24.5 96 -13.5
execute unless entity ea320082-2513-394e-8a25-f1656e7b9224 run summon minecraft:interaction -24.5 96 -13.5 {UUID:[I;-365821822,622016846,-1977224859,1853592100],width:1.2f,height:2.4f,response:1b,Tags:["pm.int","pm.int.relay_3"]}
scoreboard players set ea320082-2513-394e-8a25-f1656e7b9224 pm.nid 33
tp ea320082-2513-394e-8a25-f1656e7b9224 -24.5 96 -13.5
