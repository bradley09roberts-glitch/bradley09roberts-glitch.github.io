package com.terracraft.player.stats;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Boolean abilities granted by equipment or buffs (Terraria's accessory "flags").
 * Abilities that need client-side movement handling are synchronised to the owning client as a bit set.
 */
public enum Ability implements StringRepresentable {
    NO_FALL_DAMAGE,
    KNOCKBACK_IMMUNE,
    FIRE_BLOCK_IMMUNE,
    WATER_WALKING,
    LAVA_WALKING,
    SWIMMING,
    WATER_BREATHING,
    NIGHT_VISION,
    SPELUNKER,
    DANGER_SENSE,
    HUNTER,
    SHINE,
    AUTO_REUSE,
    MANA_FLOWER,
    MAGNET,
    THORNS,
    DASH;

    public static final Codec<Ability> CODEC = StringRepresentable.fromEnum(Ability::values);

    public Component description() {
        return Component.translatable("ability.terracraft." + getSerializedName());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
