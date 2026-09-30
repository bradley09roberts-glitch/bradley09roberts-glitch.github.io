$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity d15c7dc9-ad9e-34f5-b019-9b855c29ec1a run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;-782467639,-1382140683,-1340499067,1546251290],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/echo_8_faded",model:"wide"},immovable:1b,Invulnerable:1b,CustomName:{"text":"A pale miner","color":"gray"},CustomNameVisible:1b,description:{"text":"","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.echo_8"]}
$tp d15c7dc9-ad9e-34f5-b019-9b855c29ec1a $(x) $(y) $(z) $(yaw) 0
$execute unless entity d3aa9423-a1b5-3296-ba10-bc2bb0127581 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-743795677,-1581960554,-1173308373,-1340967551],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_8"]}
scoreboard players set d3aa9423-a1b5-3296-ba10-bc2bb0127581 pm.nid 20
$tp d3aa9423-a1b5-3296-ba10-bc2bb0127581 $(x) $(y) $(z)
function palemeridian:npc/echo_8/apply_skin
