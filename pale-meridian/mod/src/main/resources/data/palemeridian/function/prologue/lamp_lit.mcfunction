particle minecraft:end_rod 5 90.5 346 0.3 0.3 0.3 0.02 40 normal
playsound minecraft:block.beacon.activate master @a 5 90 346 1.5 1.2
playsound minecraft:block.bell.resonate master @a 5 90 346 1.0 0.8
tellraw @a {"text":"The lamp catches. For a moment it burns much too bright, and the fog draws back from the Landing like a tide going out.","color":"gray","italic":true}
scoreboard players set #r.landing pm.world 1
scoreboard players set #req.restore pm.world 1
time of palemeridian:pall set 1000
