package com.grandline.combat;

import com.grandline.core.GLConfig;
import com.grandline.player.Characters;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Agreed PvP rule: opt-in. Allowed when both players are flagged, or in contested waters (Grand Line/Calm Belt/New World at sea). */
public final class Pvp {
    public static boolean allowed(Player attacker, @Nullable Player victim) {
        if (GLConfig.PVP_ALWAYS_ON.get()) return true;
        if (victim == null) return Characters.get(attacker).pvpFlag;
        if (attacker == victim) return false;
        var a = Characters.get(attacker);
        var v = Characters.get(victim);
        if (a.crewId != null && a.crewId.equals(v.crewId)) return false;
        if (a.pvpFlag && v.pvpFlag) return true;
        return contested(victim) && contested(attacker) && (a.pvpFlag || v.pvpFlag);
    }

    public static boolean contested(Player p) {
        SeaZone z = WorldLayout.zoneAt(p.getBlockX(), p.getBlockZ());
        return z == SeaZone.NEW_WORLD || z == SeaZone.CALM_BELT;
    }

    public static float multiplier() {
        return GLConfig.PVP_DAMAGE_MULTIPLIER.get().floatValue();
    }

    private Pvp() {}
}
