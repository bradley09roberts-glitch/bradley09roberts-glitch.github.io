# A pale miner at deepcut.fig.11
execute positioned 24.5 70 -411.5 unless entity @a[distance=..72] run return fail
execute unless entity 78271077-3405-300e-a2ac-31987c510b11 run function palemeridian:npc/echo_11/new_0
tp 78271077-3405-300e-a2ac-31987c510b11 24.5 70 -411.5 180.0 0
execute unless entity c111842e-a844-3cf2-b348-1842de669852 run summon minecraft:interaction 24.5 70 -411.5 {UUID:[I;-1055816658,-1471922958,-1287120830,-563701678],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_11"]}
scoreboard players set c111842e-a844-3cf2-b348-1842de669852 pm.nid 23
tp c111842e-a844-3cf2-b348-1842de669852 24.5 70 -411.5
