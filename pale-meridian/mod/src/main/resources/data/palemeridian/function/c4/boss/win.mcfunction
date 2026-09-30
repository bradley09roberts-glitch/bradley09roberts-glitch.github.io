scoreboard players set #boss pm.world 2
kill @e[type=creaking,tag=pm.bossadd]
bossbar set palemeridian:boss visible false
time of palemeridian:surge pause
time of palemeridian:surge set 0
function palemeridian:enc/_set_bulb {x:-16,y:98,z:-23,lit:"true"}
function palemeridian:enc/_set_bulb {x:-7,y:98,z:-14,lit:"true"}
function palemeridian:enc/_set_bulb {x:-16,y:98,z:-5,lit:"true"}
function palemeridian:enc/_set_bulb {x:-25,y:98,z:-14,lit:"true"}
particle minecraft:white_ash -15 99 -13 6 4 6 0.05 800 normal
playsound minecraft:entity.creaking.death master @a -15 96 -13 2 0.5
function palemeridian:q/c4.unlooked/complete
