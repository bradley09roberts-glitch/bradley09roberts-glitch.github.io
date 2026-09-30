# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/chart_table/_at {poi:"meridian.chart_table"}
function palemeridian:npc/chart_table/despawn
