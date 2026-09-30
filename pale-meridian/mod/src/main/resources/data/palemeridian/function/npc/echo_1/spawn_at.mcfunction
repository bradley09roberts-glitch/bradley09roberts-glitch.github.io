$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 6187e44a-275b-326d-958b-679508ea6556 run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;1636295754,660288109,-1786026091,149579094],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/echo_1_faded",model:"wide"},immovable:1b,Invulnerable:1b,CustomName:{"text":"A pale miner","color":"gray"},CustomNameVisible:1b,description:{"text":"","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.echo_1"]}
$tp 6187e44a-275b-326d-958b-679508ea6556 $(x) $(y) $(z) $(yaw) 0
$execute unless entity 0dd87792-5868-3ed6-a83a-c03ab50cd356 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;232290194,1483226838,-1472544710,-1257450666],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_1"]}
scoreboard players set 0dd87792-5868-3ed6-a83a-c03ab50cd356 pm.nid 13
$tp 0dd87792-5868-3ed6-a83a-c03ab50cd356 $(x) $(y) $(z)
function palemeridian:npc/echo_1/apply_skin
