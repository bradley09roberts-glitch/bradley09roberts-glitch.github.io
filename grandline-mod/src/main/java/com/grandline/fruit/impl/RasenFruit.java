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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Rasen Rasen no Mi — ORIGINAL spiral Paramecia. Whatever the user touches can be made to spiral: a corkscrew blow
 * that bores straight through a guard, legs coiled like springs, a twist that wrenches a body around, a spiral
 * staircase raised out of nothing, and a screw-lance that drills through stone. Unlike the Uzu whirlpool (which pulls
 * things in), Rasen twists the thing itself.
 */
public final class RasenFruit {
    static final BlockState STEP = Blocks.ORANGE_TERRACOTTA.defaultBlockState();

    /** Wrench a body around: it ends up facing the other way, dizzy and slowed; mobs lose their target. */
    static void twist(LivingEntity e, int ticks) {
        float yaw = e.getYRot() + 180;
        if (e instanceof net.minecraft.server.level.ServerPlayer sp) {
            // players own their view: the turn has to be sent to their client
            sp.teleportTo(sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ(), yaw, sp.getXRot());
        } else {
            e.setYRot(yaw);
            e.setYHeadRot(yaw);
            e.yBodyRot = yaw;
        }
        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks + 40, 0));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1));
        if (e instanceof Mob m) m.setTarget(null);
        e.hurtMarked = true;
    }

    public static void init(DevilFruit f) {
        Hits.Info screw = FruitKit.info(Element.IMPACT, 1.2f);

        // Corkscrew: a twisting punch that bores straight through any guard along a short line.
        f.add(f.ability("corkscrew").cooldown(24).stamina(10).anim("punch_extend").exec((u, c) -> {
            Vec3 from = c.eye().add(0, -0.3, 0);
            Vec3 dir = FruitKit.aimDir(c);
            double len = PowerKit.rayLength(u, from, dir, 6 + u.getScale());
            for (LivingEntity e : Hits.line(u, from, dir, len, 0.9)) {
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 7), screw.withPoise(35).asUnblockable()))
                    PowerKit.swirl(e, e.position().add(dir.scale(-0.5)), 0.4, 0, 0.15);
            }
            Vec3 end = from.add(dir.scale(len));
            FruitKit.fx(u, "rasen_screw", from, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, from, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.8f, 1.6f);
            return true;
        }));

        // Coil spring: the legs coil like springs and launch the user; landing drives a shockwave into the ground.
        f.add(f.ability("coil_spring").unlock(5).cooldown(70).stamina(14).anim("cast_up").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            FruitKit.impulse(u, new Vec3(dir.x * 1.1, 1.25, dir.z * 1.1), true);
            FruitKit.fx(u, "rasen_coil", u.position(), 1.2f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.PISTON_EXTEND, 1f, 0.7f);
            OriginalKit.onLanding(c, 40, () -> {
                Vec3 at = u.position();
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.5, 0), 4.5))
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 8), screw.withStun(15).withPoise(40))) FruitKit.impulse(e, new Vec3(0, 0.6, 0), false);
                FruitKit.fx(u, "rasen_coil", at, 4.5f, 1, 0);
                PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.4f);
            });
            return true;
        }));

        // Twist: wrench a target's body around — it ends up facing the wrong way, reeling and slowed.
        f.add(f.ability("twist").unlock(12).cooldown(110).stamina(18).anim("finger_twist").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 10);
            if (t == null) return false;
            if (PowerKit.hit(u, t, FruitKit.dmg(c, 7), FruitKit.info(Element.IMPACT, 0.2f).withStun(20).withPoise(45))) twist(t, 60);
            FruitKit.fx(u, "rasen_twist", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.CHAIN_BREAK, 1f, 0.6f);
            return true;
        }));

        // Spiral staircase: a helix of steps rises around the user (temporary), hurling anyone at its foot upward.
        f.add(f.ability("spiral_stair").unlock(20).cooldown(240).stamina(22).anim("cast_ground").exec((u, c) -> {
            BlockPos base = u.blockPosition();
            int steps = 14 + c.mastery / 10;
            for (int i = 0; i < steps; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i, () -> {
                    double ang = n * Math.PI / 4;
                    for (double rr = 1.6; rr <= 2.6; rr += 1.0) {
                        BlockPos p = base.offset((int) Math.round(Math.cos(ang) * rr), n / 2, (int) Math.round(Math.sin(ang) * rr));
                        PowerKit.placeFree(c.level, p, STEP);
                    }
                });
            }
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 0.5, 0), 3.5)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 5), screw.withPoise(30))) {
                    FruitKit.impulse(e, new Vec3(0, 0.9, 0), false);
                    PowerKit.swirl(e, u.position(), 0.6, 0, 0);
                }
            }
            FruitKit.fx(u, "rasen_helix", u.position(), 2.5f, steps / 2f, 0);
            PowerKit.sound(c, u.position(), SoundEvents.PACKED_MUD_PLACE, 1.2f, 0.8f);
            return true;
        }));

        // Screw lance: a spinning lance that drills through enemies and soft stone and twists whatever it pierces.
        f.add(f.ability("screw_lance").unlock(30).cooldown(150).stamina(30).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "rasen_lance", 0.7f, 2.4, FruitKit.dmg(c, 14), FruitKit.info(Element.PIERCE, 1.0f).withPoise(60));
            p.pierce = true;
            p.breaksBlocks = true;
            p.life = 40;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) twist(hit, 40);
            };
            // a twisting point-blank burst so the throw never whiffs at melee range
            for (LivingEntity e : Hits.cone(u, 2.5 + u.getScale(), 60))
                if (Hits.hostileTo(u, e)) PowerKit.hit(u, e, FruitKit.dmg(c, 5), screw.withPoise(25));
            PowerKit.sound(c, u.position(), SoundEvents.TRIDENT_THROW.value(), 1f, 0.7f);
            return true;
        }));

        // Awakening — Helix Ascension: the ground itself spirals upward at a point, carrying enemies up a helix, then
        // the spiral snaps shut and slams them down.
        f.add(f.ability("helix_ascension").unlock(55).cooldown(1000).stamina(85).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 28);
            double r = 7;
            OriginalKit.pulses(c, 0, 4, 12, n -> {
                FruitKit.fx(u, "rasen_helix", at, (float) r, 2 + n * 0.6f, 1);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r + n * 0.2)) {
                    PowerKit.swirl(e, at, 0.5, 0.25, 0.35);
                    if (n % 3 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2.5f), FruitKit.info(Element.IMPACT, 0f).withPoise(10));
                }
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 50, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 4, 0), r + 4)) {
                    FruitKit.impulse(e, new Vec3(0, -2.4, 0), true);
                    Hits.hit(u, e, FruitKit.dmg(c, 18), FruitKit.info(Element.IMPACT, 2f).withStun(30).withPoise(100).asUnblockable());
                }
                TerrainChanges.crater(c.level, BlockPos.containing(at), 3, u);
                FruitKit.fx(u, "rasen_coil", at, 7, 1, 0);
                PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.7f);
            });
            return true;
        }));

        // Passive: spring-coiled body — no fall damage, and blunt blows and shockwaves are partly absorbed by the coil.
        f.tickHook = u -> u.fallDistance = 0;
        f.incomingHook = (u, a, amount, info) -> info.element() == Element.BLUNT || info.element() == Element.IMPACT ? amount * 0.85f : amount;
    }

    private RasenFruit() {}
}
