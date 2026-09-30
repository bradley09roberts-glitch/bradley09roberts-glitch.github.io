$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity ed011aa7-2737-37ee-a9ce-c5043b2a0afc run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;-318694745,657930222,-1446066940,992611068],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/echo_3_faded",model:"slim"},immovable:1b,Invulnerable:1b,CustomName:{"text":"A pale miner","color":"gray"},CustomNameVisible:1b,description:{"text":"","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.echo_3"]}
$tp ed011aa7-2737-37ee-a9ce-c5043b2a0afc $(x) $(y) $(z) $(yaw) 0
$execute unless entity 04afae82-73cb-34cb-9c76-4cc3dbbe75d5 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;78622338,1942697163,-1669968701,-608274987],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_3"]}
scoreboard players set 04afae82-73cb-34cb-9c76-4cc3dbbe75d5 pm.nid 15
$tp 04afae82-73cb-34cb-9c76-4cc3dbbe75d5 $(x) $(y) $(z)
function palemeridian:npc/echo_3/apply_skin
