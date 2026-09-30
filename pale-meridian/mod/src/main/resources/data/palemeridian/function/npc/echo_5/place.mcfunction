# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute unless score #ending pm.world matches 1 run return run function palemeridian:npc/echo_5/_at {poi:"deepcut.fig.5"}
function palemeridian:npc/echo_5/despawn
