package com.grandline.fruit.impl;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Tenki Tenki no Mi — ORIGINAL weather Paramecia. The user makes weather: thunderclaps called down on a foe, a beam
 * of sunshine that heals allies and dazzles enemies, rain squalls that soak (Water element — it reaches Logia bodies,
 * douses fire and wets sand), hailstorms, a roaming thunderstorm cell, and at awakening the power to change the sky
 * itself (real world weather, which also changes the winds ships sail by).
 * Passive: storm-charged — immune to lightning; in rain or thunder the user is faster and hits 15% harder.
 */
public final class TenkiFruit {

    /** A visual-only lightning bolt (no fire, no vanilla damage) — the damage goes through the combat system. */
    static void bolt(ServerLevel l, Vec3 at) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(l);
        if (b == null) return;
        b.moveTo(at.x, at.y, at.z);
        b.setVisualOnly(true);
        l.addFreshEntity(b);
    }

    static boolean stormy(LivingEntity u) {
        return u.level().isRainingAt(u.blockPosition().above()) || u.level().isThundering();
    }

    public static void init(DevilFruit f) {
        Hits.Info thunder = FruitKit.info(Element.LIGHTNING, 0.8f);
        Hits.Info rain = FruitKit.info(Element.WATER, 0.2f);

        // Thunderclap: lightning called down from a clear sky onto the target.
        f.add(f.ability("thunderclap").cooldown(30).stamina(12).anim("cast_up").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 28);
            Vec3 at = t != null ? t.position() : FruitKit.aimPoint(c, 28);
            bolt(c.level, at);
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 2.2)) Hits.hit(u, e, FruitKit.dmg(c, 8), thunder.withStun(12).withPoise(35));
            FruitKit.fx(u, "tenki_bolt", at, 2, 0, 0);
            return true;
        }));

        // Sunshine: a beam of sunlight breaks through — allies are healed and warmed, enemies dazzled and scorched.
        f.add(f.ability("sunshine").unlock(5).cooldown(240).stamina(16).anim("cast_up").exec((u, c) -> {
            Vec3 at = u.position();
            for (LivingEntity a : OriginalKit.allies(u, at.add(0, 1, 0), 7, true)) {
                OriginalKit.heal(a, 4 + c.mastery / 15f, "tenki_sun");
                a.setTicksFrozen(0);
                a.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), 7)) {
                Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.LIGHT, 0f).withPoise(10));
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
            }
            FruitKit.fx(u, "tenki_sunbeam", at, 7, 0, 0);
            PowerKit.sound(c, at, SoundEvents.BEACON_ACTIVATE, 1f, 1.4f);
            return true;
        }));

        // Rain squall: a rain cloud over an area — enemies are soaked by real water (douses fire, wets sand, reaches
        // Logia bodies); allies under it are cooled and their fires go out.
        f.add(f.ability("rain_squall").unlock(12).cooldown(200).stamina(20).anim("cast_forward").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 24);
            double r = 6;
            OriginalKit.pulses(c, 0, 10, 12, n -> {
                FruitKit.fx(u, "tenki_rain", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    e.clearFire();
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0));
                    if (n % 2 == 0) Hits.hit(u, e, FruitKit.dmg(c, 1.5f), rain.withPoise(5));
                }
                for (LivingEntity a : OriginalKit.allies(u, at.add(0, 1, 0), r, true)) a.clearFire();
            });
            PowerKit.sound(c, at, SoundEvents.WEATHER_RAIN_ABOVE, 1.5f, 1f);
            return true;
        }));

        // Hailstorm: hailstones pelt an area — ice damage that freezes and slows.
        f.add(f.ability("hailstorm").unlock(20).cooldown(220).stamina(26).anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 24);
            double r = 6;
            OriginalKit.pulses(c, 0, 6, 12, n -> {
                FruitKit.fx(u, "tenki_hail", at, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, at.add(0, 1, 0), r)) {
                    Hits.hit(u, e, FruitKit.dmg(c, 2.5f), FruitKit.info(Element.ICE, 0.1f).withPoise(10));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1));
                }
            });
            PowerKit.sound(c, at, SoundEvents.GLASS_BREAK, 1.2f, 1.5f);
            return true;
        }));

        // Storm cell: a thunderstorm parks over an area — rain and a bolt on a random enemy inside every second.
        f.add(f.ability("storm_cell").unlock(30).cooldown(400).stamina(38).anim("cast_up").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 30);
            double r = 8;
            OriginalKit.pulses(c, 0, 15, 8 + c.mastery / 20, n -> {
                FruitKit.fx(u, "tenki_storm", at, (float) r, 0, 0);
                List<LivingEntity> foes = PowerKit.foes(u, at.add(0, 1, 0), r);
                for (LivingEntity e : foes) e.clearFire();
                if (foes.isEmpty()) return;
                LivingEntity t = foes.get(u.getRandom().nextInt(foes.size()));
                bolt(c.level, t.position());
                Hits.hit(u, t, FruitKit.dmg(c, 9), thunder.withStun(10).withPoise(40));
            });
            PowerKit.sound(c, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 1f);
            return true;
        }));

        // Awakening — Change the Sky: lightning lashes every enemy around the user, and (for players) the sky over the
        // world turns: a clear sky becomes a thunderstorm, a storm becomes clear skies.
        f.add(f.ability("change_the_sky").unlock(55).cooldown(2400).stamina(90).awakened().anim("roar").exec((u, c) -> {
            Vec3 center = u.position();
            OriginalKit.pulses(c, 0, 8, 4, n -> {
                for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), 14)) {
                    if (u.getRandom().nextInt(2) != 0 && n > 0) continue;
                    bolt(c.level, e.position());
                    Hits.hit(u, e, FruitKit.dmg(c, 8), thunder.withStun(10).withPoise(40));
                }
            });
            // only a real player bends the world's sky (NPC users and automation get the local storm only)
            if (u instanceof ServerPlayer sp && !(u instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
                boolean storm = c.level.isRaining();
                if (storm) c.level.setWeatherParameters(20 * 300, 0, false, false);
                else c.level.setWeatherParameters(0, 20 * 180, true, true);
                sp.displayClientMessage(Component.translatable(storm ? "grandline.tenki.clear" : "grandline.tenki.storm").withStyle(ChatFormatting.AQUA), true);
            }
            FruitKit.fx(u, "tenki_storm", center, 14, 1, 0);
            PowerKit.sound(c, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.5f, 0.6f);
            return true;
        }));

        // Passive: storm-charged. Immune to lightning (element); rain or thunder speeds the user and adds 15% damage.
        f.outgoingHook = (u, v, amount, info) -> stormy(u) ? amount * 1.15f : amount;
        f.tickHook = u -> {
            if (u.tickCount % 20 == 0 && stormy(u)) u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, false, false));
        };
    }

    private TenkiFruit() {}
}
