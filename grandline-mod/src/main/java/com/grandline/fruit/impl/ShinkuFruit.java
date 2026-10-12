package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Shinku Shinku no Mi — ORIGINAL vacuum Logia (wave 4). The thin air of the White Sea, ten thousand metres up, taken
 * to its end: the user becomes empty space. Spheres of nothing that implode, breath stolen from the lungs, a dome of
 * dead air where no sound carries and no flame can burn, and a decompression that blasts everything away.
 * Passive: there is nothing to strike, nothing to ring and little to burn — immune to wind and sound, fire burns at
 * half strength; in the thin air of the White Sea the void is stronger (+15%).
 * Weakness: air rushing in fills the void — Impact shockwaves always connect and deal 25% more.
 */
public final class ShinkuFruit {
    static final String DEAD_AIR = "shinku_dead_air";

    /** Steal some breath: air supply drains, the target gasps (slow, weak). */
    static void gasp(LivingEntity e, int air) {
        e.setAirSupply(Math.max(-20, e.getAirSupply() - air));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0));
    }

    static boolean thinAir(LivingEntity u) {
        return u.level().dimension() == com.grandline.world.SkyWorld.LEVEL || u.getY() > 180;
    }

    public static void init(DevilFruit f) {
        Hits.Info voidHit = FruitKit.info(Element.WIND, 0.2f);

        // Void bullet: a sphere of nothing; where it lands, it implodes and drags whoever is near into the hole.
        f.add(f.ability("void_bullet").cooldown(18).stamina(8).anim("finger_pistol").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "shinku_bullet", 0.45f, 2.4, FruitKit.dmg(c, 6), voidHit.withPoise(20));
            p.life = 36;
            p.onHit = (pr, hit, pos) -> {
                for (LivingEntity e : PowerKit.foes(u, pos, 3)) PowerKit.pull(e, pos, 0.6, 0.05);
                FruitKit.fx(u, "shinku_pop", pos, 3, 0, 0);
            };
            return true;
        }));

        // Breathless: the air is pulled from the target's lungs — choking damage over three seconds, and it gasps.
        f.add(f.ability("breathless").unlock(5).cooldown(160).stamina(18).anim("grab").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 14);
            if (t == null) return false;
            OriginalKit.pulses(c, 0, 10, 6, n -> {
                if (!t.isAlive()) return;
                Hits.hit(u, t, FruitKit.dmg(c, n == 5 ? 5 : 2.5f), voidHit.withPoise(10).asUnblockable());
                gasp(t, 60);
                FruitKit.fx(u, "shinku_gasp", t.position().add(0, t.getBbHeight() * 0.8, 0), t.getBbWidth(), 0, 0);
            });
            PowerKit.sound(c, t.position(), SoundEvents.PUFFER_FISH_BLOW_OUT, 1.5f, 0.5f);
            return true;
        }));

        // Implosion: everything around a point is sucked into a hole of nothing (projectiles too) and crushed when it
        // snaps shut.
        f.add(f.ability("implosion").unlock(12).cooldown(150).stamina(24).anim("double_palm").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 18).add(0, 1, 0);
            double r = 5.5;
            OriginalKit.pulses(c, 0, 4, 5, n -> {
                for (LivingEntity e : PowerKit.foes(u, at, r)) PowerKit.pull(e, at, 0.7, 0.02);
                for (Projectile p : PowerKit.hostileProjectiles(u, at, r)) p.discard();
                FruitKit.fx(u, "shinku_implode", at, (float) r, 0, 0);
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 20, () -> {
                for (LivingEntity e : PowerKit.foes(u, at, 3)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 11), voidHit.withStun(20).withPoise(60));
                    gasp(e, 60);
                }
                FruitKit.fx(u, "shinku_pop", at, 3, 1, 0);
                PowerKit.sound(c, at, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.8f);
            });
            // whoever is already at the centre is caught at once
            for (LivingEntity e : PowerKit.foes(u, at, 2)) Hits.hit(u, e, FruitKit.dmg(c, 3), voidHit.withPoise(15));
            PowerKit.sound(c, at, SoundEvents.PUFFER_FISH_BLOW_OUT, 2f, 0.3f);
            return true;
        }));

        // Dead air: a dome of vacuum around the user for eight seconds. No sound carries and no flame burns inside;
        // enemies gasp, burn out and their arrows drop dead; the user takes nothing from ranged blows inside.
        f.add(f.ability("dead_air").unlock(20).cooldown(500).stamina(30).anim("cast_ground").exec((u, c) -> {
            Vec3 at = u.position();
            double r = 7;
            CombatState.of(u).buffs.put(DEAD_AIR, 160);
            OriginalKit.pulses(c, 0, 10, 16, n -> {
                if (n % 2 == 0) FruitKit.fx(u, "shinku_dome", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    gasp(e, 25);
                    e.clearFire();
                    if (n % 4 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2), voidHit.withPoise(10));
                }
                for (LivingEntity a : OriginalKit.allies(u, at.add(0, 1, 0), r, true)) a.clearFire();
                for (Projectile p : PowerKit.hostileProjectiles(u, at.add(0, 1, 0), r)) {
                    p.setDeltaMovement(Vec3.ZERO);
                    p.discard();
                }
            });
            PowerKit.sound(c, at, SoundEvents.BEACON_DEACTIVATE, 1.5f, 0.5f);
            return true;
        }));

        // Decompression: the user lets the void go — everything around is blasted away and the user rides the
        // rebound high into the air (drifting down slowly).
        f.add(f.ability("decompression").unlock(30).cooldown(200).stamina(30).anim("ground_slam").exec((u, c) -> {
            Vec3 at = u.position();
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 6)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 9), voidHit.withStun(10).withPoise(50))) PowerKit.push(e, at, 1.6, 0.6);
            }
            FruitKit.impulse(u, new Vec3(0, 1.3, 0), false);
            u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
            FruitKit.fx(u, "shinku_blast", at, 6, 0, 0);
            PowerKit.sound(c, at, SoundEvents.WARDEN_SONIC_BOOM, 1f, 1.2f);
            return true;
        }));

        // Awakening — Absolute Void: a vast hole of nothing opens at a point; for three seconds everything is dragged
        // to its centre while choking, then the sky rushes back in with a thunderclap.
        f.add(f.ability("absolute_void").unlock(55).cooldown(1100).stamina(90).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 26).add(0, 1, 0);
            double r = 11;
            OriginalKit.pulses(c, 0, 5, 12, n -> {
                FruitKit.fx(u, "shinku_implode", at, (float) r, 1, 0);
                for (LivingEntity e : PowerKit.foes(u, at, r)) {
                    PowerKit.swirl(e, at, 0.15, 0.6, 0.02);
                    gasp(e, 30);
                    if (n % 4 == 0) Hits.hit(u, e, FruitKit.dmg(c, 3), voidHit.withPoise(15).asUnblockable());
                }
                for (Projectile p : PowerKit.hostileProjectiles(u, at, r)) p.discard();
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 62, () -> {
                for (LivingEntity e : PowerKit.foes(u, at, r)) {
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 20), FruitKit.info(Element.IMPACT, 0f).withStun(40).withPoise(100).asUnblockable()))
                        PowerKit.push(e, at, 1.4, 0.8);
                }
                FruitKit.fx(u, "shinku_blast", at, (float) r, 1, 0);
                PowerKit.sound(c, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 3f, 0.6f);
            });
            PowerKit.sound(c, at, SoundEvents.BEACON_DEACTIVATE, 2f, 0.3f);
            return true;
        }));

        // Passive: a body of nothing (Logia): immune to wind and sound, fire half as strong; Impact always connects
        // (+25%). Thin air (the White Sea, high altitudes) strengthens the void.
        f.intangibleHook = (u, a, info) -> info.element() != Element.IMPACT;
        f.incomingHook = (u, a, amount, info) -> {
            if (info.element() == Element.SOUND) return 0f;
            if (info.element() == Element.IMPACT) return amount * 1.25f;
            if (info.element() == Element.FIRE || info.element() == Element.MAGMA) return amount * 0.5f;
            // inside the dead-air dome, ranged blows lose their push
            if (CombatState.of(u).hasBuff(DEAD_AIR) && a != null && a.distanceTo(u) > 5) return amount * 0.5f;
            return amount;
        };
        f.outgoingHook = (u, v, amount, info) -> thinAir(u) ? amount * 1.15f : amount;
        f.tickHook = u -> {
            u.fallDistance = Math.min(u.fallDistance, 3f);
            if (u.tickCount % 20 == 0 && u.isOnFire()) u.clearFire();
        };
    }

    private ShinkuFruit() {}
}
