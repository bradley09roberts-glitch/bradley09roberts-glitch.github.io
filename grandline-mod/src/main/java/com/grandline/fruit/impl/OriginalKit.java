package com.grandline.fruit.impl;

import com.grandline.combat.AbilityContext;
import com.grandline.combat.CombatState;
import com.grandline.combat.Hits;
import com.grandline.fruit.TerrainChanges;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.ship.ShipEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers shared by the third wave of ORIGINAL fruits: allies and healing, effect transfer, ships (find, judge,
 * repair, wreck), short-lived terrain that reverts early, landing detection and repeating pulses. Terrain always goes
 * through {@link TerrainChanges}, so it is temporary and never touches towns.
 */
public final class OriginalKit {

    // ------------------------------------------------------------------ allies & support

    /** Non-hostile living entities near a point (the user included when inside the radius). */
    public static List<LivingEntity> allies(LivingEntity u, Vec3 center, double r, boolean includeUser) {
        List<LivingEntity> out = new ArrayList<>();
        if (includeUser && u.position().add(0, 1, 0).distanceTo(center) <= r + 1) out.add(u);
        for (LivingEntity e : Hits.sphere(u, center, r)) if (!Hits.hostileTo(u, e) && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)) out.add(e);
        return out;
    }

    /** Heal with a little visual flourish. */
    public static void heal(LivingEntity e, float amount, String fx) {
        if (amount <= 0 || !e.isAlive()) return;
        e.heal(amount);
        if (!fx.isEmpty()) FruitKit.fx(e, fx, e.position().add(0, 1, 0), e.getBbWidth(), e.getBbHeight(), 0);
    }

    /** Remove every harmful effect (and fire). Returns how many were removed. */
    public static int cleanse(LivingEntity e) {
        List<Holder<MobEffect>> bad = new ArrayList<>();
        for (MobEffectInstance i : e.getActiveEffects()) if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && !isSystemEffect(i.getEffect())) bad.add(i.getEffect());
        for (Holder<MobEffect> h : bad) e.removeEffect(h);
        e.clearFire();
        e.setTicksFrozen(0);
        return bad.size();
    }

    /** Seastone drain and knock-outs are rules, not ailments: medicine and balance never move them. */
    static boolean isSystemEffect(Holder<MobEffect> h) {
        return h.is(com.grandline.registry.ModEffects.SEASTONE_DRAIN) || h.is(com.grandline.registry.ModEffects.KNOCKED_OUT);
    }

    /** Effects of one category currently on an entity (copies, so they can be re-applied elsewhere). */
    public static List<MobEffectInstance> effects(LivingEntity e, MobEffectCategory cat) {
        List<MobEffectInstance> out = new ArrayList<>();
        for (MobEffectInstance i : e.getActiveEffects())
            if (i.getEffect().value().getCategory() == cat && !isSystemEffect(i.getEffect()) && i.getDuration() > 0) out.add(new MobEffectInstance(i));
        return out;
    }

    /** Restore stamina (players' gauge; NPCs simply recover). */
    public static void restoreStamina(LivingEntity e, float amount) {
        if (e instanceof ServerPlayer sp) {
            CharacterData d = Characters.get(sp);
            d.stamina = Math.min(d.maxStamina(), d.stamina + amount);
            CharacterManager.markDirty(sp);
        }
    }

    // ------------------------------------------------------------------ ships

    /** Ships whose centre lies within {@code r} of a point (excluding sinking ones). */
    public static List<ShipEntity> ships(LivingEntity u, Vec3 center, double r) {
        List<ShipEntity> out = new ArrayList<>();
        for (ShipEntity s : u.level().getEntitiesOfClass(ShipEntity.class, new AABB(center, center).inflate(r + 20, 12, r + 20), s -> s.isAlive() && !s.isSinking())) {
            if (s.position().distanceTo(center) <= r + s.cls.length / 2.0) out.add(s);
        }
        out.sort((a, b) -> Double.compare(a.distanceToSqr(center), b.distanceToSqr(center)));
        return out;
    }

    /** The user's side: a ship they stand on, own, or that belongs to someone they would not attack. */
    public static boolean friendly(LivingEntity u, ShipEntity s) {
        if (s.carries(u) || s == u.getVehicle()) return true;
        if (s.owner != null) {
            if (s.owner.equals(u.getUUID())) return true;
            Player p = u.level().getPlayerByUUID(s.owner);
            if (p != null) return !Hits.hostileTo(u, p);
        }
        if (s.flag(ShipEntity.F_NPC) && u instanceof GLNpc n) return n.faction().id().equals(s.npcFaction);
        return false;
    }

    /** The ship the user stands on, else their nearest friendly ship within {@code r}. */
    @Nullable
    public static ShipEntity ownShip(LivingEntity u, double r) {
        for (ShipEntity s : ships(u, u.position(), r)) if (s.carries(u) || s == u.getVehicle()) return s;
        for (ShipEntity s : ships(u, u.position(), r)) if (friendly(u, s)) return s;
        return null;
    }

    /** Nearest ship near a point that is not on the user's side. */
    @Nullable
    public static ShipEntity enemyShip(LivingEntity u, Vec3 center, double r) {
        for (ShipEntity s : ships(u, center, r)) if (!friendly(u, s)) return s;
        return null;
    }

    /** Damage a ship's hull and shake whoever stands on its deck. */
    public static void wreck(LivingEntity u, ShipEntity s, float hull) {
        s.hurtHull(hull, u);
        for (LivingEntity e : u.level().getEntitiesOfClass(LivingEntity.class, s.getBoundingBox().inflate(s.cls.length / 2.0, 4, s.cls.length / 2.0), s::carries)) {
            if (e != u && Hits.hostileTo(u, e)) FruitKit.impulse(e, new Vec3((u.getRandom().nextDouble() - 0.5) * 0.4, 0.35, (u.getRandom().nextDouble() - 0.5) * 0.4), false);
        }
    }

    // ------------------------------------------------------------------ terrain

    /**
     * Place a temporary block that reverts after {@code ticks} (instead of the global revert delay). The early
     * restore is scheduled on the user; if they are gone the normal {@link TerrainChanges} revert still applies.
     */
    public static boolean tempFor(AbilityContext c, BlockPos pos, BlockState state, int ticks) {
        ServerLevel l = c.level;
        BlockState original = l.getBlockState(pos);
        if (!PowerKit.placeFree(l, pos, state)) return false;
        BlockPos p = pos.immutable();
        CombatState.of(c.user).schedule(l.getGameTime() + ticks, () -> {
            if (l.getBlockState(p).equals(state)) TerrainChanges.place(l, p, original);
        });
        return true;
    }

    /** Temporary shell around an entity that opens again after {@code ticks}. */
    public static int encaseFor(AbilityContext c, LivingEntity e, BlockState state, int ticks) {
        BlockPos b = e.blockPosition();
        int h = Math.max(2, (int) Math.ceil(e.getBbHeight()));
        int w = Math.max(1, (int) Math.ceil(e.getBbWidth() / 2));
        int n = 0;
        for (int dx = -w; dx <= w; dx++)
            for (int dz = -w; dz <= w; dz++)
                for (int dy = -1; dy <= h; dy++) {
                    boolean inner = Math.abs(dx) < w && Math.abs(dz) < w && dy >= 0 && dy < h;
                    if (inner) continue;
                    BlockPos p = b.offset(dx, dy, dz);
                    if (!c.level.getBlockState(p).canBeReplaced()) continue;
                    BlockState original = c.level.getBlockState(p);
                    if (TerrainChanges.place(c.level, p, state)) {
                        n++;
                        CombatState.of(c.user).schedule(c.level.getGameTime() + ticks, () -> {
                            if (c.level.getBlockState(p).equals(state)) TerrainChanges.place(c.level, p, original);
                        });
                    }
                }
        return n;
    }

    // ------------------------------------------------------------------ timing

    /** Run {@code pulse} every {@code every} ticks, {@code count} times (pulse index passed in), on the user's clock. */
    public static void pulses(AbilityContext c, int delay, int every, int count, java.util.function.IntConsumer pulse) {
        long now = c.level.getGameTime();
        for (int i = 0; i < count; i++) {
            int n = i;
            CombatState.of(c.user).schedule(now + delay + (long) i * every, () -> {
                if (c.user.isAlive()) pulse.accept(n);
            });
        }
    }

    /**
     * Call {@code land} once the user is back on the ground after a jump (checked every tick from tick 3), or at
     * {@code maxTicks} at the latest.
     */
    public static void onLanding(AbilityContext c, int maxTicks, Runnable land) {
        boolean[] done = {false};
        long now = c.level.getGameTime();
        for (int t = 3; t <= maxTicks; t++) {
            boolean last = t == maxTicks;
            CombatState.of(c.user).schedule(now + t, () -> {
                if (done[0] || !c.user.isAlive()) return;
                if (c.user.onGround() || c.user.isInWater() || last) {
                    done[0] = true;
                    land.run();
                }
            });
        }
    }

    /** Whether the user stands under the open night sky. */
    public static boolean nightSky(LivingEntity u) {
        return u.level().isNight() && u.level().canSeeSky(u.blockPosition().above());
    }

    /** A crude "how wanted is this one" for the scales: 0 (innocent) .. ~3 (Emperor-class). */
    public static float crimeWeight(LivingEntity e) {
        if (e instanceof Player p) {
            long b = Characters.get(p).bounty;
            return b <= 0 ? 0.1f : (float) Math.min(3, Math.log10(b + 1) / 3.0);
        }
        if (e instanceof GLNpc n) {
            float w = switch (n.faction()) {
                case PIRATE -> 0.9f;
                case REVOLUTIONARY, BOUNTY_HUNTER -> 0.4f;
                default -> 0.1f;
            };
            if ("bandit".equals(n.role())) w = Math.max(w, 0.7f);
            if (n.flag(GLNpc.FLAG_NOTABLE)) w += 0.6f;
            return Math.min(3, w + n.tier() * 0.12f);
        }
        return e instanceof net.minecraft.world.entity.monster.Enemy ? 0.8f : 0.2f;
    }

    private OriginalKit() {}
}
