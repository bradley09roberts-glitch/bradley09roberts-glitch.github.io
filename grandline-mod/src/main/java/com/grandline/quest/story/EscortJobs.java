package com.grandline.quest.story;

import com.grandline.core.Chunks;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import com.grandline.quest.story.JobBoard.StartGift;
import com.grandline.quest.story.SeaJobs.Data;
import com.grandline.quest.story.SeaJobs.Escort;
import com.grandline.registry.ModEntities;
import com.grandline.ship.LivingSea;
import com.grandline.ship.NavalCombat;
import com.grandline.ship.ShipEntity;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Settlement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The escort job's merchant: a real follower. Spawned beside the player when the job is taken; walks after them
 * (a goal of its own that keeps the town stroll from leading it astray), never paths through water, teleports to
 * the player when left far behind on land (as a tamed animal does), takes a seat on the ship the player boards and
 * steps ashore when the player lands. At the destination's dock, talking to them settles the job. The merchant is
 * transient in the world (refused when a chunk reloads it) but saved with the job: stowed when the player logs out,
 * snapshotted every few seconds, and put back beside the player when they return.
 */
final class EscortJobs {
    static final String TAG = "gl_escort", TAG_OF = "gl_escort_of:";
    /** Blocks: teleport to the leader beyond this on land; board the leader's ship within this; stow beyond this. */
    static final double TELEPORT = 16, BOARD = 48, STOW = 160, AT_DOCK = 24;
    /** Retired merchants (job done) and when they leave. */
    private static final Map<UUID, Long> RETIRED = new HashMap<>();
    /** Self-test counters: teleports to the leader, boardings, landings. */
    static int teleports, boardings, landings, reseats;

    static void clear() {
        RETIRED.clear();
    }

    // ================================================================== starting

    static void start(ServerPlayer sp, QuestDef q, StartGift g) {
        CharacterData d = Characters.get(sp);
        Data data = SeaJobs.data(sp.server);
        Escort old = data.escorts.remove(SeaJobs.key(d.characterId, q.id()));
        if (old != null) dismiss(sp.server, old);
        Escort e = new Escort(d.characterId, sp.getUUID(), q.id());
        data.escorts.put(SeaJobs.key(d.characterId, q.id()), e);
        data.setDirty();
        SeaJobs.LEADERS.put(d.characterId, sp);
        SeaJobs.tell(sp, Component.translatable("job.grandline.escort.meet", g.name(), g.where()).withStyle(ChatFormatting.AQUA));
        bring(sp, e, null, false);
    }

    // ================================================================== the merchant

    /** Make (or rebuild from a saved copy) the merchant beside the leader, or aboard the leader's ship. */
    @Nullable
    static HumanoidNpc bring(ServerPlayer sp, Escort e, @Nullable CompoundTag from, boolean fresh) {
        ServerLevel l = sp.serverLevel();
        ShipEntity ship = shipOf(sp);
        BlockPos spot = ship == null ? (standing(sp) ? NotableSpawner.safeSpotNear(l, sp.blockPosition()) : null) : null;
        if (ship == null && spot == null) return null;
        StartGift g = JobBoard.gift(e.quest);
        HumanoidNpc m = ModEntities.HUMANOID_NPC.get().create(l);
        if (m == null || g == null) return null;
        Vec3 at = spot != null ? Vec3.atBottomCenterOf(spot) : ship.position().add(0, ship.cls.freeboard + 0.1, 0);
        if (from != null) {
            m.load(from);
            if (fresh || l.getEntity(m.getUUID()) != null) m.setUUID(UUID.randomUUID()); // a stale copy left in a chunk is refused on load
            m.moveTo(at.x, at.y, at.z, sp.getYRot(), 0);
            m.setDeltaMovement(Vec3.ZERO);
            m.fallDistance = 0;
            m.setTarget(null);
        } else {
            QuestDef q = QuestRegistry.get(e.quest);
            Island origin = q == null || WorldPlan.of(sp.server) == null ? null : WorldPlan.of(sp.server).byId(q.island());
            int tier = origin == null ? 2 : Math.max(2, Math.min(10, origin.tier));
            m.moveTo(at.x, at.y, at.z, sp.getYRot(), 0);
            m.configure("merchant", tier, origin == null ? "" : origin.id, BlockPos.ZERO, "", l.random);
            m.setCustomName(Component.literal(g.name()));
            m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40 + 15 * tier); // stout enough to live through an ambush with help
            m.setHealth(m.getMaxHealth());
            m.addTag(LivingSea.TRANSIENT); // never restored from a chunk: the job keeps the saved copy
            m.addTag(TAG);
            m.addTag(TAG_OF + e.character);
        }
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        dress(m);
        l.addFreshEntity(m);
        if (ship != null) board(ship, m);
        e.entity = m.getUUID();
        e.stowed = null;
        e.missing = 0;
        e.snapshot = save(m);
        SeaJobs.data(sp.server).setDirty();
        return m;
    }

    /** What is not saved with an entity: the follow goal, and keeping out of the water. */
    private static void dress(HumanoidNpc m) {
        m.setPathfindingMalus(PathType.WATER, -1f);
        m.setPathfindingMalus(PathType.WATER_BORDER, 16f);
        m.goalSelector.addGoal(4, new FollowGoal(m));
    }

    private static CompoundTag save(HumanoidNpc m) {
        CompoundTag t = new CompoundTag();
        m.saveWithoutId(t);
        return t;
    }

    @Nullable
    static HumanoidNpc find(MinecraftServer server, @Nullable UUID id) {
        if (id == null) return null;
        for (ServerLevel l : server.getAllLevels()) if (l.getEntity(id) instanceof HumanoidNpc h && h.isAlive()) return h;
        return null;
    }

    /** The leader an escorted merchant follows (online, same character), or null. */
    @Nullable
    static ServerPlayer leaderOf(GLNpc npc) {
        MinecraftServer server = npc.getServer();
        if (server == null) return null;
        for (String t : npc.getTags()) {
            if (!t.startsWith(TAG_OF)) continue;
            UUID ch;
            try {
                ch = UUID.fromString(t.substring(TAG_OF.length()));
            } catch (IllegalArgumentException ex) {
                return null;
            }
            for (Escort e : SeaJobs.data(server).escorts.values())
                if (e.character.equals(ch) && npc.getUUID().equals(e.entity)) return SeaJobs.leader(server, e.character, e.player);
        }
        return null;
    }

    @Nullable
    static Escort byEntity(MinecraftServer server, UUID id) {
        for (Escort e : SeaJobs.data(server).escorts.values()) if (id.equals(e.entity)) return e;
        return null;
    }

    // ================================================================== every half second

    static void tick(ServerPlayer sp, CharacterData d, Data data, Escort e, long now) {
        MinecraftServer server = sp.server;
        HumanoidNpc m = find(server, e.entity);
        if (e.failed) { // died while the leader was away
            data.escorts.remove(SeaJobs.key(e.character, e.quest));
            data.setDirty();
            SeaJobs.fail(sp, e.quest, Component.translatable(e.failKey.isEmpty() ? "job.grandline.escort.died" : e.failKey, name(e)));
            return;
        }
        if (!d.questProgress.containsKey(e.quest)) { // done (talked to at the dock) or dropped some other way
            data.escorts.remove(SeaJobs.key(e.character, e.quest));
            data.setDirty();
            if (m != null) {
                if (e.arrived) retire(m, now);
                else m.discard();
            }
            return;
        }
        e.player = sp.getUUID();
        if (e.away) { // back from a log-out (or a restart)
            if (now - e.logoutAt > SeaJobs.abandonTicks) {
                data.escorts.remove(SeaJobs.key(e.character, e.quest));
                data.setDirty();
                dismiss(server, e);
                SeaJobs.fail(sp, e.quest, Component.translatable("job.grandline.escort.gave_up", name(e)));
                return;
            }
            e.away = false;
            data.setDirty();
        }
        if (m == null) {
            // stowed (log-out, far voyage), never placed yet, or lost in an unloaded chunk: back beside the leader
            boolean lost = e.stowed == null && e.entity != null;
            if (lost && ++e.missing < 6) return;
            CompoundTag from = e.stowed != null ? e.stowed : e.snapshot;
            if (bring(sp, e, from, lost) != null && (from != null))
                SeaJobs.tell(sp, SeaJobs.quoted(name(e), Component.translatable("job.grandline.escort.back")).withStyle(ChatFormatting.AQUA));
            return;
        }
        e.missing = 0;
        if (now - e.snapAt >= 100) {
            e.snapAt = now;
            e.snapshot = save(m);
            data.setDirty();
        }
        if (m.level() != sp.level()) {
            stow(server, data, e);
            return;
        }
        ShipEntity ship = shipOf(sp);
        ShipEntity mShip = m.getVehicle() instanceof ShipEntity s ? s : NavalCombat.shipOf(m);
        double d2 = m.distanceToSqr(sp);
        if (ship != null) {
            if (mShip != ship) {
                if (e.seatedOn != null && e.seatedOn.equals(ship.getUUID()) && reseats++ < 20)
                    com.grandline.GrandLine.LOGGER.info("Escort merchant {} left {} at {} (in water {}, vehicle {}): taken aboard again",
                            name(e), ship.shipName(), m.blockPosition().toShortString(), m.isInWater(), m.getVehicle());
                if (d2 <= BOARD * BOARD) board(ship, m);
                else stow(server, data, e); // left behind on the quay: takes passage (aboard again on the next pass)
            }
            e.seatedOn = m.getVehicle() == ship ? ship.getUUID() : null;
        } else if (mShip != null) {
            if (standing(sp)) land(sp, m);
            else if (d2 > STOW * STOW) stow(server, data, e);
        } else if (d2 > STOW * STOW) {
            stow(server, data, e);
        } else if ((d2 > TELEPORT * TELEPORT || m.isInWater()) && standing(sp)) {
            if (moveNear(sp, m)) teleports++;
        } else if (m.isInWater()) { // knocked or pushed in while the leader swims: back onto the nearest dry footing
            BlockPos dry = NotableSpawner.safeSpotNear(sp.serverLevel(), m.blockPosition());
            if (dry != null) m.teleportTo(dry.getX() + 0.5, dry.getY(), dry.getZ() + 0.5);
        }
        if (!e.arrived && m.isAlive() && m.getVehicle() == null && atDock(m, JobBoard.gift(e.quest))) {
            e.arrived = true;
            data.setDirty();
            StartGift g = JobBoard.gift(e.quest);
            SeaJobs.tell(sp, SeaJobs.quoted(name(e), Component.translatable("job.grandline.escort.arrived", g == null ? "" : g.where())).withStyle(ChatFormatting.GOLD));
        }
    }

    private static String name(Escort e) {
        StartGift g = JobBoard.gift(e.quest);
        return g == null ? "?" : g.name();
    }

    /** The ship a player is aboard (at a station or standing on deck), or null. */
    @Nullable
    static ShipEntity shipOf(ServerPlayer sp) {
        if (sp.getVehicle() instanceof ShipEntity s) return s.isSinking() ? null : s;
        ShipEntity s = NavalCombat.shipOf(sp);
        return s == null || s.isSinking() ? null : s;
    }

    /** On firm ground (not aboard, not swimming): judged from the blocks, so it holds for test players too. */
    static boolean standing(ServerPlayer sp) {
        if (sp.isPassenger() || sp.isSpectator() || sp.isInWater() || shipOf(sp) != null) return false;
        ServerLevel l = sp.serverLevel();
        BlockPos at = sp.blockPosition();
        if (!Chunks.ready(l, at.getX(), at.getZ())) return false;
        BlockPos below = at.below();
        var b = l.getBlockState(below);
        return b.getFluidState().isEmpty() && !b.getCollisionShape(l, below).isEmpty() && l.getBlockState(at).getFluidState().isEmpty();
    }

    private static boolean moveNear(ServerPlayer sp, HumanoidNpc m) {
        BlockPos spot = NotableSpawner.safeSpotNear(sp.serverLevel(), sp.blockPosition());
        if (spot == null) return false;
        m.stopRiding();
        m.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        m.getNavigation().stop();
        m.setDeltaMovement(Vec3.ZERO);
        m.fallDistance = 0;
        return true;
    }

    /** Take a passenger's seat on the leader's ship (or, with every seat taken, a place on deck). */
    static void board(ShipEntity ship, HumanoidNpc m) {
        m.stopRiding();
        m.getNavigation().stop();
        for (int seat = ship.cls.cannons + 1; seat < ship.seatCount(); seat++)
            if (ship.seatPlayer(m, seat)) {
                boardings++;
                return;
            }
        Vec3 deck = ship.toWorld(0, ship.cls.freeboard + 0.05, -ship.cls.length * 0.2);
        m.teleportTo(deck.x, deck.y, deck.z);
        boardings++;
    }

    /** Step ashore beside the leader. */
    static void land(ServerPlayer sp, HumanoidNpc m) {
        if (moveNear(sp, m)) landings++;
    }

    static boolean atDock(Entity m, @Nullable StartGift g) {
        if (g == null) return false;
        WorldPlan plan = WorldPlan.forLevel(m.level());
        Island dest = plan == null ? null : plan.byId(g.target());
        if (dest == null) return false;
        Settlement s = dest.settlement();
        double x = m.getX(), z = m.getZ();
        double r2 = AT_DOCK * AT_DOCK;
        return sq(x - g.x(), z - g.z()) < r2 || (s.hasDock && (sq(x - s.dockX, z - s.dockZ) < r2 || sq(x - s.dockTipX(), z - s.dockTipZ()) < r2));
    }

    private static double sq(double a, double b) {
        return a * a + b * b;
    }

    // ================================================================== talking, dying, leaving

    static void onTalk(ServerPlayer sp, GLNpc npc) {
        Escort e = byEntity(sp.server, npc.getUUID());
        if (e == null) return;
        CharacterData d = Characters.get(sp);
        StartGift g = JobBoard.gift(e.quest);
        String who = name(e);
        if (!e.character.equals(d.characterId)) {
            SeaJobs.tell(sp, SeaJobs.quoted(who, Component.translatable("job.grandline.escort.not_yours")));
            return;
        }
        if (g == null || !atDock(npc, g) || npc.getVehicle() != null) {
            SeaJobs.tell(sp, SeaJobs.quoted(who, Component.translatable("job.grandline.escort.lead_on", g == null ? "" : g.where())).withStyle(ChatFormatting.AQUA));
            return;
        }
        e.arrived = true;
        Quests.progress(sp, "escort", e.quest, 1);
        if (d.questProgress.containsKey(e.quest)) { // the ambush is still on the dock
            SeaJobs.tell(sp, SeaJobs.quoted(who, Component.translatable("job.grandline.escort.clear_dock")).withStyle(ChatFormatting.GOLD));
            return;
        }
        SeaJobs.tell(sp, SeaJobs.quoted(who, Component.translatable("job.grandline.escort.thanks", g.where())).withStyle(ChatFormatting.GOLD));
        Data data = SeaJobs.data(sp.server);
        data.escorts.remove(SeaJobs.key(e.character, e.quest));
        data.setDirty();
        if (npc instanceof HumanoidNpc h) retire(h, sp.server.overworld().getGameTime());
        CharacterManager.markDirty(sp);
    }

    /** The merchant was killed: the job fails (now, or when the leader comes back). */
    static void died(GLNpc npc) {
        MinecraftServer server = npc.getServer();
        if (server == null) return;
        Escort e = byEntity(server, npc.getUUID());
        if (e == null) return;
        Data data = SeaJobs.data(server);
        ServerPlayer sp = SeaJobs.leader(server, e.character, e.player);
        if (sp == null) {
            e.failed = true;
            e.failKey = "job.grandline.escort.died";
            e.entity = null;
            data.setDirty();
            return;
        }
        data.escorts.remove(SeaJobs.key(e.character, e.quest));
        data.setDirty();
        SeaJobs.fail(sp, e.quest, Component.translatable("job.grandline.escort.died", name(e)));
    }

    /** Take the merchant out of the world, keeping a copy with the job (log-out, a voyage beyond reach). */
    static void stow(MinecraftServer server, Data data, Escort e) {
        HumanoidNpc m = find(server, e.entity);
        if (m != null) {
            e.stowed = save(m);
            e.snapshot = e.stowed;
            m.stopRiding();
            m.discard();
        }
        data.setDirty();
    }

    /** The job ended without arriving: the merchant goes their own way. */
    static void dismiss(MinecraftServer server, Escort e) {
        HumanoidNpc m = find(server, e.entity);
        if (m != null) m.discard();
    }

    /** The job is done: the merchant stays on the dock a little while, then goes about their business. */
    static void retire(HumanoidNpc m, long now) {
        m.removeTag(TAG);
        for (String t : List.copyOf(m.getTags())) if (t.startsWith(TAG_OF)) m.removeTag(t);
        m.setAllegiance(Faction.CIVILIAN, m.blockPosition());
        m.getNavigation().stop();
        RETIRED.put(m.getUUID(), now + 600);
    }

    static void tickRetired(MinecraftServer server, long now) {
        if (RETIRED.isEmpty() || now % 20 != 7) return;
        Iterator<Map.Entry<UUID, Long>> it = RETIRED.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            if (en.getValue() > now) continue;
            HumanoidNpc m = find(server, en.getKey());
            if (m != null) m.discard();
            it.remove();
        }
    }

    static boolean retired(UUID id) {
        return RETIRED.containsKey(id);
    }

    // ================================================================== the follow goal

    /**
     * Walk after the leader while they are about (combat and fleeing come first). It runs whenever the leader is
     * near, so the town stroll never leads the merchant off; it never paths through water (see {@link #dress}).
     */
    static final class FollowGoal extends Goal {
        private final HumanoidNpc npc;
        private ServerPlayer leader;
        private int repath;

        FollowGoal(HumanoidNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (npc.level().isClientSide || npc.isPassenger() || npc.getTarget() != null || npc.flag(GLNpc.FLAG_SURRENDER)) return false;
            leader = leaderOf(npc);
            return leader != null && leader.level() == npc.level();
        }

        @Override
        public boolean canContinueToUse() {
            return leader != null && !leader.isRemoved() && leader.level() == npc.level() && !npc.isPassenger() && npc.getTarget() == null;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            npc.getNavigation().stop();
            leader = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (leader == null) return;
            npc.getLookControl().setLookAt(leader, 30, 30);
            double d2 = npc.distanceToSqr(leader);
            if (d2 < 3 * 3) {
                npc.getNavigation().stop();
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                // only after a leader on firm ground: one who swims off (or stands on a deck) is waited for on the shore,
                // never followed to the water's edge and over it (boarding a ship is handled by the job's tick)
                if (standing(leader)) npc.getNavigation().moveTo(leader, d2 > 10 * 10 ? 1.25 : 1.0);
                else npc.getNavigation().stop();
            }
        }
    }

    private EscortJobs() {}
}
