$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 7419ca57-7245-3c29-a99b-76449898ad3d run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;1947847255,1917140009,-1449429436,-1734824643],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/hesper_faded",model:"slim"},immovable:1b,Invulnerable:1b,CustomName:{"text":"Hesper Vane","color":"dark_aqua"},CustomNameVisible:1b,description:{"text":"Keeper of the Meridian","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.hesper"]}
$tp 7419ca57-7245-3c29-a99b-76449898ad3d $(x) $(y) $(z) $(yaw) 0
$execute unless entity 70c770e7-0913-359d-8abe-304172b303ad run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;1892118759,152253853,-1967247295,1924334509],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.hesper"]}
scoreboard players set 70c770e7-0913-359d-8abe-304172b303ad pm.nid 28
$tp 70c770e7-0913-359d-8abe-304172b303ad $(x) $(y) $(z)
function palemeridian:npc/hesper/apply_skin
