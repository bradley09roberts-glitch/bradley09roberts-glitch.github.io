# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score #ending pm.world matches 1.. run return run function palemeridian:npc/hesper/_at {poi:"glassworks.hesper_garden"}
execute run return run function palemeridian:npc/hesper/_at {poi:"meridian.hesper"}
function palemeridian:npc/hesper/despawn
