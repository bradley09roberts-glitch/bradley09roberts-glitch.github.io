# A pale miner at deepcut.fig.2
execute positioned 32.5 70 -411.5 unless entity @a[distance=..72] run return fail
execute unless entity dd3dcead-b65d-3e66-b89e-adcb4e765b70 run function palemeridian:npc/echo_2/new_0
tp dd3dcead-b65d-3e66-b89e-adcb4e765b70 32.5 70 -411.5 180.0 0
execute unless entity 275d9031-5daf-395b-9c31-af04a7056eec run summon minecraft:interaction 32.5 70 -411.5 {UUID:[I;660443185,1571764571,-1674465532,-1492816148],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.echo_2"]}
scoreboard players set 275d9031-5daf-395b-9c31-af04a7056eec pm.nid 14
tp 275d9031-5daf-395b-9c31-af04a7056eec 32.5 70 -411.5
