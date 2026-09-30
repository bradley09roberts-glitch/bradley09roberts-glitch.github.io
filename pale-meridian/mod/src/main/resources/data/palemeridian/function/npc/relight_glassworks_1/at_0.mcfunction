# Dark lamp at surge.glassworks.relight1
execute positioned 32.5 70.8 -303.5 unless entity @a[distance=..72] run return fail
execute unless entity af8b964b-362d-31d1-a5db-c15d9f4160b1 run summon minecraft:text_display 32.5 70.8 -303.5 {UUID:[I;-1349806517,908931537,-1512324771,-1623105359],billboard:"center",text:{"text":"✦ Relight","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.relight_glassworks_1"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.75f,0f],scale:[0.8f,0.8f,0.8f]}}
tp af8b964b-362d-31d1-a5db-c15d9f4160b1 32.5 70.8 -303.5
execute unless entity 42365265-a23a-3f98-96d9-f789c5f7ab55 run summon minecraft:interaction 32.5 70.8 -303.5 {UUID:[I;1110856293,-1573240936,-1764100215,-973624491],width:1.2f,height:1.4f,response:1b,Tags:["pm.int","pm.int.relight_glassworks_1"]}
scoreboard players set 42365265-a23a-3f98-96d9-f789c5f7ab55 pm.nid 25
tp 42365265-a23a-3f98-96d9-f789c5f7ab55 32.5 70.8 -303.5
