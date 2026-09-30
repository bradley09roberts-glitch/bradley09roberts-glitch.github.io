# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/mirelle/_at {poi:"hollin.mirelle"}
function palemeridian:npc/mirelle/despawn
