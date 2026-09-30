# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score #enc.hollin pm.world matches 1 unless block 136 70 112 #palemeridian:bulbs[lit=true] run return run function palemeridian:npc/relight_hollin_1/at_0
function palemeridian:npc/relight_hollin_1/despawn
