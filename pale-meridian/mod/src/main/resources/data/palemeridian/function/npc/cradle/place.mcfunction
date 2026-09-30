# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/cradle/_at {poi:"meridian.cradle"}
function palemeridian:npc/cradle/despawn
