package com.grandline.quest.story;

import com.grandline.core.Chunks;
import com.grandline.navigation.Ferries;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import com.grandline.quest.story.JobBoard.StartGift;
import com.grandline.quest.story.SeaJobs.Convoy;
import com.grandline.quest.story.SeaJobs.Data;
import com.grandline.ship.*;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The board's sea battles. A named pirate ship ("sink the ...") is put to sea from the living sea's own ships when a
 * hunter comes within {@link #SIGHT} of where she was last seen (inside the simulation distance, on loaded open
 * water, and only with room under the shared NPC ship cap); sinking or taking her completes the job for whoever hunts
 * her, and a Marine who brings her in as a prize is paid a bonus. A convoy is a merchantman captained from here
 * (the living sea's merchant AI is switched off for her): she waits off the dock until her escort is at sea beside
 * her, sails for the destination's dock with the ferries' course-keeping (heaving to whenever her escort falls
 * behind), meets pirates partway and completes the job when she makes port.
 */
final class SeaBattleJobs {
    static final String SHIP_TAG = "gl_seajob_ship:", CONVOY_TAG = "gl_seajob_convoy", RAIDER_TAG = "gl_seajob_raider";
    /** A named ship is put to sea for a hunter this close (blocks) to where she was last seen. */
    static final double SIGHT = 240;
    /** Convoy: her escort keeps within this; a leader farther than LOST for LOST_TICKS has lost her; she is in port within ARRIVE. */
    static final double ESCORT_RANGE = 110, LOST = 400, ARRIVE = 34;
    static final long LOST_TICKS = 2400;
    /** Raiders fall on a convoy once this share of the way is behind her. */
    static final double RAID_AT = 0.35;

    private static final Map<String, UUID> NAMED = new HashMap<>();
    private static final Map<String, Long> NEXT_TRY = new HashMap<>();
    private static final Map<String, Set<ServerPlayer>> HUNTERS = new HashMap<>();
    /** Self-test: named ships put to sea, and attempts refused for want of room under the cap. */
    static int namedSpawned, refusedByCap;

    static void clear() {
        NAMED.clear();
        NEXT_TRY.clear();
        HUNTERS.clear();
    }

    static void clearTimers() {
        NEXT_TRY.clear();
    }

    // ================================================================== sink the named ship

    static void tickSink(ServerPlayer sp, String quest, StartGift g, long now) {
        ServerLevel l = sp.server.overworld();
        if (sp.level() != l) return;
        String base = QuestRegistry.baseId(quest);
        Set<ServerPlayer> hunters = HUNTERS.computeIfAbsent(base, k -> new LinkedHashSet<>());
        hunters.removeIf(Entity::isRemoved);
        hunters.add(sp);
        if (named(l, base) != null) return;
        if (Math.hypot(sp.getX() - g.x(), sp.getZ() - g.z()) > SIGHT || NEXT_TRY.getOrDefault(base, 0L) > now) return;
        NEXT_TRY.put(base, now + 100);
        if (SeaEncounters.countNpcShips(l) >= SeaEncounters.cap(l.players().size())) { // the one server-wide cap on NPC ships
            refusedByCap++;
            return;
        }
        ShipClass cls = ShipClass.byId(g.target());
        Vec3 pos = spotToward(l, sp.position(), new Vec3(g.x(), 0, g.z()), cls);
        if (pos == null) return;
        ShipEntity ship = SeaEncounters.spawnShip(l, pos, yawTo(pos, sp.position()), cls, LivingSea.PIRATE, (int) Math.max(1, g.bounty()), g.name());
        if (ship == null) return;
        ship.addTag(SHIP_TAG + base);
        if (SeaJobs.keepShips) ship.noDespawn = true;
        NAMED.put(base, ship.getUUID());
        namedSpawned++;
        for (ServerPlayer p : hunters)
            if (p.level() == l && p.distanceToSqr(ship) < 320 * 320)
                SeaJobs.tell(p, Component.translatable("job.grandline.sink.sighted", g.name()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }

    /** The named ship of a job afloat now (alive, not sinking, not yet taken), or null. */
    @Nullable
    static ShipEntity named(ServerLevel l, String base) {
        UUID u = NAMED.get(base);
        if (u == null) return null;
        if (l.getEntity(u) instanceof ShipEntity s && s.isAlive() && !s.isSinking() && s.getTags().contains(SHIP_TAG + base)) return s;
        NAMED.remove(base);
        return null;
    }

    /** Open water inside the simulation distance, toward where she was seen (or round about when the hunter is there). */
    @Nullable
    static Vec3 spotToward(ServerLevel l, Vec3 from, Vec3 area, ShipClass cls) {
        int sim = l.getServer().getPlayerList().getSimulationDistance();
        double far = Mth.clamp((sim - 1) * 16 - 30, 48, 140);
        double dx = area.x - from.x, dz = area.z - from.z, dist = Math.hypot(dx, dz);
        double reach = Math.max(Math.min(dist, far), Math.min(70, far));
        double a0 = dist < 1 ? l.random.nextDouble() * Math.PI * 2 : Math.atan2(dz, dx);
        // toward where she was seen first, then all round the hunter, nearer in
        for (double r : new double[]{reach, far, Math.max(40, far * 0.7)})
            for (int k = 0; k < 14; k++) {
                double a = a0 + (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * 0.45;
                Vec3 p = LivingSea.openWater(l, from.x + Math.cos(a) * r, from.z + Math.sin(a) * r, cls.length / 2 + 4);
                if (p != null) return p;
            }
        // where she was seen, when that lies within reach
        if (dist < far + 40)
            for (int k = 0; k < 8; k++) {
                Vec3 p = LivingSea.openWater(l, area.x + Math.cos(k * 0.8) * 20, area.z + Math.sin(k * 0.8) * 20, cls.length / 2 + 4);
                if (p != null && p.distanceToSqr(from.x, p.y, from.z) > 30 * 30) return p;
            }
        return null;
    }

    static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    /** Whoever hunts this ship and is near (or fought her) has the job done. */
    private static void credit(ServerLevel sl, ShipEntity ship, String base, @Nullable ServerPlayer taker) {
        Set<ServerPlayer> who = new LinkedHashSet<>();
        if (taker != null) who.add(taker);
        who.addAll(sl.getServer().getPlayerList().getPlayers());
        who.addAll(HUNTERS.getOrDefault(base, Set.of()));
        for (ServerPlayer p : who) {
            if (p.isRemoved() || p.level() != sl || SeaJobs.active(Characters.get(p), base) == null) continue;
            if (p != taker && p.distanceToSqr(ship) > 320 * 320 && !ship.life.fighters().contains(p.getUUID())) continue;
            SeaJobs.tell(p, Component.translatable(taker == null ? "job.grandline.sink.sunk" : "job.grandline.sink.taken", shipName(base, ship)).withStyle(ChatFormatting.GOLD));
            Quests.progress(p, SeaJobs.SINK, base, 1);
        }
    }

    private static String shipName(String base, ShipEntity ship) {
        StartGift g = JobBoard.gift(base);
        return g != null ? g.name() : ship.shipName();
    }

    static void onSinking(ServerLevel sl, ShipEntity ship) {
        for (String t : List.copyOf(ship.getTags())) {
            if (t.startsWith(SHIP_TAG)) {
                String base = t.substring(SHIP_TAG.length());
                ship.removeTag(t);
                NAMED.remove(base);
                credit(sl, ship, base, null);
            } else if (t.equals(CONVOY_TAG)) lost(sl.getServer(), ship, "job.grandline.convoy.sunk");
        }
    }

    static void onCaptured(ServerPlayer sp, ShipEntity ship) {
        for (String t : List.copyOf(ship.getTags())) {
            if (t.startsWith(SHIP_TAG)) {
                String base = t.substring(SHIP_TAG.length());
                ship.removeTag(t);
                NAMED.remove(base);
                CharacterData d = Characters.get(sp);
                String q = SeaJobs.active(d, base);
                QuestDef def = q == null ? null : QuestRegistry.get(q);
                if (def != null && "marine".equals(QuestRegistry.variantFaction(q))) { // the Marines pay more for a prize brought in
                    long bonus = def.beli() / 2;
                    d.beli += bonus;
                    d.addReputation(Faction.MARINE.id(), 15);
                    CharacterManager.markDirty(sp);
                    SeaJobs.tell(sp, Component.translatable("job.grandline.sink.prize_bonus", shipName(base, ship), bonus).withStyle(ChatFormatting.GOLD));
                }
                credit((ServerLevel) ship.level(), ship, base, sp);
            } else if (t.equals(CONVOY_TAG)) lost(sp.server, ship, "job.grandline.convoy.taken");
        }
    }

    // ================================================================== convoy

    static void startConvoy(ServerPlayer sp, QuestDef q, StartGift g) {
        CharacterData d = Characters.get(sp);
        Data data = SeaJobs.data(sp.server);
        WorldPlan plan = WorldPlan.of(sp.server);
        Island origin = plan == null ? null : plan.byId(q.island());
        if (origin == null) return;
        Convoy old = data.convoys.remove(SeaJobs.key(d.characterId, q.id()));
        if (old != null) release(sp.server, old, false);
        Convoy c = new Convoy(d.characterId, sp.getUUID(), q.id());
        Vec3 harbour = anchorage(origin);
        c.x = harbour.x;
        c.z = harbour.z;
        data.convoys.put(SeaJobs.key(d.characterId, q.id()), c);
        data.setDirty();
        SeaJobs.LEADERS.put(d.characterId, sp);
        SeaJobs.tell(sp, Component.translatable("job.grandline.convoy.start", g.name(), StoryContext.islandName(origin), g.where(), (int) harbour.x, (int) harbour.z)
                .withStyle(ChatFormatting.AQUA));
        tickConvoy(sp, d, data, c, sp.server.overworld().getGameTime());
    }

    /**
     * Where a convoy's merchantman waits: at anchor in open water just beyond the island's waters, out along the dock
     * (where the ferries lie), so she never has to thread the harbour; the escort sails out to meet her.
     */
    static Vec3 anchorage(Island i) {
        var st = i.settlement();
        if (!st.hasDock) return Ferries.dockOf(i);
        double ux = st.dockDirX / 1000.0, uz = st.dockDirZ / 1000.0, ul = Math.max(1e-6, Math.hypot(ux, uz));
        double out = i.radius * 1.25 + 40;
        return new Vec3(i.x + ux / ul * out, WorldPlan.SEA_LEVEL + 0.9, i.z + uz / ul * out);
    }

    @Nullable
    static ShipEntity convoyShip(ServerLevel l, Convoy c) {
        if (c.ship == null) return null;
        return l.getEntity(c.ship) instanceof ShipEntity s && s.isAlive() && !s.isSinking() && s.getTags().contains(CONVOY_TAG) ? s : null;
    }

    /** Every half second for the convoy's leader: launch or rebuild her, follow her escort, pirates, failures. */
    static void tickConvoy(ServerPlayer sp, CharacterData d, Data data, Convoy c, long now) {
        MinecraftServer server = sp.server;
        ServerLevel l = server.overworld();
        StartGift g = JobBoard.gift(c.quest);
        String key = SeaJobs.key(c.character, c.quest);
        if (c.failed) {
            data.convoys.remove(key);
            data.setDirty();
            SeaJobs.fail(sp, c.quest, Component.translatable(c.failKey, g == null ? "?" : g.name()));
            return;
        }
        if (g == null || !d.questProgress.containsKey(c.quest)) {
            data.convoys.remove(key);
            data.setDirty();
            release(server, c, true);
            return;
        }
        c.player = sp.getUUID();
        if (c.away) {
            if (now - c.logoutAt > SeaJobs.abandonTicks) {
                data.convoys.remove(key);
                data.setDirty();
                release(server, c, true);
                SeaJobs.fail(sp, c.quest, Component.translatable("job.grandline.convoy.gave_up", g.name()));
                return;
            }
            c.away = false;
            data.setDirty();
        }
        ShipEntity ship = convoyShip(l, c);
        double away = sp.level() != l ? Double.MAX_VALUE : Math.hypot(sp.getX() - (ship != null ? ship.getX() : c.x), sp.getZ() - (ship != null ? ship.getZ() : c.z));
        if (c.stage > 0 && away > LOST) { // she has been left behind
            if (c.awaySince < 0) c.awaySince = now;
            else if (now - c.awaySince > LOST_TICKS) {
                data.convoys.remove(key);
                data.setDirty();
                release(server, c, true);
                SeaJobs.fail(sp, c.quest, Component.translatable("job.grandline.convoy.lost", g.name()));
                return;
            }
        } else c.awaySince = -1;
        if (ship == null) {
            // not launched yet, or gone with an unloaded chunk or a restart: put her (back) to sea where she lay
            if (away < 200) launch(sp, l, data, c, g);
            return;
        }
        c.x = ship.getX();
        c.z = ship.getZ();
        c.yaw = ship.getYRot();
        c.hull = ship.hull();
        if (now % 100 < 10) data.setDirty();
        if (LivingSea.beaten(ship)) { // boarders cut down her crew
            lost(server, ship, "job.grandline.convoy.overrun");
            return;
        }
        // her merchant AI is off, so keep her hands aboard as the living sea's deck discipline does: anyone who fell or
        // was left behind (not fighting on another deck) climbs back on board
        for (UUID u : NavalCombat.npcCrew(ship))
            if (l.getEntity(u) instanceof com.grandline.npc.GLNpc h && h.isAlive() && !h.isPassenger() && !ship.carries(h) && NavalCombat.shipOf(h) == null
                    && h.distanceToSqr(ship) < 120 * 120) {
                Vec3 deck = ship.toWorld(0, ship.cls.freeboard + 0.05, (l.random.nextDouble() - 0.5) * ship.cls.length * 0.4);
                h.teleportTo(deck.x, deck.y, deck.z);
                h.setDeltaMovement(Vec3.ZERO);
                h.fallDistance = 0;
                h.getNavigation().stop();
            }
        ShipEntity escort = EscortJobs.shipOf(sp);
        boolean was = c.escorted;
        c.escorted = escort != null && escort != ship && escort.distanceToSqr(ship) < ESCORT_RANGE * ESCORT_RANGE;
        if (c.stage == 1 && c.escorted) {
            c.stage = 2;
            data.setDirty();
            SeaJobs.tell(sp, Component.translatable("job.grandline.convoy.under_way", g.name(), g.where()).withStyle(ChatFormatting.AQUA));
        } else if (c.stage == 2 && was && !c.escorted)
            SeaJobs.tell(sp, Component.translatable("job.grandline.convoy.waiting", g.name()).withStyle(ChatFormatting.GRAY));
        if (c.stage == 2 && !c.raided && c.total > 0 && now >= c.raidNext && remaining(ship, g) < c.total * (1 - RAID_AT)) raid(sp, l, c, ship, g, now);
    }

    private static double remaining(ShipEntity ship, StartGift g) {
        return Math.hypot(ship.getX() - g.x(), ship.getZ() - g.z());
    }

    /** Put the merchantman to sea off the origin's dock (first time) or where she lay (after a restart or an unloaded chunk). */
    @Nullable
    private static ShipEntity launch(ServerPlayer sp, ServerLevel l, Data data, Convoy c, StartGift g) {
        WorldPlan plan = WorldPlan.of(sp.server);
        QuestDef q = QuestRegistry.get(c.quest);
        Island origin = plan == null || q == null ? null : plan.byId(q.island());
        Island dest = plan == null ? null : plan.byId(g.target());
        if (origin == null || dest == null || !Chunks.ready(l, (int) c.x, (int) c.z)) return null;
        ShipClass cls = origin.zone.isBlue() ? ShipClass.CARAVEL : ShipClass.BRIGANTINE;
        Vec3 pos = LivingSea.openWater(l, c.x, c.z, cls.length / 2 + 2);
        if (pos == null) pos = Shipyard.findWaterPublic(l, BlockPos.containing(c.x, WorldPlan.SEA_LEVEL, c.z));
        if (pos == null) return null;
        Vec3 dock = new Vec3(g.x(), pos.y, g.z());
        boolean first = c.stage == 0;
        ShipEntity s = SeaEncounters.spawnShip(l, pos, first ? yawTo(pos, dock) : c.yaw, cls, LivingSea.MERCHANT, Math.max(1, origin.zone.dangerTier), g.name());
        if (s == null) return null;
        s.npcAiOff = true; // captained from here, not by the merchant traffic's AI
        s.noDespawn = true;
        s.life.route = dest.id;
        s.life.origin = origin.id;
        s.addTag(CONVOY_TAG);
        s.setSail(0);
        s.setAnchored(true);
        if (c.hull > 0) s.setHull(Math.min(c.hull, s.maxHull()));
        if (first) c.total = Math.hypot(pos.x - dock.x, pos.z - dock.z);
        c.stage = 1;
        c.escorted = false;
        c.ship = s.getUUID();
        c.x = pos.x;
        c.z = pos.z;
        data.setDirty();
        SeaJobs.tell(sp, Component.translatable(first ? "job.grandline.convoy.ready" : "job.grandline.convoy.back", g.name()).withStyle(ChatFormatting.AQUA));
        return s;
    }

    /** Pirates fall on the convoy: a ship from ahead and abeam, within the shared cap (tried for a minute, then they never came). */
    private static void raid(ServerPlayer sp, ServerLevel l, Convoy c, ShipEntity ship, StartGift g, long now) {
        c.raidNext = now + 100;
        if (++c.raidTries > 12) {
            c.raided = true;
            return;
        }
        if (SeaEncounters.countNpcShips(l) >= SeaEncounters.cap(l.players().size())) {
            refusedByCap++;
            return;
        }
        SeaZone zone = WorldLayout.zoneAt(ship.getBlockX(), ship.getBlockZ());
        ShipClass pc = SeaJobs.pirateClass(zone, Math.max(1, zone.dangerTier));
        Vec3 fwd = ship.heading(), side = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 pos = null;
        for (int k = 0; k < 6 && pos == null; k++) {
            Vec3 p = ship.position().add(fwd.scale(60)).add(side.scale((k % 2 == 0 ? 1 : -1) * (45 + 15 * (k / 2))));
            pos = LivingSea.openWater(l, p.x, p.z, pc.length / 2 + 4);
        }
        if (pos == null) return;
        ShipEntity r = SeaEncounters.spawnShip(l, pos, yawTo(pos, ship.position()), pc, LivingSea.PIRATE, Math.max(1, zone.dangerTier), PeopleNames.crew(l.random));
        if (r == null) return;
        r.addTag(RAIDER_TAG);
        if (SeaJobs.keepShips) r.noDespawn = true;
        c.raider = r.getUUID();
        c.raided = true;
        SeaJobs.data(sp.server).setDirty();
        SeaJobs.tell(sp, Component.translatable("job.grandline.convoy.pirates", r.shipName(), g.name()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    /** Every tick: the convoys' helm (she holds while waiting or when her escort falls behind). */
    static void drive(MinecraftServer server, Data data, long now) {
        ServerLevel l = server.overworld();
        List<Convoy> arrived = null;
        for (Convoy c : data.convoys.values()) {
            if (c.ship == null || c.stage == 0 || !(l.getEntity(c.ship) instanceof ShipEntity ship) || ship.isSinking()) continue;
            StartGift g = JobBoard.gift(c.quest);
            WorldPlan plan = WorldPlan.forLevel(l);
            Island dest = g == null || plan == null ? null : plan.byId(g.target());
            if (dest == null) continue;
            boolean led = SeaJobs.leader(server, c.character, c.player) != null;
            if (c.stage < 2 || !c.escorted || !led || LivingSea.beaten(ship)) {
                ship.setSail(0);
                ship.setAnchored(c.stage < 2);
                continue;
            }
            Vec3 dock = Ferries.dockOf(dest);
            double d = Math.hypot(dock.x - ship.getX(), dock.z - ship.getZ());
            // as the ferries do: a hull held off the dock point by the coast, or working about the harbour for half a minute, has made port
            if (dest.distance(ship.getBlockX(), ship.getBlockZ()) < dest.radius * 1.25 + 40) c.near++;
            if (d < 70 && ship.speed() < 0.02f) c.pinned++;
            else c.pinned = 0;
            if (d < ARRIVE || c.pinned > 100 || c.near > 600) {
                if (arrived == null) arrived = new ArrayList<>();
                arrived.add(c);
                continue;
            }
            ship.setAnchored(false);
            float diff = Ferries.helm(ship, l, dock, dest.id);
            ship.setSail(Math.abs(diff) > 70 || d < 90 ? 1 : 2);
        }
        if (arrived != null) for (Convoy c : arrived) arrive(server, data, c);
    }

    /** She made port: the job is done and she goes about her trade. */
    private static void arrive(MinecraftServer server, Data data, Convoy c) {
        data.convoys.remove(SeaJobs.key(c.character, c.quest));
        data.setDirty();
        release(server, c, true);
        ServerPlayer sp = SeaJobs.leader(server, c.character, c.player);
        StartGift g = JobBoard.gift(c.quest);
        if (sp == null || g == null) return;
        SeaJobs.tell(sp, Component.translatable("job.grandline.convoy.arrived", g.name(), g.where()).withStyle(ChatFormatting.GOLD));
        Quests.progress(sp, SeaJobs.CONVOY, QuestRegistry.baseId(c.quest), 1);
    }

    /** The merchantman went down, was overrun or taken: the job fails. */
    private static void lost(MinecraftServer server, ShipEntity ship, String why) {
        Data data = SeaJobs.data(server);
        for (Convoy c : List.copyOf(data.convoys.values())) {
            if (!ship.getUUID().equals(c.ship)) continue;
            release(server, c, true);
            ServerPlayer sp = SeaJobs.leader(server, c.character, c.player);
            StartGift g = JobBoard.gift(c.quest);
            if (sp == null) { // told when they are back
                c.failed = true;
                c.failKey = why;
                c.ship = null;
            } else {
                data.convoys.remove(SeaJobs.key(c.character, c.quest));
                SeaJobs.fail(sp, c.quest, Component.translatable(why, g == null ? ship.shipName() : g.name()));
            }
            data.setDirty();
        }
    }

    /** Hand the merchantman back to the living sea ({@code sailOn}), or take her out of the world. */
    static void release(MinecraftServer server, Convoy c, boolean sailOn) {
        ServerLevel l = server.overworld();
        if (c.ship == null || !(l.getEntity(c.ship) instanceof ShipEntity ship)) return;
        ship.removeTag(CONVOY_TAG);
        ship.npcAiOff = false;
        ship.noDespawn = false;
        if (sailOn || ship.isSinking()) return;
        for (UUID u : new ArrayList<>(NavalCombat.npcCrew(ship))) if (l.getEntity(u) != null) l.getEntity(u).discard();
        NavalCombat.unlink(ship);
        ship.discard();
    }

    private SeaBattleJobs() {}
}
