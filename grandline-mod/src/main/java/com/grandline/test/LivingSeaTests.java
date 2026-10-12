package com.grandline.test;

import com.grandline.navigation.Ferries;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.registry.ModEntities;
import com.grandline.registry.ModItems;
import com.grandline.ship.*;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.SettlementType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Self-test steps for the living sea (ship/LivingSea): NPC ships fighting each other with nobody watching, salvage
 * from a beaten ship, bounty hunters, merchant traffic that rounds islands, hailing and attacking merchants, a
 * harbour raid with guards, a raider recruit and defenders, despawning, the spawn rules (Calm Belt, harbours, the
 * sky, the server-wide cap with several players) and, in phase 2, that NPC ships are not restored from disk.
 * Run alone with GL_SELFTEST=livingsea; phase 1 of the full self-test runs it too.
 */
final class LivingSeaTests {
    private static final String FIXTURE = "selftest-livingsea.txt";
    private static final Set<Long> FORCED = new HashSet<>(), TICKETS = new HashSet<>();

    // ------------------------------------------------------------------ helpers

    private static void force(ServerLevel l, int cx, int cz) {
        if (FORCED.add(ChunkPos.asLong(cx, cz))) l.setChunkForced(cx, cz, true);
    }

    /** Keep chunks around a point entity-ticking (as a player nearby would). */
    private static void area(ServerLevel l, double x, double z, int r) {
        int cx = BlockPos.containing(x, 0, z).getX() >> 4, cz = BlockPos.containing(x, 0, z).getZ() >> 4;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) force(l, cx + dx, cz + dz);
    }

    /** As {@link #area}, generating the chunks now (game code only reads fully loaded chunks). */
    private static void loadNow(ServerLevel l, double x, double z, int r) {
        area(l, x, z, r);
        int cx = BlockPos.containing(x, 0, z).getX() >> 4, cz = BlockPos.containing(x, 0, z).getZ() >> 4;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) l.getChunk(cx + dx, cz + dz);
    }

    /** Keep the chunks under a moving ship ticking. */
    private static void keep(ServerLevel l, Entity e) {
        if (e == null || e.isRemoved()) return;
        ChunkPos cp = e.chunkPosition();
        if (TICKETS.add(cp.toLong())) l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, 3, cp);
        force(l, cp.x, cp.z);
    }

    private static void release(ServerLevel l) {
        for (long c : FORCED) l.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
        for (long c : TICKETS) {
            ChunkPos cp = new ChunkPos(c);
            l.getChunkSource().removeRegionTicket(TicketType.FORCED, cp, 3, cp);
        }
        FORCED.clear();
        TICKETS.clear();
    }

    /** The same fake player again (its record untouched). */
    private static FakePlayer again(ServerLevel l, String name) {
        return FakePlayerFactory.get(l, new GameProfile(UUID.nameUUIDFromBytes(("gl-livingsea-" + name).getBytes(StandardCharsets.UTF_8)), name));
    }

    /** A fake player with a fresh civilian record (no bounty, 100,000 Beli). */
    private static FakePlayer player(ServerLevel l, String name) {
        FakePlayer fp = again(l, name);
        CharacterData d = Characters.get(fp);
        d.faction = Faction.CIVILIAN;
        d.bounty = 0;
        d.beli = 100_000;
        return fp;
    }

    private static Vec3 sea(double x, double z) {
        return new Vec3(x, WorldPlan.SEA_LEVEL + 0.9, z);
    }

    /** Remove a ship with its crew and anyone standing on it. */
    private static void scrap(ServerLevel l, ShipEntity s) {
        if (s == null || s.isRemoved()) return;
        for (UUID u : new ArrayList<>(NavalCombat.npcCrew(s))) if (l.getEntity(u) != null) l.getEntity(u).discard();
        for (GLNpc e : l.getEntitiesOfClass(GLNpc.class, s.getBoundingBox().inflate(s.cls.length), e -> s.carries(e) || e.getVehicle() == s)) e.discard();
        NavalCombat.unlink(s);
        s.discard();
    }

    /** Remove every NPC ship, sea crew and salvage boat in a box. */
    private static void clearBox(ServerLevel l, AABB box) {
        for (ShipEntity s : l.getEntitiesOfClass(ShipEntity.class, box, s -> s.flag(ShipEntity.F_NPC))) scrap(l, s);
        for (Entity e : l.getEntitiesOfClass(Entity.class, box, e -> e.getTags().contains(LivingSea.CREW) || e.getTags().contains(LivingSea.SALVAGE)
                || e.getTags().contains(LivingSea.RESPONDER))) e.discard();
    }

    private static boolean gone(ShipEntity s) {
        return s.isRemoved() || s.isSinking() || LivingSea.beaten(s);
    }

    private static int alive(ServerLevel l, ShipEntity s) {
        int n = 0;
        for (UUID u : NavalCombat.npcCrew(s)) if (l.getEntity(u) instanceof GLNpc m && m.isAlive() && !m.flag(GLNpc.FLAG_SURRENDER)) n++;
        return n;
    }

    /** Open water ±150 blocks, plan-sampled so it works before the chunks exist. */
    private static double[] openSea(WorldPlan p, Island home, int dx) {
        WorldPlan.Sample smp = new WorldPlan.Sample();
        for (int r = 0; r < 60; r++) {
            int x = home.x + dx + r * 97, z = home.z + home.radius + 700 + (r % 5) * 61;
            boolean open = true;
            for (int ox = -150; ox <= 150 && open; ox += 15)
                for (int oz = -150; oz <= 150 && open; oz += 15) {
                    p.sample(x + ox, z + oz, smp);
                    if (smp.height > WorldPlan.SEA_LEVEL - 5) open = false;
                }
            if (open) return new double[]{x + 0.5, z + 0.5};
        }
        return new double[]{home.x + dx + 0.5, home.z + home.radius + 700.5};
    }

    private static boolean port(Island i) {
        return i.settlementType != SettlementType.NONE && i.settlementType != SettlementType.RUINS && i.settlement().hasDock;
    }

    // ------------------------------------------------------------------ phase 1

    static void phase1(Deque<SelfTest.Step> steps) {
        final double[] at = new double[2], at2 = new double[2];
        final ShipEntity[] s = new ShipEntity[12];
        final int[] waited = {0};
        final double[] num = new double[12];
        final Vec3[] lastPos = new Vec3[1];
        final Island[] isl = new Island[2];
        final List<HumanoidNpc> guards = new ArrayList<>();
        final List<ShipEntity> spawned = new ArrayList<>();

        // ---------- test waters ----------
        steps.add(srv -> {
            WorldPlan p = WorldPlan.of(srv);
            Island home = p.byId("shiokaze_isle");
            double[] o = openSea(p, home, 1300), o2 = openSea(p, home, 2300);
            at[0] = o[0];
            at[1] = o[1];
            at2[0] = o2[0];
            at2[1] = o2[1];
            ServerLevel l = srv.overworld();
            loadNow(l, at[0], at[1], 6);
            loadNow(l, at2[0], at2[1], 3);
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            for (double[] pt : new double[][]{{0, 0}, {60, 0}, {0, -40}, {60, -40}, {-40, 40}})
                if (!l.isPositionEntityTicking(BlockPos.containing(at[0] + pt[0], 64, at[1] + pt[1])) && ++waited[0] < 3000) return -1;
            if (!l.isPositionEntityTicking(BlockPos.containing(at2[0], 64, at2[1])) && ++waited[0] < 3000) return -1;
            SelfTest.check("living-sea test waters loaded", true, "after " + waited[0] + " ticks at " + (int) at[0] + "," + (int) at[1] + " and " + (int) at2[0] + "," + (int) at2[1]);
            clearBox(l, new AABB(at[0] - 200, 0, at[1] - 200, at[0] + 260, 200, at[1] + 200));
            return 0;
        });

        // ---------- a Marine ship and a pirate ship find each other and fight it out, nobody watching ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            Vec3 a = sea(at[0], at[1]), b = sea(at[0] + 60, at[1]);
            s[0] = SeaEncounters.spawnShip(l, a, -90, ShipClass.BRIGANTINE, LivingSea.MARINE, 3, "Selftest Marine");
            s[1] = SeaEncounters.spawnShip(l, b, 90, ShipClass.BRIGANTINE, LivingSea.PIRATE, 3, "Selftest Corsair");
            boolean ok = s[0] != null && s[1] != null;
            if (ok) {
                s[0].noDespawn = true;
                s[1].noDespawn = true;
            }
            SelfTest.check("a Marine ship and a pirate ship put to sea 60 blocks apart", ok, ok ? NavalCombat.npcCrew(s[0]).size() + " vs " + NavalCombat.npcCrew(s[1]).size() + " crew" : "spawn failed");
            waited[0] = 0;
            return ok ? 1 : 0;
        });
        steps.add(srv -> {
            if (s[0] == null || s[1] == null) return 0;
            ServerLevel l = srv.overworld();
            keep(l, s[0]);
            keep(l, s[1]);
            boolean marineSees = LivingSea.targetOf(s[0]) == s[1].getId();
            boolean pirateSees = LivingSea.targetOf(s[1]) == s[0].getId() || s[1].life.threat() == s[0].getId();
            if (!(marineSees && pirateSees) && ++waited[0] < 400) return -1;
            SelfTest.check("NPC ships of hostile factions sight each other (no player involved)", marineSees && pirateSees,
                    String.format("after %d ticks: Marine target %s, pirate %s", waited[0], marineSees ? "the pirate" : String.valueOf(LivingSea.targetOf(s[0])),
                            LivingSea.targetOf(s[1]) == s[0].getId() ? "engages" : s[1].life.threat() == s[0].getId() ? "runs" : "ignores her"));
            waited[0] = 0;
            num[0] = s[0].hull();
            num[1] = s[1].hull();
            return 0;
        });
        steps.add(srv -> {
            if (s[0] == null || s[1] == null) return 0;
            ServerLevel l = srv.overworld();
            keep(l, s[0]);
            keep(l, s[1]);
            for (int k = 0; k < 2; k++) if (!s[k].isRemoved()) lastPos[0] = gone(s[k]) ? s[k].position() : lastPos[0];
            if (!gone(s[0]) && !gone(s[1]) && ++waited[0] < 7200) return -1;
            int loser = gone(s[1]) ? 1 : gone(s[0]) ? 0 : -1;
            String how = loser < 0 ? "undecided" : s[loser].isSinking() || s[loser].isRemoved() ? "sunk" : "crew beaten";
            SelfTest.check("the sea battle is decided without a player (one ship sunk or her crew beaten)", loser >= 0,
                    String.format("after %d ticks: %s %s; Marine hull %.0f->%.0f, %d shots, %d hits, %d crew standing; pirate hull %.0f->%.0f, %d shots, %d hits, %d crew standing",
                            waited[0], loser < 0 ? "-" : s[loser].shipName(), how, num[0], s[0].hull(), NavalCombat.shotsFired(s[0]), NavalCombat.shipHits(s[0]), alive(l, s[0]),
                            num[1], s[1].hull(), NavalCombat.shotsFired(s[1]), NavalCombat.shipHits(s[1]), alive(l, s[1])));
            num[2] = loser;
            if (loser >= 0) lastPos[0] = s[loser].position();
            waited[0] = 0;
            return 0;
        });
        // ---------- the beaten ship founders and leaves salvage ----------
        steps.add(srv -> {
            if (num[2] < 0 || lastPos[0] == null) return 0;
            ServerLevel l = srv.overworld();
            ShipEntity loser = s[(int) num[2]];
            if (!loser.isRemoved()) {
                keep(l, loser);
                lastPos[0] = loser.position();
            }
            keep(l, s[1 - (int) num[2]]);
            List<ChestBoat> boats = l.getEntitiesOfClass(ChestBoat.class, new AABB(lastPos[0], lastPos[0]).inflate(60, 20, 60), b -> b.getTags().contains(LivingSea.SALVAGE));
            if (boats.isEmpty() && ++waited[0] < 1500) return -1;
            String table = "-";
            int stacks = 0;
            if (!boats.isEmpty()) {
                ChestBoat b = boats.get(0);
                table = b.getLootTable() == null ? "already rolled" : b.getLootTable().location().toString();
                b.unpackLootTable(null);
                for (int i = 0; i < b.getContainerSize(); i++) if (!b.getItem(i).isEmpty()) stacks++;
            }
            SelfTest.check("a beaten ship goes down and leaves a salvage boat with loot", !boats.isEmpty() && stacks > 0,
                    boats.size() + " salvage boat(s) after " + waited[0] + " ticks, loot table " + table + ", " + stacks + " stacks; loser " + (loser.isRemoved() ? "sunk" : loser.isSinking() ? "sinking" : "afloat"));
            return 0;
        });
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            scrap(l, s[0]);
            scrap(l, s[1]);
            clearBox(l, new AABB(at[0] - 200, 0, at[1] - 200, at[0] + 260, 200, at[1] + 200));
            // ---------- bounty hunters run down a pirate ship ----------
            s[2] = SeaEncounters.spawnShip(l, sea(at[0], at[1] - 40), -90, ShipClass.CARAVEL, LivingSea.HUNTER, 2, "Selftest Hunter");
            s[3] = SeaEncounters.spawnShip(l, sea(at[0] + 60, at[1] - 40), -90, ShipClass.SLOOP, LivingSea.PIRATE, 1, "Selftest Quarry");
            if (s[2] != null) s[2].noDespawn = true;
            if (s[3] != null) s[3].noDespawn = true;
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            if (s[2] == null || s[3] == null) {
                SelfTest.check("bounty hunters chase a pirate ship", false, "spawn failed");
                return 0;
            }
            ServerLevel l = srv.overworld();
            keep(l, s[2]);
            keep(l, s[3]);
            if (LivingSea.targetOf(s[2]) != s[3].getId() && ++waited[0] < 300) return -1;
            SelfTest.check("bounty hunters chase a pirate ship", LivingSea.targetOf(s[2]) == s[3].getId(),
                    "after " + waited[0] + " ticks; the outgunned pirate sloop " + (s[3].life.threat() == s[2].getId() ? "runs" : LivingSea.targetOf(s[3]) == s[2].getId() ? "turns to fight" : "does neither"));
            scrap(l, s[2]);
            scrap(l, s[3]);
            return 0;
        });

        // ---------- merchant traffic: bound for a port beyond an island, she rounds it ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            WorldPlan p = WorldPlan.of(srv);
            Island home = p.byId("shiokaze_isle");
            Island dest = null;
            for (Island i : p.islands) // the nearest port of the same sea clear of the island itself
                if (i != home && i.zone == home.zone && port(i) && i.distance(home.x, home.z) > home.radius + i.radius + 150
                        && (dest == null || i.distance(home.x, home.z) < dest.distance(home.x, home.z))) dest = i;
            isl[0] = dest;
            if (dest == null) {
                SelfTest.check("a merchant ship sails for her port and rounds the island in the way", false, "no port in reach");
                return 0;
            }
            Vec3 dock = Ferries.dockOf(dest);
            Vec3 u = new Vec3(home.x - dock.x, 0, home.z - dock.z).normalize();
            Vec3 start = null;
            for (int extra = 80; extra <= 240 && start == null; extra += 40) {
                Vec3 c = new Vec3(home.x, 0, home.z).add(u.scale(home.radius * 1.3 + extra));
                loadNow(l, c.x, c.z, 2);
                start = LivingSea.openWater(l, c.x, c.z, 12);
            }
            if (start == null) {
                SelfTest.check("a merchant ship sails for her port and rounds the island in the way", false, "no open water behind " + home.id);
                return 0;
            }
            // the straight course runs over the island: she has to go round
            Vec3 d = dock.subtract(start);
            double t = Math.max(0, Math.min(1, new Vec3(home.x - start.x, 0, home.z - start.z).dot(d) / d.lengthSqr()));
            num[3] = new Vec3(start.x + d.x * t - home.x, 0, start.z + d.z * t - home.z).length();
            s[4] = MerchantShips.spawnBound(l, start, ShipClass.CARAVEL, dest);
            if (s[4] == null) {
                SelfTest.check("a merchant ship sails for her port and rounds the island in the way", false, "spawn failed");
                return 0;
            }
            s[4].noDespawn = true;
            s[4].testSpeedMult = 3f;
            num[4] = start.distanceTo(dock);
            num[5] = 0; // ticks with land under the hull
            num[6] = 0; // stopped ticks
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            ShipEntity m = s[4];
            if (m == null || isl[0] == null) return 0;
            ServerLevel l = srv.overworld();
            WorldPlan p = WorldPlan.of(srv);
            Island home = p.byId("shiokaze_isle");
            keep(l, m);
            WorldPlan.Sample smp = new WorldPlan.Sample();
            if (!m.isRemoved()) {
                for (Vec3 pt : new Vec3[]{m.position(), m.toWorld(0, 0, m.cls.length / 2.0 - 1)}) {
                    p.sample((int) Math.floor(pt.x), (int) Math.floor(pt.z), smp);
                    if (smp.height >= WorldPlan.SEA_LEVEL) {
                        num[5]++;
                        break;
                    }
                }
                if (m.speed() < 0.02f) num[6]++;
            }
            Vec3 dock = Ferries.dockOf(isl[0]);
            Vec3 toDest = new Vec3(dock.x - home.x, 0, dock.z - home.z).normalize();
            double past = new Vec3(m.getX() - home.x, 0, m.getZ() - home.z).dot(toDest);
            double closer = num[4] - m.position().distanceTo(dock);
            boolean done = m.life.arrived || past > home.radius * 0.2; // round the island and out the other side
            if (!done && !m.isRemoved() && ++waited[0] < 6000) return -1;
            SelfTest.check("a merchant ship sails for her port and rounds the island in the way", done && (m.life.route.equals(isl[0].id) || m.life.arrived) && closer > 0,
                    String.format("bound for %s; the straight course passes %.0f blocks from %s's centre (radius %d); after %d ticks %.0f blocks closer, %.0f past the island, at %s, stopped %d ticks",
                            isl[0].id, num[3], home.id, home.radius, waited[0], closer, past - home.radius * 0.2, m.blockPosition().toShortString(), (int) num[6]));
            SelfTest.check("the merchant ship never sails over land", num[5] == 0, (int) num[5] + " ticks with land under the hull");
            scrap(l, m);
            return 0;
        });

        // ---------- hailing a merchant at sea: her captain trades ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            s[5] = SeaEncounters.spawnShip(l, sea(at2[0], at2[1]), 0, ShipClass.SLOOP, LivingSea.MERCHANT, 1, "Selftest Trader");
            if (s[5] == null) {
                SelfTest.check("hailing a merchant ship at sea opens her captain's trade", false, "spawn failed");
                return 0;
            }
            s[5].noDespawn = true;
            s[5].npcAiOff = true;
            s[5].setAnchored(true);
            return 2;
        });
        steps.add(srv -> {
            if (s[5] == null) return 0;
            ServerLevel l = srv.overworld();
            GLNpc captain = null;
            for (UUID u : NavalCombat.npcCrew(s[5])) if (l.getEntity(u) instanceof GLNpc m && m.role().equals("sea_merchant")) captain = m;
            if (captain == null) {
                SelfTest.check("hailing a merchant ship at sea opens her captain's trade", false, "no captain aboard");
                return 0;
            }
            FakePlayer fp = player(l, "SeaTrader");
            fp.moveTo(captain.getX(), captain.getY(), captain.getZ());
            com.grandline.dialogue.Dialogues.open(fp, captain);
            CharacterData d = Characters.get(fp);
            long before = d.beli;
            com.grandline.dialogue.Shops.buy(fp, captain.getId(), 0);
            int steaks = 0;
            for (ItemStack st : fp.getInventory().items) if (st.is(ModItems.SEA_KING_STEAK.get())) steaks += st.getCount();
            SelfTest.check("hailing a merchant ship at sea opens her captain's trade", captain.archetype().merchant() && d.beli < before && steaks > 0,
                    captain.getName().getString() + " (" + captain.role() + ") sold " + steaks + " sea king steak for " + (before - d.beli) + " Beli");
            return 0;
        });
        // ---------- attacking her from her own deck: piracy, and her crew fight ----------
        steps.add(srv -> {
            if (s[5] == null) return 0;
            ServerLevel l = srv.overworld();
            FakePlayer fp = player(l, "SeaBrigand");
            CharacterData d = Characters.get(fp);
            Vec3 deck = s[5].toWorld(0, s[5].cls.freeboard + 0.05, -1);
            fp.moveTo(deck.x, deck.y, deck.z);
            boolean aboard = s[5].carries(fp);
            s[5].getParts()[0].hurt(fp.damageSources().playerAttack(fp), 4f);
            int turned = 0;
            for (UUID u : NavalCombat.npcCrew(s[5])) if (l.getEntity(u) instanceof GLNpc m && m.getTarget() == fp) turned++;
            SelfTest.check("attacking a merchant ship raises the attacker's bounty and her crew fight back", d.bounty > 0 && turned > 0 && s[5].life.wronged(fp),
                    "aboard=" + aboard + ", bounty " + d.bounty + ", " + turned + " of the crew turned on the attacker");
            for (UUID u : NavalCombat.npcCrew(s[5])) if (l.getEntity(u) instanceof GLNpc m) m.setTarget(null);
            fp.moveTo(at2[0] + 500, 80, at2[1] + 500);
            scrap(l, s[5]);
            return 0;
        });
        // ---------- caught by a stronger ship she strikes her colours ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            FakePlayer cap = player(l, "SeaCaptain");
            ShipEntity g = ModEntities.SHIPS.get(ShipClass.GALLEON).get().create(l);
            g.moveTo(at2[0], WorldPlan.SEA_LEVEL + 0.9, at2[1] - 30, 0, 0);
            g.setShipName("Selftest Man-o'-War");
            g.setHull(g.cls.hull);
            g.owner = cap.getUUID();
            g.setAnchored(true);
            l.addFreshEntity(g);
            s[6] = g;
            int side = g.seatLocal(1).x >= 0 ? 1 : -1;
            Vec3 mp = g.toWorld(side * 24, 0, 0);
            s[7] = SeaEncounters.spawnShip(l, sea(mp.x, mp.z), 0, ShipClass.SLOOP, LivingSea.MERCHANT, 1, "Selftest Prize");
            if (s[7] == null) {
                SelfTest.check("a merchant caught by a stronger ship strikes her colours", false, "spawn failed");
                return 0;
            }
            s[7].noDespawn = true;
            s[7].npcAiOff = true;
            s[7].setAnchored(true);
            Vec3 deck = g.toWorld(0, g.cls.freeboard + 0.05, 2);
            cap.moveTo(deck.x, deck.y, deck.z);
            num[7] = g.carries(cap) ? 1 : 0;
            for (int i = 0; i < 3; i++) {
                Vec3 aim = s[7].toWorld(0, s[7].cls.freeboard * 0.5, (i - 1) * 2.5).subtract(g.muzzle(1));
                g.fireShot(1, aim, cap, 12f, NavalCombat.CANNONBALL, false);
            }
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            if (s[7] == null) return 0;
            ServerLevel l = srv.overworld();
            FakePlayer cap = again(l, "SeaCaptain");
            if (!s[7].life.yielded() && ++waited[0] < 120) return -1;
            int surrendered = 0, crew = 0;
            for (UUID u : NavalCombat.npcCrew(s[7])) if (l.getEntity(u) instanceof GLNpc m && m.isAlive()) {
                crew++;
                if (m.flag(GLNpc.FLAG_SURRENDER)) surrendered++;
            }
            int cargo = l.getEntitiesOfClass(ChestBoat.class, s[7].getBoundingBox().inflate(20), b -> b.getTags().contains(LivingSea.SALVAGE)).size();
            SelfTest.check("a merchant caught by a stronger ship strikes her colours (crew surrender, cargo over the side)",
                    s[7].life.yielded() && surrendered == crew && cargo > 0 && Characters.get(cap).bounty > 0,
                    String.format("captain aboard the galleon=%s, %d shot hits, yielded=%s after %d ticks, %d/%d crew surrendered, %d cargo boat(s), captain's bounty %d",
                            num[7] > 0, NavalCombat.shipHits(s[6]), s[7].life.yielded(), waited[0], surrendered, crew, cargo, Characters.get(cap).bounty));
            boolean took = NavalCombat.capture(s[7], cap);
            SelfTest.check("a merchant that struck her colours can be taken as a prize", took && s[7].flag(ShipEntity.F_PRIZE) && !s[7].getTags().contains(LivingSea.TRANSIENT),
                    "prize=" + s[7].flag(ShipEntity.F_PRIZE) + " transient=" + s[7].getTags().contains(LivingSea.TRANSIENT));
            ShipRegistry.get(srv).remove(s[7].shipId);
            scrap(l, s[7]);
            s[6].discard();
            clearBox(l, new AABB(at2[0] - 120, 0, at2[1] - 120, at2[0] + 120, 200, at2[1] + 120));
            return 0;
        });

        // ---------- a harbour raid ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            WorldPlan p = WorldPlan.of(srv);
            Island home = p.byId("shiokaze_isle");
            Island town = null;
            for (Island i : p.islands)
                if (LivingSea.raidable(i) && i.zone == home.zone && (town == null || i.distance(home.x, home.z) < town.distance(home.x, home.z))) town = i;
            isl[1] = town;
            if (town == null) {
                SelfTest.check("pirates sail in to raid a harbour", false, "no raidable town in " + home.zone.id());
                return 0;
            }
            var st = town.settlement();
            loadNow(l, st.dockTipX(), st.dockTipZ(), 7);
            List<BlockPos> spots = HarbourRaids.landingSpots(l, town);
            for (int k = 0; k < 2 && !spots.isEmpty(); k++) {
                BlockPos b = spots.get(Math.max(0, spots.size() - 1 - k));
                HumanoidNpc g = ModEntities.HUMANOID_NPC.get().create(l);
                g.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, 0, 0);
                g.configure("guard", town.tier, town.id, b, "", l.random);
                l.addFreshEntity(g);
                guards.add(g);
            }
            s[8] = HarbourRaids.start(l, town, new Vec3(st.dockTipX(), 64, st.dockTipZ()), 100);
            SelfTest.check("pirates sail in to raid a harbour", s[8] != null && !spots.isEmpty(),
                    town.id + " (" + town.settlementType + ", tier " + town.tier + "): " + spots.size() + " landing spots, raid ship " + (s[8] == null ? "none" : s[8].shipName() + " at " + s[8].blockPosition().toShortString()));
            if (s[8] == null) return 0;
            s[8].noDespawn = true;
            s[8].testSpeedMult = 2f;
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            ShipEntity r = s[8];
            if (r == null) return 0;
            ServerLevel l = srv.overworld();
            keep(l, r);
            if (r.life.raidPhase == HarbourRaids.APPROACH && !r.isRemoved() && ++waited[0] < 3600) return -1;
            List<GLNpc> raiders = HarbourRaids.raiders(r, l);
            int ashore = 0;
            for (GLNpc m : raiders) if (!m.isInWater() && m.getY() > WorldPlan.SEA_LEVEL) ashore++;
            SelfTest.check("the raid ship sails into the harbour and lands raiders", r.life.raidPhase == HarbourRaids.ASHORE && ashore >= 2,
                    String.format("phase %d after %d ticks, %d raiders, %d ashore, ship %.0f blocks from the dock point", r.life.raidPhase, waited[0], raiders.size(), ashore,
                            r.position().distanceTo(Ferries.dockOf(isl[1]))));
            SelfTest.check("the raid ship holds her fire on the town (town protection)", NavalCombat.shotsFired(r) == 0, NavalCombat.shotsFired(r) + " shots fired");
            waited[0] = 0;
            return 0;
        });
        steps.add(srv -> {
            ShipEntity r = s[8];
            if (r == null || r.life.raidPhase != HarbourRaids.ASHORE) return 0;
            ServerLevel l = srv.overworld();
            keep(l, r);
            int responding = 0;
            for (GLNpc g : l.getEntitiesOfClass(GLNpc.class, r.getBoundingBox().inflate(160, 40, 160), g -> g.getTarget() != null && g.getTarget().getTags().contains(LivingSea.RAIDER)
                    && !g.getTags().contains(LivingSea.RAIDER))) responding++;
            if (responding == 0 && ++waited[0] < 300) return -1;
            int responders = l.getEntitiesOfClass(GLNpc.class, r.getBoundingBox().inflate(160, 40, 160), g -> g.getTags().contains(LivingSea.RESPONDER)).size();
            SelfTest.check("the town's guards respond to the raiders", responding > 0, responding + " defenders on the raiders after " + waited[0] + " ticks (" + guards.size() + " guards, " + responders + " Marines turned out)");
            return 0;
        });
        steps.add(srv -> {
            ShipEntity r = s[8];
            if (r == null || r.life.raidPhase != HarbourRaids.ASHORE) return 0;
            ServerLevel l = srv.overworld();
            GLNpc raider = null;
            for (GLNpc m : HarbourRaids.raiders(r, l)) if (m.isAlive() && !m.flag(GLNpc.FLAG_SURRENDER)) raider = m;
            if (raider == null) {
                SelfTest.check("joining a raid raises your bounty and the raiders take you for one of theirs", false, "no raider standing");
                return 0;
            }
            FakePlayer jo = player(l, "SeaRaider");
            jo.moveTo(raider.getX() + 1, raider.getY(), raider.getZ());
            raider.setTarget(null);
            boolean may = HarbourRaids.mayJoin(jo, raider);
            boolean chose = LivingSea.choose(jo, raider, "join_raid");
            SelfTest.check("joining a raid raises your bounty and the raiders take you for one of theirs", may && chose && Characters.get(jo).bounty > 0 && !raider.isHostileTo(jo),
                    "offered=" + may + ", bounty " + Characters.get(jo).bounty + ", raider hostile=" + raider.isHostileTo(jo));
            jo.moveTo(at2[0] + 600, 80, at2[1] + 600);
            // a defender cuts the raiders down
            FakePlayer de = player(l, "SeaDefender");
            num[8] = Characters.get(de).reputation("island/" + isl[1].id);
            for (GLNpc m : HarbourRaids.raiders(r, l)) if (m.isAlive()) {
                de.moveTo(m.getX() + 1, m.getY(), m.getZ());
                m.hurt(de.damageSources().playerAttack(de), 5000f);
            }
            de.moveTo(at2[0] + 600, 80, at2[1] - 600);
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            ShipEntity r = s[8];
            if (r == null || isl[1] == null) return 0;
            ServerLevel l = srv.overworld();
            keep(l, r);
            if (r.life.raidPhase != HarbourRaids.LEAVING && ++waited[0] < 200) return -1;
            FakePlayer de = again(l, "SeaDefender");
            int rep = Characters.get(de).reputation("island/" + isl[1].id);
            SelfTest.check("beating the raiders repels the raid and the island thanks its defenders", r.life.raidPhase == HarbourRaids.LEAVING && rep - num[8] >= 40,
                    "phase " + r.life.raidPhase + " after " + waited[0] + " ticks, island reputation " + (int) num[8] + " -> " + rep);
            scrap(l, r);
            for (HumanoidNpc g : guards) g.discard();
            var st = isl[1].settlement();
            clearBox(l, new AABB(st.dockTipX() - 160, 0, st.dockTipZ() - 160, st.dockTipX() + 160, 250, st.dockTipZ() + 160));
            for (Entity e : l.getEntitiesOfClass(GLNpc.class, new AABB(st.dockTipX() - 160, 0, st.dockTipZ() - 160, st.dockTipX() + 160, 250, st.dockTipZ() + 160),
                    e -> e.getTags().contains(LivingSea.RAIDER))) e.discard();
            // a second raid on a town without guards: the Marines turn out
            s[10] = HarbourRaids.start(l, isl[1], new Vec3(st.dockTipX(), 64, st.dockTipZ()), 100);
            if (s[10] != null) {
                s[10].noDespawn = true;
                s[10].testSpeedMult = 2f;
            }
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            ShipEntity r = s[10];
            if (r == null) {
                SelfTest.check("with too few guards a Marine detachment turns out against raiders", false, "second raid did not start");
                return 0;
            }
            ServerLevel l = srv.overworld();
            keep(l, r);
            int marines = 0;
            for (GLNpc g : l.getEntitiesOfClass(GLNpc.class, r.getBoundingBox().inflate(160, 40, 160), g -> g.getTags().contains(LivingSea.RESPONDER)
                    && g.getTarget() != null && g.getTarget().getTags().contains(LivingSea.RAIDER))) marines++;
            if (marines == 0 && r.life.raidPhase != HarbourRaids.LEAVING && !r.isRemoved() && ++waited[0] < 4000) return -1;
            SelfTest.check("with too few guards a Marine detachment turns out against raiders", marines > 0,
                    marines + " Marines on the raiders after " + waited[0] + " ticks (raid phase " + r.life.raidPhase + ")");
            scrap(l, r);
            var st = isl[1].settlement();
            AABB box = new AABB(st.dockTipX() - 160, 0, st.dockTipZ() - 160, st.dockTipX() + 160, 250, st.dockTipZ() + 160);
            clearBox(l, box);
            for (Entity e : l.getEntitiesOfClass(GLNpc.class, box, e -> e.getTags().contains(LivingSea.RAIDER))) e.discard();
            return 0;
        });

        // ---------- nobody near: an NPC ship despawns with her crew ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            s[9] = SeaEncounters.spawnShip(l, sea(at[0], at[1] + 40), 0, ShipClass.SLOOP, LivingSea.PIRATE, 1, "Selftest Drifter");
            num[9] = s[9] == null ? 0 : NavalCombat.npcCrew(s[9]).size();
            spawned.clear();
            if (s[9] != null) spawned.add(s[9]);
            waited[0] = 0;
            return 1;
        });
        steps.add(srv -> {
            ShipEntity d = s[9];
            if (d == null) return 0;
            ServerLevel l = srv.overworld();
            if (!d.isRemoved() && ++waited[0] < 100) return -1;
            int left = 0;
            for (UUID u : NavalCombat.npcCrew(d)) if (l.getEntity(u) != null) left++;
            SelfTest.check("an NPC ship with no player within 320 blocks despawns with her crew", d.isRemoved() && left == 0,
                    "removed=" + d.isRemoved() + " after " + waited[0] + " ticks, " + left + " of " + (int) num[9] + " crew left");
            return 0;
        });

        // ---------- where living-sea ships may appear ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            clearBox(l, new AABB(at[0] - 200, 0, at[1] - 200, at[0] + 260, 200, at[1] + 200));
            LivingSea.Rules r = new LivingSea.Rules();
            r.force = r.ignoreGap = r.ignoreCrowd = r.seaOnly = true;
            r.spawnDist = 40;
            FakePlayer calm = player(l, "SeaCalm");
            calm.moveTo(at[0], WorldPlan.SEA_LEVEL + 1, 5000.5);
            List<ShipEntity> a = LivingSea.decide(l, List.of(calm), 1, r);
            List<ShipEntity> b = List.of();
            String harbour = "no raid town";
            if (isl[1] != null) {
                var st = isl[1].settlement();
                double ux = st.dockDirX / 1000.0, uz = st.dockDirZ / 1000.0;
                FakePlayer hb = player(l, "SeaHarbour");
                hb.moveTo(st.dockTipX() + ux * 8 + 0.5, WorldPlan.SEA_LEVEL + 1, st.dockTipZ() + uz * 8 + 0.5);
                b = LivingSea.decide(l, List.of(hb), 1, r);
                harbour = "harbour of " + isl[1].id + " (in harbour waters=" + LivingSea.inHarbour(WorldPlan.of(srv), hb.getBlockX(), hb.getBlockZ()) + ")";
            }
            ServerLevel sky = srv.getLevel(com.grandline.world.SkyWorld.LEVEL);
            List<ShipEntity> c = new ArrayList<>();
            if (sky != null) {
                FakePlayer sp = FakePlayerFactory.get(sky, new GameProfile(UUID.nameUUIDFromBytes("gl-livingsea-sky".getBytes(StandardCharsets.UTF_8)), "SeaSky"));
                sp.moveTo(0.5, WorldPlan.SEA_LEVEL + 1, 0.5);
                c.addAll(LivingSea.decide(sky, List.of(sp), 1, r));
                c.addAll(LivingSea.decide(l, List.of(sp), 1, r));
            }
            FakePlayer open = player(l, "SeaOpen");
            open.moveTo(at[0], WorldPlan.SEA_LEVEL + 1, at[1]);
            List<ShipEntity> d = LivingSea.decide(l, List.of(open), 1, r);
            SelfTest.check("living-sea ships never appear in the Calm Belt, a harbour or the sky; they do on the open sea",
                    a.isEmpty() && b.isEmpty() && c.isEmpty() && !d.isEmpty(),
                    String.format("Calm Belt (zone %s) %d, %s %d, sky %d, open sea %d %s", WorldLayout.zoneAt((int) at[0], 5000).id(), a.size(), harbour, b.size(), c.size(), d.size(),
                            d.stream().map(x -> x.npcFaction).toList()));
            for (ShipEntity x : a) scrap(l, x);
            for (ShipEntity x : b) scrap(l, x);
            for (ShipEntity x : d) scrap(l, x);
            return 0;
        });
        // ---------- one server-wide cap with several players ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            clearBox(l, new AABB(at[0] - 200, 0, at[1] - 200, at[0] + 260, 200, at[1] + 200));
            LivingSea.Rules r = new LivingSea.Rules();
            r.force = r.ignoreGap = r.ignoreCrowd = r.seaOnly = true;
            r.spawnDist = 40;
            List<FakePlayer> ps = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                FakePlayer fp = player(l, "SeaCap" + k);
                fp.moveTo(at[0] + (k == 1 ? 30 : 0), WorldPlan.SEA_LEVEL + 1, at[1] + (k == 2 ? 30 : 0));
                ps.add(fp);
            }
            int before = SeaEncounters.countNpcShips(l);
            List<ShipEntity> got = new ArrayList<>();
            for (int round = 0; round < 8; round++) got.addAll(LivingSea.decide(l, ps, ps.size(), r));
            int after = SeaEncounters.countNpcShips(l), cap = SeaEncounters.cap(ps.size());
            List<ShipEntity> one = LivingSea.decide(l, ps.subList(0, 1), 1, r);
            SelfTest.check("all NPC ships share one server-wide cap with several players", after <= cap && !got.isEmpty() && (after < SeaEncounters.cap(1) || one.isEmpty()),
                    String.format("%d NPC ships before, %d spawned over 8 forced rounds for 3 players, %d now (cap %d); with 1 player (cap %d) %d more",
                            before, got.size(), after, cap, SeaEncounters.cap(1), one.size()));
            for (ShipEntity x : got) scrap(l, x);
            for (ShipEntity x : one) scrap(l, x);
            // convoys: a merchantman with Marine escorts keeping station
            List<ShipEntity> cv = MerchantShips.spawnNear(l, sea(at[0], at[1]), SeaZone.EAST_BLUE, 40, true, 3, l.random);
            boolean escorted = cv.size() >= 2;
            for (int i = 1; i < cv.size(); i++) escorted &= cv.get(0).shipId.equals(LivingSea.leaderOf(cv.get(i))) && cv.get(i).npcFaction.equals(LivingSea.MARINE);
            SelfTest.check("a convoy sails as a merchantman with Marine escorts on her quarters", escorted && cv.get(0).npcFaction.equals(LivingSea.MERCHANT),
                    cv.stream().map(x -> x.shipName() + " (" + x.npcFaction + ", " + x.cls.id() + ")").toList() + (cv.isEmpty() ? "" : " bound for " + cv.get(0).life.route));
            for (ShipEntity x : cv) scrap(l, x);
            return 0;
        });

        // ---------- fixture for phase 2: NPC ships are not restored from disk ----------
        steps.add(srv -> {
            ServerLevel l = srv.overworld();
            Vec3 at0 = sea(at[0] - 40, at[1] + 40);
            ShipEntity t = SeaEncounters.spawnShip(l, at0, 0, ShipClass.SLOOP, LivingSea.MERCHANT, 1, "Selftest Transient");
            ShipEntity c = ModEntities.SHIPS.get(ShipClass.ROWBOAT).get().create(l);
            c.moveTo(at0.x + 16, at0.y, at0.z, 0, 0);
            c.setShipName("Selftest Control");
            c.setHull(c.cls.hull);
            c.setAnchored(true);
            l.addFreshEntity(c);
            if (t != null) {
                t.noDespawn = true;
                t.npcAiOff = true;
                t.setAnchored(true);
                UUID crew = NavalCombat.npcCrew(t).isEmpty() ? t.getUUID() : NavalCombat.npcCrew(t).iterator().next();
                Files.writeString(Path.of(FIXTURE), t.getUUID() + " " + c.getUUID() + " " + crew + " " + (int) at0.x + " " + (int) at0.z);
            }
            SelfTest.check("living-sea fixture written", t != null, t == null ? "spawn failed" : "transient merchant beside a plain rowboat");
            release(l);
            return 40;
        });
    }

    // ------------------------------------------------------------------ phase 2

    static void phase2(Deque<SelfTest.Step> steps) {
        final String[] fx = new String[1];
        final int[] waited = {0};
        steps.add(srv -> {
            if (!Files.exists(Path.of(FIXTURE))) return 0; // the full phase 1 writes it; a quick run may not
            fx[0] = Files.readString(Path.of(FIXTURE)).trim();
            String[] p = fx[0].split(" ");
            loadNow(srv.overworld(), Integer.parseInt(p[3]), Integer.parseInt(p[4]), 2);
            return 20;
        });
        steps.add(srv -> {
            if (fx[0] == null) return 0;
            String[] p = fx[0].split(" ");
            ServerLevel l = srv.overworld();
            if (l.getEntity(UUID.fromString(p[1])) == null && ++waited[0] < 400) return -1; // entities load asynchronously
            boolean control = l.getEntity(UUID.fromString(p[1])) instanceof ShipEntity;
            boolean ship = l.getEntity(UUID.fromString(p[0])) != null, crew = l.getEntity(UUID.fromString(p[2])) != null;
            SelfTest.check("living-sea ships and crews are not restored after a restart (a plain ship beside them is)", control && !ship && !crew,
                    "control rowboat " + (control ? "back" : "missing") + ", merchant " + (ship ? "restored" : "gone") + ", crew " + (crew ? "restored" : "gone"));
            release(l);
            return 0;
        });
    }

    private LivingSeaTests() {}
}
