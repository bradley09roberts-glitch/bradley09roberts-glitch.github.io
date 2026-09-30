execute if score #enc.hollin pm.world matches 1 run return fail
scoreboard players set #enc.hollin pm.world 1
scoreboard players set #t.hollin pm.world 0
scoreboard players set #idle.hollin pm.world 0
scoreboard players set #spawn.hollin pm.world 0
function palemeridian:enc/_set_bulb {x:104,y:70,z:112,lit:"false"}
function palemeridian:enc/_set_bulb {x:136,y:70,z:112,lit:"false"}
function palemeridian:enc/_set_bulb {x:104,y:70,z:126,lit:"false"}
function palemeridian:enc/_set_bulb {x:136,y:70,z:126,lit:"false"}
bossbar set palemeridian:encounter name {"text":"Hold the Light","color":"red"}
bossbar set palemeridian:encounter max 4
bossbar set palemeridian:encounter value 0
bossbar set palemeridian:encounter players @a[x=120,y=67,z=118,distance=..46]
bossbar set palemeridian:encounter visible true
time of palemeridian:surge resume
playsound minecraft:entity.warden.nearby_closer master @a 120 67 118 2 0.5
tellraw @a[x=120,y=67,z=118,distance=..54] [{"text":"The Pall surges. ","color":"red","italic":true},{"text":"The lamps gutter and die. Watchers step out of the fog — they move only when you look away. Relight the lamps.","color":"gray","italic":true}]
