execute if score #c3.show.notes pm.world matches 1 run return run tellraw @s {"text":"She already remembers her field notes.","color":"gray"}
execute unless items entity @s container.* *[minecraft:custom_data~{pm:{item:"field_notes"}}] unless items entity @s weapon.offhand *[minecraft:custom_data~{pm:{item:"field_notes"}}] run return run tellraw @s [{"text":"You are not carrying her field notes. ","color":"gray"},{"text":"Her field notes should still be at her camp in the Aldercross cider-press loft.","color":"dark_aqua"}]
scoreboard players set #c3.show.notes pm.world 1
scoreboard players add c3.remind pm.qp 1
tellraw @a [{"text":"Tamsin: ","color":"gold"},{"text":"My notes. 'Blanks are never accidents.' I underlined that twice. I was very pleased with myself.","color":"white"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 1.2
function palemeridian:hud/refresh
execute if score c3.remind pm.qp matches 3.. run return run function palemeridian:c3/tamsin_restored
function palemeridian:dlg/show/npc/tamsin/remind
