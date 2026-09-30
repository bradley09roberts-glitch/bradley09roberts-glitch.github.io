$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 0e7c3147-5e85-3788-83c8-3d9350da5d92 run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;243020103,1585788808,-2084029037,1356488082],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/jory_faded",model:"wide"},immovable:1b,Invulnerable:1b,CustomName:{"text":"Jory","color":"dark_green"},CustomNameVisible:1b,description:{"text":"the Bell-ringer","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.jory"]}
$tp 0e7c3147-5e85-3788-83c8-3d9350da5d92 $(x) $(y) $(z) $(yaw) 0
$execute unless entity fd0b185e-bb6a-30ae-af17-be891144b9c8 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-49604514,-1150668626,-1357398391,289716680],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.jory"]}
scoreboard players set fd0b185e-bb6a-30ae-af17-be891144b9c8 pm.nid 8
$tp fd0b185e-bb6a-30ae-af17-be891144b9c8 $(x) $(y) $(z)
function palemeridian:npc/jory/apply_skin
