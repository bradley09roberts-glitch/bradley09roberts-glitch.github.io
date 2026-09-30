# A pale miner at deepcut.fig.4
execute positioned 22.5 70 -415.5 unless entity @a[distance=..72] run return fail
execute unless entity 003086e7-986c-3fb9-b1a0-4845edacc4bc run function palemeridian:npc/echo_4/new_0
tp 003086e7-986c-3fb9-b1a0-4845edacc4bc 22.5 70 -415.5 180.0 0
execute unless entity 05c19164-f166-3a44-b0b5-d9054eb20b2d run summon minecraft:interaction 22.5 70 -415.5 {UUID:[I;96571748,-244958652,-1330259707,1320291117],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_4"]}
scoreboard players set 05c19164-f166-3a44-b0b5-d9054eb20b2d pm.nid 16
tp 05c19164-f166-3a44-b0b5-d9054eb20b2d 22.5 70 -415.5
