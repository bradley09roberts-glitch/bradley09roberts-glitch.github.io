execute if score #c2.mem.apiary pm.world matches 1 run return fail
scoreboard players set #c2.mem.apiary pm.world 1
scoreboard players add c2.memories pm.qp 1
particle minecraft:white_ash 314 79 -73 1.2 1 1.2 0.01 120 normal
playsound minecraft:block.amethyst_block.resonate master @a 314 78 -73 1 0.7
execute unless entity 5cce3784-1b01-3267-916e-719f760def7f run summon minecraft:text_display 314 79.6 -73 {UUID:[I;1557018500,453063271,-1855032929,1980624767],billboard:"center",text:{"text":"Col! Leave the queen alone, you'll get us both stung!","color":"white","italic":true},background:0,text_opacity:170,Tags:["pm.echo"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]}}
schedule function palemeridian:c1/echo_fade 200t append
tellraw @a {"text":"Bees that aren't there hum around the empty hives. Two boys' voices, laughing.","color":"gray","italic":true}
tellraw @a {"text":"Brannoc and his brother kept these hives together.","color":"gray","italic":true}
function palemeridian:hud/refresh
execute if score c2.memories pm.qp matches 3.. run function palemeridian:q/c2.memories/complete
