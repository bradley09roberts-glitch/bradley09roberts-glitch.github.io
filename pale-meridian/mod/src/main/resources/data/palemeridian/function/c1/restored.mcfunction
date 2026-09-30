scoreboard players set #r.hollin pm.world 1
scoreboard players set #req.hollin pm.world 1
time of palemeridian:pall set 2000
scoreboard players set #chapter pm.world 2
function palemeridian:enc/_set_bulb {x:101,y:70,z:118,lit:"true"}
function palemeridian:enc/_set_bulb {x:139,y:70,z:118,lit:"true"}
function palemeridian:enc/_set_bulb {x:120,y:70,z:103,lit:"true"}
function palemeridian:enc/_set_bulb {x:120,y:70,z:133,lit:"true"}
function palemeridian:enc/_set_bulb {x:108,y:70,z:140,lit:"true"}
function palemeridian:enc/_set_bulb {x:114,y:70,z:128,lit:"true"}
function palemeridian:enc/_set_bulb {x:162,y:70,z:109,lit:"true"}
function palemeridian:enc/_set_bulb {x:96,y:70,z:102,lit:"true"}
function palemeridian:enc/_set_bulb {x:89,y:70,z:92,lit:"true"}
function palemeridian:enc/_set_bulb {x:104,y:70,z:112,lit:"true"}
function palemeridian:enc/_set_bulb {x:136,y:70,z:112,lit:"true"}
function palemeridian:enc/_set_bulb {x:104,y:70,z:126,lit:"true"}
function palemeridian:enc/_set_bulb {x:136,y:70,z:126,lit:"true"}
data modify storage palemeridian:npc odile set value "restored"
data modify storage palemeridian:npc mirelle set value "restored"
data modify storage palemeridian:npc jory set value "restored"
function palemeridian:npc/odile/apply_skin
function palemeridian:npc/mirelle/apply_skin
function palemeridian:npc/jory/apply_skin
setworldspawn 120 67 128 180 0
playsound minecraft:block.bell.resonate master @a ~ ~ ~ 2 1.0
title @a title {"text":"Hollin","color":"white"}
title @a subtitle {"text":"remembers","color":"gray","italic":true}
tellraw @a {"text":"The fog rolls back from the square like a tide going out. Colour seeps into the thatch, the moss, the faces at the windows.","color":"gray","italic":true}
tellraw @a {"text":"(Hollin is restored. Speak with Odile.)","color":"dark_aqua"}
