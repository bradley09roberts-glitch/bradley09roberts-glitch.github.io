# Mirelle: choose the conversation for the current state
execute if score #ending pm.world matches 1 run return run function palemeridian:dlg/show/npc/mirelle/ep_true
execute if score #ending pm.world matches 2 run return run function palemeridian:dlg/show/npc/mirelle/ep_blank
execute unless score c1.surge pm.q matches 2 run return run function palemeridian:dlg/show/npc/mirelle/faded
execute run return run function palemeridian:dlg/show/npc/mirelle/restored
