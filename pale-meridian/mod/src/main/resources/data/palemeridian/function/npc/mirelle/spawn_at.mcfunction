$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 384bc19c-b8c0-33a7-b149-4b8df6b5d414 run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;944488860,-1195363417,-1320596595,-155855852],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/mirelle_faded",model:"slim"},immovable:1b,Invulnerable:1b,CustomName:{"text":"Mirelle","color":"red"},CustomNameVisible:1b,description:{"text":"the Baker","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.mirelle"]}
$tp 384bc19c-b8c0-33a7-b149-4b8df6b5d414 $(x) $(y) $(z) $(yaw) 0
$execute unless entity 4452b007-8088-3dfd-b31b-b5166cffaa33 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;1146269703,-2138554883,-1290029802,1828694579],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.mirelle"]}
scoreboard players set 4452b007-8088-3dfd-b31b-b5166cffaa33 pm.nid 7
$tp 4452b007-8088-3dfd-b31b-b5166cffaa33 $(x) $(y) $(z)
function palemeridian:npc/mirelle/apply_skin
