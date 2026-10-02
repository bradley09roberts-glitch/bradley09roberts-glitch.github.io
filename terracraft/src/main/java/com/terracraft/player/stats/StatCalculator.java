package com.terracraft.player.stats;

import com.terracraft.player.TerraPlayerData;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Rebuilds a player's {@link PlayerStats} from all stat sources.
 * <p>
 * Sources are pluggable {@link StatSource}s (armor, accessories, buffs, well-fed...) registered at
 * startup, so new systems add themselves instead of editing this class.
 */
public final class StatCalculator {
    /** Contributes to a player's stats during recalculation. */
    @FunctionalInterface
    public interface StatSource {
        void contribute(Player player, TerraPlayerData data, PlayerStats stats);
    }

    private static final List<StatSource> SOURCES = new ArrayList<>();
    /** Absolute ceiling for mana, as in Terraria. */
    public static final int MANA_CAP = 400;

    private StatCalculator() {}

    public static void addSource(StatSource source) {
        SOURCES.add(source);
    }

    public static void recompute(Player player, TerraPlayerData data) {
        PlayerStats stats = data.stats();
        stats.clear();
        for (StatSource source : SOURCES) {
            source.contribute(player, data, stats);
        }
        stats.maxLife = Math.max(1, data.baseMaxLife() + stats.getInt(Stat.MAX_LIFE));
        stats.maxMana = Math.max(0, Math.min(MANA_CAP, data.baseMaxMana() + stats.getInt(Stat.MAX_MANA)));
        if (data.mana() > stats.maxMana) {
            data.setMana(stats.maxMana);
        }
    }
}
