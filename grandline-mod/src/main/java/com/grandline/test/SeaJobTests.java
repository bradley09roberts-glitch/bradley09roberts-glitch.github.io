package com.grandline.test;

import com.grandline.GrandLine;
import com.grandline.navigation.Ferries;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import com.grandline.quest.story.IslandStories;
import com.grandline.quest.story.JobBoard;
import com.grandline.quest.story.SeaJobs;
import com.grandline.quest.story.StoryModel.Site;
import com.grandline.quest.story.StoryModel.SiteRef;
import com.grandline.quest.story.StorySites;
import com.grandline.registry.ModEntities;
import com.grandline.ship.*;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Settlement;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * Self-test of the sea jobs (quest/story/SeaJobs, EscortJobs, SeaBattleJobs) on the dedicated server, with fake
 * players driven step by step (they cannot ride, so they stand on decks; the ship under them is sailed by the test):
 * <ul>
 *   <li>Escort: taking the job brings the merchant to the player's side; they walk after the player, teleport when
 *   left 40 blocks behind on land, stay out of the sea when the player swims off, take a seat on the player's ship,
 *   sail the whole way to the destination seated, step ashore beside the player, face the dock ambush, and talking to
 *   them pays the job. Their death fails the job; /jobs abandon and a long log-out end it cleanly; a short log-out
 *   stows them and they come back.</li>
 *   <li>Sink the named ship: nothing far from where she was seen, nothing with the NPC ship cap full, then she is put
 *   to sea near the hunter; sinking her pays; a Marine who takes her as a prize is paid the bonus.</li>
 *   <li>Convoy: she waits off the dock for her escort, sails when the player's ship is beside her, pirates fall on her
 *   partway, she makes port and the job pays; rebuilt from her saved state when her ship is gone; sunk, the job fails.</li>
 *   <li>Variants per faction, the generic "sea_battle" objective (Privateer's Commission), translations.</li>
 * </ul>
 * Phase 2 checks that an escort stowed at the end of phase 1 comes back after the restart. Quick run:
 * GL_SELFTEST=seajobs.
 */
final class SeaJobTests {
    private static final String FIXTURE = "selftest-seajobs.txt";
    private static final Set<Long> FORCED = new HashSet<>(), TICKETS = new HashSet<>();
    private static final Map<UUID, List<String>> INBOX = new HashMap<>();

    // ------------------------------------------------------------------ helpers

    private static FakePlayer fp(ServerLevel l, String name) {
        return FakePlayerFactory.get(l, new GameProfile(UUID.nameUUIDFromBytes(("gl-seajobs-" + name).getBytes(StandardCharsets.UTF_8)), name));
    }

    private static FakePlayer character(ServerLevel l, String name, Faction f) {
        FakePlayer p = fp(l, name);
        CharacterData d = Characters.get(p);
        d.created = true;
        d.name = name;
        d.faction = f;
        d.beli = 100_000;
        d.bounty = 0;
        d.knockdownTicks = 0;
        return p;
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents tc ? tc.getKey() : c.getString();
    }

    private static boolean got(FakePlayer p, String key) {
        for (String k : INBOX.getOrDefault(p.getUUID(), List.of())) if (k.equals(key)) return true;
        return false;
    }

    private static void listen() {
        SeaJobs.tap = (p, m) -> {
            INBOX.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(key(m));
            if (m.getContents() instanceof TranslatableContents tc && tc.getKey().equals("job.grandline.says") && tc.getArgs().length > 1 && tc.getArgs()[1] instanceof Component c)
                INBOX.get(p.getUUID()).add(key(c));
        };
    }

    private static void force(ServerLevel l, int cx, int cz) {
        if (FORCED.add(ChunkPos.asLong(cx, cz))) l.setChunkForced(cx, cz, true);
    }

    /** Keep chunks around a point entity-ticking and generate them now (game code only reads loaded chunks). */
    private static void loadNow(ServerLevel l, double x, double z, int r) {
        int cx = BlockPos.containing(x, 0, z).getX() >> 4, cz = BlockPos.containing(x, 0, z).getZ() >> 4;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                force(l, cx + dx, cz + dz);
                l.getChunk(cx + dx, cz + dz);
            }
    }

    private static boolean ticking(ServerLevel l, double x, double z) {
        return l.isPositionEntityTicking(BlockPos.containing(x, 64, z));
    }

    /** Keep the chunks under a moving ship loaded and ticking. */
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

    /** Remove a ship with her crew and anyone aboard. */
    private static void scrap(ServerLevel l, ShipEntity s) {
        if (s == null || s.isRemoved()) return;
        for (UUID u : new ArrayList<>(NavalCombat.npcCrew(s))) if (l.getEntity(u) != null) l.getEntity(u).discard();
        for (GLNpc e : l.getEntitiesOfClass(GLNpc.class, s.getBoundingBox().inflate(s.cls.length), e -> s.carries(e) || e.getVehicle() == s)) e.discard();
        NavalCombat.unlink(s);
        s.discard();
    }

    /** Every NPC ship in the level (strays from earlier tests would fill the shared cap). */
    private static void clearNpcShips(ServerLevel l) {
        List<ShipEntity> all = new ArrayList<>();
        for (Entity e : l.getAllEntities()) if (e instanceof ShipEntity s && s.flag(ShipEntity.F_NPC) && !s.isRemoved()) all.add(s);
        for (ShipEntity s : all) scrap(l, s);
    }

    private static void clearBox(ServerLevel l, double x, double z, double r) {
        AABB box = new AABB(x - r, 0, z - r, x + r, 256, z + r);
        for (Entity e : l.getEntitiesOfClass(Entity.class, box, e -> e.getTags().contains(LivingSea.CREW) || e.getTags().contains(LivingSea.SALVAGE))) e.discard();
    }

    private static BlockPos stand(ServerLevel l, double x, double z, int r) {
        return NotableSpawner.findStand(l, (int) Math.floor(x), (int) Math.floor(z), r);
    }

    private static void put(FakePlayer p, BlockPos b) {
        p.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
    }

    private static void onDeck(FakePlayer p, ShipEntity s) {
        Vec3 deck = s.toWorld(0, s.cls.freeboard + 0.05, 1);
        p.moveTo(deck.x, deck.y, deck.z);
    }

    private static ShipEntity ownShip(ServerLevel l, FakePlayer owner, ShipClass cls, Vec3 pos, float yaw) {
        ShipEntity s = ModEntities.SHIPS.get(cls).get().create(l);
        s.moveTo(pos.x, WorldPlan.SEA_LEVEL + 0.9, pos.z, yaw, 0);
        s.setHull(s.cls.hull);
        s.owner = owner.getUUID();
        s.ownerName = Characters.get(owner).name;
        s.setShipName("Selftest " + owner.getName().getString());
        l.addFreshEntity(s);
        return s;
    }

    /** Open water beyond an island's waters, out along its dock (where the ferries lie). */
    private static Vec3 anchorage(Island i) {
        Settlement st = i.settlement();
        double ux = st.dockDirX / 1000.0, uz = st.dockDirZ / 1000.0, ul = Math.max(1e-6, Math.hypot(ux, uz));
        double out = i.radius * 1.25 + 40;
        return new Vec3(i.x + ux / ul * out, WorldPlan.SEA_LEVEL + 0.9, i.z + uz / ul * out);
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object[] box, int i) {
        return (T) box[i];
    }

    // ------------------------------------------------------------------ choosing the test jobs

    /** Board islands whose job of a kind has the shortest route (origin dock to destination dock), same sea. */
    private static String bestJob(MinecraftServer s, String kind) {
        WorldPlan plan = WorldPlan.of(s);
        String best = null;
        double bd = Double.MAX_VALUE;
        for (Island i : plan.islands) {
            String id = "job_" + kind + "_" + i.id;
            QuestDef q = QuestRegistry.get(id);
            JobBoard.StartGift g = JobBoard.gift(id);
            if (q == null || g == null) continue;
            Island dest = plan.byId(g.target());
            if (dest == null || dest.zone != i.zone || !i.settlement().hasDock || !dest.settlement().hasDock || !LivingSea.openZone(i.zone)) continue;
            double d = Ferries.dockOf(i).distanceTo(Ferries.dockOf(dest));
            if (d < 250) continue;
            if (d < bd) {
                bd = d;
                best = id;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ phase 1

    static void phase1(Deque<SelfTest.Step> steps) {
        generation(steps);
        escort(steps);
        sink(steps);
        convoy(steps);
        steps.add(s -> {
            SeaJobs.tap = null;
            SeaJobs.keepShips = false;
            release(s.overworld());
            return 0;
        });
    }

    // ---------- the board: jobs generated, variants per faction, translations ----------
    private static void generation(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            listen();
            SeaJobs.keepShips = true;
            IslandStories.ensure(s);
            int sink = 0, convoy = 0, escort = 0, badSpec = 0;
            for (QuestDef q : QuestRegistry.all()) {
                if (!q.giverRole().equals(JobBoard.GIVER) || !q.id().startsWith("job_")) continue;
                JobBoard.StartGift g = JobBoard.gift(q.id());
                String type = q.id().substring(4, q.id().indexOf('_', 4));
                if (!List.of("sink", "convoy", "escort").contains(type)) continue;
                if (QuestRegistry.variantFaction(q.id()) == null) {
                    if (type.equals("sink")) sink++;
                    if (type.equals("convoy")) convoy++;
                    if (type.equals("escort")) escort++;
                }
                if (g == null || !g.kind().equals(type) || q.objectives().stream().noneMatch(o -> o.type().equals(type) && SeaJobs.progresses(o.type(), o.target()))) badSpec++;
            }
            SelfTest.check("sea jobs: notice boards post named pirate ships, convoys and follower escorts", sink > 40 && convoy > 40 && escort > 40 && badSpec == 0,
                    sink + " sink jobs, " + convoy + " convoys, " + escort + " escorts; " + badSpec + " without a sea objective or job spec");
            // faction versions and pay
            ServerLevel l = s.overworld();
            String sinkId = bestJob(s, "sink") != null ? bestJob(s, "sink") : null;
            String someSink = null;
            for (QuestDef q : QuestRegistry.all()) if (q.id().startsWith("job_sink_") && QuestRegistry.variantFaction(q.id()) == null) { someSink = q.id(); break; }
            if (sinkId == null) sinkId = someSink;
            List<String> problems = new ArrayList<>();
            if (sinkId == null) problems.add("no sink job");
            else {
                Map<Faction, String> v = new EnumMap<>(Faction.class);
                for (Faction f : List.of(Faction.CIVILIAN, Faction.MARINE, Faction.BOUNTY_HUNTER, Faction.PIRATE)) {
                    FakePlayer p = character(l, "Variant" + f.id(), f);
                    v.put(f, QuestRegistry.resolve(sinkId, Characters.get(p)));
                }
                QuestDef base = QuestRegistry.get(sinkId), mar = QuestRegistry.get(sinkId + ".marine"), bh = QuestRegistry.get(sinkId + ".bounty_hunter");
                if (!v.get(Faction.MARINE).endsWith(".marine") || !v.get(Faction.BOUNTY_HUNTER).endsWith(".bounty_hunter") || !v.get(Faction.CIVILIAN).equals(sinkId) || !v.get(Faction.PIRATE).equals(sinkId))
                    problems.add("variants " + v);
                if (base == null || mar == null || bh == null || !(mar.beli() > base.beli() && bh.beli() > mar.beli())) problems.add("pay base/marine/hunter");
                else problems.add("ok: pay " + base.beli() + " / " + mar.beli() + " / " + bh.beli());
            }
            String conv = bestJob(s, "convoy");
            QuestDef cq = conv == null ? null : QuestRegistry.get(conv), cm = conv == null ? null : QuestRegistry.get(conv + ".marine");
            if (cq == null || cm == null || cm.beli() <= cq.beli()) problems.add("convoy variants");
            // pay grows with tier and distance
            QuestDef nearSink = null, farSink = null;
            for (QuestDef q : QuestRegistry.all()) {
                if (!q.id().startsWith("job_sink_") || QuestRegistry.variantFaction(q.id()) != null) continue;
                if (nearSink == null || q.beli() < nearSink.beli()) nearSink = q;
                if (farSink == null || q.beli() > farSink.beli()) farSink = q;
            }
            boolean scaled = nearSink != null && farSink.beli() > nearSink.beli() * 3;
            SelfTest.check("sea jobs: Marine and bounty-hunter versions pay more; pay scales with tier and distance",
                    problems.size() == 1 && problems.get(0).startsWith("ok") && scaled,
                    problems + (nearSink == null ? "" : ", sink pay ranges " + nearSink.beli() + " (" + nearSink.island() + ") to " + farSink.beli() + " (" + farSink.island() + ")"));
            // the classic board's sea-battle job, per faction
            FakePlayer civ = character(l, "VariantCivilian", Faction.CIVILIAN), pir = character(l, "VariantPirate", Faction.PIRATE);
            Island any = WorldPlan.of(s).byId("shiokaze_isle");
            boolean civOffered = JobBoard.offers(any, Characters.get(civ)).stream().anyMatch(q -> q.id().equals("board_privateer"));
            boolean pirOffered = JobBoard.offers(any, Characters.get(pir)).stream().anyMatch(q -> q.id().equals("board_privateer.pirate"))
                    && JobBoard.offers(any, Characters.get(pir)).stream().noneMatch(q -> q.id().equals("board_privateer"));
            SelfTest.check("sea jobs: every board offers a sea-battle commission (pirates get the blockade run instead)", civOffered && pirOffered,
                    "civilian " + civOffered + ", pirate " + pirOffered);
            // translations of every key the sea jobs use
            String lang = resource("/assets/grandline/lang/en_us.json");
            List<String> missing = new ArrayList<>();
            for (String k : List.of("job.grandline.sink", "job.grandline.sink.desc", "job.grandline.sink.desc.marine", "job.grandline.sink.desc.bounty_hunter",
                    "job.grandline.convoy", "job.grandline.convoy.desc", "job.grandline.convoy.desc.marine", "job.grandline.escort", "job.grandline.escort.desc.follow",
                    "job.grandline.escort.meet", "job.grandline.escort.back", "job.grandline.escort.arrived", "job.grandline.escort.not_yours", "job.grandline.escort.lead_on",
                    "job.grandline.escort.clear_dock", "job.grandline.escort.thanks", "job.grandline.escort.died", "job.grandline.escort.gave_up", "job.grandline.says",
                    "job.grandline.failed", "job.grandline.abandoned", "job.grandline.list_entry", "job.grandline.list_empty", "job.grandline.no_such_job",
                    "job.grandline.sink.where", "job.grandline.sink.sighted", "job.grandline.sink.sunk", "job.grandline.sink.taken", "job.grandline.sink.prize_bonus",
                    "job.grandline.convoy.start", "job.grandline.convoy.ready", "job.grandline.convoy.back", "job.grandline.convoy.under_way", "job.grandline.convoy.waiting",
                    "job.grandline.convoy.pirates", "job.grandline.convoy.arrived", "job.grandline.convoy.sunk", "job.grandline.convoy.overrun", "job.grandline.convoy.taken",
                    "job.grandline.convoy.lost", "job.grandline.convoy.gave_up", "quest.grandline.board_privateer", "quest.grandline.board_privateer.pirate"))
                if (lang == null || !lang.contains("\"" + k + "\"")) missing.add(k);
            SelfTest.check("sea jobs: every message and title is translated", lang != null && missing.isEmpty(), lang == null ? "no language file" : missing.toString());
            return 0;
        });
    }

    private static String resource(String path) {
        try (var in = SeaJobTests.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- escort: the merchant follows, boards, sails, lands; death, abandon, log-outs ----------
    private static void escort(Deque<SelfTest.Step> steps) {
        // box: 0 job id, 1 gift, 2 origin, 3 dest, 4 player, 5 merchant, 6 ship, 7 start stand, 8 counters before, 9 beli before
        final Object[] b = new Object[12];
        final int[] waited = {0};
        final double[] num = new double[8];
        steps.add(s -> {
            ServerLevel l = s.overworld();
            String id = bestJob(s, "escort");
            if (id == null) {
                SelfTest.check("sea jobs: an escort job to test", false, "none found");
                return 0;
            }
            WorldPlan plan = WorldPlan.of(s);
            JobBoard.StartGift g = JobBoard.gift(id);
            Island origin = plan.byId(QuestRegistry.get(id).island()), dest = plan.byId(g.target());
            b[0] = id;
            b[1] = g;
            b[2] = origin;
            b[3] = dest;
            Settlement os = origin.settlement();
            loadNow(l, os.dockX, os.dockZ, 4);
            loadNow(l, os.dockTipX(), os.dockTipZ(), 3);
            Settlement ds = dest.settlement();
            loadNow(l, ds.dockX, ds.dockZ, 4);
            loadNow(l, g.x(), g.z(), 2);
            Vec3 out = anchorage(origin);
            loadNow(l, out.x, out.z, 2);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            Island origin = get(b, 2), dest = get(b, 3);
            if ((!ticking(l, origin.settlement().dockX, origin.settlement().dockZ) || !ticking(l, dest.settlement().dockX, dest.settlement().dockZ)) && ++waited[0] < 3000) return -1;
            FakePlayer p = character(l, "Escorter", Faction.CIVILIAN);
            b[4] = p;
            // stand on the quay by the dock root
            Settlement os = origin.settlement();
            BlockPos at = stand(l, os.dockX - os.dockDirX * 6 / 1000.0, os.dockZ - os.dockDirZ * 6 / 1000.0, 12);
            if (at == null) {
                SelfTest.check("sea jobs: taking an escort job brings the merchant to your side", false, "no footing by " + origin.id + "'s dock");
                b[0] = null;
                return 0;
            }
            b[7] = at;
            put(p, at);
            b[8] = SeaJobs.counters();
            String id = get(b, 0);
            boolean started = Quests.start(p, id);
            JobBoard.StartGift g = get(b, 1);
            SeaJobs.Escort e = SeaJobs.escortOf(s, Characters.get(p).characterId, id);
            HumanoidNpc m = e == null ? null : SeaJobs.merchantOf(s, e);
            b[5] = m;
            boolean named = m != null && m.getCustomName() != null && m.getCustomName().getString().equals(g.name());
            SelfTest.check("sea jobs: taking an escort job brings the merchant to your side", started && m != null && named && m.distanceTo(p) < 5 && got(p, "job.grandline.escort.meet"),
                    id + " (" + origin.id + " -> " + dest.id + ", " + (int) Ferries.dockOf(origin).distanceTo(Ferries.dockOf(dest)) + " blocks): merchant "
                            + (m == null ? "none" : m.getCustomName().getString() + " " + String.format("%.1f", m.distanceTo(p)) + " blocks away, role " + m.role()));
            if (m == null) b[0] = null;
            // walk: a spot about 10 blocks off, level and dry, along the island
            BlockPos walk = null;
            for (int k = 0; k < 16 && walk == null; k++) {
                double a = k * Math.PI / 8;
                BlockPos c = stand(l, at.getX() + Math.cos(a) * 11, at.getZ() + Math.sin(a) * 11, 2);
                if (c != null && Math.abs(c.getY() - at.getY()) <= 1 && c.distSqr(at) > 8 * 8) walk = c;
            }
            b[9] = walk;
            if (walk != null) put(p, walk);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (b[0] == null) return 0;
            FakePlayer p = get(b, 4);
            HumanoidNpc m = get(b, 5);
            BlockPos walk = get(b, 9);
            if (walk == null) {
                SelfTest.check("sea jobs: the escorted merchant walks after you", false, "no level spot near the dock to walk to");
                return 0;
            }
            if (waited[0] % 10 == 0) SeaJobs.tickPlayer(p);
            if (m.distanceTo(p) > 3.5 && ++waited[0] < 400) return -1;
            int[] before = get(b, 8), now = SeaJobs.counters();
            SelfTest.check("sea jobs: the escorted merchant walks after you", m.distanceTo(p) <= 3.5 && now[0] == before[0] && m.isAlive(),
                    String.format("%.1f blocks after %d ticks (%d teleports)", m.distanceTo(p), waited[0], now[0] - before[0]));
            return 0;
        });
        // left far behind on land: the merchant teleports to you
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            FakePlayer p = get(b, 4);
            HumanoidNpc m = get(b, 5);
            BlockPos at = get(b, 7);
            Island origin = get(b, 2);
            BlockPos far = null;
            for (int k = 0; k < 16 && far == null; k++) {
                double a = Math.atan2(origin.z - at.getZ(), origin.x - at.getX()) + (k % 2 == 0 ? 1 : -1) * (k / 2) * 0.3;
                BlockPos c = stand(l, at.getX() + Math.cos(a) * 40, at.getZ() + Math.sin(a) * 40, 4);
                if (c != null && c.distSqr(at) > 30 * 30 && l.isPositionEntityTicking(c)) far = c;
            }
            if (far == null) {
                SelfTest.check("sea jobs: left far behind on land, the merchant catches up at once", false, "no footing 40 blocks inland");
                return 0;
            }
            int[] before = SeaJobs.counters();
            put(p, far);
            double was = m.distanceTo(p);
            SeaJobs.tickPlayer(p);
            int[] now = SeaJobs.counters();
            SelfTest.check("sea jobs: left far behind on land, the merchant catches up at once", now[0] == before[0] + 1 && m.distanceTo(p) < 6,
                    String.format("%.0f blocks -> %.1f blocks, %d teleport", was, m.distanceTo(p), now[0] - before[0]));
            put(p, at); // back by the dock: the merchant teleports again (or walks)
            SeaJobs.tickPlayer(p);
            waited[0] = 0;
            num[0] = 0;
            return 1;
        });
        // the player swims off: the merchant stays out of the sea
        steps.add(s -> {
            if (b[0] == null) return 0;
            FakePlayer p = get(b, 4);
            HumanoidNpc m = get(b, 5);
            Island origin = get(b, 2);
            Settlement os = origin.settlement();
            if (waited[0] == 0) {
                double ux = os.dockDirX / 1000.0, uz = os.dockDirZ / 1000.0;
                // swimming off the end of the dock
                p.moveTo(os.dockTipX() + ux * 9, WorldPlan.SEA_LEVEL - 0.5, os.dockTipZ() + uz * 9);
            }
            if (waited[0] % 10 == 0) SeaJobs.tickPlayer(p);
            if (m.isInWater()) num[0]++;
            if (++waited[0] < 160) return -1;
            SelfTest.check("sea jobs: the merchant never follows you into the sea", num[0] == 0 && !m.isPassenger() && m.isAlive(),
                    String.format("%d of %d ticks in the water; %.1f blocks from the swimmer", (int) num[0], waited[0], m.distanceTo(p)));
            return 0;
        });
        // the player goes aboard their own ship by the dock: the merchant takes a seat
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            FakePlayer p = get(b, 4);
            HumanoidNpc m = get(b, 5);
            Island origin = get(b, 2), dest = get(b, 3);
            Settlement os = origin.settlement();
            BlockPos tip = stand(l, os.dockTipX(), os.dockTipZ(), 6);
            put(p, tip != null ? tip : get(b, 7));
            SeaJobs.tickPlayer(p); // out along the dock: the merchant comes along
            Vec3 water = Shipyard.findWaterPublic(l, new BlockPos(os.dockTipX(), WorldPlan.SEA_LEVEL, os.dockTipZ()));
            if (water == null) {
                SelfTest.check("sea jobs: the merchant boards your ship as a passenger", false, "no water by " + origin.id + "'s dock");
                b[0] = null;
                return 0;
            }
            Vec3 to = Ferries.dockOf(dest);
            ShipEntity ship = ownShip(l, p, ShipClass.CARAVEL, water, (float) Math.toDegrees(Math.atan2(-(to.x - water.x), to.z - water.z)));
            b[6] = ship;
            return 2;
        });
        steps.add(s -> {
            if (b[0] == null) return 0;
            FakePlayer p = get(b, 4);
            HumanoidNpc m = get(b, 5);
            ShipEntity ship = get(b, 6);
            int[] before = SeaJobs.counters();
            double was = m.distanceTo(p);
            onDeck(p, ship);
            SeaJobs.tickPlayer(p);
            int[] now = SeaJobs.counters();
            SelfTest.check("sea jobs: the merchant boards your ship as a passenger", m.getVehicle() == ship && now[1] == before[1] + 1 && ship.seatIndex(m) > ship.cls.cannons,
                    String.format("%.0f blocks from the deck; vehicle %s, seat %d", was, m.getVehicle() == null ? "none" : m.getVehicle().getName().getString(), ship.seatIndex(m)));
            // the test cannot helm her out of a crowded harbour: she is towed out to open water beyond the island's waters
            // (where the ferries lie), passenger and all, and sails from there
            ServerLevel l = s.overworld();
            Island origin = get(b, 2);
            Vec3 out = anchorage(origin);
            Vec3 w = LivingSea.openWater(l, out.x, out.z, ship.cls.length / 2 + 2);
            if (w == null) w = Shipyard.findWaterPublic(l, BlockPos.containing(out.x, WorldPlan.SEA_LEVEL, out.z));
            if (w != null) ship.moveTo(w.x, WorldPlan.SEA_LEVEL + 0.9, w.z, ship.getYRot(), 0);
            onDeck(p, ship);
            num[5] = now[1];
            ship.autopilot = true;
            ship.testSpeedMult = 4f;
            ship.setAnchored(false);
            ship.setSail(3);
            waited[0] = 0;
            num[1] = 0; // ticks with the merchant seated
            num[2] = 0; // ticks sailed
            num[3] = 0; // ticks in the destination's waters
            return 1;
        });
        // sail to the destination with the merchant seated all the way
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            FakePlayer p = get(b, 4);
            HumanoidNpc m = get(b, 5);
            ShipEntity ship = get(b, 6);
            Island dest = get(b, 3);
            keep(l, ship);
            Vec3 to = Ferries.dockOf(dest);
            if (waited[0] % 10 == 0) SeaJobs.tickPlayer(p);
            Ferries.helm(ship, l, to, dest.id);
            ship.setSail(3);
            onDeck(p, ship);
            num[2]++;
            if (m.getVehicle() == ship) num[1]++;
            double d = Math.hypot(to.x - ship.getX(), to.z - ship.getZ());
            if (dest.distance(ship.getBlockX(), ship.getBlockZ()) < dest.radius * 1.25 + 40) num[3]++;
            if (d > 45 && num[3] < 600 && m.isAlive() && ++waited[0] < 9000) return -1;
            num[4] = d;
            SelfTest.check("sea jobs: the merchant stays aboard the whole voyage to the destination", d <= 45 || num[3] >= 600 ? num[1] >= num[2] * 0.97 : false,
                    String.format("%d ticks under sail, merchant seated %d of them (%d boardings after the first); %.0f blocks from %s's dock point at the end",
                            (int) num[2], (int) num[1], SeaJobs.counters()[1] - (int) num[5], d, dest.id));
            num[6] = d <= 45 || num[3] >= 600 ? 0 : 1; // the landing needs the destination
            return 0;
        });
        // ashore at the destination: the merchant steps off beside you; the ambush; talk to be paid
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            FakePlayer p = get(b, 4);
            HumanoidNpc m = get(b, 5);
            Island dest = get(b, 3);
            JobBoard.StartGift g = get(b, 1);
            Settlement ds = dest.settlement();
            BlockPos quay = stand(l, ds.dockX - ds.dockDirX * 4 / 1000.0, ds.dockZ - ds.dockDirZ * 4 / 1000.0, 12);
            if (num[6] == 1) { // never got there: drop the job and go on with the other escort checks at home
                SelfTest.check("sea jobs: ashore at the destination, the merchant steps off beside you", false, "the voyage did not reach " + dest.id);
                ((ShipEntity) get(b, 6)).discard();
                put(p, get(b, 7));
                SeaJobs.abandon(p, get(b, 0));
                return 0;
            }
            if (quay == null) {
                SelfTest.check("sea jobs: ashore at the destination, the merchant steps off beside you", false, "no footing at " + dest.id + "'s dock");
                return 0;
            }
            int[] before = SeaJobs.counters();
            put(p, quay);
            SeaJobs.tickPlayer(p);
            int[] now = SeaJobs.counters();
            SeaJobs.Escort e = SeaJobs.escortOf(s, Characters.get(p).characterId, get(b, 0));
            SelfTest.check("sea jobs: ashore at the destination, the merchant steps off beside you", m.getVehicle() == null && m.distanceTo(p) < 6 && now[2] == before[2] + 1
                            && !m.isInWater() && e != null && e.arrived && got(p, "job.grandline.escort.arrived"),
                    String.format("%.1f blocks from you, vehicle %s, at the dock %s", m.distanceTo(p), m.getVehicle(), e != null && e.arrived));
            // talking before the dock is safe does not finish it
            ((ShipEntity) get(b, 6)).discard();
            com.grandline.dialogue.Dialogues.open(p, m);
            boolean early = got(p, "job.grandline.escort.clear_dock") && Characters.get(p).questProgress.containsKey(get(b, 0));
            // the ambush: pirates on the dock, put down (the last one may complete the job: the merchant was already spoken to)
            CharacterData d = Characters.get(p);
            long beli = d.beli;
            StorySites.tickPlayer(p);
            int killed = 0;
            for (SiteRef r : IslandStories.sitesOf(get(b, 0))) {
                Site site = IslandStories.site(r.site());
                if (site == null) continue;
                for (LivingEntity foe : StorySites.foes(l, site)) {
                    foe.hurt(p.damageSources().playerAttack(p), 1_000_000f);
                    if (!foe.isAlive()) killed++;
                }
            }
            QuestDef q = QuestRegistry.get(get(b, 0));
            com.grandline.dialogue.Dialogues.open(p, m);
            boolean done = !d.questProgress.containsKey(q.id()) && d.beli - beli >= q.beli();
            SelfTest.check("sea jobs: with the ambush beaten, talking to the merchant on the dock pays the escort", early && killed >= 3 && done && got(p, "job.grandline.escort.thanks")
                            && !m.getTags().contains("gl_escort") && SeaJobs.escortOf(s, d.characterId, q.id()) == null,
                    "talked early: told to wait " + early + "; " + killed + " ambushers beaten; paid " + (d.beli - beli) + " of " + q.beli() + " Beli; " + g.name() + " retired " + !m.getTags().contains("gl_escort"));
            return 0;
        });
        // the merchant killed: the job fails
        steps.add(s -> {
            if (b[0] == null) return 0;
            FakePlayer p = get(b, 4);
            String id = get(b, 0);
            CharacterData d = Characters.get(p);
            INBOX.clear();
            boolean started = Quests.start(p, id);
            SeaJobs.Escort e = SeaJobs.escortOf(s, d.characterId, id);
            HumanoidNpc m = e == null ? null : SeaJobs.merchantOf(s, e);
            if (m != null) m.hurt(m.damageSources().generic(), 1_000_000f);
            boolean failed = !d.questProgress.containsKey(id) && SeaJobs.escortOf(s, d.characterId, id) == null;
            SelfTest.check("sea jobs: if the merchant dies the escort fails, with a message", started && m != null && !m.isAlive() && failed
                            && got(p, "job.grandline.escort.died") && got(p, "job.grandline.failed"),
                    "started " + started + ", merchant " + (m == null ? "none" : "alive=" + m.isAlive()) + ", job dropped " + failed + ", messages " + INBOX.get(p.getUUID()));
            return 0;
        });
        // abandoned with /jobs abandon: the merchant goes
        steps.add(s -> {
            if (b[0] == null) return 0;
            FakePlayer p = get(b, 4);
            String id = get(b, 0);
            CharacterData d = Characters.get(p);
            boolean started = Quests.start(p, id);
            SeaJobs.Escort e = SeaJobs.escortOf(s, d.characterId, id);
            HumanoidNpc m = e == null ? null : SeaJobs.merchantOf(s, e);
            boolean ok = SeaJobs.abandon(p, id);
            SelfTest.check("sea jobs: /jobs abandon ends an escort cleanly", started && m != null && ok && m.isRemoved() && !d.questProgress.containsKey(id)
                            && SeaJobs.escortOf(s, d.characterId, id) == null && got(p, "job.grandline.abandoned") && !SeaJobs.abandon(p, id),
                    "started " + started + ", merchant removed " + (m != null && m.isRemoved()) + ", job dropped " + !d.questProgress.containsKey(id));
            return 0;
        });
        // logged out: stowed; back soon -> the merchant returns; away too long -> the job lapses
        steps.add(s -> {
            if (b[0] == null) return 0;
            FakePlayer p = get(b, 4);
            String id = get(b, 0);
            CharacterData d = Characters.get(p);
            Quests.start(p, id);
            SeaJobs.Escort e = SeaJobs.escortOf(s, d.characterId, id);
            HumanoidNpc m = e == null ? null : SeaJobs.merchantOf(s, e);
            SeaJobs.loggedOut(p);
            boolean stowed = e != null && e.stowed() && m != null && m.isRemoved();
            SeaJobs.tickPlayer(p);
            HumanoidNpc back = e == null ? null : SeaJobs.merchantOf(s, e);
            boolean returned = back != null && back.distanceTo(p) < 6 && back.getCustomName() != null && m != null && back.getCustomName().getString().equals(m.getCustomName().getString())
                    && back.getTags().contains("gl_escort") && d.questProgress.containsKey(id) && got(p, "job.grandline.escort.back");
            SelfTest.check("sea jobs: an escort survives a log-out (the merchant is stowed and steps off beside you again)", stowed && returned,
                    "stowed " + stowed + ", back " + (back == null ? "no" : String.format("%.1f blocks away as %s", back.distanceTo(p), back.getCustomName().getString())));
            SeaJobs.loggedOut(p);
            e.logoutAt -= SeaJobs.abandonTicks + 20; // as if away for more than ten minutes of server time
            SeaJobs.tickPlayer(p);
            SelfTest.check("sea jobs: away too long, the escort lapses cleanly", !d.questProgress.containsKey(id) && SeaJobs.escortOf(s, d.characterId, id) == null
                            && SeaJobs.merchantOf(s, e) == null && got(p, "job.grandline.escort.gave_up"),
                    "job dropped " + !d.questProgress.containsKey(id) + ", record " + SeaJobs.escortOf(s, d.characterId, id));
            return 0;
        });
    }

    // ---------- sink the named pirate ship ----------
    private static void sink(Deque<SelfTest.Step> steps) {
        final Object[] b = new Object[8];
        final int[] waited = {0};
        final long[] num = new long[4];
        steps.add(s -> {
            ServerLevel l = s.overworld();
            String id = null;
            WorldPlan plan = WorldPlan.of(s);
            for (Island i : plan.islands) { // a Blue Sea job: small ships, quick to generate
                String k = "job_sink_" + i.id;
                if (JobBoard.gift(k) != null && i.zone.isBlue()) {
                    id = k;
                    break;
                }
            }
            if (id == null) {
                SelfTest.check("sea jobs: a named pirate ship to hunt", false, "no sink job in the Blues");
                return 0;
            }
            JobBoard.StartGift g = JobBoard.gift(id);
            b[0] = id;
            b[1] = g;
            loadNow(l, g.x(), g.z(), 7);
            clearNpcShips(l);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            JobBoard.StartGift g = get(b, 1);
            if ((!ticking(l, g.x() + 90, g.z()) || !ticking(l, g.x() - 90, g.z() + 90)) && ++waited[0] < 3000) return -1;
            FakePlayer p = character(l, "ShipHunter", Faction.CIVILIAN);
            b[2] = p;
            String id = get(b, 0);
            boolean started = Quests.start(p, id);
            // far away: nothing
            SeaJobs.resetSinkTimers();
            int[] c0 = SeaJobs.counters();
            p.moveTo(g.x() + 600, WorldPlan.SEA_LEVEL - 0.5, g.z());
            SeaJobs.tickPlayer(p);
            boolean farNone = SeaJobs.namedShip(l, id) == null && SeaJobs.counters()[3] == c0[3];
            // within range but the NPC ship cap is full: refused
            p.moveTo(g.x() + 70.5, WorldPlan.SEA_LEVEL - 0.5, g.z() + 0.5);
            List<ShipEntity> fillers = new ArrayList<>();
            int cap = SeaEncounters.cap(l.players().size());
            for (int k = 0; SeaEncounters.countNpcShips(l) < cap && k < 16; k++) {
                Vec3 w = LivingSea.openWater(l, g.x() - 80 + (k % 4) * 40, g.z() - 80 + (k / 4) * 45, 8);
                if (w == null) continue;
                ShipEntity f = SeaEncounters.spawnShip(l, w, 0, ShipClass.SLOOP, LivingSea.MARINE, 1, "Selftest Filler");
                if (f == null) continue;
                f.npcAiOff = true;
                f.noDespawn = true;
                fillers.add(f);
            }
            boolean full = SeaEncounters.countNpcShips(l) >= cap;
            SeaJobs.resetSinkTimers();
            SeaJobs.tickPlayer(p);
            int[] c1 = SeaJobs.counters();
            boolean capRefused = full && SeaJobs.namedShip(l, id) == null && c1[4] > c0[4];
            for (ShipEntity f : fillers) scrap(l, f);
            // room again: she is put to sea near the hunter
            SeaJobs.resetSinkTimers();
            SeaJobs.tickPlayer(p);
            ShipEntity ship = SeaJobs.namedShip(l, id);
            b[3] = ship;
            int sim = s.getPlayerList().getSimulationDistance();
            double dist = ship == null ? -1 : Math.sqrt(ship.distanceToSqr(p.getX(), ship.getY(), p.getZ()));
            SelfTest.check("sea jobs: the named pirate ship is put to sea only near where she was seen and only with room under the NPC ship cap",
                    started && farNone && capRefused && ship != null && ship.shipName().equals(g.name()) && ship.npcFaction.equals(LivingSea.PIRATE) && ship.flag(ShipEntity.F_NPC)
                            && dist > 30 && dist < Math.max(48, (sim - 1) * 16) + 20 && got(p, "job.grandline.sink.sighted"),
                    String.format("job %s; 600 blocks off: none %s; cap %d full %s: refused %s; then %s %s, %.0f blocks from the hunter (simulation distance %d chunks)", id, farNone, cap, full,
                            capRefused, ship == null ? "no ship" : "the " + ship.shipName(), ship == null ? "" : ship.cls.id() + " with " + NavalCombat.npcCrew(ship).size() + " crew", dist, sim));
            if (ship == null) b[0] = null;
            return 0;
        });
        // sink her: the job pays
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            FakePlayer p = get(b, 2);
            ShipEntity ship = get(b, 3);
            String id = get(b, 0);
            CharacterData d = Characters.get(p);
            QuestDef q = QuestRegistry.get(id);
            long beli = d.beli;
            SeaJobs.tickPlayer(p);
            boolean single = SeaJobs.namedShip(l, id) == ship; // no second copy while she is afloat
            ship.hurtHull(ship.hull() + 1, p);
            boolean done = !d.questProgress.containsKey(id) && d.beli - beli >= q.beli();
            SelfTest.check("sea jobs: sinking the named pirate ship completes the job and pays", single && ship.isSinking() && done && got(p, "job.grandline.sink.sunk"),
                    "one ship " + single + ", sinking " + ship.isSinking() + ", paid " + (d.beli - beli) + " of " + q.beli() + " Beli (and the Marines' standing reward)");
            return 0;
        });
        // a Marine takes her as a prize instead: the bonus
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            JobBoard.StartGift g = get(b, 1);
            FakePlayer m = character(l, "MarineHunter", Faction.MARINE);
            b[4] = m;
            String id = QuestRegistry.resolve(get(b, 0), Characters.get(m));
            b[5] = id;
            boolean started = Quests.start(m, id);
            m.moveTo(g.x() - 60.5, WorldPlan.SEA_LEVEL - 0.5, g.z() + 40.5);
            SeaJobs.resetSinkTimers();
            SeaJobs.tickPlayer(m);
            ShipEntity ship = SeaJobs.namedShip(l, get(b, 0));
            b[6] = ship;
            if (ship == null) {
                SelfTest.check("sea jobs: a Marine who takes the named ship as a prize is paid a bonus", false, "started " + started + "; she was not put to sea again");
                return 0;
            }
            for (UUID u : new ArrayList<>(NavalCombat.npcCrew(ship))) if (l.getEntity(u) instanceof GLNpc c) c.hurt(m.damageSources().playerAttack(m), 1_000_000f);
            waited[0] = 0;
            return 25; // the crew-beaten check runs once a second
        });
        steps.add(s -> {
            if (b[6] == null) return 0;
            ServerLevel l = s.overworld();
            FakePlayer m = get(b, 4);
            ShipEntity ship = get(b, 6);
            String id = get(b, 5);
            CharacterData d = Characters.get(m);
            QuestDef q = QuestRegistry.get(id);
            long beli = d.beli;
            boolean took = NavalCombat.capture(ship, m);
            long paid = d.beli - beli;
            SelfTest.check("sea jobs: a Marine who takes the named ship as a prize is paid a bonus", id.endsWith(".marine") && took && ship.flag(ShipEntity.F_PRIZE)
                            && !d.questProgress.containsKey(id) && paid >= q.beli() + q.beli() / 2 && got(m, "job.grandline.sink.prize_bonus") && got(m, "job.grandline.sink.taken"),
                    id + ": taken " + took + ", paid " + paid + " Beli (job " + q.beli() + " + bonus " + q.beli() / 2 + ")");
            ShipRegistry.get(s).remove(ship.shipId);
            scrap(l, ship);
            return 0;
        });
        // any sea battle counts for "sea_battle" objectives: the Privateer's Commission
        steps.add(s -> {
            if (b[1] == null) return 0;
            ServerLevel l = s.overworld();
            JobBoard.StartGift g = get(b, 1);
            FakePlayer p = character(l, "Privateer", Faction.CIVILIAN);
            CharacterData d = Characters.get(p);
            boolean started = Quests.start(p, "board_privateer");
            p.moveTo(g.x() + 0.5, WorldPlan.SEA_LEVEL - 0.5, g.z() + 60.5);
            Vec3 w = LivingSea.openWater(l, g.x() + 40, g.z() - 40, 8);
            ShipEntity pirate = w == null ? null : SeaEncounters.spawnShip(l, w, 0, ShipClass.SLOOP, LivingSea.PIRATE, 1, "Selftest Sea Rat");
            if (pirate == null) {
                SelfTest.check("sea jobs: beating a pirate ship counts for sea-battle objectives (Privateer's Commission)", false, "no pirate ship");
                return 0;
            }
            pirate.noDespawn = true;
            long beli = d.beli;
            LivingSea.attackedByPlayer(pirate, p, p);
            pirate.hurtHull(pirate.hull() + 1, p);
            QuestDef q = QuestRegistry.get("board_privateer");
            SelfTest.check("sea jobs: beating a pirate ship counts for sea-battle objectives (Privateer's Commission)",
                    started && !d.questProgress.containsKey("board_privateer") && d.beli - beli >= q.beli(),
                    "started " + started + ", paid " + (d.beli - beli) + " Beli (commission " + q.beli() + ")");
            return 200; // let the wrecks founder
        });
        steps.add(s -> {
            if (b[1] == null) return 0;
            JobBoard.StartGift g = get(b, 1);
            clearBox(s.overworld(), g.x(), g.z(), 260);
            return 0;
        });
    }

    // ---------- convoy ----------
    private static void convoy(Deque<SelfTest.Step> steps) {
        // 0 job, 1 gift, 2 origin, 3 dest, 4 player, 5 merchant ship, 6 escort ship, 7 raider
        final Object[] b = new Object[10];
        final int[] waited = {0};
        final double[] num = new double[10];
        steps.add(s -> {
            ServerLevel l = s.overworld();
            String id = bestJob(s, "convoy");
            if (id == null) {
                SelfTest.check("sea jobs: a convoy job to test", false, "none found");
                return 0;
            }
            WorldPlan plan = WorldPlan.of(s);
            JobBoard.StartGift g = JobBoard.gift(id);
            Island origin = plan.byId(QuestRegistry.get(id).island()), dest = plan.byId(g.target());
            b[0] = id;
            b[1] = g;
            b[2] = origin;
            b[3] = dest;
            Vec3 od = anchorage(origin);
            loadNow(l, od.x, od.z, 5);
            loadNow(l, origin.settlement().dockX, origin.settlement().dockZ, 2);
            loadNow(l, g.x(), g.z(), 4);
            clearNpcShips(l);
            waited[0] = 0;
            return 1;
        });
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            Island origin = get(b, 2), dest = get(b, 3);
            Vec3 od = anchorage(origin);
            if ((!ticking(l, od.x, od.z) || !ticking(l, origin.settlement().dockX, origin.settlement().dockZ)) && ++waited[0] < 3000) return -1;
            FakePlayer p = character(l, "ConvoyCaptain", Faction.CIVILIAN);
            b[4] = p;
            Settlement os = origin.settlement();
            BlockPos quay = stand(l, os.dockX, os.dockZ, 12);
            if (quay != null) put(p, quay);
            else p.moveTo(od.x, WorldPlan.SEA_LEVEL + 2, od.z);
            String id = get(b, 0);
            boolean started = Quests.start(p, id); // taken at the board
            p.moveTo(od.x + 30, WorldPlan.SEA_LEVEL - 0.5, od.z); // then out to where she lies
            SeaJobs.tickPlayer(p);
            SeaJobs.Convoy c = SeaJobs.convoyOf(s, Characters.get(p).characterId, id);
            ShipEntity ship = c == null ? null : SeaJobs.shipOf(s, c);
            b[5] = ship;
            JobBoard.StartGift g = get(b, 1);
            SelfTest.check("sea jobs: a convoy's merchantman waits off the dock for her escort", started && ship != null && ship.npcFaction.equals(LivingSea.MERCHANT)
                            && ship.shipName().equals(g.name()) && c.stage == 1 && ship.anchored() && got(p, "job.grandline.convoy.ready"),
                    id + " (" + origin.id + " -> " + dest.id + ", " + (c == null ? 0 : (int) c.total) + " blocks): " + (ship == null ? "no ship" : "the " + ship.shipName() + " ("
                            + ship.cls.id() + ") " + String.format("%.0f", ship.position().distanceTo(od)) + " blocks off the anchorage beyond the harbour, stage " + c.stage));
            if (ship == null) {
                b[0] = null;
                return 0;
            }
            num[0] = ship.getX();
            num[1] = ship.getZ();
            return 60;
        });
        // the player comes alongside in their own ship: she weighs anchor
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            FakePlayer p = get(b, 4);
            ShipEntity ship = get(b, 5);
            double moved = Math.hypot(ship.getX() - num[0], ship.getZ() - num[1]);
            Vec3 side = ship.toWorld(26, 0, 0);
            ShipEntity esc = ownShip(l, p, ShipClass.SLOOP, side, ship.getYRot());
            b[6] = esc;
            return 2;
        });
        steps.add(s -> {
            if (b[0] == null) return 0;
            FakePlayer p = get(b, 4);
            ShipEntity ship = get(b, 5), esc = get(b, 6);
            double moved = Math.hypot(ship.getX() - num[0], ship.getZ() - num[1]);
            onDeck(p, esc);
            SeaJobs.tickPlayer(p);
            SeaJobs.Convoy c = SeaJobs.convoyOf(s, Characters.get(p).characterId, get(b, 0));
            SelfTest.check("sea jobs: with the player's ship alongside she weighs anchor", moved < 2 && c != null && c.stage == 2 && got(p, "job.grandline.convoy.under_way"),
                    String.format("moved %.1f blocks while waiting alone; stage %d", moved, c == null ? -1 : c.stage));
            ship.testSpeedMult = 3f;
            waited[0] = 0;
            num[2] = -1; // remaining share of the way when the pirates came
            num[3] = 0; // ticks the raider had a target
            num[4] = -1;
            num[5] = ship.hull(); // lowest hull on the way
            return 1;
        });
        // under way: pirates partway, beaten off; she makes port
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            FakePlayer p = get(b, 4);
            ShipEntity ship = get(b, 5), esc = get(b, 6);
            String id = get(b, 0);
            JobBoard.StartGift g = get(b, 1);
            CharacterData d = Characters.get(p);
            SeaJobs.Convoy c = SeaJobs.convoyOf(s, d.characterId, id);
            if (c == null || ship.isRemoved()) { // done (or lost)
                QuestDef q = QuestRegistry.get(id);
                boolean paid = !d.questProgress.containsKey(id) && got(p, "job.grandline.convoy.arrived");
                SelfTest.check("sea jobs: pirates fall on the convoy partway and target her or her escort",
                        num[2] > 0.3 && num[2] < 0.8 && num[3] > 0 && b[7] != null,
                        String.format("pirates came with %.0f%% of the way left; raider %s, had a target for %d ticks (merchant or escort: %s)", num[2] * 100,
                                b[7] == null ? "none" : ((ShipEntity) b[7]).shipName(), (int) num[3], num[4] > 0));
                SelfTest.check("sea jobs: the convoy makes port and the job pays", paid && got(p, "job.grandline.convoy.pirates"),
                        String.format("after %d ticks; arrived %s, job done %s; reward %d Beli; her hull fell to %.0f on the way; messages %s", waited[0],
                                got(p, "job.grandline.convoy.arrived"), !d.questProgress.containsKey(id), q.beli(), num[5], INBOX.get(p.getUUID())));
                return 0;
            }
            keep(l, ship);
            num[5] = Math.min(num[5], ship.hull());
            Island dest = get(b, 3);
            if (dest.distance(ship.getBlockX(), ship.getBlockZ()) < dest.radius * 1.25 + 120) ship.testSpeedMult = 1f; // the harbour at her own pace
            if (waited[0] % 10 == 0) SeaJobs.tickPlayer(p);
            // keep station on her beam
            Vec3 side = ship.toWorld(26, 0, -4);
            esc.moveTo(side.x, WorldPlan.SEA_LEVEL + 0.9, side.z, ship.getYRot(), 0);
            keep(l, esc); // her chunk must tick too (her deck moves with her only when she ticks)
            onDeck(p, esc);
            if (c.raider != null && b[7] == null && l.getEntity(c.raider) instanceof ShipEntity r) {
                b[7] = r;
                num[2] = Math.hypot(ship.getX() - g.x(), ship.getZ() - g.z()) / Math.max(1, c.total);
            }
            ShipEntity r = get(b, 7);
            if (r != null && !r.isRemoved() && !r.isSinking()) {
                int t = LivingSea.targetOf(r);
                if (t >= 0) {
                    num[3]++;
                    if (t == ship.getId() || t == esc.getId()) num[4] = 1;
                }
                if (num[3] > 40) r.hurtHull(r.hull() + 1, p); // beaten off by the escort
            }
            if (++waited[0] < 12000) return -1;
            SelfTest.check("sea jobs: the convoy makes port and the job pays", false, String.format("timed out %.0f blocks from port", Math.hypot(ship.getX() - g.x(), ship.getZ() - g.z())));
            return 0;
        });
        // rebuilt from her saved state when her ship is gone (an unloaded chunk, a restart); then sunk: the job fails
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            FakePlayer p = get(b, 4);
            String id = get(b, 0);
            Island origin = get(b, 2);
            CharacterData d = Characters.get(p);
            INBOX.clear();
            Vec3 od = anchorage(origin);
            BlockPos quay = stand(l, origin.settlement().dockX, origin.settlement().dockZ, 12);
            if (quay != null) put(p, quay);
            boolean started = Quests.start(p, id);
            p.moveTo(od.x + 30, WorldPlan.SEA_LEVEL - 0.5, od.z);
            SeaJobs.tickPlayer(p);
            SeaJobs.Convoy c = SeaJobs.convoyOf(s, d.characterId, id);
            ShipEntity ship = c == null ? null : SeaJobs.shipOf(s, c);
            if (ship == null) {
                SelfTest.check("sea jobs: a convoy is rebuilt from her saved state when her ship is gone (restart, unloaded chunk)", false, "started " + started + ", no ship");
                return 0;
            }
            ship.setHull(150);
            SeaJobs.tickPlayer(p); // saves her state
            UUID old = ship.getUUID();
            scrap(l, ship);
            SeaJobs.tickPlayer(p);
            ShipEntity back = SeaJobs.shipOf(s, c);
            b[5] = back;
            SelfTest.check("sea jobs: a convoy is rebuilt from her saved state when her ship is gone (restart, unloaded chunk)",
                    back != null && !back.getUUID().equals(old) && Math.abs(back.hull() - 150) < 1 && c.stage == 1 && back.position().distanceTo(od) < 120 && got(p, "job.grandline.convoy.back"),
                    back == null ? "not rebuilt" : String.format("hull %.0f, stage %d, %.0f blocks from where she lay", back.hull(), c.stage, back.position().distanceTo(od)));
            return 0;
        });
        steps.add(s -> {
            if (b[0] == null || b[5] == null) return 0;
            ServerLevel l = s.overworld();
            FakePlayer p = get(b, 4);
            ShipEntity ship = get(b, 5);
            String id = get(b, 0);
            CharacterData d = Characters.get(p);
            long beli = d.beli;
            ship.hurtHull(ship.hull() + 1, null);
            SelfTest.check("sea jobs: if the convoy's merchantman sinks the job fails, with a message", ship.isSinking() && !d.questProgress.containsKey(id)
                            && SeaJobs.convoyOf(s, d.characterId, id) == null && got(p, "job.grandline.convoy.sunk") && got(p, "job.grandline.failed") && d.beli == beli,
                    "sinking " + ship.isSinking() + ", job dropped " + !d.questProgress.containsKey(id) + ", messages " + INBOX.get(p.getUUID()));
            return 200;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            if (b[6] instanceof ShipEntity esc) esc.discard();
            clearNpcShips(l);
            if (b[2] instanceof Island origin) clearBox(l, Ferries.dockOf(origin).x, Ferries.dockOf(origin).z, 200);
            return 0;
        });
    }

    // ------------------------------------------------------------------ the phase 2 fixture (last step of phase 1)

    /** Leave an escort stowed (as when its player logs out at shutdown) for phase 2. */
    static void fixture(Deque<SelfTest.Step> steps) {
        final Object[] b = new Object[4];
        final int[] waited = {0};
        steps.add(s -> {
            ServerLevel l = s.overworld();
            String id = bestJob(s, "escort");
            if (id == null) return 0;
            Island origin = WorldPlan.of(s).byId(QuestRegistry.get(id).island());
            b[0] = id;
            b[1] = origin;
            loadNow(l, origin.settlement().dockX, origin.settlement().dockZ, 3);
            return 1;
        });
        steps.add(s -> {
            if (b[0] == null) return 0;
            ServerLevel l = s.overworld();
            Island origin = get(b, 1);
            Settlement os = origin.settlement();
            if (!ticking(l, os.dockX, os.dockZ) && ++waited[0] < 3000) return -1;
            FakePlayer p = character(l, "Persister", Faction.CIVILIAN);
            BlockPos at = stand(l, os.dockX, os.dockZ, 12);
            if (at == null) return 0;
            put(p, at);
            String id = get(b, 0);
            Quests.start(p, id);
            CharacterData d = Characters.get(p);
            SeaJobs.Escort e = SeaJobs.escortOf(s, d.characterId, id);
            HumanoidNpc m = e == null ? null : SeaJobs.merchantOf(s, e);
            SeaJobs.loggedOut(p); // the server stopping logs everyone out: the merchant is stowed with the job
            String name = m == null || m.getCustomName() == null ? "" : m.getCustomName().getString();
            try {
                Files.writeString(Path.of(FIXTURE), d.characterId + "\n" + id + "\n" + at.asLong() + "\n" + name);
            } catch (Exception ex) {
                GrandLine.LOGGER.warn("sea jobs fixture not written", ex);
            }
            SelfTest.check("sea jobs: an escort in progress is stowed for the restart", e != null && e.stowed() && m != null && m.isRemoved() && !name.isEmpty(),
                    "merchant " + name + ", stowed " + (e != null && e.stowed()));
            release(l);
            return 0;
        });
    }

    // ------------------------------------------------------------------ phase 2

    static void phase2(Deque<SelfTest.Step> steps) {
        final String[] fx = new String[4];
        final int[] waited = {0};
        steps.add(s -> {
            try {
                String[] p = Files.readString(Path.of(FIXTURE)).split("\n");
                System.arraycopy(p, 0, fx, 0, Math.min(4, p.length));
            } catch (Exception e) {
                SelfTest.check("sea jobs: an escort in progress survives a restart", false, "no phase 1 fixture: " + e);
                return 0;
            }
            BlockPos at = BlockPos.of(Long.parseLong(fx[2]));
            loadNow(s.overworld(), at.getX(), at.getZ(), 3);
            return 1;
        });
        steps.add(s -> {
            if (fx[0] == null) return 0;
            ServerLevel l = s.overworld();
            BlockPos at = BlockPos.of(Long.parseLong(fx[2]));
            if (!ticking(l, at.getX(), at.getZ()) && ++waited[0] < 3000) return -1;
            listen();
            UUID ch = UUID.fromString(fx[0]);
            String id = fx[1];
            SeaJobs.Escort saved = SeaJobs.escortOf(s, ch, id);
            boolean wasStowed = saved != null && saved.stowed();
            // the player file brings back the character and its job (fake players are not saved): put them back
            FakePlayer p = character(l, "Persister", Faction.CIVILIAN);
            CharacterData d = Characters.get(p);
            d.characterId = ch;
            d.questProgress.put(id, new CompoundTag());
            put(p, at);
            SeaJobs.tickPlayer(p);
            HumanoidNpc m = saved == null ? null : SeaJobs.merchantOf(s, saved);
            boolean back = m != null && m.getCustomName() != null && m.getCustomName().getString().equals(fx[3]) && m.distanceTo(p) < 6 && m.getTags().contains("gl_escort");
            SelfTest.check("sea jobs: an escort in progress survives a restart (the merchant steps off beside the returning player)", wasStowed && back,
                    "saved " + (saved != null) + ", stowed " + wasStowed + ", merchant " + (m == null ? "none" : m.getCustomName().getString() + String.format(" %.1f blocks away", m.distanceTo(p)))
                            + " (phase 1: " + fx[3] + ")");
            SeaJobs.abandon(p, id);
            SeaJobs.tap = null;
            release(l);
            return 0;
        });
    }

    private SeaJobTests() {}
}
