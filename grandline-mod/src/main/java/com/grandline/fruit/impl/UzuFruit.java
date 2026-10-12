package com.grandline.fruit.impl;

import com.grandline.combat.AbilityContext;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.TerrainChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Uzu Uzu no Mi — ORIGINAL whirlpool Paramecia. The user spins anything into a vortex: pulling, spinning and drilling.
 * Near water the whirls draw in real sea water (Water element, which reaches even Logia users); at sea the Maelstrom
 * drags enemies — and boats — down under the waves. On land the same power raises whirlwinds of air.
 */
public final class UzuFruit {

    /** Whether there is water within {@code r} blocks of a point (sampled). */
    static boolean nearWater(ServerLevel l, Vec3 p, int r) {
        BlockPos c = BlockPos.containing(p);
        for (int dx = -r; dx <= r; dx += 2)
            for (int dz = -r; dz <= r; dz += 2)
                for (int dy = -3; dy <= 1; dy++)
                    if (l.getBlockState(c.offset(dx, dy, dz)).is(Blocks.WATER)) return true;
        return false;
    }

    static Element element(AbilityContext c, Vec3 at) {
        return nearWater(c.level, at, 4) ? Element.WATER : Element.WIND;
    }

    /** Spin boats (and their crews) caught in a whirlpool. */
    static void swirlBoats(LivingEntity u, Vec3 center, double r, double spin, double inward) {
        for (Entity b : u.level().getEntitiesOfClass(Boat.class, new AABB(center, center).inflate(r, 4, r))) {
            Vec3 d = b.position().subtract(center).multiply(1, 0, 1);
            double len = d.length();
            if (len < 0.3) continue;
            Vec3 n = d.scale(1 / len);
            b.setDeltaMovement(b.getDeltaMovement().add(new Vec3(-n.z, 0, n.x).scale(spin)).subtract(n.scale(Math.min(inward, len * 0.3))));
            b.hurtMarked = true;
        }
    }

    public static void init(DevilFruit f) {
        // Vortex: a whirl at a point that pulls enemies in and spins them around.
        f.add(f.ability("vortex").cooldown(60).stamina(18).anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 18);
            Element el = element(c, at);
            double r = 4.5 + c.mastery / 25.0;
            for (int i = 0; i < 10; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 4L, () -> {
                    FruitKit.fx(u, el == Element.WATER ? "whirlpool" : "vortex", at, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                        PowerKit.swirl(e, at, 0.45, 0.25, 0.08);
                        if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 1.4f), FruitKit.info(el, 0f).withPoise(8));
                    }
                });
            }
            return true;
        }));

        // Spiral drill: a drilling spiral that bores through enemies and spins them on impact.
        f.add(f.ability("spiral_drill").unlock(5).cooldown(40).stamina(14).anim("thrust").exec((u, c) -> {
            Element el = element(c, u.position());
            AbilityProjectile p = FruitKit.shoot(c, el == Element.WATER ? "water_drill" : "spiral_drill", 0.8f, 2.2, FruitKit.dmg(c, 8), FruitKit.info(el, 0.6f).withStun(10).withPoise(30));
            p.pierce = true;
            p.breaksBlocks = true;
            p.life = 34;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) PowerKit.swirl(hit, hit.position().add(u.getRandom().nextDouble() - 0.5, 0, u.getRandom().nextDouble() - 0.5), 0.6, 0, 0.2);
            };
            return true;
        }));

        // Riptide dash: ride a spinning current; enemies on the way are dragged along and spun out at the end.
        f.add(f.ability("riptide_dash").unlock(10).cooldown(60).stamina(14).anim("dash").exec((u, c) -> {
            Element el = element(c, u.position());
            Vec3 start = u.position();
            Vec3 end = FruitKit.blink(u, c.look(), el == Element.WATER ? 18 : 12);
            for (LivingEntity e : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end) + 1, 1.3)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 6), FruitKit.info(el, 0.2f).withPoise(30))) PowerKit.swirl(e, end, 0.8, 0.4, 0.3);
            }
            FruitKit.fx(u, "whirl_trail", start.add(0, 1, 0), (float) end.x, (float) end.y + 1, (float) end.z);
            return true;
        }));

        // Tidal spiral: the user spins in place, flinging everyone around them outward in a spiral.
        f.add(f.ability("tidal_spiral").unlock(20).cooldown(90).stamina(22).anim("spin_kick").exec((u, c) -> {
            Element el = element(c, u.position());
            Vec3 center = u.position();
            for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), 4.5)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 8), FruitKit.info(el, 0f).withStun(15).withPoise(40))) {
                    PowerKit.swirl(e, center, 1.2, 0, 0.4);
                    PowerKit.push(e, center, 0.5, 0);
                }
            }
            FruitKit.fx(u, el == Element.WATER ? "whirlpool" : "vortex", center, 4.5f, 1, 0);
            PowerKit.sound(c, center, SoundEvents.TRIDENT_RIPTIDE_1.value(), 1f, 1f);
            return true;
        }));

        // Maelstrom: a great whirlpool. At sea it drags enemies (and boats) under; on land it is a smaller dust devil.
        f.add(f.ability("maelstrom").unlock(35).cooldown(300).stamina(45).anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 26);
            boolean sea = nearWater(c.level, at, 5);
            double r = sea ? 10 : 6;
            int pulses = sea ? 24 : 16;
            Element el = sea ? Element.WATER : Element.WIND;
            for (int i = 0; i < pulses; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    FruitKit.fx(u, sea ? "whirlpool" : "vortex", at, (float) r, 0, 0);
                    if (sea) swirlBoats(u, at, r, 0.12, 0.08);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r + 1)) {
                        boolean under = sea && e.isInWater();
                        PowerKit.swirl(e, at, 0.4, 0.35, under ? -0.3 : 0.12);
                        if (under) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                        if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 1.5f), FruitKit.info(el, 0f).withPoise(8));
                    }
                });
            }
            PowerKit.sound(c, at, SoundEvents.GENERIC_SPLASH, 1.5f, 0.5f);
            return true;
        }));

        // Awakening — Charybdis: a colossal maelstrom. On land it carves a spiral trench into the ground.
        f.add(f.ability("charybdis").unlock(60).cooldown(1400).stamina(95).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 40);
            boolean sea = nearWater(c.level, at, 6);
            double r = 16;
            Element el = sea ? Element.WATER : Element.WIND;
            Set<Long> dug = new HashSet<>();
            for (int i = 0; i < 32; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    FruitKit.fx(u, sea ? "whirlpool" : "vortex", at, (float) r, 1, 0);
                    if (sea) swirlBoats(u, at, r, 0.2, 0.12);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                        boolean under = sea && e.isInWater();
                        PowerKit.swirl(e, at, 0.55, 0.5, under ? -0.4 : 0.1);
                        if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2.5f), FruitKit.info(el, 0f).withPoise(12));
                    }
                    if (!sea) {
                        // spiral trench: one step along an inward spiral every pulse
                        double ang = n * 0.6, rad = r * (1 - n / 36.0);
                        BlockPos p = BlockPos.containing(at.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad));
                        if (dug.add(p.asLong())) TerrainChanges.crater(c.level, p, 1, u);
                    }
                });
            }
            return true;
        }));

        // Passive: whirling body — close-range attackers are spun aside and their blows partly deflected.
        f.incomingHook = (u, a, amount, info) -> {
            if (a == null || a.distanceTo(u) > 3.5 || !Hits.hostileTo(u, a)) return amount;
            PowerKit.swirl(a, u.position(), 0.35, 0, 0.1);
            return amount * 0.85f;
        };
    }

    private UzuFruit() {}
}
