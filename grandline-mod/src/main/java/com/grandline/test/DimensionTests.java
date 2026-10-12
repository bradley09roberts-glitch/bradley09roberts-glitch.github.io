package com.grandline.test;

import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.MarkerCollector;
import com.grandline.registry.ModBlocks;
import com.grandline.registry.ModEntities;
import com.grandline.ship.ShipClass;
import com.grandline.ship.ShipEntity;
import com.grandline.ship.ShipRegistry;
import com.grandline.ship.SpecialRoutes;
import com.grandline.world.DeepWorld;
import com.grandline.world.Island;
import com.grandline.world.SkyWorld;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Plot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/** Self-test steps for the White Sea and the deep sea (terrain, bubbles, and ships with crews travelling between seas). */
final class DimensionTests {

    private static void load(ServerLevel l, double x, double z, int radius) {
        ChunkPos cp = new ChunkPos(BlockPos.containing(x, 0, z));
        l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, radius, cp);
        l.setChunkForced(cp.x, cp.z, true); // a dimension with no players only ticks entities while it has forced chunks
        l.getChunk(cp.x, cp.z);
    }

    /** Keep the chunks under a moving ship ticking (players aboard would do this in real play). */
    private static void follow(ShipEntity ship) {
        if (ship == null || ship.isRemoved() || !(ship.level() instanceof ServerLevel l)) return;
        ChunkPos cp = new ChunkPos(ship.blockPosition());
        l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, 3, cp);
        l.setChunkForced(cp.x, cp.z, true);
    }

    private static ShipEntity findShip(ServerLevel l, UUID shipId, double x, double z, double r) {
        for (ShipEntity s : l.getEntitiesOfClass(ShipEntity.class, new AABB(x - r, -64, z - r, x + r, 400, z + r)))
            if (s.shipId.equals(shipId)) return s;
        return null;
    }

    private static HumanoidNpc deckHand(ServerLevel l, ShipEntity ship) {
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        Vec3 deck = ship.toWorld(0, ship.cls.freeboard + 0.05, -1);
        n.moveTo(deck.x, deck.y, deck.z, 0, 0);
        n.configure("villager", 1, "", BlockPos.containing(deck), "", l.random);
        n.setNoAi(true);
        n.setPersistenceRequired();
        l.addFreshEntity(n);
        return n;
    }

    private static ShipEntity ship(ServerLevel l, ShipClass c, double x, double z, float yaw, String name) {
        load(l, x, z, 3);
        ShipEntity ship = ModEntities.SHIPS.get(c).get().create(l);
        ship.moveTo(x, WorldPlan.SEA_LEVEL + 0.9, z, yaw, 0);
        ship.setShipName(name);
        ship.setHull(c.hull);
        l.addFreshEntity(ship);
        ShipRegistry.get(l.getServer()).register(ship);
        return ship;
    }

    /** A ferry booked at a dockworker sails the passenger to another island of the same sea and lands them on its dock. */
    static void ferry(Deque<SelfTest.Step> steps) {
        // every route offered at the dock gets a ferry with a townsperson aboard (NeoForge's FakePlayer refuses to ride)
        final List<ShipEntity> ferries = new ArrayList<>();
        final List<HumanoidNpc> riders = new ArrayList<>();
        final List<Double> startDist = new ArrayList<>();
        final List<String> dests = new ArrayList<>();
        final int[] waited = {0};
        steps.add(s -> {
            ServerLevel ow = s.overworld();
            WorldPlan plan = WorldPlan.of(s);
            Island home = plan.byId("shiokaze_isle");
            var sp = SelfTest.fake(s);
            var d = com.grandline.player.Characters.get(sp);
            for (Island i : plan.islands) if (i.zone == home.zone) d.discoveredIslands.add(i.id);
            var st = home.settlement();
            BlockPos dock = new BlockPos(st.dockX, 0, st.dockZ);
            load(ow, dock.getX(), dock.getZ(), 3);
            dock = dock.atY(ow.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dock.getX(), dock.getZ()));
            HumanoidNpc worker = ModEntities.HUMANOID_NPC.get().create(ow);
            worker.moveTo(dock.getX() + 0.5, dock.getY(), dock.getZ() + 0.5, 0, 0);
            worker.configure("dockworker", 1, home.id, dock, "", ow.random);
            ow.addFreshEntity(worker);
            var opts = new net.minecraft.nbt.ListTag();
            com.grandline.navigation.Ferries.addOptions(sp, worker, opts);
            SelfTest.check("dockworkers offer ferries to discovered islands of the same sea", !opts.isEmpty(), opts.size() + " routes, first: " + (opts.isEmpty() ? "" : opts.getString(0)));
            for (int k = 0; k < opts.size(); k++) {
                String dest = opts.getString(k).split("\\|")[1];
                HumanoidNpc rider = ModEntities.HUMANOID_NPC.get().create(ow);
                rider.moveTo(dock.getX() + 1.5, dock.getY(), dock.getZ() + 0.5, 0, 0);
                rider.configure("villager", 1, home.id, dock, "", ow.random);
                ow.addFreshEntity(rider);
                ShipEntity f = com.grandline.navigation.Ferries.launch(rider, worker, plan.byId(dest), false);
                if (f == null || rider.getVehicle() != f) { rider.discard(); continue; }
                ferries.add(f);
                riders.add(rider);
                dests.add(dest);
                startDist.add(f.position().distanceTo(f.ferryTarget));
            }
            SelfTest.check("booking a passage puts you aboard the ferry", ferries.size() == opts.size() && !ferries.isEmpty(),
                    ferries.size() + "/" + opts.size() + " passengers aboard, bound for " + dests);
            worker.discard();
            return 1;
        });
        // under way at real ferry speed: every ferry clears the harbour and closes on its destination
        steps.add(s -> {
            if (ferries.isEmpty()) return 0;
            int under = 0;
            StringBuilder lag = new StringBuilder();
            for (int k = 0; k < ferries.size(); k++) {
                ShipEntity f = ferries.get(k);
                follow(f);
                double now = f.ferryTarget == null ? 0 : f.position().distanceTo(f.ferryTarget);
                if (f.isRemoved() || startDist.get(k) - now >= 150) under++;
                else lag.append(String.format(" %s: %.0f closer at %s yaw %.0f speed %.2f wp %s;", dests.get(k), startDist.get(k) - now, f.blockPosition().toShortString(), f.getYRot(), f.speed(), f.ferryWaypoint == null ? "-" : BlockPos.containing(f.ferryWaypoint).toShortString()));
            }
            if (under < ferries.size() && ++waited[0] < 1500) return -1;
            SelfTest.check("every ferry gets under way toward its destination", under == ferries.size(),
                    under + "/" + ferries.size() + " cleared the harbour and closed 150 blocks within " + waited[0] + " ticks" + lag);
            // keep the first; skip the long middle of its voyage: open water ~220 blocks short of the dock
            for (int k = 1; k < ferries.size(); k++) {
                ferries.get(k).ferryTarget = null;
                ferries.get(k).discard();
                riders.get(k).discard();
            }
            ShipEntity f = ferries.get(0);
            Vec3 t = f.ferryTarget;
            if (t == null) return 0;
            Vec3 dir = f.position().subtract(t).multiply(1, 0, 1).normalize();
            ServerLevel ow = s.overworld();
            Vec3 near = t.add(dir.scale(220));
            load(ow, near.x, near.z, 4);
            Vec3 w = com.grandline.ship.Shipyard.findWaterPublic(ow, BlockPos.containing(near));
            if (w != null) near = w;
            f.moveTo(near.x, near.y, near.z, f.getYRot(), 0);
            riders.get(0).moveTo(near.x, near.y + 2, near.z);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (ferries.isEmpty()) return 0;
            ShipEntity f = ferries.get(0);
            var sp = riders.get(0);
            follow(f);
            if (!f.isRemoved() && ++waited[0] < 3000) return -1;
            Island d = WorldPlan.of(s).byId(dests.get(0));
            double away = d.distance(sp.getBlockX(), sp.getBlockZ()) - d.radius;
            SelfTest.check("the ferry sails there through the world and lands you on the destination's dock", f.isRemoved() && !sp.isPassenger() && away < 120,
                    String.format("after %d ticks: %s, passenger %.0f blocks from %s's shore", waited[0], f.isRemoved() ? "arrived" : "still sailing at " + f.blockPosition().toShortString(), away, dests.get(0)));
            sp.discard();
            return 0;
        });
    }

    /** Mary Geoise is a point on top of the Red Line: its set piece is built, it is protected, and it can be discovered. */
    static void holyLand(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            ServerLevel ow = s.overworld();
            WorldPlan plan = WorldPlan.of(s);
            Island mg = plan.byId("mary_geoise");
            if (mg == null) { SelfTest.check("Mary Geoise is built on the Red Line", false, "no island"); return 0; }
            load(ow, mg.x, mg.z, 3);
            int quartz = 0;
            for (int dx = -24; dx <= 24; dx++)
                for (int dz = -24; dz <= 24; dz++)
                    if (ow.getBlockState(new BlockPos(mg.x + dx, 301, mg.z + dz)).is(net.minecraft.world.level.block.Blocks.QUARTZ_BRICKS)) quartz++;
            int guards = 0;
            for (var p : mg.settlement().plots)
                for (var m : com.grandline.npc.MarkerCollector.markers(mg, p)) if (m.kind().equals("npc")) guards++;
            boolean prot = com.grandline.world.TownProtection.protectedAt(plan, mg.x, mg.z);
            var sp = SelfTest.fake(s);
            var d = com.grandline.player.Characters.get(sp);
            d.discoveredIslands.remove(mg.id);
            sp.moveTo(mg.x + 3.5, 302, mg.z + 3.5);
            sp.tickCount = 40;
            com.grandline.navigation.Navigation.tick(sp, d);
            SelfTest.check("Mary Geoise is built on the Red Line, protected, peopled and discoverable", quartz > 200 && guards > 0 && prot && d.discoveredIslands.contains(mg.id),
                    quartz + " quartz floor blocks at y301, " + guards + " NPC markers, protected=" + prot + ", discovered=" + d.discoveredIslands.contains(mg.id));
            return 0;
        });
    }

    /** Fruit awakening: mastery + level start the trial; fruit kills and the New World complete it. */
    static void awakening(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            var sp = SelfTest.fake(s);
            if (sp == null) return 0;
            var d = com.grandline.player.Characters.get(sp);
            String before = d.fruit;
            boolean wasAwake = d.fruitAwakened;
            d.fruitAwakened = false;
            d.questProgress.remove("awakening_trial");
            d.completedQuests.remove("awakening_trial");
            d.flags.remove(com.grandline.quest.Awakening.TRIAL_FLAG);
            if (d.fruit == null) d.fruit = "mera";
            int lvl = d.level;
            d.level = Math.max(d.level, com.grandline.quest.Awakening.LEVEL);
            d.masteryXp.put("fruit/" + d.fruit, 1_000_000);
            com.grandline.quest.Awakening.check(sp, d);
            boolean started = d.questProgress.containsKey("awakening_trial");
            com.grandline.quest.Quests.progress(sp, "fruit_kill", "any", 12);
            com.grandline.quest.Quests.progress(sp, "discover", "any_new_world", 1);
            com.grandline.quest.Awakening.check(sp, d);
            SelfTest.check("fruit awakening: mastery + level start the trial, the trial awakens the fruit", started && d.fruitAwakened,
                    "started=" + started + " awakened=" + d.fruitAwakened);
            d.fruitAwakened = wasAwake;
            d.level = lvl;
            d.fruit = before;
            return 0;
        });
    }

    /** Every ship class launches in open water, has a deck and seats, sails under way and (if it has one) a hold below. */
    static void shipClasses(Deque<SelfTest.Step> steps) {
        final ShipEntity[] fleet = new ShipEntity[ShipClass.values().length];
        final double[][] start = new double[fleet.length][2];
        steps.add(s -> {
            ServerLevel ow = s.overworld();
            // open water in the East Blue, south of the starter island
            int bx = 36000, bz = 21400;
            for (ShipClass c : ShipClass.values()) {
                int i = c.ordinal();
                double x = bx + i * 90, z = bz;
                load(ow, x, z, 3);
                Vec3 water = com.grandline.ship.Shipyard.findWaterPublic(ow, BlockPos.containing(x, 63, z));
                if (water != null) {
                    x = water.x;
                    z = water.z;
                }
                Vec3 wind = com.grandline.ship.Wind.at(ow, x, z);
                float yaw = wind.lengthSqr() < 1e-4 ? -90 : (float) Math.toDegrees(Math.atan2(-wind.x, wind.z)); // run before the wind
                fleet[i] = ship(ow, c, x, z, yaw, "Selftest " + c.id());
                fleet[i].autopilot = true;
                fleet[i].testSpeedMult = 3f;
                fleet[i].setAnchored(false);
                fleet[i].setSail(3);
                start[i][0] = fleet[i].getX();
                start[i][1] = fleet[i].getZ();
            }
            return 120;
        });
        steps.add(s -> {
            for (ShipClass c : ShipClass.values()) {
                ShipEntity sh = fleet[c.ordinal()];
                if (sh == null || sh.isRemoved()) {
                    SelfTest.check("ship class " + c.id() + " launches and sails", false, "ship missing");
                    continue;
                }
                follow(sh);
                double moved = Math.hypot(sh.getX() - start[c.ordinal()][0], sh.getZ() - start[c.ordinal()][1]);
                boolean hold = !c.interior || com.grandline.ship.ShipInteriors.prepare(s, sh) != null;
                SelfTest.check("ship class " + c.id() + " launches and sails", moved > 3 && sh.seatCount() >= c.cannons + 1 && hold,
                        String.format("moved %.1f blocks in 120 ticks, %d seats, %d cannons, hold=%s", moved, sh.seatCount(), c.cannons, c.interior ? hold : "n/a"));
                sh.discard();
            }
            return 0;
        });
    }

    static void phase1(Deque<SelfTest.Step> steps) {
        // ---------- local sea weather ----------
        steps.add(s -> {
            ServerLevel ow = s.overworld();
            var Type = com.grandline.world.weather.SeaWeather.Type.CLEAR;
            int calm = 0, samples = 0, rough = 0;
            StringBuilder odd = new StringBuilder();
            for (int x = -50000; x <= 50000; x += 2500) {
                samples++;
                var w = com.grandline.world.weather.SeaWeather.at(ow, x, 5200);
                if (w.type() == Type) calm++;
                else odd.append(" x=").append(x).append(':').append(w.type()).append('/').append(String.format("%.2f", w.intensity()));
            }
            SelfTest.check("the Calm Belt is always calm", calm == samples, calm + "/" + samples + " points clear" + odd);
            java.util.Map<String, Integer> seen = new java.util.TreeMap<>();
            for (int x = -56000; x <= 56000; x += 400)
                for (int z = -3500; z <= 3500; z += 700) {
                    var w = com.grandline.world.weather.SeaWeather.at(ow, x, z);
                    if (w.intensity() > 0.3f) {
                        rough++;
                        seen.merge(w.type().name().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
                    }
                }
            SelfTest.check("storms, fog and squalls roam the Grand Line", rough > 0, rough + " rough points: " + seen);
            WorldPlan plan = WorldPlan.of(s);
            Island drum = plan.byId("drum_island");
            var dw = com.grandline.world.weather.SeaWeather.at(ow, drum.x, drum.z);
            SelfTest.check("winter islands keep their own snowfall", dw.type() == com.grandline.world.weather.SeaWeather.Type.SNOW, dw.toString());
            Island ala = plan.byId("alabasta");
            var aw = com.grandline.world.weather.SeaWeather.at(ow, ala.x, ala.z);
            SelfTest.check("desert islands stay dry", aw.type() == Type, aw.toString());
            SelfTest.check("global weather cycle is replaced by local weather", !ow.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE), "");
            return 0;
        });
        // ---------- the dimensions exist and generate as designed ----------
        steps.add(s -> {
            ServerLevel sky = s.getLevel(SkyWorld.LEVEL), deep = s.getLevel(DeepWorld.LEVEL);
            SelfTest.check("sky and deep-sea dimensions are loaded", sky != null && deep != null, "");
            if (sky == null || deep == null) return 0;
            WorldPlan sp = WorldPlan.forLevel(sky), dp = WorldPlan.forLevel(deep);
            SelfTest.check("White Sea plan has its islands", sp != null && sp.kind == WorldPlan.Kind.SKY && sp.byId("angel_island") != null && sp.byId("upper_yard") != null,
                    sp == null ? "" : sp.islands.size() + " sky islands");
            SelfTest.check("deep-sea plan has Fish-Man Island", dp != null && dp.kind == WorldPlan.Kind.DEEP && dp.byId("fishman_island") != null, dp == null ? "" : dp.islands.size() + " deep islands");
            load(sky, SkyWorld.ARRIVAL_X, SkyWorld.ARRIVAL_Z, 3);
            int x = SkyWorld.ARRIVAL_X, z = SkyWorld.ARRIVAL_Z + 40;
            sky.getChunk(x >> 4, z >> 4);
            boolean sea = sky.getBlockState(new BlockPos(x, 55, z)).is(Blocks.WATER) && sky.getBlockState(new BlockPos(x, 63, z)).is(Blocks.WATER)
                    && sky.getBlockState(new BlockPos(x, 64, z)).isAir();
            boolean floor = sky.getBlockState(new BlockPos(x, SkyWorld.FLOOR_Y, z)).is(ModBlocks.ISLAND_CLOUD.get());
            SelfTest.check("White Sea: Sea Cloud over an Island Cloud floor", sea && floor, "y55=" + sky.getBlockState(new BlockPos(x, 55, z)).getBlock().getName().getString()
                    + " y47=" + sky.getBlockState(new BlockPos(x, SkyWorld.FLOOR_Y, z)).getBlock().getName().getString());
            String biome = sky.getBiome(new BlockPos(x, 60, z)).unwrapKey().map(k -> k.location().toString()).orElse("?");
            SelfTest.check("White Sea biome tints the sea white", biome.equals("grandline:white_sea"), biome);
            Island angel = sp.byId("angel_island");
            load(sky, angel.x, angel.z, 2);
            int top = sky.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, angel.x, angel.z);
            var below = sky.getBlockState(new BlockPos(angel.x, top - 1, angel.z));
            SelfTest.check("Angel Island stands on Island Cloud above the sea", top > WorldPlan.SEA_LEVEL + 1, "top y=" + top + " " + below.getBlock().getName().getString());
            int markers = 0;
            for (Plot p : angel.settlement().plots) for (var m : MarkerCollector.markers(angel, p)) if (m.kind().equals("npc")) markers++;
            SelfTest.check("Skypiea town has residents to spawn", markers > 5, markers + " NPC markers");
            return 0;
        });
        steps.add(s -> {
            ServerLevel deep = s.getLevel(DeepWorld.LEVEL);
            if (deep == null) return 0;
            load(deep, 0, 0, 3);
            load(deep, 160, 0, 2);
            boolean water = deep.getBlockState(new BlockPos(DeepWorld.ENTRY_X, 120, 0)).is(Blocks.WATER);
            load(deep, DeepWorld.ENTRY_X, 0, 2);
            water &= deep.getBlockState(new BlockPos(DeepWorld.ENTRY_X, 120, 0)).is(Blocks.WATER) && deep.getBlockState(new BlockPos(DeepWorld.ENTRY_X, 200, 0)).is(Blocks.WATER);
            SelfTest.check("deep sea is water far above the ships", water, "");
            boolean dry = deep.getBlockState(new BlockPos(160, 150, 0)).isAir();
            boolean skin = deep.getBlockState(new BlockPos(0, 233, 0)).is(ModBlocks.BUBBLE_MEMBRANE.get());
            boolean outside = deep.getBlockState(new BlockPos(0, 236, 0)).is(Blocks.WATER);
            SelfTest.check("Fish-Man Island lies in an air bubble with a membrane skin", dry && skin && outside,
                    "inside=" + deep.getBlockState(new BlockPos(160, 150, 0)).getBlock().getName().getString() + " skin=" + skin + " above=" + outside);
            // provoke the sea: water next to the membrane gets a fluid tick
            for (int dx = -2; dx <= 2; dx++) deep.scheduleTick(new BlockPos(dx, 234, 0), Fluids.WATER, 1);
            return 30;
        });
        steps.add(s -> {
            ServerLevel deep = s.getLevel(DeepWorld.LEVEL);
            if (deep == null) return 0;
            boolean stillDry = deep.getBlockState(new BlockPos(0, 231, 0)).isAir() && deep.getBlockState(new BlockPos(1, 231, 0)).isAir();
            SelfTest.check("the bubble holds the sea back (no leak after fluid ticks)", stillDry, deep.getBlockState(new BlockPos(0, 231, 0)).toString());
            return 0;
        });

        // ---------- Knock Up Stream → White Sea → Cloud End ----------
        final ShipEntity[] sh = new ShipEntity[1];
        final HumanoidNpc[] hand = new HumanoidNpc[1];
        final UUID[] id = new UUID[1];
        final int[] waited = {0};
        steps.add(s -> {
            ServerLevel ow = s.overworld();
            BlockPos ku = SpecialRoutes.knockUpPoint(s);
            SelfTest.check("Knock Up Stream located off Jaya", ku != null, ku == null ? "" : ku.toShortString());
            if (ku == null) return 0;
            ServerLevel sky = s.getLevel(SkyWorld.LEVEL);
            load(sky, SkyWorld.ARRIVAL_X, SkyWorld.ARRIVAL_Z, 3);
            // outside the eruption column (it erupts by itself during the first 30 s of server time); the ride is started below
            sh[0] = ship(ow, ShipClass.SLOOP, ku.getX() + 70.5, ku.getZ() + 12.5, -90, "Selftest Skyfarer");
            id[0] = sh[0].shipId;
            hand[0] = deckHand(ow, sh[0]);
            return 60; // let the chunks finish loading, as they would around a real crew
        });
        steps.add(s -> {
            if (sh[0] == null) return 0;
            SpecialRoutes.startRide(sh[0], SpecialRoutes.KNOCK_UP);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (id[0] == null) return 0;
            ServerLevel sky = s.getLevel(SkyWorld.LEVEL);
            follow(sh[0]);
            if (waited[0] % 20 == 0 && sh[0] != null && hand[0] != null)
                com.grandline.GrandLine.LOGGER.info("[trace] ship {} {} {} crew {} {} {} removed={} carried={} ride={} t={} shipTicking={} crewTicking={}", sh[0].getX(), sh[0].getY(), sh[0].getZ(),
                        hand[0].getX(), hand[0].getY(), hand[0].getZ(), hand[0].isRemoved(), sh[0].carries(hand[0]), sh[0].ride, sh[0].rideT,
                        ((ServerLevel) sh[0].level()).isPositionEntityTicking(sh[0].blockPosition()), ((ServerLevel) hand[0].level()).isPositionEntityTicking(hand[0].blockPosition()));
            ShipEntity up = findShip(sky, id[0], SkyWorld.ARRIVAL_X, SkyWorld.ARRIVAL_Z, 80);
            if (up == null && ++waited[0] < 600) return -1;
            SelfTest.check("the Knock Up Stream carries a ship to the White Sea", up != null, up == null ? "not found after " + waited[0] + " ticks" : "arrived at " + up.blockPosition().toShortString() + " after " + waited[0] + " ticks");
            sh[0] = up;
            return 5; // let the new ship lay out its deck before judging who stands on it
        });
        steps.add(s -> {
            ShipEntity up = sh[0];
            if (up == null) return 0;
            ServerLevel sky = (ServerLevel) up.level();
            HumanoidNpc crew = null;
            for (HumanoidNpc n : sky.getEntitiesOfClass(HumanoidNpc.class, up.getBoundingBox().inflate(12))) if (n.getUUID().equals(hand[0].getUUID())) crew = n;
            SelfTest.check("crew on deck travels with the ship", crew != null && up.carries(crew), crew == null ? "crew member missing" : "on deck " + (int) crew.distanceTo(up) + " blocks from centre");
            var rec = ShipRegistry.get(s).get(id[0]);
            SelfTest.check("ship registry records the new dimension", rec != null && rec.dim.equals("grandline:sky"), rec == null ? "no record" : rec.dim);
            BlockPos ku = SpecialRoutes.knockUpPoint(s);
            load(s.overworld(), ku.getX() + 1400, ku.getZ(), 3);
            SpecialRoutes.startRide(up, SpecialRoutes.CLOUD_FALL);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (id[0] == null) return 0;
            BlockPos ku = SpecialRoutes.knockUpPoint(s);
            follow(sh[0]);
            ShipEntity down = findShip(s.overworld(), id[0], ku.getX() + 1400, ku.getZ(), 80);
            if (down == null && ++waited[0] < 400) return -1;
            SelfTest.check("the Cloud End drops the ship back to the Blue Sea", down != null, down == null ? "not found" : "splashed down at " + down.blockPosition().toShortString());
            return 0;
        });

        // ---------- coated dive at Sabaody → deep sea → New World ----------
        final ShipEntity[] dv = new ShipEntity[1];
        final HumanoidNpc[] diver = new HumanoidNpc[1];
        final UUID[] did = new UUID[1];
        steps.add(s -> {
            BlockPos dive = SpecialRoutes.divePoint(s);
            SelfTest.check("dive point found between Sabaody and the Red Line", dive != null, dive == null ? "" : dive.toShortString());
            if (dive == null) return 0;
            ServerLevel deep = s.getLevel(DeepWorld.LEVEL);
            load(deep, DeepWorld.ENTRY_X, DeepWorld.ENTRY_Z, 3);
            ServerLevel ow = s.overworld();
            dv[0] = ship(ow, ShipClass.GALLEON, dive.getX() - 35.5, dive.getZ() + 0.5, -90, "Selftest Diver");
            dv[0].addUpgrade(SpecialRoutes.COATING);
            did[0] = dv[0].shipId;
            diver[0] = deckHand(ow, dv[0]);
            dv[0].autopilot = true;
            dv[0].setAnchored(false);
            dv[0].setSail(3);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (did[0] == null) return 0;
            ServerLevel deep = s.getLevel(DeepWorld.LEVEL);
            follow(dv[0]);
            ShipEntity down = findShip(deep, did[0], DeepWorld.ENTRY_X, DeepWorld.ENTRY_Z, 80);
            if (down == null && ++waited[0] < 700) return -1;
            SelfTest.check("a coated ship sailing onto the dive point sinks to the deep sea", down != null, down == null ? "not found after " + waited[0] + " ticks" : "arrived after " + waited[0] + " ticks at " + down.blockPosition().toShortString());
            if (down == null) return 0;
            dv[0] = down;
            down.autopilot = false;
            down.setSail(0);
            return 30;
        });
        steps.add(s -> {
            if (dv[0] == null || !dv[0].level().dimension().equals(DeepWorld.LEVEL)) return 0;
            ServerLevel deep = (ServerLevel) dv[0].level();
            HumanoidNpc crew = null;
            for (HumanoidNpc n : deep.getEntitiesOfClass(HumanoidNpc.class, dv[0].getBoundingBox().inflate(16))) if (n.getUUID().equals(diver[0].getUUID())) crew = n;
            boolean breathing = crew != null && crew.hasEffect(MobEffects.WATER_BREATHING) && crew.getAirSupply() == crew.getMaxAirSupply();
            SelfTest.check("the coating bubble keeps the crew breathing 10,000 m down", breathing, crew == null ? "crew member missing" : "air=" + crew.getAirSupply() + " effects=" + crew.getActiveEffects().size());
            boolean waterAbove = deep.getBlockState(dv[0].blockPosition().above(20)).is(Blocks.WATER);
            SelfTest.check("the ship travels under the sea", waterAbove, deep.getBlockState(dv[0].blockPosition().above(20)).toString());
            // sail to the far end of the deep sea and surface in the New World
            Vec3 out = SpecialRoutes.newWorldSurface(s);
            load(s.overworld(), out.x, out.z, 3);
            load(deep, DeepWorld.EXIT_X - 30, 0, 3);
            dv[0].setPos(DeepWorld.EXIT_X - 30, dv[0].getY(), 0);
            dv[0].setYRot(-90);
            dv[0].autopilot = true;
            dv[0].setSail(3);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (did[0] == null) return 0;
            Vec3 out = SpecialRoutes.newWorldSurface(s);
            follow(dv[0]);
            ShipEntity up = findShip(s.overworld(), did[0], out.x, out.z, 80);
            if (up == null && ++waited[0] < 500) return -1;
            SelfTest.check("sailing out of the deep sea surfaces in the New World", up != null, up == null ? "not found" : "surfaced at " + up.blockPosition().toShortString());
            if (up != null) {
                SelfTest.check("the coating is spent after the ascent", !up.hasUpgrade(SpecialRoutes.COATING), up.upgrades().toString());
                up.autopilot = false;
                up.setAnchored(true);
            }
            return 0;
        });
        // ---------- a ship left in the sky for phase 2 ----------
        steps.add(s -> {
            ServerLevel sky = s.getLevel(SkyWorld.LEVEL);
            if (sky == null) return 0;
            ShipEntity anchor = ship(sky, ShipClass.CARAVEL, SkyWorld.ARRIVAL_X + 60.5, SkyWorld.ARRIVAL_Z - 40.5, -90, "Selftest Sky Anchor");
            anchor.setAnchored(true);
            ShipRegistry.get(s).update(anchor);
            Files.writeString(Path.of("selftest-sky.txt"), anchor.shipId + " " + anchor.getBlockX() + " " + anchor.getBlockZ());
            SelfTest.check("sky persistence fixture written", true, anchor.shipId.toString());
            return 40;
        });
    }

    static void phase2(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            String[] p = Files.readString(Path.of("selftest-sky.txt")).trim().split(" ");
            UUID id = UUID.fromString(p[0]);
            int x = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
            var rec = ShipRegistry.get(s).get(id);
            SelfTest.check("sky ship's registry entry survived restart", rec != null && rec.dim.equals("grandline:sky"), rec == null ? "missing" : rec.dim);
            ServerLevel sky = s.getLevel(SkyWorld.LEVEL);
            load(sky, x, z, 3);
            return 40;
        });
        steps.add(s -> {
            String[] p = Files.readString(Path.of("selftest-sky.txt")).trim().split(" ");
            UUID id = UUID.fromString(p[0]);
            ShipEntity found = findShip(s.getLevel(SkyWorld.LEVEL), id, Integer.parseInt(p[1]), Integer.parseInt(p[2]), 40);
            SelfTest.check("ship left on the White Sea is still there after restart", found != null, found == null ? "not found" : found.shipName() + " at " + found.blockPosition().toShortString());
            return 0;
        });
    }

    private DimensionTests() {}
}
