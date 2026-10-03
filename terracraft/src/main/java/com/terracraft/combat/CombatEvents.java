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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;


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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(CombatEvents.class);
    }

    // ------------------------------------------------------------------ immunities

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DamageSource source = event.getSource();
        PlayerStats stats = TerraPlayerData.get(player).stats();
        if (stats.has(Ability.FIRE_BLOCK_IMMUNE) && (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE)
            || source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.CAMPFIRE))) {
            player.clearFire();
            event.setCanceled(true);
            return;
        }
        if (source.is(DamageTypes.LAVA) && (stats.has(Ability.LAVA_IMMUNE) || TerraPlayerData.get(player).lavaImmunityTicks > 0)) {
            player.clearFire();
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ melee crits

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        ItemStack weapon = player.getMainHandItem();
        TerraItemStats stats = TerraItemStats.of(weapon);
        DamageClass type = stats.isWeapon() ? stats.damageClass() : DamageClass.MELEE;
        int chance = DamageCalc.BASE_CRIT + stats.crit() + TerraPlayerData.get(player).stats().critBonus(type);
        boolean crit = DamageCalc.rollCrit(chance, player.getRandom());
        event.setCriticalHit(crit);
        event.setDamageMultiplier(crit ? 2.0F : 1.0F);
    }

    // ------------------------------------------------------------------ main pipeline

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingIncomingDamageEvent event) {
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
        if (target instanceof com.terracraft.entity.mob.TerrariaMob mob && mob.lifeScale() > 1.0F) {
            // Enemies with more than 1000 life keep Minecraft health <= 1000; damage is scaled to match.
            amount /= mob.lifeScale();
        }
        event.setAmount(Math.max(amount, 0.0F));
        if (amount > 0.0F && target instanceof Player player) {
            // Taking damage restarts Terraria's natural life regeneration ramp.
            com.terracraft.player.LifeRegenManager.onHurt(TerraPlayerData.get(player));
            if (TerraPlayerData.get(player).stats().has(com.terracraft.player.stats.Ability.HONEY_COMB)) {
                // Terraria: 1-3 bees (more for bigger hits) and the Honey buff
                int bees = 1 + Math.min(2, (int) (amount / 20.0F));
                com.terracraft.registry.content.WeaponContent.releaseBees(player,
                    source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker ? attacker : null, bees, 8.0F);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.terracraft.registry.ModEffects.REGENERATION.getHolder().orElseThrow(), 100, 0, false, true, true));
            }
            if (TerraPlayerData.get(player).stats().has(com.terracraft.player.stats.Ability.PANIC)) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.terracraft.registry.ModEffects.SWIFTNESS.getHolder().orElseThrow(), 160, 0, false, true, true));
            }
        }
    }
}
