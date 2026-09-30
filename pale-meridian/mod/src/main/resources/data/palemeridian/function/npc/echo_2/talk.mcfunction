# A pale miner: choose the conversation for the current state
execute if score #k.2 pm.world matches 1 run return run function palemeridian:dlg/show/npc/echo_2/named
execute run return run function palemeridian:dlg/show/npc/echo/unnamed
