package com.grandline.npc;

import com.grandline.balance.Tuning;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Melee attack tokens. Only a few foes may press a melee attack on the same victim at once; the rest hold a ring a
 * few steps out, circle, guard and wait for a slot (ranged foes and bosses do not need one). A holder gives its slot
 * up after a short burst so the pressure rotates instead of one foe chaining blows forever. Slots lapse on their own
 * if the holder stops renewing them (it died, lost sight, was knocked out), so nothing can leak.
 */
public final class AttackSlots {
    /** victim -> (attacker entity id -> tick the claim lapses) */
    private static final Map<UUID, Map<Integer, Long>> HELD = new HashMap<>();
    private static final int LAPSE = 30;

    private AttackSlots() {}

    /** Claim or renew a melee slot on the victim; false when all slots are taken by others. */
    public static boolean claim(LivingEntity victim, LivingEntity attacker, long now) {
        Map<Integer, Long> m = HELD.computeIfAbsent(victim.getUUID(), k -> new HashMap<>());
        for (Iterator<Long> it = m.values().iterator(); it.hasNext(); ) if (it.next() < now) it.remove();
        if (m.containsKey(attacker.getId()) || m.size() < Tuning.meleeSlots(victim)) {
            m.put(attacker.getId(), now + LAPSE);
            return true;
        }
        return false;
    }

    public static void release(LivingEntity victim, LivingEntity attacker) {
        Map<Integer, Long> m = HELD.get(victim.getUUID());
        if (m != null) {
            m.remove(attacker.getId());
            if (m.isEmpty()) HELD.remove(victim.getUUID());
        }
    }

    /** How many foes currently hold a slot on the victim (self-test and the threat readout). */
    public static int holders(LivingEntity victim, long now) {
        Map<Integer, Long> m = HELD.get(victim.getUUID());
        if (m == null) return 0;
        int n = 0;
        for (long t : m.values()) if (t >= now) n++;
        return n;
    }

    public static void clear() {
        HELD.clear();
    }
}
