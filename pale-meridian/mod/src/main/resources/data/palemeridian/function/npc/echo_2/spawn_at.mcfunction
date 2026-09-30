$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity dd3dcead-b65d-3e66-b89e-adcb4e765b70 run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;-583151955,-1235403162,-1197560373,1316379504],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/echo_2_faded",model:"slim"},immovable:1b,Invulnerable:1b,CustomName:{"text":"A pale miner","color":"gray"},CustomNameVisible:1b,description:{"text":"","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.echo_2"]}
$tp dd3dcead-b65d-3e66-b89e-adcb4e765b70 $(x) $(y) $(z) $(yaw) 0
$execute unless entity 275d9031-5daf-395b-9c31-af04a7056eec run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;660443185,1571764571,-1674465532,-1492816148],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_2"]}
scoreboard players set 275d9031-5daf-395b-9c31-af04a7056eec pm.nid 14
$tp 275d9031-5daf-395b-9c31-af04a7056eec $(x) $(y) $(z)
function palemeridian:npc/echo_2/apply_skin
