# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/foremans_log/_at {poi:"glassworks.log"}
function palemeridian:npc/foremans_log/despawn
