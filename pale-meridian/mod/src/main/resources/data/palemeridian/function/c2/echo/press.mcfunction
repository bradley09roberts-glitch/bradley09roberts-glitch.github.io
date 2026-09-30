execute if score #c2.mem.press pm.world matches 1 run return fail
scoreboard players set #c2.mem.press pm.world 1
scoreboard players add c2.memories pm.qp 1
particle minecraft:white_ash 346 80 -26 1.2 1 1.2 0.01 120 normal
playsound minecraft:block.amethyst_block.resonate master @a 346 79 -26 1 0.7
execute unless entity dc28f5ac-42ac-3b9c-91cf-ffa7cde3d938 run summon minecraft:text_display 346 80.6 -26 {UUID:[I;-601295444,1118583708,-1848639577,-840705736],billboard:"center",text:{"text":"One more barrel and then the Deepcut. Tobin says the pay's double past the blue seam.","color":"white","italic":true},background:0,text_opacity:170,Tags:["pm.echo"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]}}
schedule function palemeridian:c1/echo_fade 200t append
tellraw @a {"text":"The smell of crushed apples. A young man's voice, cheerful, saying goodbye to someone.","color":"gray","italic":true}
tellraw @a {"text":"In the loft, a bedroll and a lamp that is still warm, and a note in a hand you know.","color":"gray","italic":true}
function palemeridian:hud/refresh
execute if score c2.memories pm.qp matches 3.. run function palemeridian:q/c2.memories/complete
