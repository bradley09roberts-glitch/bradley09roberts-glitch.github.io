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
        if (e.getLevel() instanceof ServerLevel sl) {
            long t = Perf.start();
            CombatState.tickAll(sl.getGameTime(), sl);
            Perf.lap("combatState", t);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        net.minecraft.server.MinecraftServer s = e.getServer();
        long t = Perf.start(); // opt-in subsystem timing for the load test (a boolean check otherwise)
        TerrainChanges.tick(s);
        t = Perf.lap("terrainChanges", t);
        PopulationManager.tick(s);
        t = Perf.lap("population", t);
        com.grandline.world.Encounters.tick(s);
        t = Perf.lap("encounters", t);
        com.grandline.ship.ShipInteriors.tick(s);
        t = Perf.lap("shipInteriors", t);
        com.grandline.ship.SeaEncounters.tick(s);
        t = Perf.lap("seaEncounters", t);
        com.grandline.ship.LivingSea.tick(s);
        t = Perf.lap("livingSea", t);
        com.grandline.ship.SpecialRoutes.tick(s);
        t = Perf.lap("specialRoutes", t);
        com.grandline.world.weather.SeaWeather.tick(s);
        t = Perf.lap("seaWeather", t);
        com.grandline.npc.crew.CrewBonuses.tick(s);
        t = Perf.lap("crewBonuses", t);
        com.grandline.npc.PrisonSystem.tick(s);
        t = Perf.lap("prison", t);
        com.grandline.creature.CreatureSpawns.tick(s);
        t = Perf.lap("creatures", t);
        com.grandline.npc.notable.NotableSpawner.tick(s);
        Perf.lap("notables+crew", t);
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
        if (!(e.getLevel().getLevel() instanceof ServerLevel sl) || !replacesVanillaHostiles(sl)) return;
        MobSpawnType t = e.getSpawnType();
        if ((t == MobSpawnType.NATURAL || t == MobSpawnType.CHUNK_GENERATION) && e.getEntity().getType().getCategory() == MobCategory.MONSTER) {
            e.setSpawnCancelled(true);
            e.setCanceled(true);
        }
    }

    /** Whether vanilla hostiles are replaced in this level: the ocean world's overworld, when the config says so. */
    public static boolean replacesVanillaHostiles(ServerLevel sl) {
        return sl.dimension() == Level.OVERWORLD && sl.getChunkSource().getGenerator() instanceof ArchipelagoChunkGenerator
                && GLConfig.REPLACE_VANILLA_HOSTILES.get();
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
