package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Doru Doru no Mi — wax. Wax that hardens to steel: shackles, walls, armour, harpoons and the Candle Service Set that
 * slowly turns its victims into wax statues. Fire melts it all.
 */
public final class DoruFruit {
    static final BlockState WAX = Blocks.WHITE_CONCRETE.defaultBlockState();
    static final String ARMOUR = "doru_champion";

    public static void init(DevilFruit f) {
        // Candle Lock: wax shackles harden around the target's feet.
        f.add(f.ability("candle_lock").cooldown(30).stamina(11).anim("throw").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 14);
            if (t == null) return false;
            if (PowerKit.hit(u, t, FruitKit.dmg(c, 5), FruitKit.info(Element.PHYSICAL, 0f).withPoise(25))) {
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50 + c.mastery / 2, 6));
                FruitKit.impulse(t, new Vec3(0, -0.3, 0), false);
            }
            FruitKit.fx(u, "wax_bind", t.position(), t.getBbWidth(), 0.8f, 0);
            PowerKit.sound(c, t.position(), SoundEvents.CANDLE_PLACE, 1.4f, 0.6f);
            return true;
        }));

        // Candle Wall: a wall of hardened wax rises in front of the user.
        f.add(f.ability("candle_wall").unlock(5).cooldown(160).stamina(20).anim("cast_ground").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 mid = u.position().add(dir.scale(3));
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            int placed = 0;
            for (int w = -3; w <= 3; w++) {
                Vec3 p = mid.add(side.scale(w));
                BlockPos g = PowerKit.ground(c.level, (int) Math.floor(p.x), (int) Math.floor(u.getY()), (int) Math.floor(p.z));
                if (g == null) continue;
                for (int h = 1; h <= 3; h++) if (PowerKit.placeFree(c.level, g.above(h), WAX)) placed++;
            }
            // hardening wax splashes whoever stands right where the wall rises
            for (LivingEntity e : PowerKit.foes(u, mid.add(0, 1, 0), 2)) Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.PHYSICAL, 1.5f));
            FruitKit.fx(u, "wax_wall", mid.add(0, 1.5, 0), (float) dir.x, (float) dir.z, 3.5f);
            return placed > 0 || c.target != null;
        }));

        // Candle Champion: a suit of hardened wax armour — tougher and hitting harder (fire melts it off).
        f.add(f.ability("candle_champion").unlock(15).cooldown(600).stamina(28).anim("harden").exec((u, c) -> {
            CombatState.of(u).buffs.put(ARMOUR, 300 + c.mastery * 2);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 2.5)) Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.BLUNT, 1.6f));
            FruitKit.fx(u, "wax_armour", u.position().add(0, 1, 0), 1.2f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.CANDLE_PLACE, 1.4f, 0.4f);
            return true;
        }));

        // Doru Doru Arts: Mori — a harpoon of steel-hard wax.
        f.add(f.ability("arts_mori").unlock(25).cooldown(70).stamina(20).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "wax_harpoon", 0.6f, 2.7, FruitKit.dmg(c, 11), FruitKit.info(Element.PIERCE, 1.2f).withPoise(45));
            p.pierce = true;
            p.life = 30;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
            };
            return true;
        }));

        // Awakening — Candle Service Set: a giant wax cake with a spinning wheel; whoever is caught is slowly coated in
        // wax until they stand there as a statue.
        f.add(f.ability("candle_service_set").unlock(55).cooldown(1000).stamina(80).awakened().anim("cast_ground").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 20);
            double r = 6;
            PowerKit.paintDisc(c.level, at, 5, WAX, WAX);
            List<LivingEntity> caught = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 6L, () -> {
                    FruitKit.fx(u, "wax_spiral", at, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                        if (!caught.contains(e)) caught.add(e);
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, Math.min(6, 1 + n / 2)));
                        if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 1.5f), FruitKit.info(Element.PHYSICAL, 0f).withPoise(8));
                    }
                });
            }
            CombatState.of(u).schedule(c.level.getGameTime() + 62, () -> {
                for (LivingEntity e : caught) {
                    if (!e.isAlive() || e.distanceToSqr(at) > (r + 1) * (r + 1)) continue;
                    Hits.hit(u, e, FruitKit.dmg(c, 10), FruitKit.info(Element.PHYSICAL, 0f).withStun(60).asUnblockable());
                    CanonKit.disable(e, 60, 6);
                    PowerKit.encase(c.level, e, WAX);
                    FruitKit.fx(u, "wax_bind", e.position(), e.getBbWidth(), e.getBbHeight(), 0);
                }
            });
            return true;
        }));

        // Passive: wax hard as steel — physical blows do 15% less (40% less in the Candle Champion armour), but fire
        // melts wax: +50% fire damage, and a fire hit melts the armour off.
        f.incomingHook = (u, a, amount, info) -> {
            CombatState s = CombatState.of(u);
            if (info.element() == Element.FIRE || info.element() == Element.MAGMA) {
                s.buffs.remove(ARMOUR);
                return amount * 1.5f;
            }
            if (!info.element().physical() && info.element() != Element.PHYSICAL) return amount;
            return amount * (s.hasBuff(ARMOUR) ? 0.6f : 0.85f);
        };
        f.outgoingHook = (u, v, amount, info) -> CombatState.of(u).hasBuff(ARMOUR) && info.element().physical() ? amount * 1.4f : amount;
    }

    private DoruFruit() {}
}
