$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 816e3979-45b6-3612-9bf8-b899620fafda run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;-2123482759,1169569298,-1678198631,1645195226],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/echo_9_faded",model:"slim"},immovable:1b,Invulnerable:1b,CustomName:{"text":"A pale miner","color":"gray"},CustomNameVisible:1b,description:{"text":"","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.echo_9"]}
$tp 816e3979-45b6-3612-9bf8-b899620fafda $(x) $(y) $(z) $(yaw) 0
$execute unless entity 393750e9-f361-3a58-9ee2-a17368ccf45a run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;959926505,-211731880,-1629314701,1758262362],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_9"]}
scoreboard players set 393750e9-f361-3a58-9ee2-a17368ccf45a pm.nid 21
$tp 393750e9-f361-3a58-9ee2-a17368ccf45a $(x) $(y) $(z)
function palemeridian:npc/echo_9/apply_skin
