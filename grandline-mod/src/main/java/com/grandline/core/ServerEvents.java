package com.grandline.core;

import com.grandline.GrandLine;
import com.grandline.combat.CombatState;
import com.grandline.fruit.FruitWorldData;
import com.grandline.fruit.TerrainChanges;
import com.grandline.item.DevilFruitItem;
import com.grandline.npc.PopulationManager;
import com.grandline.player.Blue;
import com.grandline.player.StartLocations;
import com.grandline.world.TownProtection;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import com.grandline.world.gen.ArchipelagoChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** World-level rules: ticking systems, spawn point, vanilla hostile replacement, town protection, fruit ledger hooks. */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class ServerEvents {

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel sl) CombatState.tickAll(sl.getGameTime(), sl);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        TerrainChanges.tick(e.getServer());
        PopulationManager.tick(e.getServer());
        com.grandline.world.Encounters.tick(e.getServer());
        com.grandline.ship.ShipInteriors.tick(e.getServer());
        com.grandline.ship.SeaEncounters.tick(e.getServer());
        com.grandline.ship.SpecialRoutes.tick(e.getServer());
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        WorldPlan plan = WorldPlan.of(e.getServer());
        if (plan != null) {
            GrandLine.LOGGER.info("Ocean world active: {} islands", plan.islands.size());
            e.getServer().overworld().getWorldBorder().setSize(WorldLayout.WORLD_BORDER * 2);
        }
    }

    /** New ocean worlds spawn on the East Blue starter island instead of Reverse Mountain at (0,0). */
    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return;
        if (!(sl.getChunkSource().getGenerator() instanceof ArchipelagoChunkGenerator)) return;
        BlockPos p = StartLocations.startPos(sl, Blue.EAST);
        if (p == null) return;
        sl.setDefaultSpawnPos(p, 0);
        e.setCanceled(true);
        GrandLine.LOGGER.info("Ocean world spawn set to {}", p);
    }

    /** Agreed: vanilla hostile mobs are replaced by setting-appropriate enemies in the ocean world. */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent e) {
        if (!GLConfig.REPLACE_VANILLA_HOSTILES.get()) return;
        if (!(e.getLevel().getLevel() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return;
        if (!(sl.getChunkSource().getGenerator() instanceof ArchipelagoChunkGenerator)) return;
        MobSpawnType t = e.getSpawnType();
        if ((t == MobSpawnType.NATURAL || t == MobSpawnType.CHUNK_GENERATION) && e.getEntity().getType().getCategory() == MobCategory.MONSTER) {
            e.setSpawnCancelled(true);
            e.setCanceled(true);
        }
    }

    /** Town protection: settlements cannot be broken (configurable); creative/op players are exempt. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        if (e.getPlayer().isCreative() || e.getPlayer().hasPermissions(2)) return;
        if (!TownProtection.canModify(sl, e.getPos())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || !(e.getEntity() instanceof net.minecraft.world.entity.player.Player p)) return;
        if (p.isCreative() || p.hasPermissions(2)) return;
        if (!TownProtection.canModify(sl, e.getPos())) e.setCanceled(true);
    }

    /** Devil Fruit items never despawn; destroyed fruit items return to the world pool. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide) return;
        if (e.getEntity() instanceof ItemEntity ie && DevilFruitItem.fruit(ie.getItem()) != null) ie.setUnlimitedLifetime();
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getLevel() instanceof ServerLevel sl)) return;
        if (!(e.getEntity() instanceof ItemEntity ie)) return;
        var f = DevilFruitItem.fruit(ie.getItem());
        if (f == null) return;
        Entity.RemovalReason r = ie.getRemovalReason();
        if (r == Entity.RemovalReason.KILLED || (r == Entity.RemovalReason.DISCARDED && !ie.getItem().isEmpty())) {
            FruitWorldData.get(sl.getServer()).itemDestroyed(f.id);
        }
    }

    private ServerEvents() {}
}
