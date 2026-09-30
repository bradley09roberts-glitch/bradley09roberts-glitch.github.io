execute if score #c1.name.bakery pm.world matches 1 run return fail
scoreboard players set #c1.name.bakery pm.world 1
scoreboard players add c1.names pm.qp 1
particle minecraft:white_ash 145 69 113 1.2 1 1.2 0.01 120 normal
playsound minecraft:block.amethyst_block.resonate master @a 145 68 113 1 0.6
execute unless entity b27e69dc-60ee-3fd9-8307-a5d5d6bd5b48 run summon minecraft:text_display 145 69.6 113 {UUID:[I;-1300338212,1626226649,-2096650795,-692233400],billboard:"center",text:{"text":"Mother Dunn says the loaves won't wait!","color":"white","italic":true},background:0,text_opacity:170,Tags:["pm.echo"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]}}
schedule function palemeridian:c1/echo_fade 200t append
tellraw @a {"text":"The smell of bread that isn't there. Over the counter, painted and half-flaked: DUNN & DAUGHTER.","color":"gray","italic":true}
tellraw @a {"text":"This was the Dunns' bakery. Bread.","color":"gray","italic":true}
function palemeridian:hud/refresh
execute if score c1.names pm.qp matches 4.. run function palemeridian:q/c1.names/complete
