# Brannoc at meridian.brannoc
execute positioned 6.5 68 -7.5 unless entity @a[distance=..72] run return fail
execute unless entity afbde27c-30a9-3951-99de-07d41fd64350 run function palemeridian:npc/brannoc/new_0
tp afbde27c-30a9-3951-99de-07d41fd64350 6.5 68 -7.5 180.0 0
execute if data storage palemeridian:npc {brannoc:"faded"} as afbde27c-30a9-3951-99de-07d41fd64350 unless entity @s[tag=pm.skin.faded] run function palemeridian:npc/brannoc/apply_skin
execute if data storage palemeridian:npc {brannoc:"restored"} as afbde27c-30a9-3951-99de-07d41fd64350 unless entity @s[tag=pm.skin.restored] run function palemeridian:npc/brannoc/apply_skin
execute unless entity 896604d6-9f82-30a0-990b-a1cac3fd5d16 run summon minecraft:interaction 6.5 68 -7.5 {UUID:[I;-1989802794,-1618857824,-1727290934,-1006805738],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.brannoc"]}
scoreboard players set 896604d6-9f82-30a0-990b-a1cac3fd5d16 pm.nid 9
tp 896604d6-9f82-30a0-990b-a1cac3fd5d16 6.5 68 -7.5
