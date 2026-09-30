execute if score #c1.name.well pm.world matches 1 run return fail
scoreboard players set #c1.name.well pm.world 1
scoreboard players add c1.names pm.qp 1
particle minecraft:white_ash 120 68 118 1.2 1 1.2 0.01 120 normal
playsound minecraft:block.amethyst_block.resonate master @a 120 67 118 1 0.6
execute unless entity 9f00e0ab-597a-35b4-845c-58b348c54a44 run summon minecraft:text_display 120 68.6 118 {UUID:[I;-1627332437,1501181364,-2074322765,1220889156],billboard:"center",text:{"text":"One for the bell, one for the rope. Pell always pays.","color":"white","italic":true},background:0,text_opacity:170,Tags:["pm.echo"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]}}
schedule function palemeridian:c1/echo_fade 200t append
tellraw @a {"text":"A coin rings on the well's edge, and an old man's voice counts under his breath.","color":"gray","italic":true}
tellraw @a {"text":"The Pells kept the well. Water.","color":"gray","italic":true}
function palemeridian:hud/refresh
execute if score c1.names pm.qp matches 4.. run function palemeridian:q/c1.names/complete
