execute if score #c1.name.hall pm.world matches 1 run return fail
scoreboard players set #c1.name.hall pm.world 1
scoreboard players add c1.names pm.qp 1
particle minecraft:white_ash 93 69 115 1.2 1 1.2 0.01 120 normal
playsound minecraft:block.amethyst_block.resonate master @a 93 68 115 1 0.6
execute unless entity c9243e0c-3394-3b31-ac3e-033ec295bf9b run summon minecraft:text_display 93 69.6 115 {UUID:[I;-920371700,865352497,-1405222082,-1030373477],billboard:"center",text:{"text":"Again, children, and louder. The Marsh way!","color":"white","italic":true},background:0,text_opacity:170,Tags:["pm.echo"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]}}
schedule function palemeridian:c1/echo_fade 200t append
tellraw @a {"text":"Children's voices recite something in the empty hall, led by a woman who keeps time with a lamp hook.","color":"gray","italic":true}
tellraw @a {"text":"The Marshes taught in the hall. Words.","color":"gray","italic":true}
function palemeridian:hud/refresh
execute if score c1.names pm.qp matches 4.. run function palemeridian:q/c1.names/complete
