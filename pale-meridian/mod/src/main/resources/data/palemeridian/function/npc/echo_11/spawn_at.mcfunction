$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 78271077-3405-300e-a2ac-31987c510b11 run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;2015826039,872755214,-1565773416,2085686033],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/echo_11_faded",model:"wide"},immovable:1b,Invulnerable:1b,CustomName:{"text":"A pale miner","color":"gray"},CustomNameVisible:1b,description:{"text":"","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.echo_11"]}
$tp 78271077-3405-300e-a2ac-31987c510b11 $(x) $(y) $(z) $(yaw) 0
$execute unless entity c111842e-a844-3cf2-b348-1842de669852 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-1055816658,-1471922958,-1287120830,-563701678],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_11"]}
scoreboard players set c111842e-a844-3cf2-b348-1842de669852 pm.nid 23
$tp c111842e-a844-3cf2-b348-1842de669852 $(x) $(y) $(z)
function palemeridian:npc/echo_11/apply_skin
