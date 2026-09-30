# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/tamsin_notes/_at {poi:"aldercross.tamsin_note"}
function palemeridian:npc/tamsin_notes/despawn
