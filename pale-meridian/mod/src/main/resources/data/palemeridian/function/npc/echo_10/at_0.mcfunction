# A pale miner at deepcut.fig.10
execute positioned 34.5 70 -415.5 unless entity @a[distance=..72] run return fail
execute unless entity f0691d83-7354-357f-be9e-756d4e105284 run function palemeridian:npc/echo_10/new_0
tp f0691d83-7354-357f-be9e-756d4e105284 34.5 70 -415.5 180.0 0
execute unless entity c346ef11-85bc-3178-8439-fa749873c4df run summon minecraft:interaction 34.5 70 -415.5 {UUID:[I;-1018761455,-2051264136,-2076575116,-1737243425],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_10"]}
scoreboard players set c346ef11-85bc-3178-8439-fa749873c4df pm.nid 22
tp c346ef11-85bc-3178-8439-fa749873c4df 34.5 70 -415.5
