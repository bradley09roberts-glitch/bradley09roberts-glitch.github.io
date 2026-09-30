execute store result score #n pm.tmp run data get storage palemeridian:tmp eleven
execute if score #n pm.tmp matches 0 run return run dialog show @s palemeridian:journal/eleven_none
data modify storage palemeridian:tmp elevenj set value {list:""}
function palemeridian:ui/_eleven_join
function palemeridian:ui/_eleven_dialog with storage palemeridian:tmp elevenj
