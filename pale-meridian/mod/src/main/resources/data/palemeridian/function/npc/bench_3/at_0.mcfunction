# Survey benchmark at bench.3
execute positioned 92.5 67 90.3 unless entity @a[distance=..72] run return fail
execute unless entity 532d14fc-8906-357c-ad37-0f2de4d12ce4 run summon minecraft:text_display 92.5 67 90.3 {UUID:[I;1395463420,-1996081796,-1388900563,-456053532],billboard:"center",text:{"text":"✎ Survey benchmark","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.bench_3"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.5499999999999998f,0f],scale:[0.8f,0.8f,0.8f]}}
tp 532d14fc-8906-357c-ad37-0f2de4d12ce4 92.5 67 90.3
execute unless entity f40d8ea6-fa4f-3b0b-be16-2cf83bd8d9d8 run summon minecraft:interaction 92.5 67 90.3 {UUID:[I;-200438106,-95470837,-1105842952,1004067288],width:1.0f,height:1.2f,response:1b,Tags:["pm.int","pm.int.bench_3"]}
scoreboard players set f40d8ea6-fa4f-3b0b-be16-2cf83bd8d9d8 pm.nid 38
tp f40d8ea6-fa4f-3b0b-be16-2cf83bd8d9d8 92.5 67 90.3
