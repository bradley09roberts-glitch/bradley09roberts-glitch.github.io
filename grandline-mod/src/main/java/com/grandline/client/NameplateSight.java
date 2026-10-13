package com.grandline.client;

import com.grandline.combat.Cover;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Whether the local player can see an NPC, for its name tag: always-on name tags (named characters, quest actors)
 * show only while some part of the body is in plain sight, so they never give away someone behind a wall. Uses the
 * same line test as combat ({@link Cover}); each answer is reused for a quarter of a second.
 */
public final class NameplateSight {
    private static final Map<Integer, long[]> CACHE = new HashMap<>();

    private NameplateSight() {}

    public static boolean visible(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        long now = ClientState.clientTicks;
        long[] c = CACHE.get(e.getId());
        if (c == null || now - c[0] >= 5) {
            boolean v = e.distanceToSqr(mc.player) < 48 * 48 && Cover.canSee(mc.player, e);
            c = new long[]{now, v ? 1 : 0};
            CACHE.put(e.getId(), c);
            if (CACHE.size() > 512) CACHE.entrySet().removeIf(en -> now - en.getValue()[0] > 100);
        }
        return c[1] == 1;
    }
}
