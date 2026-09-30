# Dark lamp at surge.hollin.relight3
execute positioned 136.5 69.8 126.5 unless entity @a[distance=..72] run return fail
execute unless entity 00b8ee5b-c5cc-3f0f-bd91-03daecddac58 run summon minecraft:text_display 136.5 69.8 126.5 {UUID:[I;12119643,-976470257,-1114569766,-321016744],billboard:"center",text:{"text":"✦ Relight","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.relight_hollin_3"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.75f,0f],scale:[0.8f,0.8f,0.8f]}}
tp 00b8ee5b-c5cc-3f0f-bd91-03daecddac58 136.5 69.8 126.5
execute unless entity 5e03affd-1dce-3c6a-b5eb-8e8e023bfd08 run summon minecraft:interaction 136.5 69.8 126.5 {UUID:[I;1577299965,500055146,-1242853746,37485832],width:1.2f,height:1.4f,response:1b,Tags:["pm.int","pm.int.relight_hollin_3"]}
scoreboard players set 5e03affd-1dce-3c6a-b5eb-8e8e023bfd08 pm.nid 5
tp 5e03affd-1dce-3c6a-b5eb-8e8e023bfd08 136.5 69.8 126.5
