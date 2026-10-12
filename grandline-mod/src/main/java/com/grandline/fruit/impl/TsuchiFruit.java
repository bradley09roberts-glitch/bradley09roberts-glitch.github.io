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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Tsuchi Tsuchi no Mi — ORIGINAL soil Logia. The user's body is earth: clods and walls of packed soil, landslides,
 * burrowing through the ground and erupting under a foe, earthen prisons and a tectonic upheaval.
 * Rule of the soil body: it is only intangible while it touches the ground — knock a Tsuchi user into the air and
 * they can be hit. Earth grounds lightning (immune), but water turns the body to mud (+25% and solid while wet).
 */
public final class TsuchiFruit {
    static final String MUD = "tsuchi_mud";
    static final BlockState EARTH = Blocks.COARSE_DIRT.defaultBlockState();
    static final BlockState PACKED = Blocks.PACKED_MUD.defaultBlockState();

    public static void init(DevilFruit f) {
        Hits.Info earth = FruitKit.info(Element.BLUNT, 1.2f);

        // Clod shot: a fist of hardened earth that leaves the target's legs heavy with dirt.
        f.add(f.ability("clod_shot").cooldown(18).stamina(8).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "tsuchi_clod", 0.6f, 2.0, FruitKit.dmg(c, 6), earth.withPoise(25));
            p.gravity = 0.02;
            p.life = 40;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            };
            return true;
        }));

        // Earth wall: a wall of packed earth rises in front; enemies standing where it rises are thrown back.
        f.add(f.ability("earth_wall").unlock(5).cooldown(90).stamina(16).anim("cast_ground").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 center = u.position().add(dir.scale(3));
            for (int w = -3; w <= 3; w++) {
                double x = center.x - dir.z * w, z = center.z + dir.x * w;
                BlockPos g = PowerKit.ground(c.level, (int) Math.floor(x), (int) Math.floor(u.getY()), (int) Math.floor(z));
                int baseY = g == null ? (int) Math.floor(u.getY()) - 1 : g.getY();
                for (int y = 1; y <= 3; y++) PowerKit.placeFree(c.level, new BlockPos((int) Math.floor(x), baseY + y, (int) Math.floor(z)), PACKED);
            }
            for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), 3.5)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 4), earth.withPoise(25))) PowerKit.push(e, u.position(), 0.9, 0.3);
            }
            FruitKit.fx(u, "tsuchi_wall", center.add(0, 1.5, 0), (float) dir.x, (float) dir.z, 3.5f);
            PowerKit.sound(c, center, SoundEvents.ROOTED_DIRT_PLACE, 1.5f, 0.6f);
            return true;
        }));

        // Landslide: a rolling wave of earth runs along the ground, burying feet and tossing enemies.
        f.add(f.ability("landslide").unlock(12).cooldown(130).stamina(26).anim("cast_ground").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 start = u.position();
            Set<Integer> struck = new HashSet<>();
            int len = 14 + c.mastery / 10;
            for (int d = 1; d <= len; d++) {
                int step = d;
                CombatState.of(u).schedule(c.level.getGameTime() + d / 2, () -> {
                    Vec3 p = start.add(dir.scale(step));
                    for (int w = -1; w <= 1; w++)
                        PowerKit.paintColumn(c.level, (int) Math.floor(p.x - dir.z * w), (int) Math.floor(start.y), (int) Math.floor(p.z + dir.x * w), EARTH, null);
                    FruitKit.fx(u, "tsuchi_slide", p, (float) dir.x, (float) dir.z, 1.5f);
                    for (LivingEntity e : PowerKit.foes(u, p.add(0, 1, 0), 2.2)) {
                        if (!struck.add(e.getId())) continue;
                        if (PowerKit.hit(u, e, FruitKit.dmg(c, 8), earth.withPoise(40))) {
                            FruitKit.impulse(e, dir.scale(0.6).add(0, 0.5, 0), false);
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                        }
                    }
                });
            }
            PowerKit.sound(c, start, SoundEvents.GRAVEL_BREAK, 1.5f, 0.5f);
            return true;
        }));

        // Burrow: sink into the earth and erupt beneath the target with an uppercut of soil.
        f.add(f.ability("burrow").unlock(20).cooldown(140).stamina(24).anim("ground_slam").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 24);
            long now = c.level.getGameTime();
            CombatState.of(u).iframesUntil = now + 16;
            u.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 16, 0, false, false));
            FruitKit.fx(u, "tsuchi_burrow", u.position(), 1.2f, 0, 0);
            Vec3 exit = t != null ? t.position() : u.position().add(PowerKit.flatAim(c).scale(12));
            CombatState.of(u).schedule(now + 12, () -> {
                if (!u.isAlive()) return;
                Vec3 to = t != null && t.isAlive() ? t.position().add(PowerKit.flatAim(c).scale(-1.2)) : exit;
                PowerKit.place(u, to);
                for (LivingEntity e : PowerKit.foes(u, to.add(0, 1, 0), 3))
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 11), earth.withStun(20).withPoise(60))) FruitKit.impulse(e, new Vec3(0, 1.0, 0), false);
                FruitKit.fx(u, "tsuchi_burrow", to, 2.5f, 1, 0);
                PowerKit.sound(c, to, SoundEvents.ROOTED_DIRT_BREAK, 1.5f, 0.6f);
            });
            return true;
        }));

        // Earth prison: the ground swallows the target up to the neck — packed earth all around, crushing and holding.
        f.add(f.ability("earth_prison").unlock(30).cooldown(240).stamina(32).anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 16);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 9), earth.withStun(60).withPoise(80).asUnblockable());
            OriginalKit.encaseFor(c, t, PACKED, 100);
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 5));
            FruitKit.fx(u, "tsuchi_prison", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.PACKED_MUD_PLACE, 1.5f, 0.5f);
            return true;
        }));

        // Awakening — Tectonic Upheaval: earth pillars erupt across the area, launching whoever stands on them.
        f.add(f.ability("tectonic_upheaval").unlock(55).cooldown(1000).stamina(85).awakened().anim("ground_slam").exec((u, c) -> {
            Vec3 center = FruitKit.aimPoint(c, 24);
            double r = 10;
            for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), r)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            OriginalKit.pulses(c, 0, 3, 16, n -> {
                Vec3 p;
                LivingEntity near = n % 2 == 0 ? PowerKit.foes(u, center.add(0, 1, 0), r).stream().findAny().orElse(null) : null;
                if (near != null) p = near.position();
                else {
                    double a = u.getRandom().nextDouble() * Math.PI * 2, d = u.getRandom().nextDouble() * r;
                    p = center.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                }
                BlockPos g = PowerKit.ground(c.level, (int) Math.floor(p.x), (int) Math.floor(center.y), (int) Math.floor(p.z));
                if (g != null) {
                    int h = 2 + u.getRandom().nextInt(3);
                    for (int y = 1; y <= h; y++) PowerKit.placeFree(c.level, g.above(y), EARTH);
                }
                for (LivingEntity e : PowerKit.foes(u, p.add(0, 1, 0), 2))
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 10), earth.withStun(15).withPoise(50))) FruitKit.impulse(e, new Vec3(0, 1.2, 0), false);
                FruitKit.fx(u, "tsuchi_pillar", p, 1.5f, 4, 0);
            });
            FruitKit.fx(u, "tsuchi_quake", center, (float) r, 0, 0);
            PowerKit.sound(c, center, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.5f);
            return true;
        }));

        // Passive: a body of soil. Intangible only while grounded; earth swallows lightning; water turns it to mud.
        f.tickHook = u -> {
            if (u.tickCount % 10 == 0 && u.isInWater()) CombatState.of(u).buffs.put(MUD, 60);
        };
        f.intangibleHook = (u, a, info) -> u.onGround() && !CombatState.of(u).hasBuff(MUD);
        f.incomingHook = (u, a, amount, info) -> {
            if (info.element() == Element.LIGHTNING) return 0f;
            if (info.element() == Element.WATER) {
                CombatState.of(u).buffs.put(MUD, 80);
                return amount * 1.25f;
            }
            return amount;
        };
    }

    private TsuchiFruit() {}
}
