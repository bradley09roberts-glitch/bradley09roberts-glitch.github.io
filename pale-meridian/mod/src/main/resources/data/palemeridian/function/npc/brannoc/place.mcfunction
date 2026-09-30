# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score c4.lens pm.q matches 2 unless score #ending pm.world matches 1.. run return run function palemeridian:npc/brannoc/_at {poi:"meridian.brannoc"}
execute run return run function palemeridian:npc/brannoc/_at {poi:"aldercross.brannoc"}
function palemeridian:npc/brannoc/despawn
