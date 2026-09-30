$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity afbde27c-30a9-3951-99de-07d41fd64350 run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;-1346510212,816396625,-1713502252,534135632],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/brannoc_faded",model:"wide"},immovable:1b,Invulnerable:1b,CustomName:{"text":"Brannoc","color":"yellow"},CustomNameVisible:1b,description:{"text":"the Orchard Keeper","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.brannoc"]}
$tp afbde27c-30a9-3951-99de-07d41fd64350 $(x) $(y) $(z) $(yaw) 0
$execute unless entity 896604d6-9f82-30a0-990b-a1cac3fd5d16 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-1989802794,-1618857824,-1727290934,-1006805738],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.brannoc"]}
scoreboard players set 896604d6-9f82-30a0-990b-a1cac3fd5d16 pm.nid 9
$tp 896604d6-9f82-30a0-990b-a1cac3fd5d16 $(x) $(y) $(z)
function palemeridian:npc/brannoc/apply_skin
