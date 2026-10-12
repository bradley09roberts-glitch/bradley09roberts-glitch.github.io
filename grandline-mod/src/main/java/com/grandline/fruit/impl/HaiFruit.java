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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Hai Hai no Mi — ORIGINAL ash Logia (wave 4). The user becomes volcanic ash, the grey breath of the New World's
 * burning islands: smouldering cinders, choking clouds that swallow the light, a pyroclastic surge that buries
 * everything in its path, and ash that sets around a body like the casts of a buried city.
 * Passive: what has already burnt cannot burn again (fire and magma pass through the ash); attackers who strike the
 * cloud get ash in their eyes.
 * Weakness: water turns ash to heavy slurry — water hits deal 30% more and leave the body solid for a few seconds.
 */
public final class HaiFruit {
    static final String SLURRY = "hai_slurry";
    static final BlockState CAST = Blocks.TUFF.defaultBlockState();

    /** Ash in the lungs and eyes: blinded, gasping, slowed. */
    static void choke(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1));
        e.setAirSupply(Math.max(-10, e.getAirSupply() - 30));
    }

    public static void init(DevilFruit f) {
        Hits.Info ash = FruitKit.info(Element.SMOKE, 0.3f);
        Hits.Info ember = FruitKit.info(Element.FIRE, 0.6f);

        // Cinder shot: a smouldering clot of ash and embers; it bursts in a puff that blinds whoever it hits.
        f.add(f.ability("cinder_shot").cooldown(18).stamina(8).anim("throw").exec((u, c) -> {
            AbilityProjectile p = FruitKit.shoot(c, "hai_cinder", 0.5f, 2.0, FruitKit.dmg(c, 6), ember.withPoise(20));
            p.gravity = 0.01;
            p.life = 40;
            p.onHit = (pr, hit, pos) -> {
                for (LivingEntity e : PowerKit.foes(u, pos, 2)) choke(e, 30);
            };
            return true;
        }));

        // Ash cloud: a dense cloud of ash billows over a point for six seconds — enemies inside choke, go blind and
        // lose track of everything.
        f.add(f.ability("ash_cloud").unlock(5).cooldown(160).stamina(18).anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 18);
            double r = 4 + c.mastery / 50.0;
            OriginalKit.pulses(c, 0, 10, 12, n -> {
                if (n % 2 == 0) FruitKit.fx(u, "hai_cloud", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    choke(e, 30);
                    PowerKit.loseTrack(u, e);
                    if (n % 3 == 0) Hits.hit(u, e, FruitKit.dmg(c, 2), ash.withPoise(10));
                }
            });
            PowerKit.sound(c, at, SoundEvents.SAND_BREAK, 1.5f, 0.5f);
            return true;
        }));

        // Pyroclastic surge: the user collapses into a burning avalanche of ash and rushes forward — everyone along the
        // path is buried, scorched and thrown.
        f.add(f.ability("pyroclastic_surge").unlock(12).cooldown(140).stamina(24).anim("dash").exec((u, c) -> {
            Vec3 start = u.position();
            Vec3 dir = PowerKit.flatAim(c);
            CombatState.of(u).iframesUntil = c.level.getGameTime() + 8;
            Vec3 end = FruitKit.blink(u, dir, 11);
            for (LivingEntity e : Hits.line(u, start.add(0, 1, 0), end.subtract(start), start.distanceTo(end) + 2, 1.6)) {
                if (!Hits.hostileTo(u, e)) continue;
                if (PowerKit.hit(u, e, FruitKit.dmg(c, 9), ember.withStun(10).withPoise(45))) {
                    choke(e, 40);
                    PowerKit.push(e, start, 0.6, 0.45);
                }
            }
            FruitKit.fx(u, "hai_surge", start, (float) end.x, (float) end.y, (float) end.z);
            PowerKit.sound(c, end, SoundEvents.GRAVEL_BREAK, 2f, 0.5f);
            return true;
        }));

        // Ash cast: ash settles on the target and sets like stone around it — a cast like those of a buried city.
        f.add(f.ability("ash_cast").unlock(20).cooldown(240).stamina(28).anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 16);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 8), ash.withStun(60).withPoise(80).asUnblockable());
            OriginalKit.encaseFor(c, t, CAST, 90);
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 90, 5));
            choke(t, 60);
            FruitKit.fx(u, "hai_cast", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.STONE_BREAK, 1.5f, 0.6f);
            return true;
        }));

        // Ashfall: ash rains over a wide area; every second the fall grows heavier — enemies are slowed more and more
        // and finally the hot ash flares up around them.
        f.add(f.ability("ashfall").unlock(30).cooldown(320).stamina(34).anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 22);
            double r = 7;
            OriginalKit.pulses(c, 0, 10, 8, n -> {
                FruitKit.fx(u, "hai_fall", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, Math.min(4, n / 2)));
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
                    Hits.hit(u, e, FruitKit.dmg(c, n == 7 ? 8 : 2), (n == 7 ? ember : ash).withPoise(n == 7 ? 40 : 10));
                }
            });
            PowerKit.sound(c, at, SoundEvents.SAND_BREAK, 2f, 0.3f);
            return true;
        }));

        // Awakening — Volcanic Winter: the ash blots out the sun over a vast area. Enemies inside choke in darkness and
        // grow heavier every second until they are cast in ash where they stand; allies vanish into the gloom.
        f.add(f.ability("volcanic_winter").unlock(55).cooldown(1100).stamina(90).awakened().anim("cast_up").exec((u, c) -> {
            Vec3 at = u.position();
            double r = 12;
            OriginalKit.pulses(c, 0, 10, 12, n -> {
                if (n % 2 == 0) FruitKit.fx(u, "hai_winter", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, Math.min(5, 1 + n / 3)));
                    choke(e, 30);
                    PowerKit.loseTrack(u, e);
                    Hits.hit(u, e, FruitKit.dmg(c, 3), ash.withPoise(15));
                }
                for (LivingEntity a : OriginalKit.allies(u, at.add(0, 1, 0), r, true))
                    a.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
            });
            CombatState.of(u).schedule(c.level.getGameTime() + 122, () -> {
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 12), ember.withStun(50).withPoise(80).asUnblockable());
                    FruitKit.fx(u, "hai_cast", e.position(), e.getBbWidth(), e.getBbHeight(), 0);
                }
                PowerKit.sound(c, at, SoundEvents.STONE_BREAK, 2f, 0.3f);
            });
            PowerKit.sound(c, at, SoundEvents.ELDER_GUARDIAN_AMBIENT, 2f, 0.4f);
            return true;
        }));

        // Passive: an ash body (Logia). Fire and magma pass through what has already burnt; attackers get ash in their
        // eyes. Water turns the ash to slurry: it always connects, hits 30% harder and the body stays solid a while.
        f.intangibleHook = (u, a, info) -> {
            if (CombatState.of(u).hasBuff(SLURRY) || u.isInWaterOrRain()) return false;
            if (a != null && a.distanceTo(u) < 3.5) a.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
            return true;
        };
        f.incomingHook = (u, a, amount, info) -> {
            if (info.element() == Element.FIRE || info.element() == Element.MAGMA) return 0f;
            if (info.element() == Element.WATER) {
                CombatState.of(u).buffs.put(SLURRY, 80);
                return amount * 1.3f;
            }
            return amount;
        };
        f.tickHook = u -> {
            if (u.tickCount % 20 == 0 && u.isOnFire()) u.clearFire();
        };
    }

    private HaiFruit() {}
}
