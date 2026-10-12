package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.TerrainChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hoshi Hoshi no Mi — ORIGINAL star Logia. The user becomes starlight and star-stuff: shooting stars, collapsed
 * stars that become gravity wells, a comet's dash, starfall, constellations of light that bind enemies together, and
 * a supernova. Under the open night sky the star burns brighter (+20% damage, slow regeneration).
 */
public final class HoshiFruit {

    /** Pull living foes and hostile projectiles towards a point. */
    static void gravity(LivingEntity u, Vec3 at, double r, double strength) {
        for (LivingEntity e : PowerKit.foes(u, at, r)) PowerKit.pull(e, at, strength, 0.02);
        for (Projectile p : PowerKit.hostileProjectiles(u, at, r)) {
            Vec3 to = at.subtract(p.position());
            if (to.lengthSqr() < 1) p.discard();
            else {
                p.setDeltaMovement(p.getDeltaMovement().scale(0.8).add(to.normalize().scale(0.25)));
                p.hurtMarked = true;
            }
        }
    }

    public static void init(DevilFruit f) {
        Hits.Info light = FruitKit.info(Element.LIGHT, 0.8f);

        // Shooting star: a streak of starlight that bursts on impact.
        f.add(f.ability("shooting_star").cooldown(18).stamina(9).anim("punch_extend").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "hoshi_star", 0.5f, 3.0, FruitKit.dmg(c, 7), light.withPoise(25));
            p.explosion = 1.5f;
            p.life = 30;
            return true;
        }));

        // Gravity well: a collapsed star at a point drags everything in — even arrows and bullets — then bursts.
        f.add(f.ability("gravity_well").unlock(8).cooldown(160).stamina(24).anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 22).add(0, 1, 0);
            double r = 6 + c.mastery / 25.0;
            OriginalKit.pulses(c, 0, 4, 15, n -> {
                gravity(u, at, r, 0.3);
                FruitKit.fx(u, "hoshi_well", at, (float) r, 0, 0);
                if (n % 5 == 4) for (LivingEntity e : PowerKit.foes(u, at, r)) Hits.hit(u, e, FruitKit.dmg(c, 1.5f), FruitKit.info(Element.LIGHT, 0f).withPoise(8));
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 62, () -> {
                for (LivingEntity e : PowerKit.foes(u, at, 3.5)) Hits.hit(u, e, FruitKit.dmg(c, 10), light.withStun(15).withPoise(60));
                FruitKit.fx(u, "hoshi_burst", at, 3.5f, 0, 0);
                PowerKit.sound(c, at, SoundEvents.FIREWORK_ROCKET_BLAST, 1.5f, 0.6f);
            });
            return true;
        }));

        // Comet dash: the user streaks across the sky as a comet, burning through everyone in the way.
        f.add(f.ability("comet_dash").unlock(12).cooldown(70).stamina(16).anim("dash").exec((u, c) -> {
            Vec3 start = u.position();
            Vec3 dir = FruitKit.aimDir(c);
            Vec3 end = FruitKit.blink(u, dir, 14);
            for (LivingEntity e : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end) + 1.5, 1.4))
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 8), light.withPoise(35))) PowerKit.push(e, e.position().subtract(dir), 0.6, 0.3);
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 6;
            FruitKit.fx(u, "hoshi_comet", start.add(0, 1, 0), (float) end.x, (float) end.y + 1, (float) end.z);
            PowerKit.sound(c, end, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1f, 0.8f);
            return true;
        }));

        // Starfall: stars rain down on an area, each burst leaving a small crater.
        f.add(f.ability("starfall").unlock(25).cooldown(300).stamina(42).anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 36);
            FruitKit.fx(u, "hoshi_sky", at.add(0, 20, 0), 8, 0, 0);
            for (int i = 0; i < 12; i++) {
                double ox = (u.getRandom().nextDouble() - 0.5) * 12, oz = (u.getRandom().nextDouble() - 0.5) * 12;
                CombatState.of(u).schedule(c.level.getGameTime() + 6 + i * 3L, () -> {
                    Vec3 from = at.add(ox * 0.4, 20, oz * 0.4);
                    Vec3 to = at.add(ox, 0, oz);
                    AbilityProjectile p = AbilityProjectile.shoot(u, "hoshi_meteor", 0.9f, from, to.subtract(from), 1.8, FruitKit.dmg(c, 7), light.withPoise(35));
                    p.explosion = 2.2f;
                    p.life = 60;
                    p.onHit = (pr, hit, pos) -> {
                        if (u.getRandom().nextInt(3) == 0) TerrainChanges.crater(c.level, BlockPos.containing(pos), 1, u);
                    };
                });
            }
            // the first star always lands on the marked foe
            LivingEntity t = PowerKit.target(c, 36);
            if (t != null) CombatState.of(u).schedule(c.level.getGameTime() + 4, () -> {
                if (t.isAlive()) Hits.hit(u, t, FruitKit.dmg(c, 7), light.withPoise(35));
                FruitKit.fx(u, "hoshi_burst", t.position().add(0, 1, 0), 2, 0, 0);
            });
            return true;
        }));

        // Constellation: up to six enemies are marked as stars, then lines of light join them — each is struck and the
        // whole constellation is dragged together.
        f.add(f.ability("constellation").unlock(35).cooldown(320).stamina(40).anim("cast_forward").exec((u, c) -> {
            List<LivingEntity> stars = new ArrayList<>();
            LivingEntity first = PowerKit.target(c, 24);
            if (first != null) stars.add(first);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 22)) if (stars.size() < 6 && !stars.contains(e)) stars.add(e);
            if (stars.isEmpty()) return false;
            for (LivingEntity e : stars) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0));
                FruitKit.fx(u, "hoshi_mark", e.position().add(0, e.getBbHeight() + 0.4, 0), 0, 0, 0);
            }
            CombatState.of(u).schedule(c.level.getGameTime() + 15, () -> {
                Vec3 mid = Vec3.ZERO;
                int n = 0;
                for (LivingEntity e : stars) if (e.isAlive()) {
                    mid = mid.add(e.position());
                    n++;
                }
                if (n == 0) return;
                mid = mid.scale(1.0 / n);
                LivingEntity prev = null;
                for (LivingEntity e : stars) {
                    if (!e.isAlive()) continue;
                    Hits.hit(u, e, FruitKit.dmg(c, 9 + stars.size()), light.withStun(15).withPoise(50));
                    PowerKit.pull(e, mid, 0.9, 0.1);
                    if (prev != null) FruitKit.fx(u, "hoshi_line", prev.position().add(0, 1, 0), (float) e.getX(), (float) e.getY() + 1, (float) e.getZ());
                    prev = e;
                }
                PowerKit.sound(c, mid, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.5f, 1.2f);
            });
            return true;
        }));

        // Awakening — Supernova: the user becomes a dying star — everything is dragged in, then a blinding blast.
        f.add(f.ability("supernova").unlock(60).cooldown(1200).stamina(95).awakened().anim("roar").exec((u, c) -> {
            long now = c.level.getGameTime();
            double r = 14;
            CombatState.of(u).iframesUntil = now + 50;
            OriginalKit.pulses(c, 0, 4, 10, n -> {
                Vec3 at = u.position().add(0, 1, 0);
                gravity(u, at, r, 0.35);
                FruitKit.fx(u, "hoshi_well", at, (float) r, 1, 0);
            });
            CombatState.of(u).schedule(now + 44, () -> {
                Vec3 at = u.position().add(0, 1, 0);
                Set<Integer> hit = new HashSet<>();
                for (LivingEntity e : PowerKit.foes(u, at, 9)) {
                    if (!hit.add(e.getId())) continue;
                    Hits.hit(u, e, FruitKit.dmg(c, 22), FruitKit.info(Element.LIGHT, 3f).withStun(30).withPoise(100).asUnblockable());
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                }
                TerrainChanges.crater(c.level, u.blockPosition().below(), 3, u);
                FruitKit.fx(u, "hoshi_supernova", at, 9, 0, 0);
                PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.5f);
            });
            return true;
        }));

        // Passive: a star burns brighter in the night sky — +20% damage and slow regeneration under the open night.
        // Weakness: darkness swallows starlight (Darkness attacks always connect and deal 25% more).
        f.intangibleHook = (u, a, info) -> info.element() != Element.DARKNESS;
        f.incomingHook = (u, a, amount, info) -> info.element() == Element.DARKNESS ? amount * 1.25f : amount;
        f.outgoingHook = (u, v, amount, info) -> OriginalKit.nightSky(u) ? amount * 1.2f : amount;
        f.tickHook = u -> {
            if (u.tickCount % 40 == 0 && OriginalKit.nightSky(u) && u.getHealth() < u.getMaxHealth()) {
                u.heal(1f);
                com.grandline.net.GLNetwork.fx(u, "hoshi_twinkle", u.getX(), u.getY() + 1, u.getZ(), 0, 0, 0);
            }
        };
    }

    private HoshiFruit() {}
}
