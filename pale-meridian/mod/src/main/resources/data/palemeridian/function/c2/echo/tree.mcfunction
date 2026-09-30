execute if score #c2.mem.tree pm.world matches 1 run return fail
scoreboard players set #c2.mem.tree pm.world 1
scoreboard players add c2.memories pm.qp 1
particle minecraft:white_ash 320 79 -4 1.2 1 1.2 0.01 120 normal
playsound minecraft:block.amethyst_block.resonate master @a 320 78 -4 1 0.7
execute unless entity a0e6c1f7-c31a-34f2-86d8-7077e44341cb run summon minecraft:text_display 320 79.6 -4 {UUID:[I;-1595489801,-1021692686,-2032635785,-465354293],billboard:"center",text:{"text":"B + C. Carved the summer we were nine and ten.","color":"white","italic":true},background:0,text_opacity:170,Tags:["pm.echo"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]}}
schedule function palemeridian:c1/echo_fade 200t append
tellraw @a {"text":"Bark cut deep with two sets of initials. Someone has touched them so often the letters shine.","color":"gray","italic":true}
tellraw @a {"text":"The brothers' tree.","color":"gray","italic":true}
function palemeridian:hud/refresh
execute if score c2.memories pm.qp matches 3.. run function palemeridian:q/c2.memories/complete
