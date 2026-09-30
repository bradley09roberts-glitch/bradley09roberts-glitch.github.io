# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score #boss pm.world matches 1 unless block -7 98 -14 #palemeridian:bulbs[lit=true] run return run function palemeridian:npc/relay_1/at_0
function palemeridian:npc/relay_1/despawn
