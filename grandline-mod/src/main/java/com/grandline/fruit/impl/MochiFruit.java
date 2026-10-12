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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Mochi Mochi no Mi — Special Paramecia: a body of mochi that can be shaped into fists, the trident Mogura, spinning
 * donuts and sticky traps. Like a Logia, the body can open around an attack — but only when the user sees it coming
 * with Observation Haki. Awakened, the ground itself becomes mochi.
 */
public final class MochiFruit {
    static final BlockState MOCHI = Blocks.WHITE_CONCRETE.defaultBlockState();

    static void stick(LivingEntity e, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amp));
        if (amp >= 3) FruitKit.impulse(e, new Vec3(0, -0.3, 0), false); // glued down: no jumping out of it
    }

    public static void init(DevilFruit f) {
        Hits.Info mochi = FruitKit.info(Element.BLUNT, 1.6f);
        f.intangibleBody = true;

        // Kaku Mochi: a giant fist of mochi that sticks to whatever it hits.
        f.add(f.ability("kaku_mochi").cooldown(24).stamina(10).anim("punch_extend").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "mochi_fist", 1.2f, 1.9, FruitKit.dmg(c, 8), mochi.withPoise(35));
            p.life = 26;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) stick(hit, 40, 1);
            };
            return true;
        }));

        // Mochi Tsuki: a thrust of the mochi trident Mogura.
        f.add(f.ability("mochi_tsuki").unlock(5).cooldown(40).stamina(15).anim("spear_thrust").exec((u, c) -> {
            Vec3 dir = FruitKit.aimDir(c);
            for (LivingEntity e : Hits.line(u, c.eye(), dir, 7, 0.8)) Hits.hit(u, e, FruitKit.dmg(c, 10), FruitKit.info(Element.PIERCE, 1.4f).withPoise(50));
            FruitKit.fx(u, "mogura", c.eye(), (float) (c.eye().x + dir.x * 7), (float) (c.eye().y + dir.y * 7), (float) (c.eye().z + dir.z * 7));
            return true;
        }));

        // Sticky Mochi: the mochi engulfs the target's legs and arms — stuck in place, unable to fight back.
        f.add(f.ability("sticky_mochi").unlock(10).cooldown(150).stamina(20).anim("throw").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 14);
            if (t == null) return false;
            if (PowerKit.hit(u, t, FruitKit.dmg(c, 4), mochi.withPoise(30))) {
                int dur = 40 + c.mastery / 3;
                stick(t, dur, 5);
                CanonKit.disable(t, Math.min(dur, 35), 5);
            }
            FruitKit.fx(u, "mochi_bind", t.position().add(0, 1, 0), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.HONEY_BLOCK_PLACE, 1.4f, 0.6f);
            return true;
        }));

        // Chikara Mochi: giant mochi fists hammer the target area from above.
        f.add(f.ability("chikara_mochi").unlock(18).cooldown(130).stamina(26).anim("ground_slam").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 16);
            Vec3 at = t != null ? t.position() : FruitKit.aimPoint(c, 16);
            for (int i = 0; i < 4; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + 4 + i * 4L, () -> {
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 3)) Hits.hit(u, e, FruitKit.dmg(c, 5), mochi.withStun(10).withPoise(30));
                    FruitKit.fx(u, "mochi_fists", at, 3, 0, 0);
                });
            }
            PowerKit.sound(c, at, SoundEvents.SLIME_BLOCK_FALL, 1.5f, 0.5f);
            return true;
        }));

        // Buzz Cut Mochi: spinning mochi donuts on the arms grind through everything on a short charge.
        f.add(f.ability("buzz_cut_mochi").unlock(28).cooldown(140).stamina(28).anim("spin_slash").exec((u, c) -> {
            Vec3 dir = PowerKit.flatAim(c);
            FruitKit.impulse(u, new Vec3(dir.x * 1.6, 0.1, dir.z * 1.6), true);
            Set<Integer> struck = new HashSet<>();
            for (int i = 0; i < 8; i++) {
                int k = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 2L, () -> {
                    if (!u.isAlive()) return;
                    Vec3 p = u.position().add(dir.scale(1.2)).add(0, 1, 0);
                    for (LivingEntity e : PowerKit.foes(u, p, 2.2)) {
                        float d = struck.add(e.getId()) ? 7 : 2.5f;
                        Hits.hit(u, e, FruitKit.dmg(c, d), FruitKit.info(Element.SLASH, 0.6f).withPoise(15));
                    }
                    if (k % 2 == 0) FruitKit.fx(u, "mochi_donut", p, 1.5f, 0, 0);
                });
            }
            return true;
        }));

        // Awakening — Flowing Mochi: the ground turns to mochi that swallows the enemies' feet, then rises as a wave.
        f.add(f.ability("flowing_mochi").unlock(55).cooldown(1000).stamina(80).awakened().anim("cast_ground").exec((u, c) -> {
            Vec3 center = u.position();
            double r = 10;
            PowerKit.paintDisc(c.level, center, 9, MOCHI, MOCHI);
            for (int i = 0; i < 12; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    FruitKit.fx(u, "mochi_flood", center, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), r)) {
                        stick(e, 20, 4);
                        if (n % 3 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2), mochi.withPoise(10));
                    }
                });
            }
            CombatState.of(u).schedule(c.level.getGameTime() + 64, () -> {
                for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), r)) {
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, 11), FruitKit.info(Element.BLUNT, 1.5f).withStun(40).asUnblockable()))
                        FruitKit.impulse(e, new Vec3(0, 0.8, 0), false);
                }
                FruitKit.fx(u, "mochi_flood", center, (float) r, 1, 0);
            });
            return true;
        }));

        // Passive: a mochi body. With Observation Haki up (seeing the attack coming) the body opens around blows like a
        // Logia — Armament Haki, seastone and water still connect.
        f.intangibleHook = (u, a, info) -> CanonKit.observing(u);
    }

    private MochiFruit() {}
}
