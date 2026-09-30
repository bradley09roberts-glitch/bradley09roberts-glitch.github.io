# First-time world initialisation (runs once; guarded by #init).
scoreboard players set #init pm.world 1
scoreboard players set #chapter pm.world 0
scoreboard players set #rev pm.world 0
scoreboard players set #set.chill pm.world 1
scoreboard players set #set.difficulty pm.world 1
gamerule minecraft:respawn_radius 0
gamerule minecraft:spawn_patrols false
gamerule minecraft:spawn_wandering_traders false
gamerule minecraft:pvp false
time of palemeridian:pall pause
time of palemeridian:pall set 0
time of palemeridian:surge pause
time of palemeridian:surge set 0
function palemeridian:q/_init
scoreboard players set #figure pm.world 0
function palemeridian:q/_advance
function palemeridian:hud/refresh
