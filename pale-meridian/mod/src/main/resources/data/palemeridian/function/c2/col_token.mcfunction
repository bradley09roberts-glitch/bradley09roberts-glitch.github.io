execute if score #k.2 pm.world matches 1 run return run tellraw @s {"text":"Brannoc: Keep his token. He'd have liked you.","color":"yellow"}
tellraw @s {"text":"Brannoc: So am I. Here. He carved it for luck underground. Didn't work, but it was his.","color":"yellow"}
give @s minecraft:apple[minecraft:custom_name={"text":"Carved apple token","italic":false},minecraft:lore=[{"text":"Wood, carved into an apple. C.H.","italic":false,"color":"gray"}],minecraft:custom_data={pm:{keepsake:2}},!minecraft:consumable] 1
