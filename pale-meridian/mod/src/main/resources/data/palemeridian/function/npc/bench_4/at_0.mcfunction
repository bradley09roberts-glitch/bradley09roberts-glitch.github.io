# Survey benchmark at bench.4
execute positioned 298.5 78 -44.7 unless entity @a[distance=..72] run return fail
execute unless entity 33e76ce4-9293-3e79-bad5-68c18eb351c3 run summon minecraft:text_display 298.5 78 -44.7 {UUID:[I;870804708,-1835843975,-1160419135,-1900850749],billboard:"center",text:{"text":"✎ Survey benchmark","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.bench_4"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.5499999999999998f,0f],scale:[0.8f,0.8f,0.8f]}}
tp 33e76ce4-9293-3e79-bad5-68c18eb351c3 298.5 78 -44.7
execute unless entity 994946c0-64b6-327b-b901-2a4aeddab89b run summon minecraft:interaction 298.5 78 -44.7 {UUID:[I;-1723251008,1689662075,-1191105974,-304432997],width:1.0f,height:1.2f,response:1b,Tags:["pm.int","pm.int.bench_4"]}
scoreboard players set 994946c0-64b6-327b-b901-2a4aeddab89b pm.nid 39
tp 994946c0-64b6-327b-b901-2a4aeddab89b 298.5 78 -44.7
