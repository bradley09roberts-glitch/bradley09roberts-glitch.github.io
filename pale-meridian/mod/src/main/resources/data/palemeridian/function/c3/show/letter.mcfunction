execute if score #c3.show.letter pm.world matches 1 run return run tellraw @s {"text":"She already remembers the letter.","color":"gray"}
execute unless items entity @s container.* *[minecraft:custom_data~{pm:{item:"letter"}}] unless items entity @s weapon.offhand *[minecraft:custom_data~{pm:{item:"letter"}}] run return run tellraw @s [{"text":"You are not carrying the letter. ","color":"gray"},{"text":"The noticeboard at the Landing still has a copy of her letter.","color":"dark_aqua"}]
scoreboard players set #c3.show.letter pm.world 1
scoreboard players add c3.remind pm.qp 1
tellraw @a [{"text":"Tamsin: ","color":"gold"},{"text":"\"Come yourself. Bring light.\" That's my handwriting. That's... I wrote this to you. You came.","color":"white"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 1.2
function palemeridian:hud/refresh
execute if score c3.remind pm.qp matches 3.. run return run function palemeridian:c3/tamsin_restored
function palemeridian:dlg/show/npc/tamsin/remind
