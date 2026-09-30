# Odile: choose the conversation for the current state
execute if score #ending pm.world matches 1 run return run function palemeridian:dlg/show/npc/odile/ep_true
execute if score #ending pm.world matches 2 run return run function palemeridian:dlg/show/npc/odile/ep_blank
execute if score c4.chart pm.q matches 1 run return run function palemeridian:dlg/show/npc/odile/view
execute unless score c1.odile pm.q matches 2 run return run function palemeridian:dlg/show/npc/odile/intro
execute if score c1.names pm.q matches 1 run return run function palemeridian:dlg/show/npc/odile/names
execute if score c1.round pm.q matches 1 run return run function palemeridian:dlg/show/npc/odile/round
execute if score c1.lamp pm.q matches 1 run return run function palemeridian:dlg/show/npc/odile/lamp
execute if score c1.surge pm.q matches 1 run return run function palemeridian:dlg/show/npc/odile/surge
execute unless score #odile.told pm.world matches 1 run return run function palemeridian:dlg/show/npc/odile/restored
execute run return run function palemeridian:dlg/show/npc/odile/later
