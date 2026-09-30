# Brannoc: choose the conversation for the current state
execute if score #ending pm.world matches 1 run return run function palemeridian:dlg/show/npc/brannoc/ep_true
execute if score #ending pm.world matches 2 run return run function palemeridian:dlg/show/npc/brannoc/ep_blank
execute if score c4.chart pm.q matches 1 run return run function palemeridian:dlg/show/npc/brannoc/view
execute unless score c2.arrive pm.q matches 1.. run return run function palemeridian:dlg/show/npc/brannoc/early
execute unless score c2.brannoc pm.q matches 2 run return run function palemeridian:dlg/show/npc/brannoc/intro
execute if score c2.memories pm.q matches 1 run return run function palemeridian:dlg/show/npc/brannoc/memories
execute if score c2.hearts pm.q matches 1 run return run function palemeridian:dlg/show/npc/brannoc/hearts
execute if score c2.lamp pm.q matches 1 run return run function palemeridian:dlg/show/npc/brannoc/lamp
execute unless score #brannoc.told pm.world matches 1 if score c2.lamp pm.q matches 2 run return run function palemeridian:dlg/show/npc/brannoc/restored
execute if score c2.lamp pm.q matches 2 run return run function palemeridian:dlg/show/npc/brannoc/later
