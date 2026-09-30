# Save-schema migrations. Schema 1 is the first release; future versions add steps here, e.g.:
# execute if score #schema pm.world matches 1 run function palemeridian:core/migrate/1_to_2
execute if score #schema pm.world matches 2.. run tellraw @a[tag=pm.admin] {"text":"[Pale Meridian] This world was saved by a newer version of the pack.","color":"red"}
