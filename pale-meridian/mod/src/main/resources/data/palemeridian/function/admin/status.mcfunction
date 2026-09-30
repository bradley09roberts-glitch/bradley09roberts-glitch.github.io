# Operator diagnostics: /function palemeridian:admin/status
tellraw @s {"text":"== Pale Meridian status ==","color":"gold"}
tellraw @s [{"text":"schema "},{"score":{"name":"#schema","objective":"pm.world"}},{"text":"  chapter "},{"score":{"name":"#chapter","objective":"pm.world"}},{"text":"  ending "},{"score":{"name":"#ending","objective":"pm.world"}},{"text":"  current objective #"},{"score":{"name":"#cur","objective":"pm.world"}}]
function palemeridian:admin/quests
