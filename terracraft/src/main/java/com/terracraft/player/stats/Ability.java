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
    LAVA_IMMUNE,
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
    DASH,
    /** Panic Necklace: a burst of speed after taking damage. */
    PANIC,
    /** Honey Comb: releases bees and gives a short regeneration boost after taking damage. */
    HONEY_COMB,
    /** Meteor armor set: the Space Gun costs no mana. */
    FREE_SPACE_GUN,
    /** Volatile Gelatin: flings a bouncing gel ball at a nearby enemy every couple of seconds. */
    VOLATILE_GELATIN,
    /** Chlorophyte armor set: a leaf crystal above the player shoots leaves at nearby enemies. */
    LEAF_CRYSTAL,
    /** Sun Stone: all stats up during the day. */
    SUN_STONE,
    /** Gold Ring: coins fly to the player from much further away. */
    COIN_MAGNET,
    /** Lucky Coin: hitting enemies shakes coins out of them. */
    LUCKY_COIN,
    /** Discount Card: shop prices are 20% lower. */
    DISCOUNT;

    public static final Codec<Ability> CODEC = StringRepresentable.fromEnum(Ability::values);

    public Component description() {
        return Component.translatable("ability.terracraft." + getSerializedName());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
