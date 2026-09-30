# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score #ending pm.world matches 1.. run return run function palemeridian:npc/hesper/at_0
execute run return run function palemeridian:npc/hesper/at_1
function palemeridian:npc/hesper/despawn
