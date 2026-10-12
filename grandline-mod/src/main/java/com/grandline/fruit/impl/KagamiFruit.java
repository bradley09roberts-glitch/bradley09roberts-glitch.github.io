package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.net.GLNetwork;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Kagami Kagami no Mi — ORIGINAL mirror Paramecia. The user makes and steps through mirrors: shards of mirror glass,
 * stepping out of the reflection behind a target, a mirror wall that sends projectiles and ranged attacks straight
 * back at the shooter, blinding flashes, a prism that splits light into a fan of rays, and a hall of mirrors.
 * Being made of mirror, the user cannot be hurt by light.
 */
public final class KagamiFruit {
    static final String MIRROR = "kagami_mirror";

    /** Turn back incoming projectiles that are about to reach the user. */
    static void reflectNear(LivingEntity u, Vec3 center, double r) {
        for (Projectile p : PowerKit.hostileProjectiles(u, center, r)) {
            Vec3 v = p.getDeltaMovement();
            Vec3 to = center.subtract(p.position());
            if (v.dot(to) <= 0) continue; // moving away already
            if (to.length() > 2.0 + v.length() * 2) continue; // not arriving within ~2 ticks
            PowerKit.reflect(u, p);
        }
    }

    public static void init(DevilFruit f) {
        Hits.Info light = FruitKit.info(Element.LIGHT, 0.4f);

        // Mirror shard: a spinning shard of mirror glass; the glare blinds whoever it hits.
        f.add(f.ability("mirror_shard").cooldown(22).stamina(10).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "mirror_shard", 0.45f, 2.6, FruitKit.dmg(c, 6), FruitKit.info(Element.SLASH, 0.4f).withPoise(20));
            p.life = 40;
            p.onHit = (pr, hit, pos) -> {
                if (hit != null) hit.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
            };
            return true;
        }));

        // Mirror step: step into a mirror and out of the reflection behind the target.
        f.add(f.ability("mirror_step").unlock(5).cooldown(50).stamina(14).anim("dash").exec((u, c) -> {
            Vec3 start = u.position();
            LivingEntity t = PowerKit.target(c, 20);
            if (t != null) {
                Vec3 back = t.getLookAngle().multiply(1, 0, 1);
                if (back.lengthSqr() < 1e-4) back = t.position().subtract(u.position()).multiply(1, 0, 1);
                back = back.lengthSqr() < 1e-4 ? PowerKit.flatAim(c) : back.normalize();
                double room = Math.max(0.6, PowerKit.rayLength(u, t.position().add(0, 0.5, 0), back.scale(-1), 1.8) - 0.3);
                Vec3 behind = t.position().subtract(back.scale(room));
                PowerKit.place(u, behind);
                u.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
            } else {
                FruitKit.blink(u, c.look(), 14);
            }
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 6;
            FruitKit.fx(u, "mirror_step", start.add(0, 1, 0), 0, 0, 0);
            FruitKit.fx(u, "mirror_step", u.position().add(0, 1, 0), 0, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            return true;
        }));

        // Mirror wall: for a few seconds, projectiles and ranged attacks are reflected back at their source.
        f.add(f.ability("reflect").unlock(10).cooldown(300).stamina(22).anim("harden").exec((u, c) -> {
            CombatState.of(u).buffs.put(MIRROR, 80 + c.mastery / 2);
            FruitKit.fx(u, "mirror_shield", u.position().add(0, 1, 0), 1.6f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
            return true;
        }));

        // Decoy flash: a blinding flash off a mirror; enemies in front lose sight of the user, who slips aside unseen.
        f.add(f.ability("decoy_flash").unlock(20).cooldown(220).stamina(24).anim("cast_forward").exec((u, c) -> {
            Vec3 start = u.position();
            for (LivingEntity e : Hits.cone(u, 10, 120)) {
                if (!Hits.hostileTo(u, e)) continue;
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
                PowerKit.loseTrack(u, e);
            }
            u.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
            Vec3 side = PowerKit.yaw(PowerKit.flatAim(c), u.getRandom().nextBoolean() ? 90 : -90);
            FruitKit.blink(u, side, 4);
            FruitKit.fx(u, "mirror_flash", start.add(0, 1.2, 0), 10, 0, 0);
            FruitKit.fx(u, "mirror_step", start.add(0, 1, 0), 0, 0, 0);
            return true;
        }));

        // Kaleidoscope: light split through a prism into a fan of five rays.
        f.add(f.ability("kaleidoscope").unlock(35).cooldown(160).stamina(34).anim("cast_forward").exec((u, c) -> {
            Vec3 from = c.eye().add(0, -0.2, 0);
            Vec3 dir = FruitKit.aimDir(c);
            for (int i = -2; i <= 2; i++) {
                Vec3 d = PowerKit.yaw(dir, i * 12);
                Vec3 end = PowerKit.beam(c, from, d, 24, 0.5, FruitKit.dmg(c, 6), light.withPoise(20));
                FruitKit.fx(u, "prism_beam", from, (float) end.x, (float) end.y, (float) end.z);
            }
            PowerKit.sound(c, from, SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 1.6f);
            return true;
        }));

        // Awakening — Hall of Mirrors: a dome of mirrors; everyone inside is struck by light from every side and
        // every shot fired inside is turned back.
        f.add(f.ability("hall_of_mirrors").unlock(55).cooldown(1000).stamina(80).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 24);
            double r = 9;
            for (int i = 0; i < 24; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 5L, () -> {
                    reflectNear(u, at.add(0, 1, 0), r);
                    if (n % 2 != 0) return;
                    FruitKit.fx(u, "mirror_dome", at, (float) r, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                        Hits.hit(u, e, FruitKit.dmg(c, 3.5f), light.withPoise(15));
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0));
                    }
                });
            }
            return true;
        }));

        // Passive (with the mirror wall up): projectiles are turned back as they arrive, and attacks from range are
        // reflected along the line they came from. Close-range blows are only softened.
        f.tickHook = u -> {
            if (CombatState.of(u).hasBuff(MIRROR)) reflectNear(u, u.position().add(0, 1, 0), 6);
        };
        f.incomingHook = (u, a, amount, info) -> {
            if (!CombatState.of(u).hasBuff(MIRROR) || a == null || amount <= 0) return amount;
            if (a.distanceTo(u) < 4.5) return amount * 0.75f;
            Vec3 from = u.getEyePosition();
            Vec3 dir = a.getBoundingBox().getCenter().subtract(from).normalize();
            AbilityProjectile p = AbilityProjectile.shoot(u, "mirror_beam", 0.5f, from.add(dir.scale(0.8)), dir, 3.0, amount * 0.8f,
                    Hits.Info.of(info.element(), info.knockback()).withPoise(info.poiseDamage()));
            p.life = 30;
            GLNetwork.fx(u, "mirror_reflect", from.x, from.y, from.z, 0, 0, 0);
            return 0f;
        };
    }

    private KagamiFruit() {}
}
