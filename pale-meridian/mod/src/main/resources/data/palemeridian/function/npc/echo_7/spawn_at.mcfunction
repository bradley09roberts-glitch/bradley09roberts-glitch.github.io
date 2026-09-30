$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity adf443d0-fec4-33f8-854c-e0dd4c9bf4f4 run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;-1376500784,-20696072,-2058559267,1285289204],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/echo_7_faded",model:"wide"},immovable:1b,Invulnerable:1b,CustomName:{"text":"A pale miner","color":"gray"},CustomNameVisible:1b,description:{"text":"","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.echo_7"]}
$tp adf443d0-fec4-33f8-854c-e0dd4c9bf4f4 $(x) $(y) $(z) $(yaw) 0
$execute unless entity d6cd7599-6a50-396e-9676-d3fdfd49ee4c run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-691178087,1783642478,-1770597379,-45486516],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_7"]}
scoreboard players set d6cd7599-6a50-396e-9676-d3fdfd49ee4c pm.nid 19
$tp d6cd7599-6a50-396e-9676-d3fdfd49ee4c $(x) $(y) $(z)
function palemeridian:npc/echo_7/apply_skin
