setblock -16 97 -14 minecraft:beacon
particle minecraft:end_rod -16 101 -14 0.3 3 3 0.05 400 normal
playsound minecraft:block.beacon.activate master @a -16 101 -14 3 0.8
playsound minecraft:block.bell.resonate master @a ~ ~ ~ 2 1.2
function palemeridian:enc/_set_bulb {x:-12,y:71,z:8,lit:"true"}
function palemeridian:enc/_set_bulb {x:-8,y:71,z:0,lit:"true"}
function palemeridian:enc/_set_bulb {x:-19,y:71,z:-3,lit:"true"}
function palemeridian:enc/_set_bulb {x:-6,y:71,z:-24,lit:"true"}
setblock -4 67 -20 minecraft:sea_lantern
setblock -3 67 -20 minecraft:sea_lantern
setblock -2 67 -20 minecraft:sea_lantern
setblock -1 67 -20 minecraft:sea_lantern
setblock 0 67 -20 minecraft:sea_lantern
setblock 1 67 -20 minecraft:sea_lantern
setblock 2 67 -20 minecraft:sea_lantern
setblock 3 67 -20 minecraft:sea_lantern
setblock 4 67 -20 minecraft:sea_lantern
setblock 5 67 -20 minecraft:sea_lantern
setblock 6 67 -20 minecraft:sea_lantern
setblock 7 67 -20 minecraft:sea_lantern
setblock 8 67 -20 minecraft:sea_lantern
setblock -4 67 -19 minecraft:sea_lantern
setblock -4 67 -18 minecraft:sea_lantern
setblock -4 67 -17 minecraft:sea_lantern
setblock -4 67 -16 minecraft:sea_lantern
setblock -4 67 -15 minecraft:sea_lantern
setblock -4 67 -14 minecraft:sea_lantern
setblock -4 67 -13 minecraft:sea_lantern
setblock -4 67 -12 minecraft:sea_lantern
setblock -4 67 -11 minecraft:sea_lantern
setblock -4 67 -10 minecraft:sea_lantern
setblock -4 67 -9 minecraft:sea_lantern
fill -4 68 -20 8 68 -8 minecraft:light[level=12,waterlogged=false] replace minecraft:air
tellraw @a {"text":"The Lens catches. A column of cold blue light stands up from the Meridian through the fog, and the fog, for the first time in forty years, has to look back.","color":"gray","italic":true}
tellraw @a {"text":"(Go down to the Chart Room. The Long Chart is waiting, and so is everyone else.)","color":"dark_aqua"}
