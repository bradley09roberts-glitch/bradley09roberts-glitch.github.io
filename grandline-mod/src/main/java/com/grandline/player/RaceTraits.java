package com.grandline.player;

import com.grandline.combat.CombatState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.biome.Biome;

/** Per-race passive traits (agreed: meaningful trade-offs). Runs every server tick for created characters. */
public final class RaceTraits {
    private static void eff(ServerPlayer sp, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e, int amp) {
        sp.addEffect(new MobEffectInstance(e, 60, amp, true, false));
    }

    public static void tick(ServerPlayer sp, CharacterData d) {
        com.grandline.haki.Haki.tick(sp, d);
        com.grandline.fruit.FruitRules.tick(sp);
        com.grandline.navigation.Navigation.tick(sp, d);
        com.grandline.quest.Awakening.tick(sp, d);
        com.grandline.race.RacePowers.tick(sp, d); // swim speed, racial passives, Sulong exhaustion
        if (sp.tickCount % 20 != 0) return;
        Race r = d.race;
        boolean inWater = sp.isInWater();
        BlockPos pos = sp.blockPosition();
        float temp = sp.level().getBiome(pos).value().getBaseTemperature();
        boolean daySky = sp.level().isDay() && sp.level().canSeeSky(pos) && !sp.level().isRaining();
        if (r.breathesWater() || r == Race.WHALEFOLK) {
            if (sp.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) sp.setAirSupply(sp.getMaxAirSupply());
        }
        if (inWater && (r == Race.FISHMAN || r == Race.MERFOLK)) d.masteryXp.put("race/dryness", 0);
        switch (r) {
            case FISHMAN -> {
                if (inWater) eff(sp, MobEffects.DAMAGE_BOOST, 1);
                else if (temp > 1.5f && daySky) tickDry(sp, d);
            }
            case MERFOLK -> {
                if (inWater) eff(sp, MobEffects.DOLPHINS_GRACE, 0);
                else if (temp > 1.5f && daySky) tickDry(sp, d);
            }
            case MINK -> {
                if (CombatState.of(sp).hasBuff("sulong")) eff(sp, MobEffects.HUNGER, 1);
            }
            case REEFBORN -> {
                if (inWater && sp.getHealth() < sp.getMaxHealth()) sp.heal(0.5f);
            }
            case STONEHIDE -> {
                if (inWater) eff(sp, MobEffects.MOVEMENT_SLOWDOWN, 1);
            }
            case ABYSSAL -> {
                eff(sp, MobEffects.NIGHT_VISION, 0);
                if (daySky) eff(sp, MobEffects.WEAKNESS, 0);
            }
            case EMBERKIN -> {
                eff(sp, MobEffects.FIRE_RESISTANCE, 0);
                if (inWater || temp < 0.15f) eff(sp, MobEffects.MOVEMENT_SLOWDOWN, 0);
            }
            case VINEFOLK -> {
                if (daySky && sp.tickCount % 80 == 0 && sp.getHealth() < sp.getMaxHealth()) sp.heal(1f);
            }
            case STARBORN -> {
                if (!sp.level().isDay() && sp.level().canSeeSky(pos)) eff(sp, MobEffects.MOVEMENT_SPEED, 0);
                eff(sp, MobEffects.NIGHT_VISION, 0);
            }
            case WHALEFOLK -> {
                if (inWater) eff(sp, MobEffects.DAMAGE_BOOST, 0);
            }
            case LUNARIAN -> eff(sp, MobEffects.FIRE_RESISTANCE, 0);
            default -> {}
        }
        // gliding races: slow fall while the client reports glide
        if (CombatState.of(sp).gliding && !sp.onGround() && !inWater) {
            sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, true, false));
            sp.fallDistance = 0;
        }
    }

    private static void tickDry(ServerPlayer sp, CharacterData d) {
        int dry = d.masteryXp.merge("race/dryness", 1, Integer::sum);
        if (dry > 300) {
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, true));
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true, true));
        }
        if (sp.isInWaterRainOrBubble()) d.masteryXp.put("race/dryness", 0);
    }

    private RaceTraits() {}
}
