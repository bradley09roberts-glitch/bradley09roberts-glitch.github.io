package com.terracraft.player.stats;

import com.terracraft.combat.DamageClass;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * The computed Terraria statistics of one player: the sum of permanent upgrades, armor, set bonus,
 * accessories and buffs. Rebuilt by {@link StatCalculator} whenever equipment changes (and periodically),
 * never per damage event, so combat code reads cached values.
 */
public final class PlayerStats {
    private final EnumMap<Stat, Float> values = new EnumMap<>(Stat.class);
    public final EnumSet<Ability> abilities = EnumSet.noneOf(Ability.class);

    /** Final maximum life (Terraria units) and mana. */
    public int maxLife = 100;
    public int maxMana = 20;
    /** Identifier path of the active armor set bonus, or empty. */
    public String activeSetBonus = "";

    public void clear() {
        values.clear();
        abilities.clear();
        activeSetBonus = "";
    }

    public void add(Stat stat, float amount) {
        values.merge(stat, amount, Float::sum);
    }

    public float get(Stat stat) {
        return values.getOrDefault(stat, 0.0F);
    }

    public int getInt(Stat stat) {
        return Math.round(get(stat));
    }

    public boolean has(Ability ability) {
        return abilities.contains(ability);
    }

    public Map<Stat, Float> view() {
        return java.util.Collections.unmodifiableMap(values);
    }

    // ------------------------------------------------------------------ convenience accessors

    public int defense() {
        return Math.max(0, getInt(Stat.DEFENSE));
    }

    /** Additive damage multiplier for a class, e.g. 1.25 for +25%. */
    public float damageMultiplier(DamageClass type) {
        float bonus = get(Stat.DAMAGE);
        if (type != DamageClass.GENERIC) {
            bonus += get(Stat.damageFor(type));
        }
        return Math.max(0.0F, 1.0F + bonus);
    }

    /** Bonus critical strike chance (percent points) for a class, excluding the weapon's own crit. */
    public int critBonus(DamageClass type) {
        float bonus = get(Stat.CRIT);
        if (type != DamageClass.GENERIC && type != DamageClass.SUMMON) {
            bonus += get(Stat.critFor(type));
        }
        return Math.round(bonus);
    }

    public float manaCostMultiplier() {
        return Math.max(0.0F, 1.0F + get(Stat.MANA_COST));
    }

    public int extraJumps() {
        return Math.max(0, getInt(Stat.EXTRA_JUMPS));
    }

    /** Bit set of abilities for client sync. */
    public int abilityBits() {
        int bits = 0;
        for (Ability ability : abilities) {
            bits |= 1 << ability.ordinal();
        }
        return bits;
    }
}
