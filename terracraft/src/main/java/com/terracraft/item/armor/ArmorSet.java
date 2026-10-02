package com.terracraft.item.armor;

import com.terracraft.TerraCraft;
import com.terracraft.player.stats.StatEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A Terraria armor set: three pieces (helmet, chest, leggings) and the set bonus granted when all three
 * are worn. The bonus is a {@link StatEffects} bundle, plus an optional description for special bonuses
 * implemented in code (e.g. Meteor armor's free Space Gun shots).
 */
public record ArmorSet(Identifier id, StatEffects bonus) {
    public static ArmorSet of(String name, StatEffects bonus) {
        return new ArmorSet(TerraCraft.id(name), bonus);
    }

    public Component bonusDescription() {
        return Component.translatable("armor_set." + id.getNamespace() + "." + id.getPath() + ".bonus");
    }

    public String asset() {
        return id.getPath();
    }
}
