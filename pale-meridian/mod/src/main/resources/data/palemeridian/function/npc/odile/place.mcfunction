# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score c4.lens pm.q matches 2 unless score #ending pm.world matches 1.. run return run function palemeridian:npc/odile/at_0
execute unless score c1.surge pm.q matches 2 run return run function palemeridian:npc/odile/at_1
execute run return run function palemeridian:npc/odile/at_2
function palemeridian:npc/odile/despawn
