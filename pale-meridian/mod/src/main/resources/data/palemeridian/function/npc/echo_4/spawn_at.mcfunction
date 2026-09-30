$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 003086e7-986c-3fb9-b1a0-4845edacc4bc run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;3180263,-1737736263,-1314895803,-307444548],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/echo_4_faded",model:"wide"},immovable:1b,Invulnerable:1b,CustomName:{"text":"A pale miner","color":"gray"},CustomNameVisible:1b,description:{"text":"","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.echo_4"]}
$tp 003086e7-986c-3fb9-b1a0-4845edacc4bc $(x) $(y) $(z) $(yaw) 0
$execute unless entity 05c19164-f166-3a44-b0b5-d9054eb20b2d run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;96571748,-244958652,-1330259707,1320291117],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_4"]}
scoreboard players set 05c19164-f166-3a44-b0b5-d9054eb20b2d pm.nid 16
$tp 05c19164-f166-3a44-b0b5-d9054eb20b2d $(x) $(y) $(z)
function palemeridian:npc/echo_4/apply_skin
