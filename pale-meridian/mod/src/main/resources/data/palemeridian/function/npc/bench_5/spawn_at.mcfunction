$execute positioned $(x) $(y) $(z) unless entity @a[distance=..72] run return fail
$execute unless entity 529e730b-ac0f-31d5-a279-af4153d2d91b run summon minecraft:text_display $(x) $(y) $(z) {UUID:[I;1386115851,-1408290347,-1569083583,1406327067],billboard:"center",text:{"text":"✎ Survey benchmark","color":"gold"},background:1073741824,Tags:["pm.label","pm.label.bench_5"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,1.5499999999999998f,0f],scale:[0.8f,0.8f,0.8f]}}
$tp 529e730b-ac0f-31d5-a279-af4153d2d91b $(x) $(y) $(z)
$execute unless entity 8647df8a-ca1b-3e84-a0a0-9181ae0f1115 run summon minecraft:interaction $(x) $(y) $(z) {UUID:[I;-2042110070,-904184188,-1600089727,-1374744299],width:1.0f,height:1.2f,response:1b,Tags:["pm.int","pm.int.bench_5"]}
scoreboard players set 8647df8a-ca1b-3e84-a0a0-9181ae0f1115 pm.nid 40
$tp 8647df8a-ca1b-3e84-a0a0-9181ae0f1115 $(x) $(y) $(z)
