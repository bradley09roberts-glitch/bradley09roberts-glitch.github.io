execute if items entity @s container.* *[minecraft:custom_data~{pm:{item:"field_book"}}] run return run tellraw @s {"text":"You already carry your Field Book.","color":"gray"}
function palemeridian:items/field_book
tellraw @s {"text":"A fresh Field Book, copied from your notes.","color":"gray"}
