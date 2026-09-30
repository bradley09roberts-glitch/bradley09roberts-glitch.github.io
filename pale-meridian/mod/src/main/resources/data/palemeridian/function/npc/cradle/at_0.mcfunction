# The Great Lens at meridian.cradle
execute positioned -13.5 96.5 -13.5 unless entity @a[distance=..72] run return fail
execute unless entity 6f2f0de7-91cb-3f3b-946d-c00ee6b69df5 run summon minecraft:text_display -13.5 96.5 -13.5 {UUID:[I;1865354727,-1848950981,-1804746738,-424239627],billboard:"center",text:{"text":"✦ The Great Lens","color":"aqua"},background:1073741824,Tags:["pm.label","pm.label.cradle"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.9500000000000002f,0f],scale:[0.8f,0.8f,0.8f]}}
tp 6f2f0de7-91cb-3f3b-946d-c00ee6b69df5 -13.5 96.5 -13.5
execute unless entity db82c440-8700-3a39-8eaa-ac7900389bcb run summon minecraft:interaction -13.5 96.5 -13.5 {UUID:[I;-612187072,-2030028231,-1901417351,3709899],width:1.2f,height:1.6f,response:1b,Tags:["pm.int","pm.int.cradle"]}
scoreboard players set db82c440-8700-3a39-8eaa-ac7900389bcb pm.nid 29
tp db82c440-8700-3a39-8eaa-ac7900389bcb -13.5 96.5 -13.5
