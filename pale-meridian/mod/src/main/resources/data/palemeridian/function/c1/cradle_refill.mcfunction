# Places the Stillglass Lamp in the cradle chest if no lamp is in the chest or already built.
execute if block 120 88 96 minecraft:pearlescent_froglight run return fail
execute if items block 123 87 99 container.* minecraft:pearlescent_froglight run return fail
item replace block 123 87 99 container.13 with minecraft:pearlescent_froglight[minecraft:custom_name={"text":"Stillglass Lamp","italic":false,"color":"aqua"},minecraft:lore=[{"text":"It holds light the way a bell holds a note.","italic":false,"color":"gray"}]] 1
