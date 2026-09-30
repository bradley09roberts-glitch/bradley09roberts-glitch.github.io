execute if score #c1.name.ferry pm.world matches 1 run return fail
scoreboard players set #c1.name.ferry pm.world 1
scoreboard players add c1.names pm.qp 1
particle minecraft:white_ash 78 64 78 1.2 1 1.2 0.01 120 normal
playsound minecraft:block.amethyst_block.resonate master @a 78 63 78 1 0.6
execute unless entity e8bb1628-f085-3412-807d-1d41f553a5cc run summon minecraft:text_display 78 64.6 78 {UUID:[I;-390392280,-259705838,-2139284159,-179067444],billboard:"center",text:{"text":"Tarn's ferry! Last crossing for the Keeper's lamp-night!","color":"white","italic":true},background:0,text_opacity:170,Tags:["pm.echo"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]}}
schedule function palemeridian:c1/echo_fade 200t append
tellraw @a {"text":"Oars dip in water that isn't moving. A ferryman calls from somewhere out on the lake.","color":"gray","italic":true}
tellraw @a {"text":"The Tarns ran the ferry. Home.","color":"gray","italic":true}
function palemeridian:hud/refresh
execute if score c1.names pm.qp matches 4.. run function palemeridian:q/c1.names/complete
