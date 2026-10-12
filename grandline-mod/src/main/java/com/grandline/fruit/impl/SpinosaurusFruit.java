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
 * Ryu Ryu no Mi, Model: Spinosaurus — Ancient Zoan. Long crocodile jaws, a bladed back sail and a crushing tail; in
 * the sun the sail soaks up heat and the body mends faster, and awakened it falls into a primal frenzy.
 */
public final class SpinosaurusFruit {
    static final String FRENZY = "spino_frenzy";

    public static void init(DevilFruit f) {
        PowerKit.zoanTransform(f);

        // Spino Bite: long jaws snap shut.
        f.add(f.ability("spino_bite").cooldown(24).stamina(10).anim("headbutt").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = CanonKit.melee(c, 4.8);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 10), FruitKit.info(Element.PIERCE, 1f).withPoise(45));
            FruitKit.fx(u, "bite", t.position().add(0, t.getBbHeight() * 0.6, 0), 1, 0, 0);
            PowerKit.sound(c, t.position(), SoundEvents.EVOKER_FANGS_ATTACK, 1.2f, 0.5f);
            return true;
        }));

        // Sail Spin: a full turn with the bladed back sail raised.
        f.add(f.ability("sail_spin").unlock(6).cooldown(55).stamina(16).anim("spin_slash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 4.5)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 7), FruitKit.info(Element.SLASH, 1.6f).withPoise(30)))
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
            }
            FruitKit.fx(u, "sail_spin", u.position().add(0, 1, 0), 4.5f, 0, 0);
            return true;
        }));

        // Jaw Toss: seize the prey in the jaws and fling it overhead to the ground behind.
        f.add(f.ability("jaw_toss").unlock(15).cooldown(120).stamina(24).anim("throw_grab").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = CanonKit.melee(c, 5);
            if (t == null) return false;
            Vec3 back = PowerKit.flatAim(c).scale(-1);
            if (PowerKit.hit(u, t, FruitKit.dmg(c, 9), FruitKit.info(Element.PIERCE, 0f).withStun(25).withPoise(60))) {
                PowerKit.place(t, u.position().add(0, u.getBbHeight() + 0.3, 0));
                FruitKit.impulse(t, back.scale(1.2).add(0, 0.6, 0), true);
            }
            FruitKit.fx(u, "bite", t.position().add(0, 1, 0), 1, 0, 0);
            return true;
        }));

        // Tail Crash: the heavy tail comes down like a falling tree and caves in the ground.
        f.add(f.ability("tail_crash").unlock(25).cooldown(140).stamina(26).anim("ground_slam").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 at = u.position().add(PowerKit.flatAim(c).scale(3));
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.8, 0), 3.5))
                Hits.hit(u, e, FruitKit.dmg(c, 12), FruitKit.info(Element.BLUNT, 1.5f).withStun(30).withPoise(70));
            TerrainChanges.crater(c.level, BlockPos.containing(at).below(), 2, u);
            FruitKit.fx(u, "shockwave", at.add(0, 0.2, 0), 3.5f, 0, 0);
            PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.6f);
            return true;
        }));

        // Awakening — Primal Frenzy: the awakened beast loses itself to instinct — stronger, faster, every bite feeding it.
        f.add(f.ability("primal_frenzy").unlock(50).cooldown(1200).stamina(60).awakened().anim("roar").exec((u, c) -> {
            Zoan.setForm(u, "awakened");
            int dur = 300 + c.mastery;
            CombatState.of(u).buffs.put(FRENZY, dur);
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, dur, 1));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1));
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 7))
                Hits.hit(u, e, FruitKit.dmg(c, 8), FruitKit.info(Element.SOUND, 2f).withPoise(50));
            FruitKit.fx(u, "howl", u.position().add(0, 1, 0), 7, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_ROAR, 1.6f, 0.4f);
            return true;
        }));

        // Passive: the sail. Transformed, the body resists damage and mends — twice as fast in daylight. In the
        // frenzy, every hit is 25% stronger and heals 15% of the damage.
        f.tickHook = u -> {
            if (Zoan.form(u).isEmpty() || u.tickCount % 40 != 0) return;
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 50, 0, false, false));
            if (u.getHealth() >= u.getMaxHealth()) return;
            boolean sun = u.level().isDay() && u.level().canSeeSky(u.blockPosition().above());
            u.heal(sun ? 2f : 1f);
        };
        f.outgoingHook = (u, v, amount, info) -> {
            if (!CombatState.of(u).hasBuff(FRENZY)) return amount;
            u.heal(amount * 0.15f);
            return amount * 1.25f;
        };
    }

    private SpinosaurusFruit() {}
}
