# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score #boss pm.world matches 1 unless block -16 98 -5 #palemeridian:bulbs[lit=true] run return run function palemeridian:npc/relay_2/at_0
function palemeridian:npc/relay_2/despawn
