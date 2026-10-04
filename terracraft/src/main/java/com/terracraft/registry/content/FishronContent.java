package com.terracraft.registry.content;

import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.accessory.WingsItem;
import com.terracraft.item.weapon.AmmoType;
import com.terracraft.item.weapon.MagicWeaponItem;
import com.terracraft.item.weapon.RangedWeaponItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Duke Fishron: the Truffle Worm (a critter of the Hardmode mushroom biome, caught by right-clicking it) used as bait
 * - a fishing line cast into the ocean with a Truffle Worm in your inventory pulls the Duke out of the water - and
 * his treasure.
 */
public final class FishronContent {
    public static final RegistryObject<TerraItem> TRUFFLE_WORM = ModItems.register("truffle_worm", TabGroup.CONSUMABLES, TerraItem::new,
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.YELLOW, 0)));

    /** Tsunami: a bow that looses five arrows at once. */
    public static final RegistryObject<RangedWeaponItem> TSUNAMI = ModItems.register("tsunami", TabGroup.WEAPONS,
        p -> new RangedWeaponItem(p, AmmoType.ARROW, SoundEvents.ARROW_SHOOT, 2.0F, 5, 0.07F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().ranged(60).useTime(24).knockback(5.0F).crit(4).velocity(10.0F)
            .rarity(TerraRarity.YELLOW).value(250000).build()));
    /** Razorblade Typhoon: slow spinning blades that home in on enemies. */
    public static final RegistryObject<MagicWeaponItem> RAZORBLADE_TYPHOON = ModItems.register("razorblade_typhoon", TabGroup.WEAPONS,
        p -> new MagicWeaponItem(p, ProjectileKinds.RAZORBLADE, 1, 0.0F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(80).mana(16).useTime(40).knockback(5.0F).crit(4).velocity(6.0F)
            .rarity(TerraRarity.YELLOW).value(250000).build()));
    /** Bubble Gun: a fast stream of bubbles. */
    public static final RegistryObject<MagicWeaponItem> BUBBLE_GUN = ModItems.register("bubble_gun", TabGroup.WEAPONS,
        p -> new MagicWeaponItem(p, ProjectileKinds.BUBBLE, 1, 0.0F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(60).mana(4).useTime(8).knockback(2.0F).crit(4).velocity(12.0F)
            .rarity(TerraRarity.YELLOW).value(250000).build()));
    public static final RegistryObject<WingsItem> FISHRON_WINGS = ModItems.register("fishron_wings", TabGroup.ACCESSORIES,
        p -> new WingsItem(p, StatEffects.NONE, new WingsItem.Flight("fishron", 70, 0.5F)),
        p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(TerraRarity.YELLOW).value(400000).build()));

    private FishronContent() {}

    public static void init() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(FishronContent::onServerTick);
    }

    /** A line that has sat in ocean water for a couple of seconds with a Truffle Worm in the angler's pack. */
    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            FishingHook hook = player.fishing;
            if (hook == null || hook.tickCount < 50 || !hook.isInWater() || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            var biome = level.getBiome(hook.blockPosition());
            if (!biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_BEACH)) {
                continue;
            }
            int slot = -1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(TRUFFLE_WORM.get())) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0 || !com.terracraft.progression.ProgressionManager.isHardmode(level.getServer())) {
                continue;
            }
            if (BossSummoning.summonAt(level, player, MobContent.DUKE_FISHRON.get(), hook.getX(), hook.getY() - 1.5, hook.getZ()) != null) {
                ItemStack worm = player.getInventory().getItem(slot);
                if (!player.isCreative()) {
                    worm.shrink(1);
                }
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, hook.getX(), hook.getY(), hook.getZ(), 200, 2.0, 1.0, 2.0, 0.4);
                hook.discard();
            }
        }
    }
}
