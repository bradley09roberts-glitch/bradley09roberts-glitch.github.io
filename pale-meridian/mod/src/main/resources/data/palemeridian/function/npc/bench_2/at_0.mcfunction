# Survey benchmark at bench.2
execute positioned 102.7 67 158.5 unless entity @a[distance=..72] run return fail
execute unless entity ff281631-5573-32c6-846c-37be8b7bceef run summon minecraft:text_display 102.7 67 158.5 {UUID:[I;-14150095,1433612998,-2073282626,-1954820369],billboard:"center",text:{"text":"✎ Survey benchmark","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.bench_2"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.5499999999999998f,0f],scale:[0.8f,0.8f,0.8f]}}
tp ff281631-5573-32c6-846c-37be8b7bceef 102.7 67 158.5
execute unless entity c22e6734-14f9-39c2-bb0d-24234e93540a run summon minecraft:interaction 102.7 67 158.5 {UUID:[I;-1037146316,351877570,-1156766685,1318278154],width:1.0f,height:1.2f,response:1b,Tags:["pm.int","pm.int.bench_2"]}
scoreboard players set c22e6734-14f9-39c2-bb0d-24234e93540a pm.nid 37
tp c22e6734-14f9-39c2-bb0d-24234e93540a 102.7 67 158.5
