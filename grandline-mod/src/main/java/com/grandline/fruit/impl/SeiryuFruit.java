package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.FruitRules;
import com.grandline.fruit.TerrainChanges;
import com.grandline.fruit.Zoan;
import com.grandline.player.Characters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Uo Uo no Mi, Model: Seiryu — Mythical Zoan, an azure eastern dragon. It rides the clouds (true flight while
 * transformed), breathes concentrated heat, cuts with blades of wind and twists the air into tornadoes. Its scales
 * shrug off blades and fire.
 */
public final class SeiryuFruit {
    /** Flight toggle. The "zoan" prefix makes {@link Zoan#forceRevert} (sea, seastone) clear it automatically. */
    public static final String FLIGHT = "zoan_seiryu_flight";
    /** Marker in the player's saved data: flight was granted by this fruit, so it can always be taken back. */
    static final String GRANTED = "grandline_seiryu_flight";

    static boolean mayFly(ServerPlayer sp) {
        return CombatState.of(sp).toggles.contains(FLIGHT) && "uo_seiryu".equals(Characters.get(sp).fruit)
                && !Zoan.form(sp).isEmpty() && !FruitRules.weakened(sp);
    }

    /** Grant or revoke cloud-riding flight for a player. Called on use and every player tick (see Wave3Events). */
    public static void updateFlight(ServerPlayer sp) {
        var tag = sp.getPersistentData();
        boolean granted = tag.getBoolean(GRANTED);
        if (!granted && !CombatState.of(sp).toggles.contains(FLIGHT)) return;
        if (mayFly(sp)) {
            if (!granted) tag.putBoolean(GRANTED, true);
            if (!sp.getAbilities().mayfly) {
                sp.getAbilities().mayfly = true;
                sp.onUpdateAbilities();
            }
            // flying costs stamina; out of breath, the dragon has to land
            if (sp.getAbilities().flying && sp.tickCount % 20 == 0 && !Hits.spendStamina(sp, 3)) CombatState.of(sp).toggles.remove(FLIGHT);
            sp.fallDistance = 0;
            return;
        }
        tag.remove(GRANTED);
        CombatState.of(sp).toggles.remove(FLIGHT);
        if (!sp.isCreative() && !sp.isSpectator() && sp.getAbilities().mayfly) {
            boolean wasFlying = sp.getAbilities().flying;
            sp.getAbilities().mayfly = false;
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            if (wasFlying) sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
        }
    }

    public static void init(DevilFruit f) {
        PowerKit.zoanTransform(f);

        // Cloud Ride: the dragon takes to the sky (toggle). Players fly freely, spending stamina; NPCs surge upward.
        f.add(f.ability("cloud_ride").cooldown(20).stamina(8).anim("cast_up").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            CombatState s = CombatState.of(u);
            if (u instanceof ServerPlayer sp) {
                if (!s.toggles.remove(FLIGHT)) {
                    s.toggles.add(FLIGHT);
                    FruitKit.impulse(sp, new Vec3(0, 0.8, 0), false);
                }
                updateFlight(sp);
            } else {
                LivingEntity t = c.target;
                Vec3 to = t != null ? t.position().subtract(u.position()).multiply(1, 0, 1) : c.look().multiply(1, 0, 1);
                Vec3 d = to.lengthSqr() < 1e-4 ? Vec3.ZERO : to.normalize().scale(0.8);
                FruitKit.impulse(u, new Vec3(d.x, 1.1, d.z), true);
                u.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
            }
            FruitKit.fx(u, "dragon_cloud", u.position(), 2.5f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.ENDER_DRAGON_FLAP, 1f, 0.7f);
            return true;
        }));

        // Bolo Breath: a beam of concentrated heat from the jaws that explodes where it lands.
        f.add(f.ability("bolo_breath").unlock(8).cooldown(80).stamina(24).anim("roar").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 from = c.eye();
            Vec3 dir = FruitKit.aimDir(c);
            Vec3 end = PowerKit.beam(c, from, dir, 26, 1.0, FruitKit.dmg(c, 12), FruitKit.info(Element.FIRE, 1.5f).withPoise(60));
            for (LivingEntity e : PowerKit.foes(u, end, 3)) Hits.hit(u, e, FruitKit.dmg(c, 6), FruitKit.info(Element.FIRE, 1.5f).withPoise(30));
            FruitKit.fx(u, "bolo_breath", from, (float) end.x, (float) end.y, (float) end.z);
            FruitKit.fx(u, "explosion", end, 2.5f, 0, 0);
            PowerKit.sound(c, u.position(), SoundEvents.ENDER_DRAGON_SHOOT, 1.4f, 0.6f);
            return true;
        }));

        // Kaifu: the dragon cuts the air into three great blades of wind.
        f.add(f.ability("kaifu").unlock(15).cooldown(60).stamina(20).anim("wide_slash").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 dir = FruitKit.aimDir(c);
            for (int i = -1; i <= 1; i++) {
                AbilityProjectile p = AbilityProjectile.shoot(u, "air_blade_big", 1.2f, c.eye().add(dir.scale(0.8)), PowerKit.yaw(dir, i * 10), 2.4,
                        FruitKit.dmg(c, 8), FruitKit.info(Element.WIND, 1.2f).withPoise(35));
                p.pierce = true;
                p.life = 32;
            }
            return true;
        }));

        // Dragon Twister: the dragon coils and spins up a tornado that lifts and shreds everything nearby.
        f.add(f.ability("dragon_twister").unlock(25).cooldown(200).stamina(32).anim("polearm_spin").exec((u, c) -> {
            if (!PowerKit.inForm(u)) return false;
            Vec3 center = u.position();
            for (int i = 0; i < 10; i++) {
                int n = i;
                CombatState.of(u).schedule(c.level.getGameTime() + i * 4L, () -> {
                    FruitKit.fx(u, "tornado", center, 6, 0, 0);
                    for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), 7)) {
                        PowerKit.swirl(e, center, 0.45, 0.2, n < 9 ? 0.18 : 0.9);
                        Hits.hit(u, e, FruitKit.dmg(c, 2), FruitKit.info(Element.WIND, 0f).withPoise(10));
                    }
                });
            }
            return true;
        }));

        // Awakening — Thunder Bagua: one colossal, Haki-wreathed blow that splits the ground and calls down thunder.
        f.add(f.ability("thunder_bagua").unlock(55).cooldown(1100).stamina(85).awakened().anim("heavy_overhead").exec((u, c) -> {
            Zoan.setForm(u, "awakened");
            Hits.Info blow = FruitKit.info(Element.BLUNT, 4.5f).withStun(40).withPoise(150).asUnblockable().imbue(true);
            for (LivingEntity e : Hits.cone(u, 7, 120)) Hits.hit(u, e, FruitKit.dmg(c, 24), blow);
            Vec3 at = u.position().add(PowerKit.flatAim(c).scale(3.5));
            TerrainChanges.crater(c.level, BlockPos.containing(at).below(), 3, u);
            SoruFruit.thunder(c.level, at);
            FruitKit.fx(u, "black_lightning", at.add(0, 1, 0), 4, 0, 0);
            FruitKit.fx(u, "shockwave", at.add(0, 0.2, 0), 7, 0, 0);
            PowerKit.sound(c, at, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.4f);
            return true;
        }));

        // Passive: dragon scales — transformed, the user resists damage and fire, and blades and stabs do 25% less.
        f.tickHook = u -> {
            if (Zoan.form(u).isEmpty() || u.tickCount % 20 != 0) return;
            u.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, false, false));
            u.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 30, 0, false, false));
        };
        f.incomingHook = (u, a, amount, info) -> !Zoan.form(u).isEmpty() && (info.element() == Element.SLASH || info.element() == Element.PIERCE) ? amount * 0.75f : amount;
    }

    private SeiryuFruit() {}
}
