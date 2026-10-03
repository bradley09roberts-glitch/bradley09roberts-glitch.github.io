package com.terracraft.world;

import com.terracraft.config.TerraConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Set;

/**
 * Removes vanilla systems that would replace or bypass Terraria progression:
 * <ul>
 *     <li>villagers, zombie villagers, wandering traders and trader llamas never enter the world
 *     (also removes them from old chunks), and nothing can convert into a villager;</li>
 *     <li>vanilla hostile mobs stop spawning naturally (Terraria enemies replace them);</li>
 *     <li>travel to the Nether and the End is blocked (the Underworld is at the bottom of the overworld);</li>
 *     <li>enchanting tables and brewing stands cannot be used.</li>
 * </ul>
 * Village/outpost/mansion/stronghold generation and the related recipes are removed through datapack
 * overrides shipped in the mod ({@code data/minecraft/...}). Everything here follows the config.
 */
public final class VanillaSuppression {
    private static final Set<EntityType<?>> VILLAGER_TYPES = Set.of(EntityTypes.VILLAGER, EntityTypes.ZOMBIE_VILLAGER);
    private static final Set<EntityType<?>> TRADER_TYPES = Set.of(EntityTypes.WANDERING_TRADER, EntityTypes.TRADER_LLAMA);

    private VanillaSuppression() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(VanillaSuppression.class);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (onJoinCancels(event)) {
            event.setCanceled(true);
        }
    }

    private static boolean onJoinCancels(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        EntityType<?> type = entity.getType();
        if (TerraConfig.COMMON.disableVillagers.get() && (VILLAGER_TYPES.contains(type) || entity instanceof AbstractVillager && type != EntityTypes.WANDERING_TRADER)) {
            return true;
        }
        return TerraConfig.COMMON.disableWanderingTraders.get() && TRADER_TYPES.contains(type);
    }

    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Pre event) {
        if (onConversionCancels(event)) {
            event.setCanceled(true);
        }
    }

    private static boolean onConversionCancels(LivingConversionEvent.Pre event) {
        return TerraConfig.COMMON.disableVillagers.get() && VILLAGER_TYPES.contains(event.getOutcome());
    }

    /** Denies natural spawning of vanilla hostile mobs. Spawners, commands and eggs still work. */
    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (!TerraConfig.COMMON.disableVanillaHostileSpawns.get()) {
            return;
        }
        EntitySpawnReason reason = event.getSpawnType();
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION && reason != EntitySpawnReason.PATROL) {
            return;
        }
        Mob mob = event.getEntity();
        if (mob.getType().getCategory() == MobCategory.MONSTER && "minecraft".equals(EntityType.getKey(mob.getType()).getNamespace())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (onTravelCancels(event)) {
            event.setCanceled(true);
        }
    }

    private static boolean onTravelCancels(EntityTravelToDimensionEvent event) {
        boolean blocked = (event.getDimension() == Level.NETHER && TerraConfig.COMMON.disableNether.get())
            || (event.getDimension() == Level.END && TerraConfig.COMMON.disableEnd.get());
        if (blocked && event.getEntity() instanceof ServerPlayer player) {
            player.sendOverlayMessage(Component.translatable(event.getDimension() == Level.NETHER
                ? "message.terracraft.nether_disabled" : "message.terracraft.end_disabled").withStyle(ChatFormatting.DARK_RED));
        }
        return blocked;
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (onUseBlockCancels(event)) {
            event.setCanceled(true);
        }
    }

    private static boolean onUseBlockCancels(PlayerInteractEvent.RightClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        String key = null;
        if (TerraConfig.COMMON.disableEnchanting.get() && state.is(Blocks.ENCHANTING_TABLE)) {
            key = "message.terracraft.enchanting_disabled";
        } else if (TerraConfig.COMMON.disableVanillaBrewing.get() && state.is(Blocks.BREWING_STAND)) {
            key = "message.terracraft.brewing_disabled";
        }
        if (key == null) {
            return false;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
        return true;
    }
}
