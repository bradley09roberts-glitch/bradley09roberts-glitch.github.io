# A pale miner at deepcut.fig.9
execute positioned 14.5 70 -415.5 unless entity @a[distance=..72] run return fail
execute unless entity 816e3979-45b6-3612-9bf8-b899620fafda run function palemeridian:npc/echo_9/new_0
tp 816e3979-45b6-3612-9bf8-b899620fafda 14.5 70 -415.5 180.0 0
execute unless entity 393750e9-f361-3a58-9ee2-a17368ccf45a run summon minecraft:interaction 14.5 70 -415.5 {UUID:[I;959926505,-211731880,-1629314701,1758262362],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_9"]}
scoreboard players set 393750e9-f361-3a58-9ee2-a17368ccf45a pm.nid 21
tp 393750e9-f361-3a58-9ee2-a17368ccf45a 14.5 70 -415.5
