# Non-destructive repair: re-run activation rules, HUD, NPC placement and journal sync
function palemeridian:q/_advance
function palemeridian:hud/refresh
function palemeridian:npc/_maintain_all
execute as @a run function palemeridian:journal/sync
tellraw @s {"text":"Re-evaluated quests, HUD, NPCs and journals (non-destructive).","color":"gold"}
