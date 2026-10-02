package com.terracraft.combat;

import com.terracraft.config.TerraConfig;
import com.terracraft.item.TerraItemStats;
import com.terracraft.player.TerraPlayerData;
import com.terracraft.player.stats.Ability;
import com.terracraft.player.stats.PlayerStats;
import com.terracraft.player.stats.Stat;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

import java.lang.invoke.MethodHandles;

/**
 * TerraCraft's damage pipeline, layered onto vanilla's:
 * <ol>
 *     <li><b>Attacker</b>: class damage bonuses, Terraria's +/-15% variance and crits for player attacks
 *     (melee crits are rolled in {@link CriticalHitEvent}, projectile crits when the hit lands).</li>
 *     <li><b>Vanilla scaling</b>: vanilla environmental and vanilla mob damage against players is scaled up,
 *     because player life uses Terraria numbers (100-500).</li>
 *     <li><b>Defense</b>: Terraria defense (x0.5 Classic / x0.75 Expert / x1 Master) and endurance for
 *     players, defense x0.5 for TerraCraft enemies. Damage never drops below 1.</li>
 * </ol>
 * Fire and lava immunities granted by accessories cancel damage before it is dealt.
 */
public final class CombatEvents {
    private CombatEvents() {}

    public static void register() {
        BusGroup.DEFAULT.register(MethodHandles.lookup(), CombatEvents.class);
    }

    // ------------------------------------------------------------------ immunities

    @SubscribeEvent(priority = Priority.HIGH)
    static boolean onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return false;
        }
        DamageSource source = event.getSource();
        PlayerStats stats = TerraPlayerData.get(player).stats();
        if (stats.has(Ability.FIRE_BLOCK_IMMUNE) && (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE)
            || source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.CAMPFIRE))) {
            player.clearFire();
            return true;
        }
        if (source.is(DamageTypes.LAVA) && (stats.has(Ability.LAVA_IMMUNE) || TerraPlayerData.get(player).lavaImmunityTicks > 0)) {
            player.clearFire();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ melee crits

    @SubscribeEvent
    static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        ItemStack weapon = player.getMainHandItem();
        TerraItemStats stats = TerraItemStats.of(weapon);
        DamageClass type = stats.isWeapon() ? stats.damageClass() : DamageClass.MELEE;
        int chance = DamageCalc.BASE_CRIT + stats.crit() + TerraPlayerData.get(player).stats().critBonus(type);
        if (DamageCalc.rollCrit(chance, player.getRandom())) {
            event.setResult(Result.ALLOW);
            event.setDamageModifier(2.0F);
        } else {
            event.setResult(Result.DENY);
            event.setDamageModifier(1.0F);
        }
    }

    // ------------------------------------------------------------------ main pipeline

    @SubscribeEvent(priority = Priority.HIGH)
    static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        Entity cause = source.getEntity();
        float amount = event.getAmount();
        int armorPenetration = 0;

        // 1. attacker-side Terraria scaling
        if (source instanceof TerraDamageSource terra) {
            TerraHit hit = terra.hit();
            armorPenetration = hit.armorPenetration();
            if (!hit.preScaled() && cause instanceof Player player) {
                PlayerStats stats = TerraPlayerData.get(player).stats();
                amount *= stats.damageMultiplier(hit.damageClass());
                amount = DamageCalc.variance(amount, target.getRandom());
                armorPenetration += stats.getInt(Stat.ARMOR_PENETRATION);
                if (DamageCalc.rollCrit(hit.critChance() + stats.critBonus(hit.damageClass()), target.getRandom())) {
                    amount *= 2.0F;
                }
            }
        } else if (cause instanceof Player player) {
            PlayerStats stats = TerraPlayerData.get(player).stats();
            armorPenetration = stats.getInt(Stat.ARMOR_PENETRATION);
            if (source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player) {
                TerraItemStats weapon = TerraItemStats.of(player.getMainHandItem());
                DamageClass type = weapon.isWeapon() ? weapon.damageClass() : DamageClass.MELEE;
                amount = DamageCalc.variance(amount * stats.damageMultiplier(type), target.getRandom());
            } else if (source.getDirectEntity() instanceof AbstractArrow) {
                amount = DamageCalc.variance(amount * stats.damageMultiplier(DamageClass.RANGED), target.getRandom());
            }
        }

        // 2. vanilla damage against players is scaled to Terraria life values
        if (target instanceof Player && !(source instanceof TerraDamageSource)) {
            if (cause == null) {
                amount *= TerraConfig.COMMON.environmentalDamageMultiplier.get().floatValue();
            } else if (cause instanceof LivingEntity && !(cause instanceof Player) && !(cause instanceof HasTerrariaDefense)) {
                amount *= TerraConfig.COMMON.vanillaMobDamageMultiplier.get().floatValue();
            }
        }

        // 3. defense
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            if (target instanceof Player player) {
                PlayerStats stats = TerraPlayerData.get(player).stats();
                amount = DamageCalc.applyDefense(amount, stats.defense(), armorPenetration,
                    TerrariaDifficulty.defenseEffectiveness(player.level()));
                amount = DamageCalc.applyEndurance(amount, stats.get(Stat.ENDURANCE));
            } else if (target instanceof HasTerrariaDefense defended) {
                amount = DamageCalc.applyDefense(amount, defended.terrariaDefense(), armorPenetration, 0.5F);
            }
        }
        event.setAmount(Math.max(amount, 0.0F));
    }
}
