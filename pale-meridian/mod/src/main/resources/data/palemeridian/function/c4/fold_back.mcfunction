ride @s dismount
effect give @s minecraft:blindness 2 0 true
tp @s 72.5 64 73.5 -135 0
playsound minecraft:block.beacon.deactivate master @s ~ ~ ~ 0.6 0.5
tellraw @s {"text":"The fog folds around you, and folds you back to the shore. The island isn't ready to be seen.","color":"gray","italic":true}
