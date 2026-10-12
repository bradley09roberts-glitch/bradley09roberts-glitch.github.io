package com.grandline.test;

import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.registry.ModEntities;
import com.grandline.ship.*;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Self-test steps for naval combat (phase 1: grapple, ram, gun crews, boarding, capture, prizes, squadrons; phase 2:
 * ship customisation and prizes survive a restart). Kept apart so {@link SelfTest} only needs one call per phase.
 */
final class NavalTests {
    private static final String FIXTURE = "selftest-naval.txt";

    private static void load(ServerLevel l, double x, double z, int radius) {
        ChunkPos cp = new ChunkPos(BlockPos.containing(x, 0, z));
        l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, radius, cp);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) l.setChunkForced(cp.x + dx, cp.z + dz, true);
        l.getChunk(cp.x, cp.z);
    }

    private static ShipEntity ship(ServerLevel l, ShipClass c, double x, double z, float yaw, String name) {
        ShipEntity s = ModEntities.SHIPS.get(c).get().create(l);
        s.moveTo(x, WorldPlan.SEA_LEVEL + 0.9, z, yaw, 0);
        s.setShipName(name);
        s.setHull(c.hull);
        l.addFreshEntity(s);
        return s;
    }

    private static HumanoidNpc hand(ServerLevel l, ShipEntity ship, String role, double lx, double lz) {
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        Vec3 deck = ship.toWorld(lx, ship.cls.freeboard + 0.05, lz);
        n.moveTo(deck.x, deck.y, deck.z, 0, 0);
        n.configure(role, 4, "", BlockPos.ZERO, "", l.random);
        n.setNoAi(true);
        l.addFreshEntity(n);
        return n;
    }

    /** Open water near Shiokaze Isle (plan-sampled, so it works before the chunks exist). */
    private static double[] openSea(WorldPlan p, Island home, int dx) {
        WorldPlan.Sample smp = new WorldPlan.Sample();
        for (int r = 0; r < 40; r++) {
            int x = home.x + dx + r * 97, z = home.z + home.radius + 520 + (r % 5) * 61;
            boolean open = true;
            for (int ox = -90; ox <= 90 && open; ox += 15)
                for (int oz = -90; oz <= 90 && open; oz += 15) {
                    p.sample(x + ox, z + oz, smp);
                    if (smp.height > WorldPlan.SEA_LEVEL - 4) open = false;
                }
            if (open) return new double[]{x + 0.5, z + 0.5};
        }
        return new double[]{home.x + dx + 0.5, home.z + home.radius + 520.5};
    }

    static void phase1(Deque<SelfTest.Step> steps) {
        final double[] at = new double[2];
        final ShipEntity[] s = new ShipEntity[12];
        final double[] before = new double[8];
        final int[] waited = {0};
        steps.add(srv -> {
            WorldPlan p = WorldPlan.of(srv);
            double[] o = openSea(p, p.byId("shiokaze_isle"), 300);
            at[0] = o[0];
            at[1] = o[1];
            ServerLevel l = srv.overworld();
            // the whole test area stays entity-ticking (as it would around players aboard)
            for (int cx = ((int) at[0] - 96) >> 4; cx <= ((int) at[0] + 300) >> 4; cx++)
                for (int cz = ((int) at[1] - 64) >> 4; cz <= ((int) at[1] + 180) >> 4; cz++) l.setChunkForced(cx, cz, true);
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            double[][] pts = {{0, 0}, {17, 7}, {120, -30}, {120, 0}, {240, 0}, {276, -12}, {0, 60}, {8, 60}, {40, 100}, {120, 140}, {100, 110}, {140, 110}, {-60, 60}};
            for (double[] pt : pts)
                if (!l.isPositionEntityTicking(BlockPos.containing(at[0] + pt[0], 64, at[1] + pt[1])) && ++waited[0] < 3000) return -1;
            SelfTest.check("naval test waters loaded", true, "after " + waited[0] + " ticks at " + (int) at[0] + "," + (int) at[1]);
            return 0;
        });
        // ---------- grapple: two ships hauled alongside ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            s[0] = ship(l, ShipClass.CARAVEL, at[0], at[1], 0, "Selftest Hooker");
            s[1] = ship(l, ShipClass.CARAVEL, at[0] + 17, at[1] + 7, 35, "Selftest Hooked");
            NavalCombat.link(s[0], s[1], false);
            before[0] = Math.abs(s[0].toLocal(s[1].position()).x);
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            if (!NavalCombat.alongside(s[0], s[1]) && ++waited[0] < 400) return -1;
            Vec3 rel = s[0].toLocal(s[1].position());
            double gap = Math.abs(rel.x) - s[0].cls.halfBeamAt(0.5f) * 2;
            float yawDiff = Math.abs(net.minecraft.util.Mth.wrapDegrees(s[1].getYRot() - s[0].getYRot()));
            yawDiff = Math.min(yawDiff, 180 - yawDiff);
            SelfTest.check("grapple hauls two ships alongside", NavalCombat.alongside(s[0], s[1]) && gap < 2.5 && yawDiff < 12,
                    String.format("beam offset %.1f -> %.1f (gap %.1f), along %.1f, heading diff %.0f deg after %d ticks", before[0], Math.abs(rel.x), gap, rel.z, yawDiff, waited[0]));
            NavalCombat.unlink(s[0]);
            SelfTest.check("cutting the lines frees both ships", s[0].linkedId() < 0 && s[1].linkedId() < 0, "");
            return 0;
        });
        // ---------- ramming ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            double x = at[0] + 120, z = at[1];
            s[2] = ship(l, ShipClass.CARAVEL, x, z, 90, "Selftest Rammed");
            s[2].setAnchored(true);
            s[3] = ship(l, ShipClass.BRIGANTINE, x, z - 30, 0, "Selftest Rammer");
            s[3].autopilot = true;
            s[3].testSpeedMult = 2f;
            s[3].setAnchored(false);
            s[3].setSail(3);
            s[3].setSpeed(0.7f);
            before[1] = s[2].hull();
            before[2] = s[3].hull();
            before[3] = s[2].getX();
            before[4] = s[2].getZ();
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            if (NavalCombat.lastRamDealt(s[3]) <= 0 && ++waited[0] < 200) return -1;
            float dealt = (float) before[1] - s[2].hull(), taken = (float) before[2] - s[3].hull();
            SelfTest.check("bow impact rams the target harder than the rammer", dealt > 0 && dealt > taken * 1.5f,
                    String.format("brigantine at %.2f b/t into a caravel's side: target -%.0f hull, rammer -%.0f (after %d ticks)", 0.7, dealt, taken, waited[0]));
            return 10;
        });
        steps.add(srv -> {
            double moved = Math.hypot(s[2].getX() - before[3], s[2].getZ() - before[4]);
            SelfTest.check("ramming knocks the target ship back and stops the rammer", moved > 0.4 && s[3].speedNow() < 0.5f,
                    String.format("target shoved %.1f blocks, rammer speed now %.2f", moved, s[3].speedNow()));
            s[3].autopilot = false;
            s[3].setSail(0);
            s[3].setAnchored(true);
            return 0;
        });
        // ---------- gun crew: an NPC gunner leads a moving hostile ship ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            double x = at[0] + 240, z = at[1];
            s[4] = ship(l, ShipClass.BRIGANTINE, x, z, 0, "Selftest Gunboat");
            s[4].setAnchored(true);
            s[5] = ship(l, ShipClass.BRIGANTINE, x + 36, z - 12, 0, "Selftest Target");
            s[5].setFlag(ShipEntity.F_NPC, true);
            s[5].npcFaction = "pirate";
            s[5].npcAiOff = true;
            s[5].noDespawn = true;
            s[5].autopilot = true;
            s[5].setAnchored(false);
            s[5].setSail(1);
            HumanoidNpc lookout = hand(l, s[5], "pirate", 0, -2);
            NavalCombat.npcCrew(s[5]).add(lookout.getUUID());
            HumanoidNpc gunner = hand(l, s[4], "guard", 1, 0);
            boolean seated = s[4].seatEntity(gunner, 1);
            NavalCombat.setFireAtWill(s[4], null, true);
            SelfTest.check("an NPC takes a gun station", seated && s[4].seatIndex(gunner) == 1, "seat " + s[4].seatIndex(gunner));
            before[5] = s[5].hull();
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            if (NavalCombat.shipHits(s[4]) == 0 && ++waited[0] < 400) return -1;
            SelfTest.check("NPC gunner fires on a moving hostile ship and hits (aim leading)", NavalCombat.shotsFired(s[4]) > 0 && NavalCombat.shipHits(s[4]) > 0 && s[5].hull() < before[5],
                    String.format("%d shots, %d hits, target hull %.0f -> %.0f, target speed %.2f b/t, %d ticks", NavalCombat.shotsFired(s[4]), NavalCombat.shipHits(s[4]), before[5], s[5].hull(), s[5].speed(), waited[0]));
            NavalCombat.setFireAtWill(s[4], null, false);
            return 0;
        });
        // ---------- chain shot shreds rigging ----------
        steps.add(srv -> {
            float rig = s[5].rigging();
            Vec3 muzzle = s[4].muzzle(1);
            for (int i = 0; i < 3; i++) s[4].fireShot(1, s[5].position().add(0, 2, 0).subtract(muzzle), null, 14f, NavalCombat.CHAIN_SHOT, true);
            before[6] = rig;
            return 40;
        });
        steps.add(srv -> {
            SelfTest.check("chain shot tears rigging (the ship slows)", s[5].rigging() < before[6], String.format("rigging %.0f%% -> %.0f%%", before[6] * 100, s[5].rigging() * 100));
            return 0;
        });
        // ---------- boarding: a raider grapples a player's ship and sends a boarding party ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            FakePlayer fp = SelfTest.fake(srv);
            double x = at[0], z = at[1] + 60;
            load(l, x, z, 4);
            s[6] = ship(l, ShipClass.CARAVEL, x, z, 0, "Selftest Merchant");
            s[6].owner = fp.getUUID();
            s[6].setAnchored(true);
            ShipRegistry.get(srv).register(s[6]);
            Vec3 pos = s[6].toWorld(s[6].cls.halfBeamAt(0.5f) + ShipClass.BRIGANTINE.halfBeamAt(0.5f) + 0.6, 0, 0);
            s[7] = SeaEncounters.spawnShip(l, new Vec3(pos.x, WorldPlan.SEA_LEVEL + 0.9, pos.z), 0, ShipClass.BRIGANTINE, false, 3, "Selftest Raider");
            s[7].noDespawn = true;
            NavalCombat.link(s[7], s[6], false);
            int gunners = 0;
            for (int i = 1; i <= s[7].cls.cannons; i++) if (s[7].getFirstPassengerAtSeat(i) instanceof GLNpc) gunners++;
            SelfTest.check("raider crew mans the guns", gunners > 0 && NavalCombat.npcCrew(s[7]).size() > gunners, gunners + " of " + NavalCombat.npcCrew(s[7]).size() + " crew at the guns");
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            if (NavalCombat.boardersAboard(s[7], s[6]) == 0 && ++waited[0] < 600) return -1;
            int n = NavalCombat.boardersAboard(s[7], s[6]);
            SelfTest.check("boarding party leaps across and lands on the player's deck", n > 0, n + " boarders on deck after " + waited[0] + " ticks");
            return 0;
        });
        // ---------- capture: beat the crew, take the ship ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            FakePlayer fp = SelfTest.fake(srv);
            boolean refused = !NavalCombat.capture(s[7], fp);
            for (UUID u : new ArrayList<>(NavalCombat.npcCrew(s[7]))) if (l.getEntity(u) instanceof GLNpc m) m.discard();
            boolean beaten = NavalCombat.crewDefeated(s[7]);
            boolean took = NavalCombat.capture(s[7], fp);
            var rec = ShipRegistry.get(srv).get(s[7].shipId);
            SelfTest.check("a ship can't be taken while its crew still fights", refused, "");
            SelfTest.check("capturing a ship adds it to the player's registry as a prize", beaten && took && rec != null && fp.getUUID().equals(rec.owner) && rec.prize
                            && s[7].flag(ShipEntity.F_PRIZE) && !s[7].flag(ShipEntity.F_NPC),
                    rec == null ? "no registry record" : rec.name + " (" + rec.cls + "), prize=" + rec.prize + ", sale value ฿" + NavalCombat.prizeValue(s[7]));
            NavalCombat.unlink(s[7]);
            // a second prize sold to the shipwright
            s[8] = SeaEncounters.spawnShip(l, new Vec3(at[0] + 40, WorldPlan.SEA_LEVEL + 0.9, at[1] + 100), 0, ShipClass.SLOOP, true, 2, "Selftest Cutter");
            for (UUID u : new ArrayList<>(NavalCombat.npcCrew(s[8]))) if (l.getEntity(u) instanceof GLNpc m) m.discard();
            NavalCombat.capture(s[8], fp);
            var d = com.grandline.player.Characters.get(fp);
            long beli = d.beli, value = NavalCombat.prizeValue(s[8]);
            boolean sold = Shipyard.sellPrize(fp, s[8]);
            SelfTest.check("a shipwright buys a prize for Beli", sold && d.beli == beli + value && ShipRegistry.get(srv).get(s[8].shipId) == null && s[8].isRemoved(),
                    "฿" + beli + " -> ฿" + d.beli);
            return 0;
        });
        // ---------- squadrons ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            load(l, at[0] + 120, at[1] + 120, 4);
            List<ShipEntity> sq = SeaEncounters.spawnSquadronAt(l, new Vec3(at[0] + 120, WorldPlan.SEA_LEVEL + 0.9, at[1] + 140), 0, com.grandline.world.SeaZone.PARADISE, false);
            boolean led = sq.size() >= 2;
            for (int i = 1; i < sq.size(); i++) sq.get(i).noDespawn = true;
            if (!sq.isEmpty()) sq.get(0).noDespawn = true;
            SelfTest.check("Grand Line Marines sail as a squadron with a flagship", led && sq.get(0).cls.ordinal() > sq.get(1).cls.ordinal() && sq.stream().allMatch(x -> x.npcFaction.equals("marine")),
                    sq.size() + " ships: " + sq.stream().map(x -> x.cls.id()).toList());
            for (int i = 0; i < sq.size(); i++) s[9 + Math.min(i, 2)] = sq.get(i);
            WorldPlan p = WorldPlan.of(srv);
            Island hq = p.byId("marineford");
            SelfTest.check("Marine Headquarters waters are recognised", hq != null && SeaEncounters.nearHeadquarters(srv, hq.x + hq.radius + 500, hq.z) && !SeaEncounters.nearHeadquarters(srv, at[0], at[1]), "");
            return 100;
        });
        steps.add(srv -> {
            ShipEntity lead = s[9], esc = s[10];
            double d = lead == null || esc == null ? -1 : Math.sqrt(lead.distanceToSqr(esc));
            SelfTest.check("escorts keep station on their flagship", d > 0 && d < 60, String.format("escort %.0f blocks from the flagship", d));
            return 0;
        });
        // ---------- customisation (persistence fixture for phase 2) ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            FakePlayer fp = SelfTest.fake(srv);
            ShipEntity c = ship(l, ShipClass.GALLEON, at[0] - 60, at[1] + 60, 45, "Selftest Figurehead");
            c.owner = fp.getUUID();
            c.setAnchored(true);
            ShipRegistry.get(srv).register(c);
            ShipLook look = new ShipLook("dragon", "crescent", 0xE8C040, 0x23417E);
            long fee = Shipyard.customiseFee(c, look);
            Shipyard.customise(c, look, 0xC9483B, 0x2A4E8A);
            var rec = ShipRegistry.get(srv).get(c.shipId);
            SelfTest.check("ship customisation applies (figurehead, emblem, flag, paint)", c.look().equals(look) && c.sailColor() == 0xC9483B && c.hullColor() == 0x2A4E8A
                    && rec != null && rec.look.equals(look.encode()) && fee == 10000, c.look().encode() + ", fee ฿" + fee);
            // clean up the fighting ships, keep the customised one, the prize and the boarded merchant
            for (int i : new int[]{0, 1, 2, 3, 4, 5, 9, 10, 11}) {
                if (s[i] == null || s[i].isRemoved()) continue;
                for (UUID u : new ArrayList<>(NavalCombat.npcCrew(s[i]))) if (l.getEntity(u) != null) l.getEntity(u).discard();
                for (var e : l.getEntitiesOfClass(GLNpc.class, s[i].getBoundingBox().inflate(20), e -> true)) e.discard();
                s[i].ejectPassengers();
                s[i].discard();
            }
            for (var e : l.getEntitiesOfClass(GLNpc.class, new AABB(at[0] - 80, 40, at[1] - 80, at[0] + 80, 90, at[1] + 140), e -> e.getTags().contains("gl_boarder"))) e.discard();
            Files.writeString(Path.of(FIXTURE), c.getUUID() + " " + c.blockPosition().getX() + " " + c.blockPosition().getZ() + " " + c.look().encode() + " " + c.shipId + " " + s[7].shipId);
            return 40;
        });
    }

    static void phase2(Deque<SelfTest.Step> steps) {
        final String[] fx = new String[1];
        steps.add(srv -> {
            if (!Files.exists(Path.of(FIXTURE))) {
                SelfTest.check("naval fixture present", false, "run phase 1 first");
                return 0;
            }
            fx[0] = Files.readString(Path.of(FIXTURE)).trim();
            String[] p = fx[0].split(" ");
            load(srv.overworld(), Integer.parseInt(p[1]), Integer.parseInt(p[2]), 3);
            return 80;
        });
        final int[] waited = {0};
        steps.add(srv -> {
            if (fx[0] == null) return 0;
            String[] p = fx[0].split(" ");
            var e = srv.overworld().getEntity(UUID.fromString(p[0]));
            if (e == null && ++waited[0] < 400) return -1; // entities load asynchronously
            boolean ok = e instanceof ShipEntity c && c.look().encode().equals(p[3]) && c.sailColor() == 0xC9483B && c.hullColor() == 0x2A4E8A;
            SelfTest.check("ship customisation persisted across restart", ok, e instanceof ShipEntity c ? c.look().encode() + " sail " + Integer.toHexString(c.sailColor()) : "ship missing");
            var rec = ShipRegistry.get(srv).get(UUID.fromString(p[4]));
            SelfTest.check("registry keeps the ship's look", rec != null && rec.look.equals(p[3]), rec == null ? "no record" : rec.look);
            var prize = ShipRegistry.get(srv).get(UUID.fromString(p[5]));
            SelfTest.check("captured prize still registered after restart", prize != null && prize.prize, prize == null ? "no record" : prize.name);
            return 0;
        });
    }

    private NavalTests() {}
}
