package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.TerrainChanges;
import com.grandline.net.GLNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Kane Kane no Mi — ORIGINAL bell Paramecia. The user's body rings like a great bell. Every Kane attack leaves the
 * target "ringing" (up to 5 stacks); Resonance detonates the ringing for heavy damage that no guard can stop.
 * Sound cannot hurt the user, and anyone who strikes them up close is left dizzy and ringing.
 */
public final class KaneFruit {
    static final String MARK = "kane_ringing";
    static final Map<LivingEntity, Integer> RINGING = Collections.synchronizedMap(new WeakHashMap<>());

    static void ring(LivingEntity e) {
        CombatState s = CombatState.of(e);
        int n = s.hasBuff(MARK) ? RINGING.getOrDefault(e, 0) : 0;
        RINGING.put(e, Math.min(5, n + 1));
        s.buffs.put(MARK, 200);
    }

    static int ringing(LivingEntity e) {
        return CombatState.of(e).hasBuff(MARK) ? RINGING.getOrDefault(e, 0) : 0;
    }

    /** Hit with sound; a landed hit leaves the target ringing. */
    static boolean toll(LivingEntity u, LivingEntity e, float dmg, Hits.Info info) {
        boolean ok = PowerKit.hit(u, e, dmg, info);
        if (ok) ring(e);
        return ok;
    }

    public static void init(DevilFruit f) {
        Hits.Info sound = FruitKit.info(Element.SOUND, 1.8f);

        // Peal: the user rings, sending a shockwave ring outward.
        f.add(f.ability("bell_ring").cooldown(35).stamina(12).anim("cast_ground").exec((u, c) -> {
            Vec3 center = u.position().add(0, 1, 0);
            for (LivingEntity e : PowerKit.foes(u, center, 5.5)) toll(u, e, FruitKit.dmg(c, 6), sound.withPoise(25));
            FruitKit.fx(u, "bell_ring", center, 5.5f, 0, 0);
            PowerKit.sound(c, center, SoundEvents.BELL_BLOCK, 2f, 0.8f);
            return true;
        }));

        // Toll: a phantom bell tolls around one target — stunned and reeling.
        f.add(f.ability("toll").unlock(5).cooldown(90).stamina(18).anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 18);
            if (t == null) return false;
            toll(u, t, FruitKit.dmg(c, 5), FruitKit.info(Element.SOUND, 0f).withStun(50).withPoise(60));
            t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
            FruitKit.fx(u, "bell_toll", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.BELL_BLOCK, 2f, 0.5f);
            return true;
        }));

        // Sonic peal: a focused beam of sound.
        f.add(f.ability("sonic_peal").unlock(10).cooldown(45).stamina(16).anim("punch_extend").exec((u, c) -> {
            Vec3 from = c.eye().add(0, -0.2, 0);
            Vec3 dir = FruitKit.aimDir(c);
            double len = PowerKit.rayLength(u, from, dir, 22);
            for (LivingEntity e : Hits.line(u, from, dir, len, 1.0)) {
                if (toll(u, e, FruitKit.dmg(c, 7), FruitKit.info(Element.SOUND, 0.8f).withPoise(30)))
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
            Vec3 end = from.add(dir.scale(len));
            FruitKit.fx(u, "sound_beam", from, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, from, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.6f);
            return true;
        }));

        // Resonance: make the ringing inside every struck body resonate — ignores guard, worse the more it rings.
        f.add(f.ability("resonance").unlock(20).cooldown(120).stamina(26).anim("palm_strike").exec((u, c) -> {
            long now = c.level.getGameTime();
            for (LivingEntity e : Hits.cone(u, 6, 80)) {
                if (!Hits.hostileTo(u, e)) continue;
                int stacks = ringing(e);
                CombatState vs = CombatState.of(e);
                if (vs.blocking) {
                    // the guard itself rings apart
                    vs.blocking = false;
                    vs.staggeredUntil = now + 30;
                    GLNetwork.anim(e, "guard_break");
                }
                Hits.hit(u, e, FruitKit.dmg(c, 8 + 4 * stacks), FruitKit.info(Element.SOUND, 1.2f + stacks * 0.3f).withPoise(40 + 15 * stacks).asUnblockable());
                RINGING.remove(e);
                vs.buffs.remove(MARK);
                FruitKit.fx(u, "resonance", e.position().add(0, 1, 0), 1 + stacks, 0, 0);
            }
            PowerKit.sound(c, u.position(), SoundEvents.BELL_RESONATE, 1.5f, 1f);
            return true;
        }));

        // Bell jar: the user becomes a giant bell — untouchable for a moment — then tolls with crushing force.
        f.add(f.ability("bell_dome").unlock(30).cooldown(400).stamina(34).anim("harden").exec((u, c) -> {
            long now = c.level.getGameTime();
            CombatState.of(u).iframesUntil = now + 50;
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 3, false, false));
            FruitKit.fx(u, "bell_dome", u.position(), 2, 0, 0);
            CombatState.of(u).schedule(now + 50, () -> {
                if (!u.isAlive()) return;
                Vec3 center = u.position().add(0, 1, 0);
                for (LivingEntity e : PowerKit.foes(u, center, 8)) toll(u, e, FruitKit.dmg(c, 11), FruitKit.info(Element.SOUND, 2.5f).withStun(30).withPoise(60));
                FruitKit.fx(u, "bell_ring", center, 8, 0, 0);
                PowerKit.sound(c, center, SoundEvents.BELL_BLOCK, 3f, 0.5f);
            });
            return true;
        }));

        // Awakening — Great Bell of Doom: a colossal bell drops from the sky, then its echoes roll outward.
        f.add(f.ability("doom_bell").unlock(55).cooldown(1100).stamina(90).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 36);
            long now = c.level.getGameTime();
            FruitKit.fx(u, "great_bell", at, 4, 20, 0);
            CombatState.of(u).schedule(now + 20, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 8))
                    toll(u, e, FruitKit.dmg(c, 20), FruitKit.info(Element.SOUND, 3f).withStun(50).withPoise(100).asUnblockable());
                TerrainChanges.crater(c.level, BlockPos.containing(at), 3, u);
                FruitKit.fx(u, "bell_ring", at.add(0, 1, 0), 8, 0, 0);
                PowerKit.sound(c, at, SoundEvents.BELL_BLOCK, 4f, 0.3f);
            });
            for (int i = 1; i <= 3; i++) {
                CombatState.of(u).schedule(now + 20 + i * 15L, () -> {
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 12)) {
                        if (toll(u, e, FruitKit.dmg(c, 5), FruitKit.info(Element.SOUND, 0.8f).withPoise(20)))
                            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
                    }
                    FruitKit.fx(u, "bell_ring", at.add(0, 1, 0), 12, 0, 0);
                    PowerKit.sound(c, at, SoundEvents.BELL_BLOCK, 2f, 0.6f);
                });
            }
            return true;
        }));

        // Passive: a body of bell metal — striking it up close leaves the attacker dizzy and ringing.
        f.incomingHook = (u, a, amount, info) -> {
            if (a != null && a.distanceTo(u) < 3.5 && Hits.hostileTo(u, a) && info.element().physical()) {
                ring(a);
                a.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0));
                GLNetwork.fx(u, "bell_ring", u.getX(), u.getY() + 1, u.getZ(), 1.5f, 0, 0);
            }
            return amount;
        };
    }

    private KaneFruit() {}
}
