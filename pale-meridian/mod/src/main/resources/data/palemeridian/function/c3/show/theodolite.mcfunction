execute if score #c3.show.theodolite pm.world matches 1 run return run tellraw @s {"text":"She already remembers her theodolite.","color":"gray"}
execute unless items entity @s container.* *[minecraft:custom_data~{pm:{item:"theodolite"}}] unless items entity @s weapon.offhand *[minecraft:custom_data~{pm:{item:"theodolite"}}] run return run tellraw @s [{"text":"You are not carrying her theodolite. ","color":"gray"},{"text":"Her theodolite is in the Glassworks office, by the mine mouth.","color":"dark_aqua"}]
scoreboard players set #c3.show.theodolite pm.world 1
scoreboard players add c3.remind pm.qp 1
tellraw @a [{"text":"Tamsin: ","color":"gold"},{"text":"Oh, you beautiful thing. Brass, three screws, and a dent from the time I dropped it off Carrow Bridge. Mine.","color":"white"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 1.2
function palemeridian:hud/refresh
execute if score c3.remind pm.qp matches 3.. run return run function palemeridian:c3/tamsin_restored
function palemeridian:dlg/show/npc/tamsin/remind
