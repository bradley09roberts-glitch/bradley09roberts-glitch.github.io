package com.terracraft.registry.content;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerraDamageSource;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.accessory.AccessoryItem;
import com.terracraft.item.weapon.AmmoType;
import com.terracraft.item.weapon.MagicWeaponItem;
import com.terracraft.item.weapon.RangedWeaponItem;
import com.terracraft.item.weapon.ThrownWeaponItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.player.TerraPlayerData;
import com.terracraft.player.stats.Ability;
import com.terracraft.player.stats.Stat;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import com.terracraft.world.dungeon.DungeonManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The Dungeon after Plantera: its new skeletons and casters, the Dungeon Spirits that rise from enemies killed in
 * the Dungeon (Ectoplasm), Spectre armor (magic hits heal you) and the new treasures.
 */
public final class DungeonHardmodeContent {
    public static final RegistryObject<TerraItem> ECTOPLASM = CoreItems.material("ectoplasm", TerraRarity.YELLOW, 1000);
    public static final RegistryObject<TerraItem> SPECTRE_BAR = CoreItems.material("spectre_bar", TerraRarity.YELLOW, 15000);

    /** Spectre armor: magic damage; the set's magic hits heal you for a little of the damage dealt. */
    public static final ArmorContent.ArmorPieces SPECTRE = ArmorContent.named("spectre",
        new String[]{"spectre_hood", "spectre_robe", "spectre_pants"}, new int[]{6, 13, 8},
        new StatEffects[]{StatEffects.builder().add(Stat.MAGIC_DAMAGE, 0.40F).add(Stat.MANA_COST, -0.13F).build(),
            StatEffects.builder().add(Stat.MAGIC_DAMAGE, 0.07F).add(Stat.MAGIC_CRIT, 7).build(),
            StatEffects.builder().add(Stat.MAGIC_DAMAGE, 0.08F).add(Stat.MAGIC_CRIT, 8).build()},
        StatEffects.builder().ability(Ability.SPECTRE_HEAL).build(), TerraRarity.YELLOW, 60000);

    public static final RegistryObject<AccessoryItem> PALADINS_SHIELD = ModItems.register("paladins_shield", TabGroup.ACCESSORIES,
        p -> new AccessoryItem(p, StatEffects.builder().add(Stat.DEFENSE, 6).add(Stat.ENDURANCE, 0.10F).build()),
        p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(TerraRarity.YELLOW).value(100000).build()));
    /** Paladin's Hammer: a hammer thrown like a boomerang. */
    public static final RegistryObject<ThrownWeaponItem> PALADINS_HAMMER = ModItems.register("paladins_hammer", TabGroup.WEAPONS,
        p -> new ThrownWeaponItem(p, ProjectileKinds.PALADINS_HAMMER, false),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().melee(90).useTime(14).knockback(9.0F).crit(4).velocity(14.0F)
            .rarity(TerraRarity.YELLOW).value(100000).build()));
    public static final RegistryObject<RangedWeaponItem> SNIPER_RIFLE = ModItems.register("sniper_rifle", TabGroup.WEAPONS,
        p -> new RangedWeaponItem(p, AmmoType.BULLET, SoundEvents.GENERIC_EXPLODE.value(), 0.0F, 1, 0.0F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().ranged(185).useTime(36).knockback(8.0F).crit(29).velocity(16.0F)
            .rarity(TerraRarity.YELLOW).value(100000).build()));
    public static final RegistryObject<MagicWeaponItem> SHADOWBEAM_STAFF = ModItems.register("shadowbeam_staff", TabGroup.WEAPONS,
        p -> new MagicWeaponItem(p, ProjectileKinds.SHADOWBEAM, 1, 0.0F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(53).mana(7).useTime(16).crit(4).velocity(16.0F)
            .rarity(TerraRarity.YELLOW).value(100000).build()));

    private DungeonHardmodeContent() {}

    public static void init() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DungeonHardmodeContent::onDeath);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DungeonHardmodeContent::onDamage);
    }

    /** After Plantera, enemies killed in the Dungeon sometimes release a Dungeon Spirit. */
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !(victim instanceof TerrariaMob) || victim.getType() == MobContent.DUNGEON_SPIRIT.get()
            || !(event.getSource().getEntity() instanceof ServerPlayer) || level.getRandom().nextInt(7) != 0
            || !ProgressionManager.has(level.getServer(), ProgressionFlags.PLANTERA)
            || level.dimension() != net.minecraft.world.level.Level.OVERWORLD || !DungeonManager.layout(level).isInside(victim.blockPosition())) {
            return;
        }
        TerrariaMob spirit = MobContent.DUNGEON_SPIRIT.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (spirit != null) {
            spirit.snapTo(victim.getX(), victim.getY() + 0.5, victim.getZ(), 0, 0);
            spirit.finalizeSpawn(level, level.getCurrentDifficultyAt(victim.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            level.addFreshEntity(spirit);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, victim.getX(), victim.getY() + 1, victim.getZ(), 15, 0.3, 0.5, 0.3, 0.05);
        }
    }

    /** Spectre set: magic hits heal the caster for 8% of the damage dealt. */
    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getSource() instanceof TerraDamageSource terra && terra.hit().damageClass() == DamageClass.MAGIC
            && event.getSource().getEntity() instanceof ServerPlayer player && player.isAlive()
            && TerraPlayerData.get(player).stats().has(Ability.SPECTRE_HEAL)) {
            player.heal(Math.max(1.0F, event.getHealthDamage() * 0.08F));
        }
    }
}
