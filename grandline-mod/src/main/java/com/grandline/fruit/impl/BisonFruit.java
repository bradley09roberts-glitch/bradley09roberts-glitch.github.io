package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.Zoan;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Ushi Ushi no Mi, Model: Bison — Zoan. A heavy, tough body built for charging: horns, hooves and a stampede. */
public final class BisonFruit {
    public static void init(DevilFruit f) {
        Hits.Info ram = FruitKit.info(Element.BLUNT, 3.5f);
        PowerKit.zoanTransform(f);

        // Horn charge: thunder forward; anyone in the path is gored and thrown.
        f.add(f.ability("horn_charge").cooldown(45).stamina(14).anim("dash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 dir = PowerKit.flatAim(c);
            FruitKit.impulse(u, new Vec3(dir.x * 1.9, 0.15, dir.z * 1.9), true);
            Set<Integer> struck = new HashSet<>();
            for (int i = 0; i <= 8; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i, () -> {
                    if (!u.isAlive()) return;
                    for (LivingEntity e : PowerKit.foes(u, u.position().add(dir.scale(0.8)).add(0, 1, 0), 1.9))
                        if (struck.add(e.getId())) Hits.hit(u, e, FruitKit.dmg(c, 9), ram.withPoise(60));
                });
            }
            FruitKit.fx(u, "charge_dust", u.position(), (float) dir.x, (float) dir.z, 6);
            return true;
        }));

        // Hoof stomp: the ground shakes and nearby enemies are staggered.
        f.add(f.ability("stomp").unlock(5).cooldown(60).stamina(16).anim("ground_slam").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            FruitKit.aoe(c, u.position().add(0, 0.5, 0), 4.5, FruitKit.dmg(c, 7), FruitKit.info(Element.BLUNT, 1f).withStun(25).withPoise(40));
            FruitKit.fx(u, "shockwave", u.position().add(0, 0.2, 0), 4.5f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_STEP, 1.5f, 0.6f);
            return true;
        }));

        // Thick hide: brace the body — damage and knockback are greatly reduced for a while.
        f.add(f.ability("thick_hide").unlock(15).cooldown(500).stamina(20).anim("harden").exec((u, c) -> {
            int dur = 160 + c.mastery;
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 1, false, true));
            CombatState.of(u).buffs.put("bison_hide", dur);
            FruitKit.fx(u, "buff_hide", u.position().add(0, 1, 0), 0, 0, 0);
            return true;
        }));

        // Stampede: a rolling series of charges that tramples everything in a wide lane.
        f.add(f.ability("stampede").unlock(30).cooldown(220).stamina(40).anim("dash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 dir = PowerKit.flatAim(c);
            for (int wave = 0; wave < 4; wave++) {
                CombatState.of(u).schedule(c.level.getGameTime() + wave * 5L, () -> {
                    if (!u.isAlive()) return;
                    FruitKit.impulse(u, new Vec3(dir.x * 1.2, 0.1, dir.z * 1.2), true);
                    Vec3 from = u.position().add(0, 1, 0);
                    for (LivingEntity e : Hits.line(u, from, dir, 6, 1.6)) {
                        if (PowerKit.hit(u, e, FruitKit.dmg(c, 4), FruitKit.info(Element.BLUNT, 2f).withPoise(30)))
                            FruitKit.impulse(e, dir.scale(0.8).add(0, 0.3, 0), false);
                    }
                    FruitKit.fx(u, "charge_dust", u.position(), (float) dir.x, (float) dir.z, 6);
                });
            }
            PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_ROAR, 1f, 1.2f);
            return true;
        }));

        // Awakening — Raging Bull: the awakened bison form, unstoppable and roaring.
        f.add(f.ability("raging_bull").unlock(50).cooldown(1200).stamina(60).awakened().anim("roar").exec((u, c) -> {
            Zoan.setForm(u, "awakened");
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1));
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 0));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 0));
            FruitKit.aoe(c, u.position().add(0, 1, 0), 7, FruitKit.dmg(c, 8), FruitKit.info(Element.BLUNT, 3f).withPoise(60));
            FruitKit.fx(u, "shockwave", u.position().add(0, 0.2, 0), 7, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_ROAR, 1.5f, 0.7f);
            return true;
        }));

        // Passive: a bison's hide — transformed, the user resists damage; braced (Thick Hide), blows barely register.
        f.tickHook = u -> {
            if (Zoan.form(u).isEmpty() || u.tickCount % 20 != 0) return;
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, false, false));
        };
        f.incomingHook = (u, a, amount, info) -> CombatState.of(u).hasBuff("bison_hide") && (info.element() == Element.BLUNT || info.element() == Element.IMPACT) ? amount * 0.7f : amount;
    }

    private BisonFruit() {}
}
