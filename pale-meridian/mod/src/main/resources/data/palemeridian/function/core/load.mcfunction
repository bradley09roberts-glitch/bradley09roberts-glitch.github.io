# Pale Meridian — load
# Runs on every data-pack (re)load. Everything here is idempotent.
scoreboard objectives add pm.world dummy
scoreboard objectives add pm.q dummy
scoreboard objectives add pm.qp dummy
scoreboard objectives add pm.tmp dummy
scoreboard objectives add pm.nid dummy
scoreboard objectives add pm.talk trigger
scoreboard objectives add pm.ui trigger
scoreboard objectives add pm.joined dummy
scoreboard objectives add pm.seen dummy
scoreboard objectives add pm.chill dummy
scoreboard objectives add pm.dctx dummy
scoreboard objectives add pm.optbar dummy
scoreboard objectives add pm.optfx dummy
scoreboard objectives add pm.optwp dummy
scoreboard objectives add pm.leave minecraft.custom:minecraft.leave_game
scoreboard objectives add pm.death deathCount
scoreboard objectives add pm.kit dummy
bossbar add palemeridian:objective ""
bossbar set palemeridian:objective color white
bossbar set palemeridian:objective style progress
bossbar add palemeridian:encounter ""
bossbar set palemeridian:encounter color red
function palemeridian:poi/init
execute unless score #schema pm.world matches 1.. run scoreboard players set #schema pm.world 1
function palemeridian:core/migrate
execute if score c1.round pm.q matches 2 run setblock 117 86 93 minecraft:iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]
execute if score c1.round pm.q matches 2 run setblock 117 86 93 minecraft:iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]
execute if score #enc.hollin pm.world matches 1 run function palemeridian:enc/hollin/reset
scoreboard objectives add pm.bread dummy
scoreboard objectives add pm.gift2 dummy
execute unless score #k.count pm.world matches 0.. run scoreboard players set #k.count pm.world 0
schedule function palemeridian:core/second 20t replace
function palemeridian:hud/refresh
