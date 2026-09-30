# The Vow at fen.vow
execute positioned -344.5 64 70.5 unless entity @a[distance=..72] run return fail
execute unless entity 0331dc84-7140-3bb3-a9b4-b7087edcd11a run summon minecraft:text_display -344.5 64 70.5 {UUID:[I;53599364,1900034995,-1447774456,2128400666],billboard:"center",text:{"text":"✎ The Vow of the First Keeper","color":"white"},background:1073741824,Tags:["pm.label","pm.label.vow"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.75f,0f],scale:[0.8f,0.8f,0.8f]}}
tp 0331dc84-7140-3bb3-a9b4-b7087edcd11a -344.5 64 70.5
execute unless entity 6629e92d-d154-398a-a111-ba36ca539fbb run summon minecraft:interaction -344.5 64 70.5 {UUID:[I;1714022701,-783009398,-1592673738,-900489285],width:1.0f,height:1.4f,response:1b,Tags:["pm.int","pm.int.vow"]}
scoreboard players set 6629e92d-d154-398a-a111-ba36ca539fbb pm.nid 35
tp 6629e92d-d154-398a-a111-ba36ca539fbb -344.5 64 70.5
