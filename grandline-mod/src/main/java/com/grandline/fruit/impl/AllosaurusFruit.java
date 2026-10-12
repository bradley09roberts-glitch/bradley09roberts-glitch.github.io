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

import java.util.HashSet;
import java.util.Set;

/**
 * Ryu Ryu no Mi, Model: Allosaurus — Ancient Zoan. A predator's jaws and a whip of a tail on a body built to take
 * punishment; every bite feeds it.
 */
public final class AllosaurusFruit {
    public static void init(DevilFruit f) {
        Hits.Info jaws = FruitKit.info(Element.PIERCE, 1.2f);
        PowerKit.zoanTransform(f);

        // Allosaurus Bite: crushing jaws close on the prey.
        f.add(f.ability("allo_bite").cooldown(26).stamina(11).anim("headbutt").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = CanonKit.melee(c, 4.5);
            if (t == null) return false;
            float d = FruitKit.dmg(c, 11);
            if (PowerKit.hit(u, t, d, jaws.withPoise(50))) u.heal(d * 0.2f);
            FruitKit.fx(u, "bite", t.position().add(0, t.getBbHeight() * 0.6, 0), 1, 0, 0);
            PowerKit.sound(c, t.position(), SoundEvents.EVOKER_FANGS_ATTACK, 1.2f, 0.6f);
            return true;
        }));

        // Tail Sweep: the heavy tail swings all the way round.
        f.add(f.ability("tail_sweep").unlock(6).cooldown(50).stamina(15).anim("spin_kick").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            FruitKit.aoe(c, u.position().add(0, 0.8, 0), 4.5, FruitKit.dmg(c, 7), FruitKit.info(Element.BLUNT, 2.6f).withPoise(40));
            FruitKit.fx(u, "tail_sweep", u.position().add(0, 0.6, 0), 4.5f, 0, 0);
            return true;
        }));

        // Ripping Rush: a sprint through the enemy line, biting each one in passing.
        f.add(f.ability("ripping_rush").unlock(15).cooldown(110).stamina(24).anim("dash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 dir = PowerKit.flatAim(c);
            FruitKit.impulse(u, new Vec3(dir.x * 1.8, 0.15, dir.z * 1.8), true);
            Set<Integer> struck = new HashSet<>();
            for (int i = 0; i <= 8; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + i, () -> {
                    if (!u.isAlive()) return;
                    for (LivingEntity e : PowerKit.foes(u, u.position().add(dir.scale(0.8)).add(0, 1, 0), 2))
                        if (struck.add(e.getId()) && PowerKit.hit(u, e, FruitKit.dmg(c, 9), jaws.withPoise(45)))
                            FruitKit.impulse(e, new Vec3(-dir.z, 0.3, dir.x).scale(0.8), false);
                });
            }
            FruitKit.fx(u, "charge_dust", u.position(), (float) dir.x, (float) dir.z, 6);
            return true;
        }));

        // Ancient Roar: a roar from a lost age — the prey freezes in fear while the hunter's blood rises.
        f.add(f.ability("ancient_roar").unlock(25).cooldown(300).stamina(22).anim("roar").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 10)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.SOUND, 0.5f).withPoise(20))) {
                    CanonKit.disable(e, 25, 1);
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
                }
            }
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1));
            FruitKit.fx(u, "howl", u.position().add(0, 1, 0), 10, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.RAVAGER_ROAR, 1.6f, 0.6f);
            return true;
        }));

        // Awakening — Calamity Jaws: the awakened allosaurus bites down so hard the ground caves in under the prey.
        f.add(f.ability("calamity_jaws").unlock(50).cooldown(1000).stamina(75).awakened().anim("pounce").exec((u, c) -> {
            Zoan.setForm(u, "awakened");
            LivingEntity t = PowerKit.target(c, 7);
            Vec3 at = t != null ? t.position() : u.position().add(PowerKit.flatAim(c).scale(3));
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 2.5)) {
                float d = FruitKit.dmg(c, e == t ? 22 : 10);
                if (PowerKit.hit(u, e, d, FruitKit.info(Element.PIERCE, 3f).withStun(30).withPoise(120).asUnblockable())) u.heal(d * 0.2f);
            }
            TerrainChanges.crater(c.level, BlockPos.containing(at).below(), 2, u);
            FruitKit.fx(u, "bite", at.add(0, 1, 0), 3, 0, 0);
            FruitKit.fx(u, "shockwave", at.add(0, 0.2, 0), 4, 0, 0);
            return true;
        }));

        // Passive: Ancient Zoan durability — transformed, the body resists damage and slowly mends; bites heal
        // a tenth of the damage they deal.
        f.tickHook = u -> {
            if (Zoan.form(u).isEmpty() || u.tickCount % 40 != 0) return;
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 50, 0, false, false));
            if (u.getHealth() < u.getMaxHealth()) u.heal(1f);
        };
        f.outgoingHook = (u, v, amount, info) -> {
            if (!Zoan.form(u).isEmpty() && info.element() == Element.PIERCE) u.heal(amount * 0.1f);
            return amount;
        };
    }

    private AllosaurusFruit() {}
}
