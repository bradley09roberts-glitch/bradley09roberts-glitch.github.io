scoreboard players set #figure pm.world 2
execute at 1644f473-6fca-31b3-97e7-c1bc29fafe4b run particle minecraft:white_ash ~ ~1 ~ 0.4 0.9 0.4 0.01 120 normal
execute at 1644f473-6fca-31b3-97e7-c1bc29fafe4b run playsound minecraft:entity.creaking.freeze master @a ~ ~ ~ 1 0.6
kill 1644f473-6fca-31b3-97e7-c1bc29fafe4b
tellraw @a [{"text":"A figure with a lantern stood on the road, and then there was only fog. ","color":"gray","italic":true},{"text":"Tamsin?","color":"white","italic":true}]
