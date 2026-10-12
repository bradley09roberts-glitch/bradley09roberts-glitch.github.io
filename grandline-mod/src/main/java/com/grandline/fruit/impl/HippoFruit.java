package com.grandline.fruit.impl;

import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.Zoan;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Kaba Kaba no Mi, Model: Hippopotamus — ORIGINAL Zoan (wave 4). The river horse, the most dangerous beast of the
 * Sabaody mangrove swamps — and the curse is no joke to it: hippos never could swim, they walk along the bottom. Jaws
 * that open wide enough to bite a man in half, a charge like a runaway cart, the red "blood sweat" that heals and
 * protects, a wallow of mud, a bellowing threat and, at awakening, the stampede of the Behemoth.
 * Passive: in form a thick hide (blunt and slash -20%) and the hippo mends while it stands in mud.
 * Weakness: standard Zoan.
 */
public final class HippoFruit {
    static final BlockState MUD = Blocks.MUD.defaultBlockState();

    public static void init(DevilFruit f) {
        Hits.Info crush = FruitKit.info(Element.BLUNT, 1.2f);
        PowerKit.zoanTransform(f);

        // Gaping jaws: the jaws gape wide and slam shut on everything in front.
        f.add(f.ability("gaping_jaws").cooldown(26).stamina(11).anim("headbutt").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            for (LivingEntity e : Hits.cone(u, 4 + u.getScale(), 80)) {
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 9), FruitKit.info(Element.PIERCE, 0.6f).withPoise(50)))
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
            FruitKit.fx(u, "kaba_jaws", c.eye().add(c.look().scale(1.5)), 1.4f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_ATTACK, 1.2f, 0.7f);
            return true;
        }));

        // River charge: a thundering charge — everyone in the path is run down and thrown.
        f.add(f.ability("river_charge").unlock(5).cooldown(90).stamina(18).anim("dash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 start = u.position();
            Vec3 dir = PowerKit.flatAim(c);
            Vec3 end = FruitKit.blink(u, dir, 10);
            for (LivingEntity e : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end) + 2, 1.5)) {
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 8), crush.withStun(12).withPoise(55))) PowerKit.push(e, start, 1.0, 0.5);
            }
            FruitKit.fx(u, "kaba_charge", start, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, end, SoundEvents.RAVAGER_STEP, 2f, 0.6f);
            return true;
        }));

        // Blood sweat: the red sweat of the hippo — a sunscreen and an antiseptic. It heals, regenerates, toughens the
        // hide, puts out fire and washes poison away; it stings anyone close.
        f.add(f.ability("blood_sweat").unlock(12).cooldown(400).stamina(16).anim("harden").exec((u, c) -> {
            OriginalKit.heal(u, 6 + c.mastery / 15f, "kaba_sweat");
            u.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            u.clearFire();
            u.removeEffect(MobEffects.POISON);
            u.removeEffect(MobEffects.WITHER);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 2.5)) Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.POISON, 0.2f).withPoise(10));
            PowerKit.sound(c, u.position(), SoundEvents.SLIME_SQUISH, 1f, 0.6f);
            return true;
        }));

        // Mud wallow: the ground around the user turns to deep mud (temporary): enemies in it are bogged down, and the
        // hippo mends while it wallows.
        f.add(f.ability("mud_wallow").unlock(20).cooldown(320).stamina(20).anim("ground_slam").exec((u, c) -> {
            Vec3 at = u.position();
            double r = 4.5;
            PowerKit.paintDisc(c.level, at, r, MUD, null);
            OriginalKit.pulses(c, 0, 20, 8, n -> {
                FruitKit.fx(u, "kaba_mud", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.5, 0), r + 0.5)) {
                    if (Math.abs(e.getY() - at.y) > 2) continue;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
                    if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2), crush.withPoise(10));
                }
                if (u.position().distanceTo(at) <= r + 0.5) u.heal(1f);
            });
            PowerKit.sound(c, at, SoundEvents.MUD_PLACE, 1.5f, 0.6f);
            return true;
        }));

        // Territorial bellow: the hippo yawns its huge jaws wide and bellows — enemies around are shoved back, shaken
        // and lose their nerve.
        f.add(f.ability("territorial_bellow").unlock(30).cooldown(260).stamina(22).anim("roar").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 8)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 5), FruitKit.info(Element.SOUND, 1.5f).withPoise(40))) {
                    PowerKit.push(e, u.position(), 1.1, 0.3);
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                }
            }
            FruitKit.fx(u, "kaba_bellow", u.position().add(0, 1.5, 0), 8, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_ROAR, 1.5f, 0.5f);
            return true;
        }));

        // Awakening — Behemoth: the hippo becomes the Behemoth of legend and stamps three times — each stamp a quake
        // that throws everything around it into the air.
        f.add(f.ability("behemoth").unlock(55).cooldown(1000).stamina(80).awakened().anim("ground_slam").exec((u, c) -> {
            OriginalKit.pulses(c, 0, 15, 3, n -> {
                double r = 6 + n * 1.5;
                for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 0.5, 0), r)) {
                    if (PowerKit.hit(u, e, FruitKit.dmg(c, n == 2 ? 16 : 9), crush.withStun(20).withPoise(70).asUnblockable()))
                        FruitKit.impulse(e, new Vec3(0, 0.6 + n * 0.15, 0), false);
                }
                FruitKit.fx(u, "kaba_quake", u.position(), (float) r, n, 0);
                PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_STEP, 3f, 0.4f);
            });
            PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_ROAR, 2f, 0.3f);
            return true;
        }));

        // Passive: in form a thick hide (blunt and slash -20%); mends in mud.
        f.incomingHook = (u, a, amount, info) -> {
            if (Zoan.form(u).isEmpty()) return amount;
            Element el = info.element();
            return el == Element.BLUNT || el == Element.SLASH || el == Element.PHYSICAL ? amount * 0.8f : amount;
        };
        f.tickHook = u -> {
            if (Zoan.form(u).isEmpty() || u.tickCount % 40 != 0) return;
            if (u.level().getBlockState(u.blockPosition().below()).is(Blocks.MUD) || u.level().getBlockState(u.blockPosition()).is(Blocks.MUD)) u.heal(1f);
        };
    }

    private HippoFruit() {}
}
