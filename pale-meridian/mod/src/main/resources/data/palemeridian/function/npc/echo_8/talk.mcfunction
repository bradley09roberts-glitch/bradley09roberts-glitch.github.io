# A pale miner: choose the conversation for the current state
execute if score #k.8 pm.world matches 1 run return run function palemeridian:dlg/show/npc/echo_8/named
execute run return run function palemeridian:dlg/show/npc/echo/unnamed
