package com.longnight.event;

import com.longnight.Config;
import com.longnight.LongNight;
import com.longnight.command.LongNightCommand;
import com.longnight.content.Content;
import com.longnight.entity.CitizenEntity;
import com.longnight.game.Crime;
import com.longnight.game.Ui;
import com.longnight.game.Wards;
import com.longnight.registry.ModItems;
import com.longnight.sim.LongNightArc;
import com.longnight.sim.Settlement;
import com.longnight.sim.SimRules;
import com.longnight.sim.WorldBuilder;
import com.longnight.sim.WorldSim;
import com.longnight.world.Embodiment;
import com.longnight.world.RaidManager;
import com.longnight.world.ServerHooks;
import com.longnight.world.SiteFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.List;

/** Game-bus wiring: the simulation's heartbeat and its hooks into vanilla events. */
public final class ServerEvents {
    private static long lastWardDay = Long.MIN_VALUE;

    @SubscribeEvent
    public void reload(AddReloadListenerEvent e) {
        e.addListener(new Content.Loader("longnight_cultures"));
        e.addListener(new Content.Loader("longnight_faiths"));
        e.addListener(new Content.Loader("names"));
        e.addListener(new Content.Loader("lore"));
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent e) {
        LongNightCommand.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void started(ServerStartedEvent e) {
        MinecraftServer server = e.getServer();
        if (Content.CULTURES.isEmpty()) Content.loadBundled();
        WorldSim sim = WorldSim.get(server);
        sim.rules = SimRules.fromConfig();
        sim.hooks = new ServerHooks(server);
        ServerLevel ow = server.overworld();
        if (!sim.generated) {
            // one society: no wandering traders outside longnight's own people
            server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DO_TRADER_SPAWNING).set(false, server);
            startGeneration(server, sim);
        } else {
            LongNight.LOG.info("Loaded the world of the Long Night: day {}, {} settlements, {} people", sim.lastDay, sim.settlements.size(), sim.people.size());
        }
    }

    private static volatile Thread generator;

    /**
     * Writing the world's history needs thousands of terrain-noise samples (slow with continent-scale terrain mods),
     * so it runs on a background thread into a separate WorldSim and is merged on the server thread when done.
     * Nothing touches the live world from the worker: it only reads generator noise.
     */
    static void startGeneration(MinecraftServer server, WorldSim sim) {
        if (generator != null && generator.isAlive()) return;
        ServerLevel ow = server.overworld();
        BlockPos spawn = ow.getSharedSpawnPos();
        long seed = ow.getSeed();
        int settlements = Config.START_SETTLEMENTS.get(), camps = Config.START_CAMPS.get(), bandits = Config.START_BANDIT_CAMPS.get();
        SimRules rules = sim.rules;
        ServerHooks real = new ServerHooks(server);
        generator = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                WorldSim temp = new WorldSim();
                temp.rules = rules;
                temp.hooks = new com.longnight.sim.WorldHooks() {
                    @Override
                    public boolean plotLooksBuildable(BlockPos origin, int w, int d) {
                        return real.plotLooksBuildable(origin, w, d);
                    }
                };
                List<WorldBuilder.Site> sites = SiteFinder.survey(ow, spawn, seed);
                WorldBuilder.generate(temp, sites, settlements, camps, bandits, seed);
                server.execute(() -> merge(server, temp, t0));
            } catch (Throwable ex) {
                LongNight.LOG.error("World history generation failed", ex);
            }
        }, "longnight-worldgen");
        generator.setDaemon(true);
        generator.start();
        LongNight.LOG.info("Writing the chronicle of the world in the background...");
    }

    static void merge(MinecraftServer server, WorldSim temp, long t0) {
        WorldSim sim = WorldSim.get(server);
        if (sim.generated) return;
        sim.people.putAll(temp.people);
        sim.settlements.putAll(temp.settlements);
        sim.factions.putAll(temp.factions);
        sim.news.addAll(temp.news);
        sim.history.addAll(temp.history);
        sim.spareSites.addAll(temp.spareSites);
        sim.nextNewsId = Math.max(sim.nextNewsId, temp.nextNewsId);
        sim.lastDay = WorldSim.dayOf(server.overworld());
        sim.generated = true;
        sim.setDirty();
        LongNight.LOG.info("Generated the world of the Long Night in {} ms: {} settlements, {} people, {} realms",
                System.currentTimeMillis() - t0, sim.settlements.size(), sim.people.size(), sim.factions.size());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) welcome(p, sim);
    }

    @SubscribeEvent
    public void stopping(ServerStoppingEvent e) {
        RaidManager.endAllOnStop(WorldSim.get(e.getServer()));
        Embodiment.clear();
        Crime.clear();
    }

    @SubscribeEvent
    public void tick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        ServerLevel ow = server.overworld();
        WorldSim sim = WorldSim.get(server);
        if (!sim.generated) return;
        sim.tick(server);
        Embodiment.tick(ow, sim);
        RaidManager.tick(ow, sim);
        long day = WorldSim.dayOf(ow);
        if (day != lastWardDay) {
            lastWardDay = day;
            Wards.daily(ow, sim, day);
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        WorldSim sim = WorldSim.get(p.serverLevel().getServer());
        if (!sim.generated) {
            p.sendSystemMessage(Ui.hint("The chronicle of the Haldmarch is still being written. People and places will appear in a moment."));
            return;
        }
        welcome(p, sim);
    }

    static void welcome(ServerPlayer p, WorldSim sim) {
        if (sim.knownPlaces.containsKey(p.getUUID())) {
            p.sendSystemMessage(Ui.hint("The Haldmarch remembers you. (Use your journal or /longnight journal.)"));
            return;
        }
        // first arrival: a few things a traveller would know
        sim.knownPlaces.put(p.getUUID(), new java.util.HashSet<>());
        List<Settlement> near = sim.settlements.values().stream().filter(s -> s.kind == Settlement.Kind.SETTLEMENT && s.center != null)
                .sorted(Comparator.comparingDouble(s -> s.center.distSqr(p.blockPosition()))).limit(2).toList();
        for (Settlement s : near) sim.learnOf(p.getUUID(), s);
        p.getInventory().add(new ItemStack(ModItems.SETTLEMENT_LEDGER.get()));
        p.sendSystemMessage(Component.literal("§6§lKingdoms of the Long Night§r"));
        p.sendSystemMessage(Component.literal("§7You wake on the road with little but a journal. The Haldmarch is old: its realms have kings and grudges, "
                + "its people have names and memories, and the night is longer than it used to be.§r"));
        if (!near.isEmpty()) {
            Settlement s = near.get(0);
            double dx = s.center.getX() - p.getX(), dz = s.center.getZ() - p.getZ();
            p.sendSystemMessage(Component.literal("§7A passing carter told you of §f" + s.name + "§7, some " + (int) Math.sqrt(dx * dx + dz * dz)
                    + " blocks to the " + com.longnight.game.Journal.compass(dx, dz) + ".§r"));
        }
        p.sendSystemMessage(Ui.hint("Right-click with the journal to see places you know. /longnight help for more."));
    }

    @SubscribeEvent
    public void spawn(FinalizeSpawnEvent e) {
        if (e.getLevel() instanceof ServerLevel sl && Wards.blocks(sl, e.getEntity(), e.getSpawnType())) e.setSpawnCancelled(true);
    }

    @SubscribeEvent
    public void join(EntityJoinLevelEvent e) {
        // the walking dead hunt townsfolk caught outdoors at night
        if (!e.getLevel().isClientSide && e.getEntity() instanceof Zombie z && Config.MONSTERS_HUNT_CITIZENS.get()) {
            z.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(z, CitizenEntity.class, 10, true, false,
                    c -> c instanceof CitizenEntity ce && !ce.isHostile() && !ce.isSleeping() && ce.level().canSeeSky(ce.blockPosition())));
        }
    }

    @SubscribeEvent
    public void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof EnderDragon && e.getEntity().level() instanceof ServerLevel sl) {
            WorldSim sim = WorldSim.get(sl.getServer());
            LongNightArc.onDragonSlain(sim, WorldSim.dayOf(sl.getServer().overworld()));
            for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.literal("§5Far above, in a world you cannot see, every star flickers at once.§r"));
            }
        }
    }

    @SubscribeEvent
    public void breakBlock(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer sp && e.getLevel() instanceof ServerLevel sl && sl.dimension() == Level.OVERWORLD) {
            Crime.vandalism(sl, sp, e.getPos());
        }
    }
}
