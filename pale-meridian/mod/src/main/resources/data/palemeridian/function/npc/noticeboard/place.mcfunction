# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/noticeboard/_at {poi:"landing.noticeboard"}
function palemeridian:npc/noticeboard/despawn
