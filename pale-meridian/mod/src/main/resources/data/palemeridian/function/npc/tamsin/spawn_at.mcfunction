$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity db6205b6-e232-38b4-8a9e-626e74e210bf run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;-614333002,-500025164,-1969331602,1960972479],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/tamsin_faded",model:"slim"},immovable:1b,Invulnerable:1b,CustomName:{"text":"Tamsin Reed","color":"gold"},CustomNameVisible:1b,description:{"text":"Chartered Survey","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.tamsin"],equipment:{mainhand:{id:"minecraft:lantern",count:1}}}
$tp db6205b6-e232-38b4-8a9e-626e74e210bf $(x) $(y) $(z) $(yaw) 0
$execute unless entity e0992e0f-4a4e-3d4d-b38d-e81a605756eb run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-526832113,1246641485,-1282545638,1616336619],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.tamsin"]}
scoreboard players set e0992e0f-4a4e-3d4d-b38d-e81a605756eb pm.nid 11
$tp e0992e0f-4a4e-3d4d-b38d-e81a605756eb $(x) $(y) $(z)
function palemeridian:npc/tamsin/apply_skin
