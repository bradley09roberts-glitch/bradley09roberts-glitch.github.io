# Relay lamp at meridian.relay1
execute positioned -6.5 96 -13.5 unless entity @a[distance=..72] run return fail
execute unless entity 383284fc-b247-3e1f-b45f-64a63bce0e4a run summon minecraft:text_display -6.5 96 -13.5 {UUID:[I;942834940,-1303953889,-1268816730,1003359818],billboard:"center",text:{"text":"✦ Relight","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.relay_1"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,2.75f,0f],scale:[0.8f,0.8f,0.8f]}}
tp 383284fc-b247-3e1f-b45f-64a63bce0e4a -6.5 96 -13.5
execute unless entity 543c28a5-7f60-321b-9e24-28e6a198a93c run summon minecraft:interaction -6.5 96 -13.5 {UUID:[I;1413228709,2137010715,-1641797402,-1583830724],width:1.2f,height:2.4f,response:1b,Tags:["pm.int","pm.int.relay_1"]}
scoreboard players set 543c28a5-7f60-321b-9e24-28e6a198a93c pm.nid 31
tp 543c28a5-7f60-321b-9e24-28e6a198a93c -6.5 96 -13.5
