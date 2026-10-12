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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Kame Kame no Mi, Model: Genbu — ORIGINAL Mythical Zoan. The user becomes the Black Tortoise of the North, the
 * shell-backed guardian entwined with a serpent (the Azure Dragon's northern counterpart): a shell no blow can crack,
 * a venomous serpent tail, stomps that burst with northern water, a ward that shelters the whole crew, and at awakening the Winter of the North that drowns and freezes an area.
 * Passive: a living fortress — in form, blows from behind hit the shell (-50%) and the old tortoise slowly mends.
 */
public final class GenbuFruit {
    static final String SHELL = "genbu_shell";
    static final BlockState ICE = Blocks.PACKED_ICE.defaultBlockState();

    public static void init(DevilFruit f) {
        Hits.Info tide = FruitKit.info(Element.WATER, 1.6f);
        PowerKit.zoanTransform(f);

        // Shell guard: withdraw into the shell — damage is nearly nullified, knockback ignored, and melee attackers
        // take part of their blow back.
        f.add(f.ability("shell_guard").cooldown(160).stamina(14).anim("harden").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            int dur = 60 + c.mastery / 2;
            CombatState.of(u).buffs.put(SHELL, dur);
            u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 3, false, false));
            // the shell slams shut on anyone pressed against it
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 2.5 + u.getScale() * 0.5)) Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.BLUNT, 1.5f).withPoise(20));
            FruitKit.fx(u, "genbu_shell", u.position().add(0, 1, 0), u.getBbWidth() + 0.8f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.SHIELD_BLOCK, 1.2f, 0.5f);
            return true;
        }));

        // Serpent tail: the serpent coiled about the shell lashes out — a venomous strike.
        f.add(f.ability("serpent_tail").unlock(5).cooldown(40).stamina(12).anim("whip_crack").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 from = c.eye().add(0, -0.5, 0);
            Vec3 dir = FruitKit.aimDir(c);
            double len = PowerKit.rayLength(u, from, dir, 7 + u.getScale());
            for (LivingEntity e : Hits.line(u, from, dir, len, 0.8)) {
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 7), FruitKit.info(Element.PIERCE, 0.5f).withPoise(30)))
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
            }
            Vec3 end = from.add(dir.scale(len));
            FruitKit.fx(u, "genbu_serpent", from, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, from, SoundEvents.EVOKER_FANGS_ATTACK, 0.8f, 1.4f);
            return true;
        }));

        // Tidal stomp: a stomp bursts northern water out in a ring — larger beside the sea.
        f.add(f.ability("tidal_stomp").unlock(15).cooldown(90).stamina(18).anim("ground_slam").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            double r = UzuFruit.nearWater(c.level, u.position(), 5) ? 7.5 : 5;
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 0.5, 0), r))
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 8), tide.withPoise(40))) PowerKit.push(e, u.position(), 0.9, 0.35);
            FruitKit.fx(u, "genbu_wave", u.position(), (float) r, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.GENERIC_SPLASH, 2f, 0.5f);
            return true;
        }));

        // Northern ward: the guardian shelters its allies — resistance and a barrier of absorption for the whole crew
        // nearby, and every hostile projectile in the area is knocked out of the air.
        f.add(f.ability("northern_ward").unlock(30).cooldown(700).stamina(30).anim("roar").exec((u, c) -> {
            int dur = 300 + c.mastery * 2;
            for (LivingEntity a : OriginalKit.allies(u, u.position().add(0, 1, 0), 8, true)) {
                a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 1));
                a.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, dur, 1));
                FruitKit.fx(u, "genbu_ward", a.position().add(0, 1, 0), a.getBbWidth() + 0.5f, 0, 0);
            }
            for (Projectile p : PowerKit.hostileProjectiles(u, u.position().add(0, 1, 0), 10)) p.discard();
            // the roar's pressure shoves enemies back from the crew
            for (LivingEntity e : PowerKit.foes(u, u.position().add(0, 1, 0), 5))
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 3), tide.withPoise(20))) PowerKit.push(e, u.position(), 0.8, 0.2);
            PowerKit.sound(c, u.position(), SoundEvents.CONDUIT_ACTIVATE, 1.5f, 0.7f);
            return true;
        }));

        // Awakening — Winter of the North: a towering wave of northern sea crashes onto a point, then freezes solid —
        // the ground and sea surface ice over (temporarily) and everyone caught is frozen in place.
        f.add(f.ability("winter_of_the_north").unlock(55).cooldown(1100).stamina(90).awakened().anim("roar").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 28);
            double r = 9;
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                Hits.hit(u, e, FruitKit.dmg(c, 14), tide.withPoise(80));
                PowerKit.push(e, at, 0.6, 0.5);
            }
            FruitKit.fx(u, "genbu_wave", at, (float) r, 1, 0);
            PowerKit.sound(c, at, SoundEvents.GENERIC_SPLASH, 2.5f, 0.3f);
            CombatState.of(u).schedule(c.level.getGameTime() + 20, () -> {
                PowerKit.paintDisc(c.level, at, r - 1, null, ICE);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 8), FruitKit.info(Element.ICE, 0f).withStun(60).withPoise(80).asUnblockable());
                    e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + 120));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 4));
                }
                FruitKit.fx(u, "genbu_freeze", at, (float) r, 0, 0);
                PowerKit.sound(c, at, SoundEvents.GLASS_BREAK, 2f, 0.4f);
            });
            return true;
        }));

        // Passive: a living fortress. In form the shell takes blows from behind (-50%); withdrawn (Shell Guard) almost
        // nothing gets through and melee attackers take part of it back. The old tortoise slowly mends.
        f.incomingHook = (u, a, amount, info) -> {
            if (CombatState.of(u).hasBuff(SHELL)) {
                if (a != null && a.distanceTo(u) < 4) PowerKit.push(a, u.position(), 0.6, 0.2);
                return amount * 0.15f;
            }
            if (Zoan.form(u).isEmpty() || a == null) return amount;
            Vec3 to = a.position().subtract(u.position()).multiply(1, 0, 1);
            Vec3 look = u.getLookAngle().multiply(1, 0, 1);
            if (to.lengthSqr() > 1e-4 && look.lengthSqr() > 1e-4 && to.normalize().dot(look.normalize()) < -0.3) return amount * 0.5f;
            return amount;
        };
        f.tickHook = u -> {
            if (Zoan.form(u).isEmpty() || u.tickCount % 60 != 0) return;
            if (u.getHealth() < u.getMaxHealth()) u.heal(1f);
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 70, 0, false, false));
        };
    }

    private GenbuFruit() {}
}
