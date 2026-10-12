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

import java.util.List;

/**
 * Hebi Hebi no Mi, Model: Yamata no Orochi — Mythical Zoan, the eight-headed serpent. Many heads bite at once, fangs
 * carry venom and coils crush; and a serpent with eight heads does not die from losing one.
 */
public final class OrochiFruit {
    static final String LIFE_CD = "orochi_head_lost";

    static int heads(LivingEntity u) {
        String form = Zoan.form(u);
        return form.equals("full") || form.equals("awakened") ? 8 : 4;
    }

    public static void init(DevilFruit f) {
        Hits.Info fang = FruitKit.info(Element.PIERCE, 0.5f);
        PowerKit.zoanTransform(f);

        // Yamata Bite: the heads lunge one after another (four in hybrid form, all eight in full form).
        f.add(f.ability("yamata_bite").cooldown(30).stamina(12).anim("claw_flurry").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            int heads = heads(u);
            for (int i = 0; i < heads; i++) {
                int k = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 2L, () -> {
                    if (!u.isAlive()) return;
                    List<LivingEntity> foes = Hits.cone(u, 6, 120).stream().filter(e -> Hits.hostileTo(u, e)).toList();
                    if (foes.isEmpty()) return;
                    LivingEntity e = foes.get(k % foes.size());
                    Hits.hit(u, e, FruitKit.dmg(c, 2.6f), fang.withPoise(12));
                    FruitKit.fx(u, "serpent_strike", u.getEyePosition(), (float) e.getX(), (float) e.getY() + e.getBbHeight() * 0.6f, (float) e.getZ());
                });
            }
            return true;
        }));

        // Venom Fang: one head sinks its fangs in deep — the poison keeps working.
        f.add(f.ability("venom_fang").unlock(6).cooldown(70).stamina(16).anim("headbutt").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = CanonKit.melee(c, 5.5);
            if (t == null) return false;
            if (PowerKit.hit(u, t, FruitKit.dmg(c, 8), fang.withPoise(40))) {
                t.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 2));
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
            }
            FruitKit.fx(u, "venom", t.position().add(0, 1, 0), 1, 0, 0);
            PowerKit.sound(c, t.position(), SoundEvents.EVOKER_FANGS_ATTACK, 1f, 1.2f);
            return true;
        }));

        // Serpent Coil: the body wraps around the target and squeezes.
        f.add(f.ability("serpent_coil").unlock(15).cooldown(160).stamina(26).anim("grab").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = CanonKit.melee(c, 5.5);
            if (t == null) return false;
            Vec3 hold = t.position();
            CanonKit.disable(t, 50, 5);
            for (int i = 0; i < 5; i++) {
                CombatState.of(u).schedule(c.level.getGameTime() + 4 + i * 10L, () -> {
                    if (!t.isAlive() || !u.isAlive()) return;
                    if (t.position().distanceToSqr(hold) > 1) PowerKit.place(t, hold);
                    Hits.hit(u, t, FruitKit.dmg(c, 3.2f), FruitKit.info(Element.BLUNT, 0f).withPoise(15).asUnblockable());
                    FruitKit.fx(u, "coil", t.position().add(0, 1, 0), t.getBbWidth(), t.getBbHeight(), 0);
                });
            }
            return true;
        }));

        // Regrowth: severed heads and torn scales grow back.
        f.add(f.ability("regrowth").unlock(25).cooldown(700).stamina(20).anim("focus").exec((u, c) -> {
            u.heal(u.getMaxHealth() * 0.3f);
            CanonKit.cleanse(u);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 2.5)) Hits.hit(u, e, FruitKit.dmg(c, 3), fang); // writhing new heads snap
            FruitKit.fx(u, "venom", u.position().add(0, 1, 0), 1.5f, 1, 0);
            return true;
        }));

        // Awakening — Eight-Headed Fury: all eight heads lash out around the user at once.
        f.add(f.ability("eight_head_fury").unlock(50).cooldown(1000).stamina(80).awakened().anim("roar").exec((u, c) -> {
            Zoan.setForm(u, "awakened");
            Vec3 center = u.position().add(0, 1, 0);
            List<LivingEntity> foes = PowerKit.foes(u, center, 8);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                Vec3 tip = center.add(Math.cos(a) * 6, 0, Math.sin(a) * 6);
                FruitKit.fx(u, "serpent_strike", center, (float) tip.x, (float) tip.y, (float) tip.z);
                if (foes.isEmpty()) continue;
                LivingEntity e = foes.get(i % foes.size());
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 8), fang.withPoise(40).asUnblockable())) e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
            }
            PowerKit.sound(c, center, SoundEvents.RAVAGER_ROAR, 1.4f, 1.3f);
            return true;
        }));

        // Passive: eight lives. A blow that would kill the user takes a head instead: the blow is absorbed and the user
        // is left with half their remaining health (once every three minutes). Transformed, its own venom cannot poison it.
        f.tickHook = u -> {
            if (!Zoan.form(u).isEmpty() && u.hasEffect(MobEffects.POISON)) u.removeEffect(MobEffects.POISON);
        };
        f.incomingHook = (u, a, amount, info) -> {
            if (amount * 1.3f < u.getHealth()) return amount; // margin for Haki and other multipliers applied afterwards
            CombatState s = CombatState.of(u);
            long now = u.level().getGameTime();
            if (s.onCooldown(LIFE_CD, now)) return amount;
            s.startCooldown(LIFE_CD, now, 3600);
            u.setHealth(Math.max(1f, u.getHealth() * 0.5f));
            com.grandline.net.GLNetwork.fx(u, "head_lost", u.getX(), u.getY() + 1.5, u.getZ(), 0, 0, 0);
            CanonKit.tell(u, "grandline.orochi.head_lost");
            return 0f;
        };
    }

    private OrochiFruit() {}
}
