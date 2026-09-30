# Noticeboard at landing.noticeboard
execute positioned -2.5 87 349.5 unless entity @a[distance=..72] run return fail
execute unless entity 5aafebdf-137a-3698-97fe-5ac19474049d run summon minecraft:text_display -2.5 87 349.5 {UUID:[I;1521478623,326776472,-1744938303,-1804335971],billboard:"center",text:{"text":"✉ A letter for you","color":"white"},background:1073741824,Tags:["pm.label","pm.label.noticeboard"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.9500000000000002f,0f],scale:[0.8f,0.8f,0.8f]}}
tp 5aafebdf-137a-3698-97fe-5ac19474049d -2.5 87 349.5
execute unless entity d2fcd54c-cbc5-3b3a-845e-b79113c2ce75 run summon minecraft:interaction -2.5 87 349.5 {UUID:[I;-755182260,-876266694,-2074167407,331533941],width:1.1f,height:1.6f,response:1b,Tags:["pm.int","pm.int.noticeboard"]}
scoreboard players set d2fcd54c-cbc5-3b3a-845e-b79113c2ce75 pm.nid 1
tp d2fcd54c-cbc5-3b3a-845e-b79113c2ce75 -2.5 87 349.5
