package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.TerrainChanges;
import com.grandline.fruit.Zoan;
import com.grandline.ship.ShipEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Tako Tako no Mi, Model: Kraken — ORIGINAL Mythical Zoan. The user becomes the sea monster sailors fear most: eight
 * crushing arms, clouds of ink, and the strength to wrap a warship and break its hull. Like every fruit user the kraken
 * still sinks in the sea — its terror is in reaching out of the water, not swimming in it.
 */
public final class TakoKrakenFruit {

    public static void init(DevilFruit f) {
        Hits.Info arm = FruitKit.info(Element.BLUNT, 1.6f);
        PowerKit.zoanTransform(f);

        // Tentacle lash: a wide sweep of arms; whoever is struck is dragged towards the kraken.
        f.add(f.ability("tentacle_lash").cooldown(24).stamina(10).anim("wide_slash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            double reach = 5 + u.getScale();
            for (LivingEntity e : Hits.cone(u, reach, 170)) {
                if (Hits.hostileTo(u, e) && PowerKit.hit(u, e, FruitKit.dmg(c, 6), arm.withPoise(25))) PowerKit.pull(e, u.position(), 0.55, 0.12);
            }
            Vec3 d = PowerKit.flatAim(c);
            FruitKit.fx(u, "kraken_lash", u.position().add(0, 1, 0), (float) reach, (float) d.x, (float) d.z);
            PowerKit.sound(c, u.position(), SoundEvents.SLIME_SQUISH, 1.2f, 0.5f);
            return true;
        }));

        // Ink jet: a blast of ink blinds everything in front, and the jet throws the kraken backwards out of reach.
        f.add(f.ability("ink_jet").unlock(5).cooldown(120).stamina(16).anim("cast_forward").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            for (LivingEntity e : Hits.cone(u, 9, 100)) {
                if (!Hits.hostileTo(u, e)) continue;
                Hits.hit(u, e, FruitKit.dmg(c, 2), FruitKit.info(Element.DARKNESS, 0.3f).withPoise(15));
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                PowerKit.loseTrack(u, e);
            }
            Vec3 back = PowerKit.flatAim(c).scale(-1.3).add(0, 0.35, 0);
            FruitKit.impulse(u, back, true);
            FruitKit.fx(u, "kraken_ink", c.eye(), (float) c.look().x, (float) c.look().y, (float) c.look().z);
            PowerKit.sound(c, u.position(), SoundEvents.SQUID_SQUIRT, 1.5f, 0.6f);
            return true;
        }));

        // Constrict: seize one target in the arms, hold it fast and squeeze.
        f.add(f.ability("constrict").unlock(15).cooldown(160).stamina(22).anim("grab").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            LivingEntity t = PowerKit.target(c, 10 + u.getScale());
            if (t == null) return false;
            Vec3 dir = PowerKit.flatAim(c);
            double reach = Math.max(0.8, PowerKit.rayLength(u, u.position().add(0, 0.5, 0), dir, 1.5 + u.getScale()) - 0.4);
            Vec3 hold = u.position().add(dir.scale(reach));
            OriginalKit.pulses(c, 0, 10, 6, n -> {
                if (!t.isAlive()) return;
                if (t.distanceToSqr(hold) > 1.5) PowerKit.place(t, new Vec3(hold.x, t.getY(), hold.z));
                t.setDeltaMovement(Vec3.ZERO);
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 6, false, false));
                Hits.hit(u, t, FruitKit.dmg(c, n == 5 ? 7 : 3), arm.withStun(12).withPoise(20).asUnblockable());
                FruitKit.fx(u, "kraken_grip", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            });
            PowerKit.sound(c, t.position(), SoundEvents.SLIME_SQUISH, 1.5f, 0.4f);
            return true;
        }));

        // Ship wrecker: wrap the arms around the nearest enemy ship and crush its hull. With no ship in reach the kraken
        // hauls its target into the air and smashes it down.
        f.add(f.ability("ship_wrecker").unlock(25).cooldown(400).stamina(40).anim("double_palm").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            ShipEntity ship = OriginalKit.enemyShip(u, u.position(), 22);
            if (ship != null) {
                float per = 14 + c.mastery / 8f;
                OriginalKit.pulses(c, 0, 10, 8, n -> {
                    if (!ship.isAlive() || ship.isSinking()) return;
                    OriginalKit.wreck(u, ship, per * (c.awakened ? 1.3f : 1f));
                    FruitKit.fx(u, "kraken_crush", ship.position().add(0, 1, 0), ship.cls.length / 2f, ship.cls.beam, 0);
                    PowerKit.sound(c, ship.position(), SoundEvents.WOOD_BREAK, 2f, 0.5f);
                });
                return true;
            }
            LivingEntity t = PowerKit.target(c, 12 + u.getScale());
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 6), arm.withStun(30).asUnblockable());
            FruitKit.impulse(t, new Vec3(0, 1.1, 0), true);
            FruitKit.fx(u, "kraken_grip", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            CombatState.of(u).schedule(c.level.getGameTime() + 14, () -> {
                if (!t.isAlive()) return;
                FruitKit.impulse(t, new Vec3(0, -2.2, 0), true);
                Hits.hit(u, t, FruitKit.dmg(c, 12), FruitKit.info(Element.BLUNT, 0.5f).withStun(25).withPoise(80).asUnblockable());
                BlockPos g = PowerKit.ground(c.level, t.getBlockX(), t.getBlockY(), t.getBlockZ());
                if (g != null) TerrainChanges.crater(c.level, g, 2, u);
                FruitKit.fx(u, "kraken_slam", t.position(), 2.5f, 0, 0);
            });
            return true;
        }));

        // Awakening — Abyssal Leviathan: colossal arms burst up in a ring around a point, seize everyone inside and
        // drag them to the centre (and under, at sea); ships caught in the ring are crushed.
        f.add(f.ability("abyssal_leviathan").unlock(55).cooldown(1100).stamina(90).awakened().anim("roar").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 at = FruitKit.aimPoint(c, 30);
            double r = 10;
            Set<Integer> grabbed = new HashSet<>();
            OriginalKit.pulses(c, 0, 5, 20, n -> {
                FruitKit.fx(u, "kraken_tentacles", at, (float) r, n, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    boolean sea = e.isInWater();
                    PowerKit.pull(e, at, 0.35, sea ? -0.25 : 0.05);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
                    if (grabbed.add(e.getId())) Hits.hit(u, e, FruitKit.dmg(c, 8), arm.withStun(20).withPoise(60));
                    else if (n % 4 == 0) Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.BLUNT, 0f).withPoise(15));
                }
                if (n % 5 == 0) for (ShipEntity s : OriginalKit.ships(u, at, r)) if (!OriginalKit.friendly(u, s)) OriginalKit.wreck(u, s, 25 + c.mastery / 5f);
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 102, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 3.5))
                    Hits.hit(u, e, FruitKit.dmg(c, 16), FruitKit.info(Element.BLUNT, 3f).withStun(30).withPoise(100).asUnblockable());
                FruitKit.fx(u, "kraken_slam", at, 4, 0, 0);
                PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.5f);
            });
            PowerKit.sound(c, at, SoundEvents.ELDER_GUARDIAN_CURSE, 1.5f, 0.5f);
            return true;
        }));

        // Passive: a boneless, regrowing body. Transformed, blunt blows and shockwaves are cushioned and lost flesh
        // regrows while badly hurt.
        f.incomingHook = (u, a, amount, info) -> !Zoan.form(u).isEmpty() && (info.element() == Element.BLUNT || info.element() == Element.IMPACT) ? amount * 0.75f : amount;
        f.tickHook = u -> {
            if (Zoan.form(u).isEmpty() || u.tickCount % 40 != 0) return;
            if (u.getHealth() < u.getMaxHealth() * 0.5f) {
                u.heal(1.5f);
                com.grandline.net.GLNetwork.fx(u, "kraken_regrow", u.getX(), u.getY() + 1, u.getZ(), 0, 0, 0);
            }
        };
    }

    private TakoKrakenFruit() {}
}
