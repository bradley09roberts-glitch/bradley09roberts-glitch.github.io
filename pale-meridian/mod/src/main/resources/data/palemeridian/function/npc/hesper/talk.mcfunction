# Hesper Vane: choose the conversation for the current state
execute unless score c4.keeper pm.q matches 1.. run return run function palemeridian:dlg/show/npc/hesper/early
execute if score c4.keeper pm.q matches 1 run return run function palemeridian:dlg/show/npc/hesper/meet
execute if score c4.lens pm.q matches 1 run return run function palemeridian:dlg/show/npc/hesper/lens
execute if score c4.unlooked pm.q matches 1 run return run function palemeridian:dlg/show/npc/hesper/fight
execute if score c4.chart pm.q matches 1 run return run function palemeridian:dlg/show/npc/hesper/view
execute if score #ending pm.world matches 1 run return run function palemeridian:dlg/show/npc/hesper/ep_true
execute if score #ending pm.world matches 2 run return run function palemeridian:dlg/show/npc/hesper/ep_blank
