# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute unless score c3.remind pm.q matches 2 run return run function palemeridian:npc/tamsin/_at {poi:"deepcut.tamsin"}
execute unless score c3.surge pm.q matches 2 run return run function palemeridian:npc/tamsin/_at {poi:"glassworks.tamsin"}
execute unless score c4.lens pm.q matches 2 run return run function palemeridian:npc/tamsin/_at {poi:"hollin.tamsin"}
execute run return run function palemeridian:npc/tamsin/_at {poi:"meridian.tamsin"}
function palemeridian:npc/tamsin/despawn
