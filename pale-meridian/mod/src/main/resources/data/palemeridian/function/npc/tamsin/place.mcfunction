# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute unless score c3.remind pm.q matches 2 run return run function palemeridian:npc/tamsin/at_0
execute unless score c3.surge pm.q matches 2 run return run function palemeridian:npc/tamsin/at_1
execute unless score c4.lens pm.q matches 2 run return run function palemeridian:npc/tamsin/at_2
execute run return run function palemeridian:npc/tamsin/at_3
function palemeridian:npc/tamsin/despawn
