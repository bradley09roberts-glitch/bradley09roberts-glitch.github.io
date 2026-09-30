$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity d2b0fb54-cab4-39b4-8e67-abd8df224940 run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;-760153260,-894158412,-1905808424,-551401152],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/echo_5_faded",model:"slim"},immovable:1b,Invulnerable:1b,CustomName:{"text":"A pale miner","color":"gray"},CustomNameVisible:1b,description:{"text":"","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.echo_5"]}
$tp d2b0fb54-cab4-39b4-8e67-abd8df224940 $(x) $(y) $(z) $(yaw) 0
$execute unless entity d92fb62c-50ae-35b6-90e1-8c2082bf5f2b run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-651184596,1353594294,-1864266720,-2101387477],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_5"]}
scoreboard players set d92fb62c-50ae-35b6-90e1-8c2082bf5f2b pm.nid 17
$tp d92fb62c-50ae-35b6-90e1-8c2082bf5f2b $(x) $(y) $(z)
function palemeridian:npc/echo_5/apply_skin
