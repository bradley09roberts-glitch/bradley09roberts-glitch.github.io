$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity f0691d83-7354-357f-be9e-756d4e105284 run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;-261546621,1934898559,-1096911507,1309692548],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/echo_10_faded",model:"wide"},immovable:1b,Invulnerable:1b,CustomName:{"text":"A pale miner","color":"gray"},CustomNameVisible:1b,description:{"text":"","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.echo_10"]}
$tp f0691d83-7354-357f-be9e-756d4e105284 $(x) $(y) $(z) $(yaw) 0
$execute unless entity c346ef11-85bc-3178-8439-fa749873c4df run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-1018761455,-2051264136,-2076575116,-1737243425],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_10"]}
scoreboard players set c346ef11-85bc-3178-8439-fa749873c4df pm.nid 22
$tp c346ef11-85bc-3178-8439-fa749873c4df $(x) $(y) $(z)
function palemeridian:npc/echo_10/apply_skin
