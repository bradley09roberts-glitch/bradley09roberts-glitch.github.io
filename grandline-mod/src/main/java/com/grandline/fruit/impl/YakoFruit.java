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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Yako Yako no Mi — ORIGINAL sea-sparkle Logia (wave 4). The user becomes a cloud of glowing plankton, the living
 * light of the deep sea around Fish-Man Island: sparks that mark their prey, an angler's lure that draws enemies into
 * a trap, the startle-flash of deep-sea creatures, and a glow that turns the whole sea milky white.
 * Passive: in darkness — the deep sea, night, caves — the swarm thrives (+15% damage, slow regeneration); every enemy
 * the light has marked takes 20% more from the user.
 * Weakness: ink and darkness smother the glow — Darkness always connects and deals 30% more.
 */
public final class YakoFruit {
    static final String MARK = "yako_mark";

    /** Mark an enemy with clinging light: it glows (cannot hide) and takes more from the user. */
    static void mark(LivingEntity e, int ticks) {
        CombatState.of(e).buffs.put(MARK, ticks);
        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
        e.removeEffect(MobEffects.INVISIBILITY);
    }

    static boolean marked(LivingEntity e) {
        return CombatState.of(e).hasBuff(MARK);
    }

    /** The deep sea, night or any dark place. */
    static boolean dark(LivingEntity u) {
        if (u.level().dimension() == com.grandline.world.DeepWorld.LEVEL) return true;
        return u.level().getMaxLocalRawBrightness(u.blockPosition().above()) < 7;
    }

    public static void init(DevilFruit f) {
        Hits.Info light = FruitKit.info(Element.LIGHT, 0.4f);

        // Glow bead: a spark of living light that clings to whoever it hits (marked).
        f.add(f.ability("glow_bead").cooldown(16).stamina(8).anim("finger_pistol").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "yako_spark", 0.4f, 2.4, FruitKit.dmg(c, 5), light.withPoise(15));
            p.life = 40;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) mark(hit, 200);
            };
            return true;
        }));

        // Lure light: an angler's lure bobs at a point for six seconds; enemies are drawn in, dazed and fixated on the
        // light — then it bursts.
        f.add(f.ability("lure_light").unlock(5).cooldown(220).stamina(18).anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 16).add(0, 1.2, 0);
            OriginalKit.pulses(c, 0, 10, 12, n -> {
                FruitKit.fx(u, "yako_lure", at, 0, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at, 10)) {
                    PowerKit.pull(e, at, 0.45, 0.02);
                    e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0));
                    PowerKit.loseTrack(u, e);
                }
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 120, () -> {
                for (LivingEntity e : PowerKit.foes(u, at, 3.5)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 9), light.withStun(20).withPoise(40));
                    mark(e, 200);
                }
                FruitKit.fx(u, "yako_burst", at, 3.5f, 0, 0);
                PowerKit.sound(c, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.5f, 1.4f);
            });
            // whoever is already standing on the lure is caught at once
            for (LivingEntity e : PowerKit.foes(u, at, 2.5)) Hits.hit(u, e, FruitKit.dmg(c, 3), light.withPoise(15));
            PowerKit.sound(c, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.8f);
            return true;
        }));

        // Abyssal flash: the startle-flash of a deep-sea creature — a cone of blinding light; enemies reel and lose you.
        f.add(f.ability("abyssal_flash").unlock(12).cooldown(120).stamina(16).anim("double_palm").exec((u, c) -> {
            for (LivingEntity e : Hits.cone(u, 8, 100)) {
                if (!Hits.hostileTo(u, e)) continue;
                Hits.hit(u, e, FruitKit.dmg(c, 4), light.withStun(15).withPoise(30));
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                PowerKit.loseTrack(u, e);
            }
            FruitKit.fx(u, "yako_flash", c.eye(), (float) c.look().x, (float) c.look().y, (float) c.look().z);
            PowerKit.sound(c, u.position(), SoundEvents.BEACON_ACTIVATE, 1f, 2f);
            return true;
        }));

        // Sea sparkle: the swarm spreads wide — every enemy around is marked and revealed, allies see in the dark and
        // move quicker in the glow.
        f.add(f.ability("sea_sparkle").unlock(20).cooldown(400).stamina(24).anim("cast_up").exec((u, c) -> {
            int dur = 240 + c.mastery * 2;
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 14)) {
                mark(e, dur);
                Hits.hit(u, e, FruitKit.dmg(c, 2), light.withPoise(10));
            }
            for (LivingEntity a : OriginalKit.allies(u, u.position().add(0, 1, 0), 10, true)) {
                a.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, dur + 200, 0, false, false));
                a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 0));
            }
            FruitKit.fx(u, "yako_sparkle", u.position(), 14, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 1.2f);
            return true;
        }));

        // Living light: the user scatters into the swarm and gathers again behind the target, bursting into light.
        f.add(f.ability("living_light").unlock(30).cooldown(180).stamina(26).anim("dash").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 18);
            Vec3 from = u.position();
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 10;
            if (t != null) {
                Vec3 away = t.position().subtract(from).multiply(1, 0, 1);
                Vec3 dir = away.lengthSqr() < 1e-4 ? PowerKit.flatAim(c) : away.normalize();
                Vec3 back = t.position().add(dir.scale(1.4)), front = t.position().subtract(dir.scale(1.4));
                PowerKit.place(u, u.level().noCollision(u, u.getBoundingBox().move(back.subtract(u.position()))) ? back : front);
                u.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
                Hits.hit(u, t, FruitKit.dmg(c, 9), light.withStun(15).withPoise(45));
                mark(t, 200);
            } else FruitKit.blink(u, c.look(), 12);
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 3)) if (e != t) Hits.hit(u, e, FruitKit.dmg(c, 4), light.withPoise(20));
            FruitKit.fx(u, "yako_swarm", from, (float) u.getX(), (float) u.getY(), (float) u.getZ());
            PowerKit.sound(c, u.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 1.6f);
            return true;
        }));

        // Awakening — Milky Sea: the ground (or the sea) all around glows like a milky sea for ten seconds; enemies in
        // it are dazzled and burned by light every second (marked ones twice over), allies vanish in the glare and mend.
        f.add(f.ability("milky_sea").unlock(55).cooldown(1100).stamina(85).awakened().anim("cast_ground").exec((u, c) -> {
            Vec3 at = u.position();
            double r = 12;
            OriginalKit.pulses(c, 0, 20, 10, n -> {
                FruitKit.fx(u, "yako_milky", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    Hits.hit(u, e, FruitKit.dmg(c, marked(e) ? 8 : 4), light.withPoise(20));
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
                    if (n == 0) mark(e, 220);
                    if (e instanceof Mob m && m.getTarget() != null && !Hits.hostileTo(u, m.getTarget())) m.setTarget(null);
                }
                for (LivingEntity a : OriginalKit.allies(u, at.add(0, 1, 0), r, true)) {
                    OriginalKit.heal(a, 1.5f, "");
                    a.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
                }
            });
            PowerKit.sound(c, at, SoundEvents.BEACON_POWER_SELECT, 2f, 0.7f);
            return true;
        }));

        // Passive: a body of living light (Logia), immune to light. Darkness smothers it (always connects, +30%). In
        // the dark it thrives; marked enemies take 20% more from it.
        f.intangibleHook = (u, a, info) -> info.element() != Element.DARKNESS;
        f.incomingHook = (u, a, amount, info) -> info.element() == Element.DARKNESS ? amount * 1.3f : amount;
        f.outgoingHook = (u, v, amount, info) -> {
            float a = marked(v) ? amount * 1.2f : amount;
            return dark(u) ? a * 1.15f : a;
        };
        f.tickHook = u -> {
            if (u.tickCount % 40 == 0 && dark(u) && u.getHealth() < u.getMaxHealth()) u.heal(1f);
        };
    }

    private YakoFruit() {}
}
