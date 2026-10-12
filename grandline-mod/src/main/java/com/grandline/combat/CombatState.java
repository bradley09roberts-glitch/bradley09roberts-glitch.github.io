package com.grandline.combat;

import net.minecraft.world.entity.LivingEntity;

import java.util.*;

/** Transient (non-saved) combat state for any living entity: cooldowns, combo, guard, i-frames, stagger, toggles. */
public final class CombatState {
    private static final Map<LivingEntity, CombatState> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    public static CombatState of(LivingEntity e) {
        return STATES.computeIfAbsent(e, k -> new CombatState());
    }

    public final Map<String, Long> cooldowns = new HashMap<>();
    public int comboStep = 0;
    public long lastAttack = -100;
    /** Last ability that executed and when (credits fruit kills for the awakening trial). */
    public String lastAbility = "";
    public long lastAbilityAt = -1000;
    public long nextAttackAllowed = 0;
    public boolean blocking = false;
    public long blockStart = -100;
    public long iframesUntil = 0;
    public long staggeredUntil = 0;
    public float poise = 100;
    public boolean combatMode = false;
    public boolean gliding = false;
    public int lockTarget = -1;
    /** Haki toggles. */
    public boolean observation, armament, conquerorAura;
    /** Fruit toggles (e.g. Hie ice armour, Gomu gear). Keyed by toggle id. */
    public final Set<String> toggles = new HashSet<>();
    /** Ticks remaining for timed buffs keyed by id. */
    public final Map<String, Integer> buffs = new HashMap<>();
    /** Scheduled actions (wind-up, multi-hit). */
    public final List<Scheduled> scheduled = new ArrayList<>();
    public long lastHurtByPlayerTick = -1000;

    public record Scheduled(long tick, Runnable action) {}

    public boolean onCooldown(String id, long now) {
        Long t = cooldowns.get(id);
        return t != null && t > now;
    }

    public long cooldownLeft(String id, long now) {
        Long t = cooldowns.get(id);
        return t == null ? 0 : Math.max(0, t - now);
    }

    public void startCooldown(String id, long now, int ticks) {
        cooldowns.put(id, now + ticks);
    }

    public boolean staggered(long now) {
        return staggeredUntil > now;
    }

    public boolean invulnerable(long now) {
        return iframesUntil > now;
    }

    public boolean hasBuff(String id) {
        return buffs.getOrDefault(id, 0) > 0;
    }

    public void schedule(long tick, Runnable r) {
        synchronized (scheduled) {
            scheduled.add(new Scheduled(tick, r));
        }
    }

    /** Run due scheduled actions; called every server tick for entities with state. */
    public void tick(long now) {
        List<Runnable> due = null;
        synchronized (scheduled) {
            if (!scheduled.isEmpty()) {
                Iterator<Scheduled> it = scheduled.iterator();
                while (it.hasNext()) {
                    Scheduled s = it.next();
                    if (s.tick <= now) {
                        if (due == null) due = new ArrayList<>();
                        due.add(s.action);
                        it.remove();
                    }
                }
            }
        }
        if (due != null) for (Runnable r : due) r.run();
        if (!buffs.isEmpty()) buffs.replaceAll((k, v) -> v - 1);
        buffs.values().removeIf(v -> v <= 0);
        if (poise < 100) poise = Math.min(100, poise + 0.8f);
    }

    public static void tickAll(long now, net.minecraft.server.level.ServerLevel level) {
        List<Map.Entry<LivingEntity, CombatState>> entries;
        synchronized (STATES) {
            entries = new ArrayList<>(STATES.entrySet());
        }
        for (var e : entries) {
            LivingEntity le = e.getKey();
            if (le == null || le.level() != level) continue;
            if (le.isRemoved()) {
                STATES.remove(le);
                continue;
            }
            e.getValue().tick(now);
        }
    }
}
