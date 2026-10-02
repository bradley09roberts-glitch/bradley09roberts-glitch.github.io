package com.terracraft.player.stats;

import com.mojang.serialization.Codec;
import com.terracraft.combat.DamageClass;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Every numeric player statistic that equipment, set bonuses and buffs can modify.
 * <p>
 * {@link Format} controls tooltip rendering: PERCENT values are fractions (0.08 = 8%),
 * FLAT values are plain numbers. Modifiers are additive, exactly like Terraria's stat stacking.
 */
public enum Stat implements StringRepresentable {
    DEFENSE(Format.FLAT),
    MAX_LIFE(Format.FLAT),
    MAX_MANA(Format.FLAT),
    LIFE_REGEN(Format.REGEN),
    MANA_REGEN(Format.FLAT),
    MANA_COST(Format.PERCENT),
    ENDURANCE(Format.PERCENT),

    DAMAGE(Format.PERCENT),
    MELEE_DAMAGE(Format.PERCENT),
    RANGED_DAMAGE(Format.PERCENT),
    MAGIC_DAMAGE(Format.PERCENT),
    SUMMON_DAMAGE(Format.PERCENT),

    CRIT(Format.FLAT_PERCENT),
    MELEE_CRIT(Format.FLAT_PERCENT),
    RANGED_CRIT(Format.FLAT_PERCENT),
    MAGIC_CRIT(Format.FLAT_PERCENT),

    MELEE_SPEED(Format.PERCENT),
    KNOCKBACK(Format.PERCENT),
    ARMOR_PENETRATION(Format.FLAT),
    MAX_MINIONS(Format.FLAT),
    MAX_SENTRIES(Format.FLAT),
    AMMO_CONSERVATION(Format.PERCENT),

    MOVE_SPEED(Format.PERCENT),
    JUMP_HEIGHT(Format.PERCENT),
    EXTRA_JUMPS(Format.FLAT),
    MINING_SPEED(Format.PERCENT),
    REACH(Format.FLAT),
    FALL_DAMAGE(Format.PERCENT),
    LAVA_IMMUNITY_SECONDS(Format.FLAT),
    BREATH(Format.PERCENT);

    public static final Codec<Stat> CODEC = StringRepresentable.fromEnum(Stat::values);

    public enum Format { FLAT, PERCENT, FLAT_PERCENT, REGEN }

    private final Format format;

    Stat(Format format) {
        this.format = format;
    }

    public Format format() {
        return format;
    }

    public Component displayName() {
        return Component.translatable("stat.terracraft." + getSerializedName());
    }

    public static Stat damageFor(DamageClass type) {
        return switch (type) {
            case MELEE -> MELEE_DAMAGE;
            case RANGED -> RANGED_DAMAGE;
            case MAGIC -> MAGIC_DAMAGE;
            case SUMMON -> SUMMON_DAMAGE;
            case GENERIC -> DAMAGE;
        };
    }

    public static Stat critFor(DamageClass type) {
        return switch (type) {
            case MELEE -> MELEE_CRIT;
            case RANGED -> RANGED_CRIT;
            case MAGIC -> MAGIC_CRIT;
            case SUMMON, GENERIC -> CRIT;
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
