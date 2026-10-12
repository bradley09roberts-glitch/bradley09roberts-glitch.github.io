package com.grandline.client.render;

import com.grandline.client.ClientState;
import com.grandline.npc.GLNpc;
import com.grandline.player.Appearance;
import com.grandline.player.Race;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Resolves race, appearance and transformation for any rendered humanoid. */
public final class RaceVisuals {
    public record Info(Race race, Appearance appearance, String zoanForm, String fruit, boolean armament) {}

    private static final Appearance DEFAULT = new Appearance();

    public static Info of(LivingEntity e) {
        if (e instanceof Player p) {
            CompoundTag v = ClientState.visuals(p.getId());
            if (!v.getBoolean("created") && !v.contains("previewRace")) return new Info(Race.HUMAN, DEFAULT, "", "", false);
            Race r = Race.byId(v.contains("previewRace") ? v.getString("previewRace") : v.getString("race"));
            Appearance a = Appearance.load(v.getCompound("appearance"));
            return new Info(r, a, v.getString("zoanForm"), v.getString("fruit"), v.getBoolean("arm"));
        }
        if (e instanceof GLNpc n) {
            Appearance a = new Appearance();
            Race r = Race.byId(n.raceId());
            a.featureColor = r == Race.FISHMAN ? 0x3A7BD5 : 0xA0A0A0;
            return new Info(r, a, n.zoanForm(), n.fruitId() == null ? "" : n.fruitId(), n.flag(GLNpc.FLAG_ARMAMENT));
        }
        return new Info(Race.HUMAN, DEFAULT, "", "", false);
    }

    public static float legScale(Race r) {
        return r == Race.LONG_LEG ? 1.55f : 1f;
    }

    public static float armScale(Race r) {
        return r == Race.LONG_ARM ? 1.45f : 1f;
    }

    private RaceVisuals() {}
}
