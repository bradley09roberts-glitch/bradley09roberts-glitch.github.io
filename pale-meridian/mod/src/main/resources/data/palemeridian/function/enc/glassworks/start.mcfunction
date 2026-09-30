execute if score #enc.glassworks pm.world matches 1 run return fail
scoreboard players set #enc.glassworks pm.world 1
scoreboard players set #t.glassworks pm.world 0
scoreboard players set #idle.glassworks pm.world 0
scoreboard players set #spawn.glassworks pm.world 0
function palemeridian:enc/_set_candle {x:16,y:71,z:-304,lit:"false"}
function palemeridian:enc/_set_candle {x:32,y:71,z:-304,lit:"false"}
function palemeridian:enc/_set_candle {x:16,y:71,z:-288,lit:"false"}
function palemeridian:enc/_set_candle {x:32,y:71,z:-288,lit:"false"}
bossbar set palemeridian:encounter name {"text":"The Collapse","color":"red"}
bossbar set palemeridian:encounter max 4
bossbar set palemeridian:encounter value 0
bossbar set palemeridian:encounter players @a[x=24,y=70,z=-295,distance=..38]
bossbar set palemeridian:encounter visible true
time of palemeridian:surge resume
playsound minecraft:entity.warden.nearby_closer master @a 24 70 -295 2 0.5
tellraw @a[x=24,y=70,z=-295,distance=..46] [{"text":"The Pall surges. ","color":"red","italic":true},{"text":"The lamps gutter and die. Watchers step out of the fog — they move only when you look away. Relight the lamps.","color":"gray","italic":true}]
tellraw @a {"text":"At the mine mouth, timber groans. Figures that are not the eleven step out of the dark.","color":"gray","italic":true}
