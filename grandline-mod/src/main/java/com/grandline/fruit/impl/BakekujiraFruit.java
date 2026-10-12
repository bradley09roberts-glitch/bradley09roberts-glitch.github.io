package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.Zoan;
import com.grandline.ship.ShipEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Kujira Kujira no Mi, Model: Bakekujira — ORIGINAL Mythical Zoan (wave 4). The user becomes the ghost whale of the
 * New World's sailors' tales: a whale of bare bones and cold light that swims through the air instead of the sea — so
 * the fruit's curse never catches it while it stays aloft. It breaches out of the ground, sings a song that chills the
 * blood, swallows a foe whole, fires its ribs like harpoons and, at awakening, falls on an area out of the sky.
 * Passive: half spirit — in form, plain physical blows (no Haki) lose 20%; immune to darkness; no fall damage in form.
 * Weakness: standard Zoan; light banishes ghosts (+25%).
 */
public final class BakekujiraFruit {
    static final String SWIM = "kujira_swim";

    public static void init(DevilFruit f) {
        Hits.Info bone = FruitKit.info(Element.BLUNT, 1.6f);
        Hits.Info ghost = FruitKit.info(Element.DARKNESS, 0.3f);
        PowerKit.zoanTransform(f);

        // Bone breach: the whale rises out of the ground (or the deck) around the user and slams back down.
        f.add(f.ability("bone_breach").cooldown(30).stamina(12).anim("ground_slam").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            double r = 4 + u.getScale();
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 0.5, 0), r))
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 8), bone.withPoise(40))) FruitKit.impulse(e, new Vec3(0, 0.7, 0), false);
            FruitKit.fx(u, "kujira_breach", u.position(), (float) r, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.SKELETON_HURT, 1.5f, 0.4f);
            return true;
        }));

        // Ghost song: a long, mournful whale song — every enemy around is chilled to the bone: withering, slowed and
        // lost in fear.
        f.add(f.ability("ghost_song").unlock(5).cooldown(160).stamina(16).anim("roar").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 12)) {
                Hits.hit(u, e, FruitKit.dmg(c, 4), FruitKit.info(Element.SOUND, 0.2f).withPoise(25));
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                PowerKit.loseTrack(u, e);
            }
            FruitKit.fx(u, "kujira_song", u.position().add(0, 1, 0), 12, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 1.5f, 0.4f);
            return true;
        }));

        // Sky swim: the ghost whale swims through the air for eight seconds — it rises gently, glides fast and never
        // falls hard (over a gap, a cliff or the open sea).
        f.add(f.ability("sky_swim").unlock(12).cooldown(400).stamina(20).anim("cast_up").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            int dur = 160 + c.mastery;
            CombatState.of(u).buffs.put(SWIM, dur);
            u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, dur, 0, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 25, 0, false, false));
            // the tail's first stroke buffets whoever is close
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 0.5, 0), 3)) {
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 4), bone.withPoise(20))) PowerKit.push(e, u.position(), 0.8, 0.3);
            }
            FruitKit.fx(u, "kujira_swim", u.position(), 0, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.PHANTOM_FLAP, 1.5f, 0.4f);
            return true;
        }));

        // Engulf: the whale swallows one target whole — held in the dark of its ribcage for two and a half seconds,
        // ground and chilled, then spat out.
        f.add(f.ability("engulf").unlock(20).cooldown(240).stamina(26).anim("grab").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = PowerKit.target(c, 6 + u.getScale());
            if (t == null) return false;
            OriginalKit.pulses(c, 0, 10, 5, n -> {
                if (!t.isAlive()) return;
                PowerKit.place(t, new Vec3(u.getX(), u.getY() + 0.2, u.getZ()));
                t.setDeltaMovement(Vec3.ZERO);
                t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 6, false, false));
                Hits.hit(u, t, FruitKit.dmg(c, 3), ghost.withStun(12).withPoise(20).asUnblockable());
                FruitKit.fx(u, "kujira_engulf", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 52, () -> {
                if (!t.isAlive()) return;
                Vec3 out = u.getLookAngle().multiply(1, 0, 1).normalize();
                PowerKit.place(t, u.position().add(out.scale(1.5)));
                Hits.hit(u, t, FruitKit.dmg(c, 7), bone.withPoise(40).asUnblockable());
                FruitKit.impulse(t, out.scale(1.4).add(0, 0.5, 0), true);
                PowerKit.sound(c, t.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.6f);
            });
            PowerKit.sound(c, t.position(), SoundEvents.EVOKER_FANGS_ATTACK, 1.5f, 0.4f);
            return true;
        }));

        // Rib harpoons: the whale tears out its own ribs and hurls them like harpoons — three bone spears that pierce
        // through and drag targets down.
        f.add(f.ability("rib_harpoons").unlock(30).cooldown(120).stamina(22).anim("throw").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 dir = FruitKit.aimDir(c);
            for (int i = -1; i <= 1; i++) {
                Vec3 d = PowerKit.yaw(dir, i * 10);
                AbilityProjectile p = AbilityProjectile.shoot(u, "kujira_bone", 0.5f, c.eye().add(d.scale(0.6)).add(0, -0.15, 0), d, 2.2, FruitKit.dmg(c, 7), FruitKit.info(Element.PIERCE, 0.4f).withPoise(30));
                p.pierce = true;
                p.life = 30;
                p.onHit = (pr, hit, pos) -> {
                    if (hit != null) {
                        hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                        FruitKit.impulse(hit, new Vec3(0, -0.4, 0), false);
                    }
                };
            }
            PowerKit.sound(c, u.position(), SoundEvents.SKELETON_SHOOT, 1.5f, 0.5f);
            return true;
        }));

        // Awakening — Abyssal Requiem: a colossal ghost whale dives out of the sky onto an area — everything beneath is
        // crushed and cursed, and enemy ships under it are stove in.
        f.add(f.ability("abyssal_requiem").unlock(55).cooldown(1200).stamina(90).awakened().anim("roar").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 28);
            double r = 9;
            FruitKit.fx(u, "kujira_requiem", at, (float) r, 0, 0);
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
            CombatState.of(u).schedule(c.level.getGameTime() + 20, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 22), bone.withStun(40).withPoise(120).asUnblockable());
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1));
                    FruitKit.impulse(e, new Vec3(0, -0.6, 0), false);
                }
                for (ShipEntity s : OriginalKit.ships(u, at, r)) if (!OriginalKit.friendly(u, s)) OriginalKit.wreck(u, s, 150);
                FruitKit.fx(u, "kujira_requiem", at, (float) r, 1, 0);
                PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.4f);
            });
            PowerKit.sound(c, at, SoundEvents.ELDER_GUARDIAN_CURSE, 2f, 0.3f);
            return true;
        }));

        // Passive: half spirit. In form, plain physical blows lose 20% and the whale never falls hard; light banishes
        // ghosts (+25%). While sky-swimming it keeps gliding level.
        f.incomingHook = (u, a, amount, info) -> {
            if (info.element() == Element.LIGHT) return amount * 1.25f;
            if (!Zoan.form(u).isEmpty() && info.element().physical() && !info.imbued() && !(a != null && com.grandline.haki.Haki.armamentActive(a)))
                return amount * 0.8f;
            return amount;
        };
        f.tickHook = u -> {
            if (Zoan.form(u).isEmpty()) return;
            u.fallDistance = 0;
            if (CombatState.of(u).hasBuff(SWIM) && u.tickCount % 10 == 0 && u.getDeltaMovement().y < -0.05)
                FruitKit.impulse(u, new Vec3(0, 0.12, 0), false);
        };
    }

    private BakekujiraFruit() {}
}
