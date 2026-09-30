scoreboard players set #boss pm.world 0
kill @e[type=creaking,tag=pm.boss]
kill @e[type=creaking,tag=pm.bossadd]
bossbar set palemeridian:boss visible false
time of palemeridian:surge pause
time of palemeridian:surge set 0
function palemeridian:enc/_set_bulb {x:-16,y:98,z:-23,lit:"true"}
function palemeridian:enc/_set_bulb {x:-7,y:98,z:-14,lit:"true"}
function palemeridian:enc/_set_bulb {x:-16,y:98,z:-5,lit:"true"}
function palemeridian:enc/_set_bulb {x:-25,y:98,z:-14,lit:"true"}
scoreboard players operation #cool.boss pm.world = #seconds pm.world
scoreboard players add #cool.boss pm.world 10
tellraw @a {"text":"The shape on the gallery slumps back into fog. It will stand up again when someone returns to the Lens.","color":"gray","italic":true}
