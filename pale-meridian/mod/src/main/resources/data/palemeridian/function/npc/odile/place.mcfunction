# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute unless score c1.surge pm.q matches 2 run return run function palemeridian:npc/odile/_at {poi:"hollin.odile"}
execute run return run function palemeridian:npc/odile/_at {poi:"hollin.odile_home"}
function palemeridian:npc/odile/despawn
