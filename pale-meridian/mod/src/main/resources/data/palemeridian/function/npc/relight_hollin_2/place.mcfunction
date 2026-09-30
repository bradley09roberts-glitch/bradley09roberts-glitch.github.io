# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score #enc.hollin pm.world matches 1 unless block 104 70 126 #palemeridian:bulbs[lit=true] run return run function palemeridian:npc/relight_hollin_2/at_0
function palemeridian:npc/relight_hollin_2/despawn
