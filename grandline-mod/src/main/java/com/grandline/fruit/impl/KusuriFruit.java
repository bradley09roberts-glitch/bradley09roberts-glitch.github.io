package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Knockdown;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Kusuri Kusuri no Mi — ORIGINAL medicine Paramecia. The user's body brews any drug they can imagine — remedies and
 * poisons alike: toxin darts, antidotes, stimulants (with a crash), anaesthetic mist, a miracle draught that can even
 * get a knocked-down crewmate back on their feet, and a great cloud that heals friends while poisoning foes.
 * Passive: the body makes its own antidotes (immune to poison) and poisoned enemies take 15% more from the user.
 */
public final class KusuriFruit {

    public static void init(DevilFruit f) {
        Hits.Info dart = FruitKit.info(Element.PIERCE, 0.2f);

        // Toxin dart: a dart loaded with a fast-acting toxin.
        f.add(f.ability("toxin_dart").cooldown(18).stamina(8).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "kusuri_dart", 0.25f, 2.8, FruitKit.dmg(c, 4), dart.withPoise(10));
            p.life = 30;
            p.onHit = (pr, hit, pos) -> {
                if (hit == null) return;
                hit.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
                hit.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            };
            return true;
        }));

        // Antidote: cures the user and nearby allies of every ailment and patches them up a little.
        f.add(f.ability("antidote").unlock(5).cooldown(200).stamina(14).anim("focus").exec((u, c) -> {
            int cured = 0;
            for (LivingEntity a : OriginalKit.allies(u, u.position().add(0, 1, 0), 6, true)) {
                cured += OriginalKit.cleanse(a);
                OriginalKit.heal(a, 4, "kusuri_cure");
            }
            // the bitter vapour stings enemies who crowd the medic
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 3)) Hits.hit(u, e, FruitKit.dmg(c, 2), FruitKit.info(Element.POISON, 0.4f).withPoise(10));
            PowerKit.sound(c, u.position(), SoundEvents.BREWING_STAND_BREW, 1f, 1.2f + Math.min(0.6f, cured * 0.1f));
            return true;
        }));

        // Stimulant: an adrenaline shot for the user and nearby allies — speed, strength, haste and stamina — followed by
        // a short crash when it wears off.
        f.add(f.ability("stimulant").unlock(12).cooldown(500).stamina(0).anim("focus").exec((u, c) -> {
            int dur = 240 + c.mastery * 2;
            for (LivingEntity a : OriginalKit.allies(u, u.position().add(0, 1, 0), 4, true)) {
                a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1));
                a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, dur, 0));
                a.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, dur, 1));
                OriginalKit.restoreStamina(a, 35);
                FruitKit.fx(u, "kusuri_stim", a.position().add(0, 1, 0), 0, 0, 0);
                CombatState.of(u).schedule(c.level.getGameTime() + dur, () -> {
                    if (!a.isAlive()) return;
                    a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
                    a.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                });
            }
            PowerKit.sound(c, u.position(), SoundEvents.GENERIC_DRINK, 1f, 1.4f);
            return true;
        }));

        // Anaesthetic: a cone of numbing mist — enemies are slowed, weakened and fumble their attacks.
        f.add(f.ability("anaesthetic").unlock(20).cooldown(180).stamina(22).anim("cast_forward").exec((u, c) -> {
            for (LivingEntity e : Hits.cone(u, 7 + u.getScale(), 90)) {
                if (!Hits.hostileTo(u, e)) continue;
                Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.POISON, 0f).withStun(20).withPoise(30));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 3));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 120, 2));
                CombatState.of(e).blocking = false;
            }
            FruitKit.fx(u, "kusuri_mist", c.eye(), (float) c.look().x, (float) c.look().y, (float) c.look().z);
            PowerKit.sound(c, u.position(), SoundEvents.SPLASH_POTION_BREAK, 1f, 0.8f);
            return true;
        }));

        // Miracle draught: the most injured ally nearby (or the user) is restored by 40% of their health; a
        // knocked-down crewmate is brought straight back to their feet.
        f.add(f.ability("miracle_draught").unlock(30).cooldown(900).stamina(30).anim("focus").exec((u, c) -> {
            if (u instanceof Player helper) {
                for (Player p : u.level().getEntitiesOfClass(Player.class, u.getBoundingBox().inflate(8), p -> p != u)) {
                    CharacterData d = Characters.get(p);
                    if (p instanceof ServerPlayer sp && d.isKnockedDown() && !Hits.hostileTo(u, p)) {
                        Knockdown.revive(sp, d, helper);
                        OriginalKit.heal(sp, sp.getMaxHealth() * 0.25f, "kusuri_cure");
                        return true;
                    }
                }
            }
            LivingEntity best = u;
            for (LivingEntity a : OriginalKit.allies(u, u.position().add(0, 1, 0), 8, true))
                if (a.getHealth() / a.getMaxHealth() < best.getHealth() / best.getMaxHealth()) best = a;
            OriginalKit.heal(best, best.getMaxHealth() * 0.4f, "kusuri_cure");
            best.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 2));
            OriginalKit.cleanse(best);
            // the spilled draught is a vicious poison to anyone else
            for (LivingEntity e : PowerKit.foes(u, best.position().add(0, 1, 0), 3)) Hits.hit(u, e, FruitKit.dmg(c, 4), FruitKit.info(Element.POISON, 0.5f).withPoise(15));
            PowerKit.sound(c, best.position(), SoundEvents.WITCH_DRINK, 1f, 1f);
            return true;
        }));

        // Awakening — Pharmacopoeia: a vast medicinal cloud; allies inside heal and are cleansed every second while
        // enemies inside are poisoned and withered.
        f.add(f.ability("pharmacopoeia").unlock(55).cooldown(1000).stamina(80).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = u.position();
            double r = 10;
            OriginalKit.pulses(c, 0, 10, 10, n -> {
                FruitKit.fx(u, "kusuri_cloud", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.POISON, 0f).withPoise(10));
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 2));
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
                }
                for (LivingEntity a : OriginalKit.allies(u, at.add(0, 1, 0), r, true)) {
                    OriginalKit.heal(a, 2, n % 3 == 0 ? "kusuri_cure" : "");
                    if (n % 3 == 0) OriginalKit.cleanse(a);
                }
            });
            PowerKit.sound(c, at, SoundEvents.BREWING_STAND_BREW, 2f, 0.6f);
            return true;
        }));

        // Passive: self-made antidotes (immune to poison, element) and poisoned enemies take 15% more from the user.
        f.outgoingHook = (u, v, amount, info) -> v.hasEffect(MobEffects.POISON) || v.hasEffect(MobEffects.WITHER) ? amount * 1.15f : amount;
        f.tickHook = u -> {
            if (u.tickCount % 20 != 0) return;
            u.removeEffect(MobEffects.POISON);
        };
    }

    private KusuriFruit() {}
}
