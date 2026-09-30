$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity fccb238b-18e4-3959-9019-36c7fc30e60d run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;-53795957,417610073,-1877395769,-63904243],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/odile_faded",model:"slim"},immovable:1b,Invulnerable:1b,CustomName:{"text":"Odile","color":"gold"},CustomNameVisible:1b,description:{"text":"the Lamplighter","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.odile"],equipment:{mainhand:{id:"minecraft:stick",count:1}}}
$tp fccb238b-18e4-3959-9019-36c7fc30e60d $(x) $(y) $(z) $(yaw) 0
$execute unless entity 7f43cb8f-46e5-330b-8826-43e191f2fd4f run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;2135149455,1189425931,-2010758175,-1846346417],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.odile"]}
scoreboard players set 7f43cb8f-46e5-330b-8826-43e191f2fd4f pm.nid 6
$tp 7f43cb8f-46e5-330b-8826-43e191f2fd4f $(x) $(y) $(z)
function palemeridian:npc/odile/apply_skin
