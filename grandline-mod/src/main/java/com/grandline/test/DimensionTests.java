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
import java.util.Deque;
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
            sh[0] = ship(ow, ShipClass.SLOOP, ku.getX() + 12.5, ku.getZ() + 12.5, -90, "Selftest Skyfarer");
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
