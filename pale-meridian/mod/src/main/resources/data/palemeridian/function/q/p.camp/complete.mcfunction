# Quest p.camp: Tamsin's Camp — complete (idempotent)
execute unless score p.camp pm.q matches 1 run return fail
scoreboard players set p.camp pm.q 2
advancement grant @a only palemeridian:journal/p/camp
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"Tamsin's Camp","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
tellraw @a {"text":"Tamsin's camp. The bedroll is cold, but the fire is still burning, which makes no sense at all.","color":"gray","italic":true}
tellraw @a {"text":"Pinned to the tent pole, in her hand: \"Lamp supplies in the chest. Eight iron nuggets and a torch make a lantern. Crafting table's by the fire.\"","color":"gray","italic":true}
function palemeridian:q/_advance
function palemeridian:hud/refresh
