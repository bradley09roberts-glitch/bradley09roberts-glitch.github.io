# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score c4.lens pm.q matches 2 unless score #ending pm.world matches 1.. run return run function palemeridian:npc/brannoc/at_0
execute run return run function palemeridian:npc/brannoc/at_1
function palemeridian:npc/brannoc/despawn
