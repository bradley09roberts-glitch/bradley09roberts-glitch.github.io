# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/brannoc/_at {poi:"aldercross.brannoc"}
function palemeridian:npc/brannoc/despawn
