# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/vow/_at {poi:"fen.vow"}
function palemeridian:npc/vow/despawn
