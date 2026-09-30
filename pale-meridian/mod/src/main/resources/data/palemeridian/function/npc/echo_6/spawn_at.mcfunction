$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 5f771b17-c653-3db3-92c1-97a91e6ea351 run summon minecraft:mannequin $(x) $(y) $(z) {UUID:[I;1601641239,-967623245,-1832806487,510567249],Rotation:[$(yaw)f,0f],profile:{texture:"palemeridian:entity/npc/echo_6_faded",model:"wide"},immovable:1b,Invulnerable:1b,CustomName:{"text":"A pale miner","color":"gray"},CustomNameVisible:1b,description:{"text":"","color":"gray","italic":true},pose:"standing",Tags:["pm.npc","pm.npc.echo_6"]}
$tp 5f771b17-c653-3db3-92c1-97a91e6ea351 $(x) $(y) $(z) $(yaw) 0
$execute unless entity 2e276d8e-13e1-3c21-a243-b9bbb17a0997 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;774335886,333528097,-1572619845,-1317402217],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_6"]}
scoreboard players set 2e276d8e-13e1-3c21-a243-b9bbb17a0997 pm.nid 18
$tp 2e276d8e-13e1-3c21-a243-b9bbb17a0997 $(x) $(y) $(z)
function palemeridian:npc/echo_6/apply_skin
