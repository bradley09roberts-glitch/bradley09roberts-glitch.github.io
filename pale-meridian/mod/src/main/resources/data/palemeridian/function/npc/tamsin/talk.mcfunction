# Tamsin Reed: choose the conversation for the current state
execute if score #ending pm.world matches 1 run return run function palemeridian:dlg/show/npc/tamsin/ep_true
execute if score #ending pm.world matches 2 run return run function palemeridian:dlg/show/npc/tamsin/ep_blank
execute if score c4.chart pm.q matches 1 run return run function palemeridian:dlg/show/npc/tamsin/view
execute unless score c3.tamsin pm.q matches 1.. run return run function palemeridian:dlg/show/npc/tamsin/early
execute if score c3.tamsin pm.q matches 1 run return run function palemeridian:c3/meet
execute if score c3.remind pm.q matches 1 run return run function palemeridian:dlg/show/npc/tamsin/remind
execute if score c3.memorial pm.q matches 1 run return run function palemeridian:dlg/show/npc/tamsin/memorial
execute if score c3.kiln pm.q matches 1 run return run function palemeridian:dlg/show/npc/tamsin/kiln
execute if score c3.lamp pm.q matches 1 run return run function palemeridian:dlg/show/npc/tamsin/lamp
execute if score c3.surge pm.q matches 1 run return run function palemeridian:dlg/show/npc/tamsin/surge
execute if score #chapter pm.world matches 4 run return run function palemeridian:dlg/show/npc/tamsin/c4
