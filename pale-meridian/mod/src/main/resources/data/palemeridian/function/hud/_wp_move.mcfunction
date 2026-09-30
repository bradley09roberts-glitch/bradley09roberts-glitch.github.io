execute if data storage palemeridian:state wp_chunk run function palemeridian:hud/_wp_unforce with storage palemeridian:state wp_chunk
$forceload add $(bx) $(bz)
$data modify storage palemeridian:state wp_chunk set value {x:$(bx),z:$(bz)}
$execute unless entity 45d47dfb-3c9a-3993-93f6-7c52e36149cf run summon minecraft:armor_stand $(x) $(y) $(z) {UUID:[I;1171553787,1016740243,-1812562862,-480163377],Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,Tags:["pm.wp"],attributes:[{id:"minecraft:waypoint_transmit_range",base:100000d}]}
$tp 45d47dfb-3c9a-3993-93f6-7c52e36149cf $(x) $(y) $(z)
waypoint modify 45d47dfb-3c9a-3993-93f6-7c52e36149cf color hex 8FD8FF
waypoint modify 45d47dfb-3c9a-3993-93f6-7c52e36149cf style set palemeridian:lamp
