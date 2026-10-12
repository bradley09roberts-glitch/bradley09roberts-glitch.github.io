package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.TerrainChanges;
import com.grandline.fruit.Zoan;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Zou Zou no Mi — Zoan, Model: Elephant. A huge, heavy body: a trunk that whips, grabs and hurls, sprays water hard
 * enough to reach a Logia, tusks that gore, and a stampede that shakes the ground.
 */
public final class ElephantFruit {
    public static void init(DevilFruit f) {
        PowerKit.zoanTransform(f);

        // Trunk Whip: a sweeping lash of the trunk.
        f.add(f.ability("trunk_whip").cooldown(24).stamina(10).anim("whip_crack").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            for (LivingEntity e : Hits.cone(u, 5, 110)) Hits.hit(u, e, FruitKit.dmg(c, 8), FruitKit.info(Element.BLUNT, 2.4f).withPoise(35));
            FruitKit.fx(u, "trunk_sweep", c.eye().add(c.look().scale(2)).add(0, -0.4, 0), 5, 0, 0);
            return true;
        }));

        // Trunk Hose: a high-pressure jet of water from the trunk — it soaks Logia bodies solid and puts out fires.
        f.add(f.ability("trunk_hose").unlock(8).cooldown(60).stamina(16).anim("cast_forward").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            for (LivingEntity e : Hits.cone(u, 8, 50)) {
                if (!Hits.hostileTo(u, e)) {
                    e.clearFire();
                    continue;
                }
                Hits.hit(u, e, FruitKit.dmg(c, 6), FruitKit.info(Element.WATER, 1.8f).withPoise(25));
                e.clearFire();
            }
            u.clearFire();
            Vec3 d = c.look();
            FruitKit.fx(u, "water_jet", c.eye(), (float) (c.eye().x + d.x * 8), (float) (c.eye().y + d.y * 8), (float) (c.eye().z + d.z * 8));
            PowerKit.sound(c, u.position(), SoundEvents.GENERIC_SPLASH, 1.2f, 0.8f);
            return true;
        }));

        // Tusk Gore: a lowered head and a thrust of the tusks — straight through any guard.
        f.add(f.ability("tusk_gore").unlock(18).cooldown(90).stamina(22).anim("headbutt").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = CanonKit.melee(c, 5);
            if (t == null) return false;
            if (PowerKit.hit(u, t, FruitKit.dmg(c, 13), FruitKit.info(Element.PIERCE, 2f).withStun(25).withPoise(80).asUnblockable()))
                FruitKit.impulse(t, new Vec3(0, 0.6, 0), false);
            FruitKit.fx(u, "claw", t.position().add(0, 1, 0), 0, 0, 0);
            return true;
        }));

        // Trunk Hurl: the trunk wraps around the target, lifts it overhead and slams it down behind.
        f.add(f.ability("trunk_hurl").unlock(28).cooldown(140).stamina(26).anim("throw_grab").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = CanonKit.melee(c, 5.5);
            if (t == null) return false;
            Vec3 back = PowerKit.flatAim(c).scale(-1);
            PowerKit.place(t, u.position().add(0, u.getBbHeight() + 0.5, 0));
            CombatState.of(u).schedule(c.level.getGameTime() + 6, () -> {
                if (!t.isAlive()) return;
                if (PowerKit.hit(u, t, FruitKit.dmg(c, 12), FruitKit.info(Element.BLUNT, 0f).withStun(30).withPoise(80)))
                    FruitKit.impulse(t, back.scale(1.6).add(0, -0.4, 0), true);
                FruitKit.fx(u, "shockwave", t.position(), 2.5f, 0, 0);
            });
            return true;
        }));

        // Awakening — Mammoth Rampage: the awakened beast stamps three times; each quake rolls further out.
        f.add(f.ability("mammoth_rampage").unlock(50).cooldown(1100).stamina(80).awakened().anim("ground_slam").exec((u, c) -> {
            Zoan.setForm(u, "awakened");
            double[] radii = {6, 8.5, 11};
            float[] dmg = {11, 8, 6};
            for (int w = 0; w < 3; w++) {
                int k = w;
                CombatState.of(u).schedule(c.level.getGameTime() + w * 10L, () -> {
                    if (!u.isAlive()) return;
                    Vec3 center = u.position();
                    for (LivingEntity e : PowerKit.foes(u, center.add(0, 0.5, 0), radii[k])) {
                        if (Math.abs(e.getY() - center.y) > 3) continue;
                        if (PowerKit.hit(u, e, FruitKit.dmg(c, dmg[k]), FruitKit.info(Element.BLUNT, 1.2f).withStun(20).withPoise(60)))
                            FruitKit.impulse(e, new Vec3(0, 0.5, 0), false);
                    }
                    FruitKit.fx(u, "shockwave", center.add(0, 0.2, 0), (float) radii[k], 0, 0);
                    TerrainChanges.crater(c.level, BlockPos.containing(center.add(PowerKit.flatAim(c).scale(radii[k] * 0.6))).below(), 2, u);
                    PowerKit.sound(c, center, SoundEvents.RAVAGER_STEP, 2f, 0.4f);
                });
            }
            PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_ROAR, 1.5f, 0.5f);
            return true;
        }));

        // Passive: a heavy elephant body. Transformed, the user is tough and strong, blunt blows land 20% softer, and
        // the trunk douses any flames on its own body.
        f.tickHook = u -> {
            if (Zoan.form(u).isEmpty()) return;
            if (u.isOnFire() && u.tickCount % 10 == 0) {
                u.clearFire();
                com.grandline.net.GLNetwork.fx(u, "steam", u.getX(), u.getY() + 1.5, u.getZ(), 0, 0, 0);
            }
            if (u.tickCount % 20 != 0) return;
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 30, 0, false, false));
        };
        f.incomingHook = (u, a, amount, info) -> !Zoan.form(u).isEmpty() && (info.element() == Element.BLUNT || info.element() == Element.IMPACT) ? amount * 0.8f : amount;
    }

    private ElephantFruit() {}
}
