scoreboard players set #r.aldercross pm.world 1
scoreboard players set #req.aldercross pm.world 1
time of palemeridian:pall set 3000
scoreboard players set #chapter pm.world 3
function palemeridian:enc/_set_bulb {x:310,y:81,z:-30,lit:"true"}
function palemeridian:enc/_set_bulb {x:326,y:81,z:-60,lit:"true"}
function palemeridian:enc/_set_bulb {x:334,y:81,z:-34,lit:"true"}
function palemeridian:enc/_set_bulb {x:326,y:81,z:-20,lit:"true"}
function palemeridian:enc/_set_bulb {x:342,y:81,z:-40,lit:"true"}
summon minecraft:bee 308 80 -77 {PersistenceRequired:1b,hive_pos:[I;308,79,-78]}
summon minecraft:bee 312 80 -77 {PersistenceRequired:1b,hive_pos:[I;312,79,-78]}
summon minecraft:bee 316 80 -77 {PersistenceRequired:1b,hive_pos:[I;316,79,-78]}
summon minecraft:bee 320 80 -77 {PersistenceRequired:1b,hive_pos:[I;320,79,-78]}
summon minecraft:bee 308 80 -70 {PersistenceRequired:1b,hive_pos:[I;308,79,-71]}
summon minecraft:bee 312 80 -70 {PersistenceRequired:1b,hive_pos:[I;312,79,-71]}
fill 304 77 -82 324 83 -64 minecraft:cornflower replace minecraft:closed_eyeblossom
fill 304 77 -82 324 83 -64 minecraft:dandelion replace minecraft:pale_moss_carpet
data modify storage palemeridian:npc brannoc set value "restored"
function palemeridian:npc/brannoc/apply_skin
title @a title {"text":"Aldercross","color":"white"}
title @a subtitle {"text":"remembers","color":"gray","italic":true}
playsound minecraft:block.beehive.work master @a ~ ~ ~ 1 1
tellraw @a {"text":"The windmill's lamp swings into life. The fog peels off the orchard rows, and somewhere in the apiary the first bee in forty years goes about its business.","color":"gray","italic":true}
tellraw @a {"text":"(Aldercross is restored. Speak with Brannoc.)","color":"dark_aqua"}
