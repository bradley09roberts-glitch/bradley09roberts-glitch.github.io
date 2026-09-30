# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/jory/_at {poi:"hollin.jory"}
function palemeridian:npc/jory/despawn
