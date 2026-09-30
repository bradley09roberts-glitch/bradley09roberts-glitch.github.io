# Blueprint landing_lamp: count placed parts, keep ghosts in sync
scoreboard players set #bp pm.tmp 0
execute if block 5 87 346 #palemeridian:bp/landing_lamp_0 run scoreboard players add #bp pm.tmp 1
execute if block 5 87 346 #palemeridian:bp/landing_lamp_0 run kill ba71e26a-4d48-3833-878c-585f5c015152
execute unless block 5 87 346 #palemeridian:bp/landing_lamp_0 unless entity ba71e26a-4d48-3833-878c-585f5c015152 run summon minecraft:block_display 5 87 346 {UUID:[I;-1166941590,1296578611,-2020845473,1543590226],block_state:{Name:"minecraft:stone_bricks"},Glowing:1b,glow_color_override:9764863,brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0.1f,0.1f,0.1f],scale:[0.8f,0.8f,0.8f]},Tags:["pm.ghost","pm.ghost.landing_lamp"]}
execute if block 5 88 346 #palemeridian:bp/landing_lamp_1 run scoreboard players add #bp pm.tmp 1
execute if block 5 88 346 #palemeridian:bp/landing_lamp_1 run kill cd92f685-af5e-3a8e-9fb4-37776ea39c89
execute unless block 5 88 346 #palemeridian:bp/landing_lamp_1 unless entity cd92f685-af5e-3a8e-9fb4-37776ea39c89 run summon minecraft:block_display 5 88 346 {UUID:[I;-846006651,-1352779122,-1615579273,1856216201],block_state:{Name:"minecraft:stone_bricks"},Glowing:1b,glow_color_override:9764863,brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0.1f,0.1f,0.1f],scale:[0.8f,0.8f,0.8f]},Tags:["pm.ghost","pm.ghost.landing_lamp"]}
execute if block 5 89 346 #palemeridian:bp/landing_lamp_2 run scoreboard players add #bp pm.tmp 1
execute if block 5 89 346 #palemeridian:bp/landing_lamp_2 run kill 78c56427-0d09-3648-a9b6-0b4e823e9eee
execute unless block 5 89 346 #palemeridian:bp/landing_lamp_2 unless entity 78c56427-0d09-3648-a9b6-0b4e823e9eee run summon minecraft:block_display 5 89 346 {UUID:[I;2026202151,218707528,-1447687346,-2109825298],block_state:{Name:"minecraft:iron_chain",Properties:{axis:"y",waterlogged:"false"}},Glowing:1b,glow_color_override:9764863,brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0.1f,0.1f,0.1f],scale:[0.8f,0.8f,0.8f]},Tags:["pm.ghost","pm.ghost.landing_lamp"]}
execute if block 5 90 346 #palemeridian:bp/landing_lamp_3 run scoreboard players add #bp pm.tmp 1
execute if block 5 90 346 #palemeridian:bp/landing_lamp_3 run kill 3f4b9c5c-02d4-372a-8866-535ad84e8497
execute unless block 5 90 346 #palemeridian:bp/landing_lamp_3 unless entity 3f4b9c5c-02d4-372a-8866-535ad84e8497 run summon minecraft:block_display 5 90 346 {UUID:[I;1061919836,47462186,-2006559910,-665942889],block_state:{Name:"minecraft:lantern",Properties:{hanging:"false",waterlogged:"false"}},Glowing:1b,glow_color_override:9764863,brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0.1f,0.1f,0.1f],scale:[0.8f,0.8f,0.8f]},Tags:["pm.ghost","pm.ghost.landing_lamp"]}
scoreboard players operation p.lamp pm.qp = #bp pm.tmp
execute if score #bp pm.tmp matches 4.. run function palemeridian:bp/landing_lamp/done
