# The Long Chart: choose the conversation for the current state
execute if score c4.chart pm.q matches 1 run return run function palemeridian:dlg/show/prop/chart/choice
execute if score #ending pm.world matches 1.. run return run function palemeridian:dlg/show/prop/chart/after
execute run return run function palemeridian:dlg/show/prop/chart/dark
